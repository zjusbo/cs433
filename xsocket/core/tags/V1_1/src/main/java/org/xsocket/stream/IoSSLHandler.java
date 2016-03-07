// $Id: IoSSLHandler.java 1276 2007-05-28 15:38:57Z grro $
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
 * SSL io handler
 * 
 * @author grro@xsocket.org
 */
final class IoSSLHandler extends IoHandler implements SSLProcessor.EventHandler {
	
	private static final Logger LOG = Logger.getLogger(IoSSLHandler.class.getName());

	// read & write queue
	private final ByteBufferQueue outAppDataQueue = new ByteBufferQueue();
	private final ByteBufferQueue inAppDataQueue = new ByteBufferQueue();

	
	// event handler
	protected IIOEventHandler ioEventHandler = null;

	
	
	// ssl stuff 
	private SSLProcessor sslProcessor = null;	
	private boolean isClientMode = false;
	private boolean isSSLConnected = false;	
	
	

		
	/**
	 * constructor 
	 * 
	 * @param successor      the successor
	 * @param sslContext     the ssl context to use
	 * @param isClientMode   true, if is in client mode 
	 * @param memoryManager  the memory manager to use
	 * @throws IOException If some other I/O error occurs
	 */
	IoSSLHandler(IoHandler successor, SSLContext sslContext,boolean isClientMode, IMemoryManager memoryManager) throws IOException {
		super(successor);

		this.isClientMode = isClientMode;
		sslProcessor = new SSLProcessor(sslContext, isClientMode, memoryManager, this);
		
		setIOEventHandler(successor.getIOEventHandler());
	}
	


	/**
	 * {@inheritDoc}
	 */
	@Override
	void open() throws IOException {
		getSuccessor().open();
		startSSL();
	}

	
	/**
	 * start the ssl mode
	 * 
	 * @throws IOException If some other I/O error occurs
	 */
	void startSSL() throws IOException {
		if (!isSSLConnected) {
			sslProcessor.start();
		}
		
		readIncomingEncryptedData();
	}
	
	
	@Override
	final boolean isChainSendBufferEmpty() {
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
	LinkedList<ByteBuffer> drainIncoming() {
		return inAppDataQueue.drain();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	final void close(boolean immediate) throws IOException {
		getSuccessor().close(immediate);
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
		synchronized (sslProcessor) {
			if (!sslProcessor.isHandshaking()) {
				if (!outAppDataQueue.isEmpty()) {
					sslProcessor.processOutAppData(outAppDataQueue.drain());
				}
			} else {
				sslProcessor.processOutAppData();
			}
		}
	}
		
	
	

	protected final void readIncomingEncryptedData() throws ClosedConnectionException, IOException {
		readIncomingEncryptedData(getSuccessor().drainIncoming());
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
	
	
	public void onHandshakeFinished() throws IOException {
		if (!isSSLConnected) {
			if (LOG.isLoggable(Level.FINE)) {
				if (isClientMode) {
					LOG.fine("[" + getId() + "] handshake has been finished (clientMode)");
				} else {
					LOG.fine("[" + getId() + "] handshake has been finished (serverMode)");
				}
			}

			isSSLConnected = true;
			ioEventHandler.onConnectEvent();
		} 	
			
		flushOutgoing();
		readIncomingEncryptedData();
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
				readIncomingEncryptedData();
			} catch (Exception e) {
	 			if (LOG.isLoggable(Level.FINE)) {
	 				LOG.fine("[" + getId() + "] error occured while receiving data. Reason: " + e.toString());
	 			}
			}
		}
		

		public void onConnectEvent() {
			
		}

		public void onWrittenEvent() {
			ioEventHandler.onWrittenEvent();
		}
		
		public void onWriteExceptionEvent(IOException ioe) {
			ioEventHandler.onWriteExceptionEvent(ioe);
		}
		
		public void onDisconnectEvent() {
			sslProcessor.destroy();
			ioEventHandler.onDisconnectEvent();
		}

		public void initiateClose() {
			ioEventHandler.initiateClose();
		}
		

		public void onConnectionTimeout() {
			ioEventHandler.onConnectionTimeout();
		}
		
		public void onIdleTimeout() {
			ioEventHandler.onIdleTimeout();
		}
	}	
}
