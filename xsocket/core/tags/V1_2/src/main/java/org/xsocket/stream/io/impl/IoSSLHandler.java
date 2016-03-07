// $Id: IoSSLHandler.java 1316 2007-06-10 08:51:18Z grro $
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
package org.xsocket.stream.io.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.xsocket.ByteBufferQueue;
import org.xsocket.ClosedConnectionException;
import org.xsocket.stream.io.spi.IIoHandlerCallback;


/**
 * SSL io handler
 * 
 * @author grro@xsocket.org
 */
final class IoSSLHandler extends ChainableIoHandler implements SSLProcessor.EventHandler {
	
	private static final Logger LOG = Logger.getLogger(IoSSLHandler.class.getName());

	// read & write queue
	private final ByteBufferQueue outAppDataQueue = new ByteBufferQueue();
	private final ByteBufferQueue inAppDataQueue = new ByteBufferQueue();

	
	private final IOEventHandler ioEventHandler = new IOEventHandler();
	
	
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
	IoSSLHandler(ChainableIoHandler successor, SSLContext sslContext,boolean isClientMode, IMemoryManager memoryManager) throws IOException {
		super(successor);

		this.isClientMode = isClientMode;
		sslProcessor = new SSLProcessor(sslContext, isClientMode, memoryManager, this);
	}

	
	public void init(IIoHandlerCallback callbackHandler) throws IOException {
		setPreviousCallback(callbackHandler);
		getSuccessor().init(ioEventHandler);
		
		startSSL();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void setPreviousCallback(IIoHandlerCallback callbackHandler) {
		super.setPreviousCallback(callbackHandler);
		getSuccessor().setPreviousCallback(ioEventHandler);		
	}

	

    /**
     * {@inheritDoc}
     */
    @Override
    public int getPendingWriteDataSize() {
    	return outAppDataQueue.getSize() + super.getPendingWriteDataSize();
    }


    int getPendingReceiveDataSize() {
    	return inAppDataQueue.getSize() + super.getPendingReceiveDataSize();
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
	


	
	/**
	 * {@inheritDoc}
	 */
	public LinkedList<ByteBuffer> drainIncoming() {
		return inAppDataQueue.drain();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void close(boolean immediate) throws IOException {
		
		if (!immediate) {
			flushOutgoing();
		}
		
		getSuccessor().close(immediate);
	}
	

	/**
	 * {@inheritDoc}
	 */
	public final void writeOutgoing(ByteBuffer buffer)  throws ClosedConnectionException, IOException {
		LinkedList<ByteBuffer> buffers = new LinkedList<ByteBuffer>();
		buffers.add(buffer);
		writeOutgoing(buffers);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void writeOutgoing(LinkedList<ByteBuffer> buffers) throws ClosedConnectionException, IOException {
		outAppDataQueue.append(buffers);
		flushOutgoing();
	}

	
	/**
	 * {@inheritDoc}
	 */
	public void flushOutgoing() throws IOException {
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
			getPreviousCallback().onConnect();
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
			getPreviousCallback().onDataRead();
		}
	}
	
	
	
	public void onOutNetDataToWrite(ByteBuffer netData) throws IOException {
		if (netData.hasRemaining()) {
			getSuccessor().writeOutgoing(netData);
		}
	}

	
	
	private final class IOEventHandler implements IIoHandlerCallback {
					
		public void onDataRead() {
			try {
				readIncomingEncryptedData();
			} catch (Exception e) {
	 			if (LOG.isLoggable(Level.FINE)) {
	 				LOG.fine("[" + getId() + "] error occured while receiving data. Reason: " + e.toString());
	 			}
			}
		}
		

		public void onConnect() {
			
		}
		
		public void onWriteException(IOException ioException) {
			getPreviousCallback().onWriteException(ioException);
		}

		public void onWritten() {
			getPreviousCallback().onWritten();
		}
		
		public void onDisconnect() {
			sslProcessor.destroy();
			getPreviousCallback().onDisconnect();
		}

		public void onConnectionAbnormalTerminated() {
			getPreviousCallback().onConnectionAbnormalTerminated();
		}
		

		public void onConnectionTimeout() {
			getPreviousCallback().onConnectionTimeout();
		}
		
		public void onIdleTimeout() {
			getPreviousCallback().onIdleTimeout();
		}
	}	
}
