// $Id: IoSSLHandler.java 1219 2007-05-05 07:36:36Z grro $
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
package org.xsocket.stream;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.xsocket.ClosedConnectionException;




/**
 * activateable SSL io handler
 * 
 * @author grro@xsocket.org
 */
final class IoActivateableSSLHandler extends IoHandler implements SSLProcessor.EventHandler {
	
	
	private static final Logger LOG = Logger.getLogger(IoSSLHandler.class.getName());
	
	private enum Mode { OFF, NON_RECEIVING, NON_RECEIVING_AND_WRITING, SSL };
	
	private Mode mode = Mode.OFF;

	
	// read & write queue
	private final ByteBufferQueue inNetDataQueue = new ByteBufferQueue();
	private final ByteBufferQueue outAppDataQueue = new ByteBufferQueue();
	private final ByteBufferQueue inAppDataQueue = new ByteBufferQueue();
	
	
	// ssl stuff 
	private SSLProcessor sslProcessor = null;	


	// event handler
	protected IIOEventHandler ioEventHandler = null;


	
	/**
	 * constructor 
	 * 
	 * @param successor      the successor
	 * @param sslContext     the ssl context to use
	 * @param isClientMode   true, if is in client mode 
	 * @param memoryManager  the memory manager to use
	 * @throws IOException If some other I/O error occurs
	 */
	IoActivateableSSLHandler(IoHandler successor, SSLContext sslContext,boolean isClientMode, IMemoryManager memoryManager) throws IOException {
		super(successor);
		
		sslProcessor = new SSLProcessor(sslContext, isClientMode, memoryManager, this);
		setIOEventHandler(successor.getIOEventHandler());
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	void open() throws IOException {
		getSuccessor().open();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	final void close(boolean immediate) throws IOException {
		getSuccessor().close(immediate);
	}
	
	
	@Override
	boolean isChainSendBufferEmpty() {
		if (getSuccessor() != null) {
			return (outAppDataQueue.isEmpty() && getSuccessor().isChainSendBufferEmpty());
		} else {
			return outAppDataQueue.isEmpty();
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	void setIOEventHandler(IIOEventHandler ioEventHandler) {
		this.ioEventHandler = ioEventHandler;
		getSuccessor().setIOEventHandler(new IOEventHandler());		
	}



	
	/**
	 * {@inheritDoc}
	 */
	@Override
	IIOEventHandler getIOEventHandler() {
		return ioEventHandler;
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	final LinkedList<ByteBuffer> drainIncoming() {
		if (mode == Mode.OFF) {
			return getSuccessor().drainIncoming();
		} else {
			return inAppDataQueue.drain();
		}
	}
	
	
	

	

	/**
	 * {@inheritDoc}
	 */
	@Override
	final void writeOutgoing(ByteBuffer buffer)  throws ClosedConnectionException, IOException {
		LinkedList<ByteBuffer> buffers = new LinkedList<ByteBuffer>();
		buffers.add(buffer);
		writeOutgoing(buffers);
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	final void writeOutgoing(LinkedList<ByteBuffer> buffers) throws ClosedConnectionException, IOException {
		outAppDataQueue.append(buffers);
		flushOutgoing();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	void flushOutgoing() throws IOException {
		if (mode == Mode.SSL) {
			synchronized (sslProcessor) {
				if (sslProcessor.isHandshaking()) {
					sslProcessor.processOutAppData();

				} else {
					if (!outAppDataQueue.isEmpty()) {
						sslProcessor.processOutAppData(outAppDataQueue.drain());
					}
				}
			}	

	
		} else if ((mode == Mode.OFF) || (mode == Mode.NON_RECEIVING)) {
			LinkedList<ByteBuffer> data = outAppDataQueue.drain(); 
			getSuccessor().writeOutgoing(data);
		}
	}

	
	void stopProcessingIncoming() {
		mode = Mode.NON_RECEIVING;
	}
	
	
	/**
	 * start SSL. <br> <br>
	 * 
	 * Return already received data to ssl handler (this data 
	 * will be interpreted as encrypted data). <br> 
	 * 
	 * @param readQueue      the queue with already received data
	 * @throws IOException if an io exception occurs
	 */
	void startSSL(ByteBufferQueue readQueue) throws IOException {
		assert (mode == Mode.NON_RECEIVING);
		
		mode = Mode.NON_RECEIVING_AND_WRITING;

		inNetDataQueue.addFirst(readQueue.drain());
		
		sslProcessor.start();
		mode = Mode.SSL;
		
		flushOutgoing();
		readIncomingEncryptedData();
	}
	
	
	
	public void onHandshakeFinished() throws IOException {
		flushOutgoing();
		readIncomingEncryptedData();
	}
	

	protected final void readIncomingEncryptedData() throws ClosedConnectionException, IOException {
		inNetDataQueue.append(getSuccessor().drainIncoming());
		readIncomingEncryptedData(inNetDataQueue.drain());
	}
	

	
	private synchronized void readIncomingEncryptedData(LinkedList<ByteBuffer> inNetDataList) throws ClosedConnectionException, IOException {
		if (inNetDataList != null) {
			if (LOG.isLoggable(Level.FINE)) {
				int size = 0;
				for (ByteBuffer buffer : inNetDataList) {
					size += buffer.remaining();
				}
				
				LOG.fine("received " + size + " bytes encrypted data");
			}
			
			sslProcessor.processInNetData(inNetDataList);
		}
	}

	
	public void onSSLProcessorClosed() throws IOException {
		close(true);
	}
	
	public void onInAppDataReceived(LinkedList<ByteBuffer> appDataList) {
		inAppDataQueue.append(appDataList);
		
		if (!inAppDataQueue.isEmpty()) {
			ioEventHandler.onDataEvent();
		}
	}
	
	
	public void onOutNetDataToWrite(ByteBuffer netData) throws IOException {
		if (netData.hasRemaining()) {
			getSuccessor().writeOutgoing(netData);
		}
	}

	
	
	private final class IOEventHandler implements IIOEventHandler {
					
		public void onDataEvent() {
			try {
				if (mode == Mode.OFF) {
					getIOEventHandler().onDataEvent();
						
				} else if (mode == Mode.SSL) {
					readIncomingEncryptedData();
	
				} else {
					assert (mode == Mode.NON_RECEIVING) || (mode == Mode.NON_RECEIVING_AND_WRITING);
					return;
				}
			} catch (Exception e) {
	 			if (LOG.isLoggable(Level.FINE)) {
	 				LOG.fine("[" + getId() + "] error occured while receiving data. Reason: " + e.toString());
	 			}
			}
		}

		public void onConnectEvent() {
			getIOEventHandler().onConnectEvent();
		}
		
		public void onWrittenEvent() {
			getIOEventHandler().onWrittenEvent();
		}
		
		public void onWriteExceptionEvent(IOException ioe) {
			getIOEventHandler().onWriteExceptionEvent(ioe);
		}
		
		public void onDisconnectEvent() {
			//getSSLProcessor().destroy();
			getIOEventHandler().onDisconnectEvent();
		}

		public void initiateClose() {
			getIOEventHandler().initiateClose();
		}
		

		public void onConnectionTimeout() {
			getIOEventHandler().onConnectionTimeout();
		}
		
		public void onIdleTimeout() {
			getIOEventHandler().onIdleTimeout();
		}
	}
}
