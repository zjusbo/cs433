// $Id: MemoryManager.java 1304 2007-06-02 13:26:34Z grro $
/*
 *  Copyright (c) xsocket.org, 2006 - 2007. All rights reserved.
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
package org.xsocket.stream.io.mina;



import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoFuture;
import org.apache.mina.common.IoFutureListener;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.WriteFuture;
import org.xsocket.ClosedConnectionException;
import org.xsocket.DataConverter;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.io.spi.IHandlerIoProvider;
import org.xsocket.stream.io.spi.IIoHandler;
import org.xsocket.stream.io.spi.IIoHandlerCallback;


/**
*
*  
* @author grro@xsocket.org
*/
final class IoHandler implements IIoHandler {
	
	private static final Logger LOG = Logger.getLogger(IoHandler.class.getName());
	
	/**
	 * TODOS:
	 * - detecting write execption and notifying it
	 *    
	 */
	
	
	private static final Timer TIMER = new Timer("TrottledWriteTimer", true);
	
	
	// queues
	private final LinkedList<DelayQueueEntry> sendQueue = new LinkedList<DelayQueueEntry>(); 
	private LinkedList<ByteBuffer> readQueue = new LinkedList<ByteBuffer>(); 
	

	
	
	private String id = null;
	private IoSession session = null;

	private IIoHandlerCallback callback = null;

	private boolean closing = false;
	

	
	
	// timer handling
	private boolean isThrottledWrite = false;
	private int sendBytesPerSec = INonBlockingConnection.UNLIMITED;
	private TimerTask delayedDelivererTask = null;


	private final WriteFutureListener writeFutureListener = new WriteFutureListener();


	
	
	public IoHandler(IoSession session, String id) {
		this.session = session;
		this.id = id;
	}
	
	
	public int getPendingWriteDataSize() {
		return 0;
	}
	
	
	public int getConnectionTimeoutSec() {
		return 0;
	}
	
	public void setConnectionTimeoutSec(int timeout) {
		
	}
	 
	
	/**
	 * set the write rate in sec
	 * 
	 * @param writeRateSec  the write rate
	 */
	void setWriteRateSec(int writeRateSec) {
		if (writeRateSec == IHandlerIoProvider.UNLIMITED) {
			isThrottledWrite = false;
		} else {
			isThrottledWrite = true;
			this.sendBytesPerSec = writeRateSec;
		}
	}
	
	
	public void writeOutgoing(ByteBuffer buffer) throws IOException {
		
		if (isThrottledWrite) {
			// append to delay queue
			int size = buffer.remaining();
			if (size > 0) {
				
				DelayQueueEntry delayQueueEntry = new DelayQueueEntry(buffer.duplicate(), sendBytesPerSec);
				
		 		synchronized (sendQueue) {
		 			sendQueue.offer(delayQueueEntry);
		 		}
			}

			// create delivery task if not exists
			if (delayedDelivererTask == null) {
				delayedDelivererTask = new DeliveryTask();			
				TIMER.schedule(delayedDelivererTask, 0, 500);
			}

		} else {
			reallyWriteOutgoing(buffer);
		}
	}

	
	
	public void reallyWriteOutgoing(ByteBuffer buffer) throws ClosedConnectionException, IOException {

		// TODO handle write exceptions
		// ? How to assign exception to the write operation 
		// Listener doesn't support it. SessionIoProccessor#doFlush() will fire a event for a write exception,
		// but by handling this event, it is not possible to detected the source (write/doFlush) of the event.
		
		WriteFuture writeFuture = getIoSession().write(org.apache.mina.common.ByteBuffer.wrap(buffer));
		writeFuture.addListener(writeFutureListener);
	}

	
	
	public final void init(final IIoHandlerCallback callback) throws IOException {
		this.callback = callback;
		
		callback.onConnect();
	}
	
	
	
	
	protected final IoSession getIoSession() {
		return session;
	}
	

	protected final IIoHandlerCallback getCallback() {
		return callback;
	}
	
	public final String getId() {
		return id;
	}
	
	public final LinkedList<ByteBuffer> drainIncoming() {
		LinkedList<ByteBuffer> result = null;
		
		synchronized (readQueue) {
			result = readQueue;
			readQueue = new LinkedList<ByteBuffer>();
		}
		
		return result;
	}
	
	
	public final int getIdleTimeoutSec() {
		return session.getIdleTime(IdleStatus.BOTH_IDLE);
	}

	
	public final void setIdleTimeoutSec(int timeout) {
		session.setIdleTime(IdleStatus.BOTH_IDLE, timeout);
	}

	public final void resumeRead() throws IOException {
		session.resumeRead();
	}
	
	public final void suspendRead() throws IOException {
		session.suspendRead();
	}
	
	public final InetAddress getLocalAddress() {
		return ((InetSocketAddress) session.getLocalAddress()).getAddress();
	}
	
	public final int getLocalPort() {
		return ((InetSocketAddress) session.getLocalAddress()).getPort();
	}
	
	public final InetAddress getRemoteAddress() {
		return ((InetSocketAddress) session.getLocalAddress()).getAddress();
	}
	
	public final int getRemotePort() {
		return ((InetSocketAddress) session.getLocalAddress()).getPort();
	}

	
	public Object getOption(String name) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}
	
	public Map<String, Class> getOptions() {
		// TODO Auto-generated method stub
		return null;
	}
	
	public void setOption(String name, Object value) throws IOException {
		// TODO Auto-generated method stub
		
	}
	
	public final boolean isOpen() {
		return session.isConnected();
	}

	
	
	public final void writeOutgoing(LinkedList<ByteBuffer> buffers) throws ClosedConnectionException, IOException {
		for (ByteBuffer buffer : buffers) {
			writeOutgoing(buffer);
		}
	}
	
	final void onWritten() {
		callback.onWritten();
	}
	
	final void onWriteException(IOException ioe) {
		callback.onWriteException(ioe);
	}


	final void onData(ByteBuffer buffer) {
		synchronized (readQueue) {
			readQueue.add(buffer);
		}
		callback.onDataRead();
	}
	
	
	final void onIdle() {
		callback.onIdleTimeout();
	}
	
	final void onDisconnect() {
		callback.onDisconnect();
	}

	
	public final void close(boolean immediate) throws IOException {
		
		if (immediate) {
			session.close();
			
		} else {
			flush();
			
			// if if there a pending writes, than wait
			if (session.getScheduledWriteMessages() > 0) {
				closing = true;
			} else {
				session.close();
			}
		}
	}
	
	

	private void flush() {
		synchronized (sendQueue) {
			if (!sendQueue.isEmpty()) {
				DelayQueueEntry[] entries = sendQueue.toArray(new DelayQueueEntry[sendQueue.size()]);
				sendQueue.clear();
								
				ByteBuffer[] buffers = new ByteBuffer[entries.length];
				for (int i = 0; i < buffers.length; i++) {
					buffers[i] = entries[i].getBuffer();
				}
				
	 			if (LOG.isLoggable(Level.FINE)) {
	 				LOG.fine("[" + getId() + "] flushing " + buffers.length + " buffers of delay queue");
	 			}
				
				for (ByteBuffer buffer : buffers) {
					try {
						reallyWriteOutgoing(buffer);
					} catch (Exception e) {
			 			if (LOG.isLoggable(Level.FINE)) {
			 				LOG.fine("[" + getId() + "] error occured while writing. Reason: " + e.toString());
			 			}						
					}
				}
			}
		}		
				
	}


	private final class DeliveryTask extends TimerTask {

		@Override
		public void run() {
			synchronized(sendQueue) {

				long currentTime = System.currentTimeMillis();
				while(!sendQueue.isEmpty()) {
					try {
						
						// get the oldest entry and write based on rate 
						DelayQueueEntry qe = sendQueue.peek();
						int remaingSize = qe.write(currentTime);
						
						// if all data of this entry is written remove entry and stay in loop 
						if (remaingSize == 0) {
							sendQueue.remove(qe);
							
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("throttling write queue is emtpy");
							}
							
							
						// ... else break loop and wait for next time event	
						} else {
							break;
						}
						
					} catch (Throwable e) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("Error occured while write delayed. Reason: " + e.toString());
						}
					}
				}
			}
		}
	}
	


	private final class DelayQueueEntry {
		private ByteBuffer buffer = null;
		private int bytesPerSec = 0;
		private long lastWriteTime = 0;
 		
		
		DelayQueueEntry(ByteBuffer buffer, int bytesPerSec) {
			this.buffer = buffer;
			this.bytesPerSec = bytesPerSec;
			this.lastWriteTime = System.currentTimeMillis();
		}

		
		ByteBuffer getBuffer() {
			return buffer;
		}
		
		
		
		int write(long currentTime) throws IOException {
			int remaingSize = buffer.remaining();
			
			long elapsedTimeMillis = currentTime - lastWriteTime;
			
			if (elapsedTimeMillis > 0) {
				int elapsedTimeSec = ((int) (elapsedTimeMillis)) / 1000; 
				
				if (elapsedTimeSec > 0) {
					int sizeToWrite = bytesPerSec * elapsedTimeSec;
					
					if (sizeToWrite > 0) {
						ByteBuffer bytesToWrite = null; 
						if (buffer.remaining() <= sizeToWrite) {
							bytesToWrite = buffer;
							remaingSize = 0;
							
						} else {
							int saveLimit = buffer.limit();
							buffer.limit(sizeToWrite);
							bytesToWrite = buffer.slice();
							buffer.position(buffer.limit());
							buffer.limit(saveLimit);
							buffer = buffer.slice();
							remaingSize = buffer.remaining();
						}
						
						lastWriteTime = currentTime;
						if (LOG.isLoggable(Level.FINE)) {
			 				LOG.fine("[" + getId() + "] release " + sizeToWrite + " bytes from delay queue");
			 			}
						reallyWriteOutgoing(bytesToWrite);
					}
				}
			}
			
			return remaingSize;
		}
		
		@Override
		public String toString() {
			return "buffer " + DataConverter.toFormatedBytesSize(buffer.remaining()) + " (write rate " + bytesPerSec + " bytes/sec)";
		}
	}
	
	
	
	
	
	
	
	
	
	
	protected final class WriteFutureListener implements IoFutureListener {
		public void operationComplete(IoFuture future) {
			callback.onWritten();
			
			// if connection is closing and no pending writes -> close connection 
			if (closing) {
				if (session.getScheduledWriteMessages() == 0) {
					session.close();
				}
			}
		}
	}
}
