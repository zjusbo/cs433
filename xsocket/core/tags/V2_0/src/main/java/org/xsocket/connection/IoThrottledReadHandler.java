/*
 *  Copyright (c) xsocket.org, 2006 - 2008. All rights reserved.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Please refer to the LGPL license at: http://www.gnu.org/copyleft/lesser.txt
 * The latest copy of this software may be found on http://www.xsocket.org/
 */
package org.xsocket.connection;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;




/**
 * Delayed read IO handler
 *
 * @author grro@xsocket.org
 */
final class IoThrottledReadHandler extends IoChainableHandler {

	private static final Logger LOG = Logger.getLogger(IoThrottledReadHandler.class.getName());

	private static final int CHECK_PERIOD_MILLIS = 500;

	// event handling
	private final IOEventHandler ioEventHandler = new IOEventHandler();
	
	// timer handling
	private int readBytesPerSec = INonBlockingConnection.UNLIMITED;
	private final AtomicInteger currentReceived = new AtomicInteger();
	private TimerTask readControlTask = null;
	
	// suspend flag 
	private boolean isSuspended = false;
	
	private int orgReadBufferSize = 0;




	/**
	 * constructor
	 * @param successor  the successor
	 */
	IoThrottledReadHandler(IoChainableHandler successor) {
		super(successor);
		
		readControlTask = new ReadControlTask(this);
		IoProvider.getTimer().schedule(readControlTask, CHECK_PERIOD_MILLIS, CHECK_PERIOD_MILLIS);
	}



	/**
	 * {@inheritDoc}
	 */
	public void init(IIoHandlerCallback callbackHandler) throws IOException {
		setPreviousCallback(callbackHandler);
		getSuccessor().init(ioEventHandler);
		
		orgReadBufferSize = (Integer) getSuccessor().getOption(IoProvider.SO_RCVBUF);
		
		getSocketHandler().setRetryRead(false);
	}


	/**
	 * {@inheritDoc}
	 */
	public boolean reset() {
		readBytesPerSec = INonBlockingConnection.UNLIMITED;
		if (readControlTask != null) {
			readControlTask.cancel();
			readControlTask = null;
		}
		
		getSocketHandler().setRetryRead(true);
		
		try {
			getSuccessor().setOption(IoProvider.SO_RCVBUF, orgReadBufferSize);
			getSuccessor().resumeRead();
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("Error occured by resuming read " + ioe.toString());
			}
		}

		return super.reset();
	}


	/**
	 * set the read rate in sec
	 *
	 * @param readRateSec  the read rate
	 */
	void setReadRateSec(int readRateSec) throws IOException {
		
		if (readRateSec < orgReadBufferSize) {
			getSuccessor().setOption(IoProvider.SO_RCVBUF, readRateSec);
		} else {
			getSuccessor().setOption(IoProvider.SO_RCVBUF, orgReadBufferSize);
		}
		this.readBytesPerSec = readRateSec;
	}



	/**
	 * {@inheritDoc}
	 */
	public void close(boolean immediate) throws IOException {
		if (!immediate) {
			flushOutgoing();
		}

		getSuccessor().close(immediate);
	}


	public void write(ByteBuffer[] buffers) throws ClosedChannelException, IOException {
		getSuccessor().write(buffers);
	}
	
	@Override
	public void flushOutgoing() throws IOException {
		getSuccessor().flushOutgoing();
	}

	

	/**
	 * {@inheritDoc}
	 */
	public void setPreviousCallback(IIoHandlerCallback callbackHandler) {
		super.setPreviousCallback(callbackHandler);
		getSuccessor().setPreviousCallback(ioEventHandler);
	}

	
	private IoSocketHandler getSocketHandler() {
		IoChainableHandler successor = this;
		do {
			successor = getSuccessor();
			if (successor != null) {
				if (successor instanceof IoSocketHandler) {
					return (IoSocketHandler) successor;
				}
			}
			
		} while (successor != null);

		return null;
	}
	

	private static final class ReadControlTask extends TimerTask {

		private WeakReference<IoThrottledReadHandler> ioThrottledReadHandlerRef = null;
		
		private int outstanding = 0;
		
		public ReadControlTask(IoThrottledReadHandler ioThrottledReadHandler) {
			ioThrottledReadHandlerRef = new WeakReference<IoThrottledReadHandler>(ioThrottledReadHandler);
		}
		
		
		@Override
		public void run() {
			
			IoThrottledReadHandler ioThrottledReadHandler = ioThrottledReadHandlerRef.get();
			
			if (ioThrottledReadHandler == null) {
				cancel();
				
			} else  {
				outstanding += ioThrottledReadHandler.currentReceived.getAndSet(0);
				
				if (outstanding > 0) {
					double periodSec = ((double) CHECK_PERIOD_MILLIS) / 1000;
					int delta = (int) (ioThrottledReadHandler.readBytesPerSec * periodSec);
					outstanding -= delta;
				}
				
				if (outstanding < 0) {
					outstanding = 0;
				}
				
				if (outstanding > 0) {
					if (!ioThrottledReadHandler.isSuspended) {
						try {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("suspending read");
							}
							ioThrottledReadHandler.getSuccessor().suspendRead();
							ioThrottledReadHandler.isSuspended = true;

						} catch (IOException ioe) {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("Error occured by suspendig read " + ioe.toString());
							}
						}
					}
				} else {
					
					if (ioThrottledReadHandler.isSuspended) {
						try {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("resuming read");
							}
							ioThrottledReadHandler.getSuccessor().resumeRead();
							ioThrottledReadHandler.isSuspended = false;
							
						} catch (IOException ioe) {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("Error occured by resuming read " + ioe.toString());
							}
						}
					}
				}
				
			}
		}
	}
	
	

	private final class IOEventHandler implements IIoHandlerCallback {


		public void onData(ByteBuffer[] data) {
			
			int read = 0;
			
			for (ByteBuffer byteBuffer : data) {
				read += byteBuffer.remaining();
			}
			
			currentReceived.addAndGet(read);
			
			
			getPreviousCallback().onData(data);
		}
		

		public void onConnect() {
			getPreviousCallback().onConnect();
		}


		public void onWriteException(IOException ioException, ByteBuffer data) {
			getPreviousCallback().onWriteException(ioException, data);
		}
		
		public void onWritten(ByteBuffer data) {
			getPreviousCallback().onWritten(data);
		}

		public void onDisconnect() {
			getPreviousCallback().onDisconnect();
		}

		public void onConnectionAbnormalTerminated() {
			getPreviousCallback().onConnectionAbnormalTerminated();
		}
	}
}
