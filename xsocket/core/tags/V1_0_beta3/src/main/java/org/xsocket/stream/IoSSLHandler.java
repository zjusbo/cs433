// $Id: IoSSLHandler.java 778 2007-01-16 07:13:20Z grro $
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
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;

import org.xsocket.ClosedConnectionException;
import org.xsocket.DataConverter;


/**
 * SSL io handler
 * 
 * @author grro@xsocket.org
 */
final class IoSSLHandler extends IoHandler {
	
	private static final Logger LOG = Logger.getLogger(IoSSLHandler.class.getName());

	// read & write queue
	private final ByteBufferQueue writeQueue = new ByteBufferQueue();
	private final ByteBufferQueue receiveQueue = new ByteBufferQueue();

	
	// event handler
	private IIOEventHandler ioEventHandler = null;

	
	// flags
	private boolean isStarted = false;
	private boolean isConnected = false;	
	
	
	// ssl stuff 
	private SSLProcessor sslProcessor = null;	
	private SSLContext sslContext = null;
	private boolean startSSL = false;
	private boolean isClientMode = false;
	
	
	// memory management
	private IMemoryManager memoryManager = null;

		
	/**
	 * constructor 
	 * 
	 * @param successor      the successor
	 * @param sslContext     the ssl context to use
	 * @param startSSL       true, is SSL should be activated
	 * @param isClientMode   true, if is in client mode 
	 * @param memoryManager  the memory manager to use
	 * @throws IOException If some other I/O error occurs
	 */
	IoSSLHandler(IoHandler successor, SSLContext sslContext, boolean startSSL, boolean isClientMode, IMemoryManager memoryManager) throws IOException {
		super(successor);

		this.memoryManager = memoryManager;
		this.sslContext = sslContext;
		this.startSSL = startSSL;
		this.isClientMode = isClientMode;
		
		sslProcessor = new SSLProcessor();
		
		setIOEventHandler(successor.getIOEventHandler());
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	void open() throws IOException {
		if (startSSL & !isClientMode) {
			startSSL();
		} 
		
		getSuccessor().open();
		
		if (startSSL & isClientMode) {
			startSSL();
		} 
	}

	
	/**
	 * start the ssl mode
	 * 
	 * @throws IOException If some other I/O error occurs
	 */
	void startSSL() throws IOException {
		if (!isStarted) {
			isStarted = true;

			sslProcessor.start();
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	String getId() {
		return getSuccessor().getId();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	InetAddress getLocalAddress() {
		return getSuccessor().getLocalAddress();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	int getLocalPort() {
		return getSuccessor().getLocalPort();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	InetAddress getRemoteAddress() {
		return getSuccessor().getRemoteAddress();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	int getRemotePort() {
		return getSuccessor().getRemotePort();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	boolean isOpen() {
		return getSuccessor().isOpen();
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
		if (isStarted){
			return receiveQueue.drain();
		} else {
			return getSuccessor().drainIncoming();
		}
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	void close() throws IOException {
		getSuccessor().close();
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	void writeOutgoing(ByteBuffer buffer)  throws ClosedConnectionException, IOException {
		if (isStarted) {
			LinkedList<ByteBuffer> buffers = new LinkedList<ByteBuffer>();
			buffers.add(buffer);
			writeOutgoing(buffers);
		} else {
			getSuccessor().writeOutgoing(buffer);
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	void writeOutgoing(LinkedList<ByteBuffer> buffers) throws ClosedConnectionException, IOException {
		if (isStarted) {
			if (sslProcessor.isHandshaking()) {
				writeQueue.append(buffers);
			} else {
				sslProcessor.write(buffers);
			}
		} else {
			getSuccessor().writeOutgoing(buffers);
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	void flushOutgoing() {
		getSuccessor().flushOutgoing();		
	}
	
	
	
	private void readIncoming() throws ClosedConnectionException, IOException {		
		if (isStarted) {
			LinkedList<ByteBuffer> buffers = getSuccessor().drainIncoming();
			if (buffers != null) {
				sslProcessor.readIncoming(buffers);
			}
		} else {
			ioEventHandler.onDataEvent();
		}
	}
	

	
	private final class IOEventHandler implements IIOEventHandler {
				
		public boolean listenForData() {
			return ioEventHandler.listenForData();
		}
		
		public void onDataEvent() {
			try {
				readIncoming();
			} catch (Exception e) {
	 			if (LOG.isLoggable(Level.FINE)) {
	 				LOG.fine("[" + getId() + "] error occured while receiving data. Reason: " + e.toString());
	 			}
			}
		}
		
		public boolean listenForConnect() {
			return false;
		}

		public void onConnectEvent() {
			// ignore socketIoConnect event. connect event will be send, when ssl handshake has been finished
		}

		
		public boolean listenForDisconnect() {
			return ioEventHandler.listenForDisconnect();
		}
		
		public void onDisconnectEvent() {
			sslProcessor.destroy();
			ioEventHandler.onDisconnectEvent();
		}


		public void onConnectionTimeout() {
			ioEventHandler.onConnectionTimeout();
		}
		
		public void onIdleTimeout() {
			ioEventHandler.onIdleTimeout();
		}
	}
	

	private final class SSLProcessor {
		
		// ssl & memory
		private SSLEngine sslEngine = null;


		// buffer size
		private int minPacketBufferSize = 0;
		private int minAppBufferSize = 0;

		
		SSLProcessor() {
			sslEngine = sslContext.createSSLEngine();
			minAppBufferSize = sslEngine.getSession().getApplicationBufferSize();
			minPacketBufferSize = sslEngine.getSession().getPacketBufferSize();
					
			sslEngine.setUseClientMode(isClientMode);
		}
		
		
		
		/**
		 * start ssl processing 
		 * 
		 * @throws IOException If some other I/O error occurs
		 */
		void start() throws IOException {
			try {
				sslEngine.beginHandshake();
			} catch (SSLException sslEx) {
				throw new RuntimeException(sslEx);
			}

			
			if (isClientMode) {
	 			if (LOG.isLoggable(Level.FINE)) {
	 				LOG.fine("[" + getId() + "] initate ssl handshake");
	 			}
				write();
			}
		}


		/**
		 * destroy this instance
		 *
		 */
		final void destroy() {
			sslEngine.closeOutbound();
		}

		
		final void readIncoming(LinkedList<ByteBuffer> buffers) throws IOException ,ClosedConnectionException {
			read(buffers);
						
			if (needWrap()) {
				write();
			}
		}

		

		final void read(LinkedList<ByteBuffer> inNetBufferList) throws IOException, ClosedConnectionException {
		
			for (ByteBuffer inNetBuffer : inNetBufferList) {
				List<ByteBuffer> inAppDataList = unwrap(inNetBuffer);
				
				boolean appData = false;
				for (ByteBuffer inAppData : inAppDataList) {
					if (inAppData.remaining() > 0) {
						appData = true;
			 			if (LOG.isLoggable(Level.FINE)) {
			 				LOG.fine("[" + getId() + "] ssl -> data (data size " + inAppData.remaining() + "), data:" + DataConverter.toString(new ByteBuffer[] { inAppData.duplicate() }, "UTF-8", 500));
						}
						if (!isHandshaking()) {
							receiveQueue.append(inAppData);
						}
					}
				}
				
				if (LOG.isLoggable(Level.FINE)) {
					if (!appData) {
						LOG.fine("received ssl system packet");
					}
				}
			}
			
			if (!receiveQueue.isEmpty()) {
				ioEventHandler.onDataEvent();
			}
		}
		
		
		/**
		 * writes the data by using the underlying connection 
		 * 
		 * @param buffers the data to write
		 * @throws IOException If some other I/O error occurs
		 * @throws ClosedConnectionException if the underlying channel is closed  
		 */
		final void write(LinkedList<ByteBuffer> buffers) throws ClosedConnectionException, IOException{
			for (ByteBuffer outAppData : buffers) {
				if (LOG.isLoggable(Level.FINE)) {
					if (outAppData.remaining() > 0) {
						LOG.fine("data -> ssl (data size " + outAppData.remaining() + "), data: " + DataConverter.toString(new ByteBuffer[] { outAppData.duplicate() }, "UTF-8", 500));
					}
				}
				
				LinkedList<ByteBuffer> outNetDataList = wrap(outAppData);
				if (outNetDataList.size() > 0) {
					getSuccessor().writeOutgoing(outNetDataList);
				}
			}
		}

		
		final void write() throws ClosedConnectionException, IOException{
 			if (LOG.isLoggable(Level.FINE)) {
 				LOG.fine("[" + getId() + "] send ssl system packet");
 			}

			ByteBuffer outAppData = ByteBuffer.allocate(0); 
			LinkedList<ByteBuffer> outNetDataList = wrap(outAppData);
			if (outNetDataList.size() > 0) {
				getSuccessor().writeOutgoing(outNetDataList);
			}
		}

		
		/**
		 * signals if wrap is needed
		 * 
		 * @return true if wrap is needed
		 */
		final boolean needWrap() {
			return (sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_WRAP);
		}

		
		/**
		 * signals if unwrap is needed
		 * 
		 * @return true if unwrap is needed
		 */
		final boolean needUnwrap() {
			return (sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_UNWRAP);
		}
		
		/**
		 * signals if is handshaking
		 * 
		 * @return true if is handshaking
		 */
		final boolean isHandshaking() {
			return !(sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING);
		}
		

		/**
		 * perform a unwrap
		 * 
		 * @param inNetData  the data to unwrap
		 * @return the unwrapped data  
		 * @throws SSLException if a ssl error occurs
		 * @throws ClosedConnectionException if the underlying channel is closed  
		 */
		final LinkedList<ByteBuffer> unwrap(ByteBuffer inNetData) throws SSLException, ClosedConnectionException, IOException {
			LinkedList<ByteBuffer> inAppDataList = new LinkedList<ByteBuffer>();

			boolean repeat = false;
			int minSize = minAppBufferSize;

				
			try {
				do {
					repeat = false;
					SSLEngineResult engineResult = null;
					
					// processing received data
					ByteBuffer inAppData = memoryManager.acquireMemory(minSize);
					engineResult = sslEngine.unwrap(inNetData, inAppData);
					
					if (engineResult.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
			 			if (LOG.isLoggable(Level.FINE)) {
			 				LOG.fine("[" + getId() + "] BUFFER_UNDERFLOW within unwrap shouldn't occur");
			 			}

					} else if (engineResult.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
						memoryManager.recycleMemory(inAppData);
						repeat = true;
						minSize += minSize;
						continue;
					
					} else if (engineResult.getStatus() == SSLEngineResult.Status.CLOSED) {
						memoryManager.recycleMemory(inAppData);
						ClosedConnectionException cce = new ClosedConnectionException("Couldn't unwrap, because connection is closed");
						throw cce;
					
					} else if (engineResult.getStatus() == SSLEngineResult.Status.OK) {
						// get remaining inNetData
						if (inNetData.position() < inNetData.limit()) {
							inNetData = inNetData.slice();
						}
							// extract  data
						inAppData.flip();
						inAppData = extractAndRecycleMemory(inAppData);
						if (inAppData.remaining() > 0) {
							inAppDataList.add(inAppData);
						}
					}
					
									
					// need task?
					if (sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
						Runnable task = null;
						while ((task = sslEngine.getDelegatedTask()) != null) {
						    task.run();
						}
					}
					
					// unwrap ?
					if (sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
						if (inNetData.hasRemaining()) {
							repeat = true;
						}
					}

					// finished ?
					if (engineResult.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED) {
			 			if (LOG.isLoggable(Level.FINE)) {
			 				LOG.fine("[" + getId() + "] handshake finished");
			 			}
			 			
						if (!writeQueue.isEmpty()) {
							LinkedList<ByteBuffer> buffers = writeQueue.drain();
							writeOutgoing(buffers);
						}
						
						if (!isConnected) {
							isConnected = true;
							if (ioEventHandler.listenForConnect()) {
								ioEventHandler.onConnectEvent();
							}
						}
					}

				} while (repeat);
			} catch (SSLException ssles) {
				ssles.printStackTrace();
				throw ssles;
			}

			return inAppDataList;
		}

		
		private ByteBuffer extractAndRecycleMemory(ByteBuffer buffer) {
			// all bytes used?
			if (buffer.limit() == buffer.capacity()) {
				return buffer;

			// not all bytes used -> slice used part
			} else {
		   		int savedLimit = buffer.limit();
		   		ByteBuffer slicedPart = buffer.slice();

		   		// .. and return the remaining buffer for reuse
		   		buffer.position(savedLimit);
		   		buffer.limit(buffer.capacity());
				ByteBuffer unused = buffer.slice();
				memoryManager.recycleMemory(unused);

				return slicedPart;
			}
		}

	

		/**
		 * perform a unwrap
		 * 
		 * 
		 * @param outAppData the data to wrap
		 * @return the wrapped data
		 * @throws SSLException if a ssl error occurs
		 * @throws ClosedConnectionException if the underlying channel is closed  
		 * @throws IOException If some other I/O error occurs

		 */
		final LinkedList<ByteBuffer> wrap(ByteBuffer outAppData) throws SSLException, ClosedConnectionException, IOException {
			LinkedList<ByteBuffer> outNetDataList = new LinkedList<ByteBuffer>();
			boolean repeat = false;
			int minSize = minPacketBufferSize;

			do {
				repeat = false;
				SSLEngineResult engineResult = null;

				// processing received data
				ByteBuffer outNetData = memoryManager.acquireMemory(minSize);
				engineResult = sslEngine.wrap(outAppData, outNetData);

				if (engineResult.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
					memoryManager.recycleMemory(outNetData);
					repeat = true;
					minSize += minSize;
					continue;

				} else if (engineResult.getStatus() == SSLEngineResult.Status.CLOSED) {
					memoryManager.recycleMemory(outNetData);
					ClosedConnectionException cce = new ClosedConnectionException("Couldn't unwrap, because connection is closed");
					throw cce;

				} else if (engineResult.getStatus() == SSLEngineResult.Status.OK) {

					// get remaining outAppData
					if (outAppData.position() < outAppData.limit()) {
						outAppData = outAppData.slice();
					}

					// extract  data
					outNetData.flip();
					outNetData = extractAndRecycleMemory(outNetData);
					outNetDataList.add(outNetData);
				}

				// need task?
				if (sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
					Runnable task = null;
					while ((task = sslEngine.getDelegatedTask()) != null) {
					    task.run();
					}
				}


				// wrap
				if (sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
					repeat = true;
				}
				
				
				// finished ?
				if (engineResult.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED) {
		 			if (LOG.isLoggable(Level.FINE)) {
		 				LOG.fine("[" + getId() + "] handshake finished");
		 			}
		 			
		 			
					if (!writeQueue.isEmpty()) {
						LinkedList<ByteBuffer> buffers = writeQueue.drain();
						writeOutgoing(buffers);
					}
					
					if (!isConnected) {
						isConnected = true;
						if (ioEventHandler.listenForConnect()) {
							ioEventHandler.onConnectEvent();
						}
					}

					break;
				}
			} while (repeat);
			return outNetDataList;
		}
	}
}
