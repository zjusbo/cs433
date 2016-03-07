// $Id: IoSSLHandler.java 1268 2007-05-23 06:26:50Z grro $
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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;


import org.xsocket.ClosedConnectionException;
import org.xsocket.DataConverter;



/**
 * ssl processor
 * 
 * @author grro@xsocket.org
 */
final class SSLProcessor {
	
	private static final Logger LOG = Logger.getLogger(SSLProcessor.class.getName());
	

	private SSLEngine sslEngine = null;
	
	private boolean isClientMode = false;
	private IMemoryManager memoryManager = null;
	private EventHandler eventHandler = null;

	// buffer size
	private int minNetBufferSize = 0;
	private int minAppBufferSize = 0;

	
	/**
	 * constructor 
	 * 
	 * @param sslContext    the ssl context
	 * @param isClientMode  true, is ssl processor runs in client mode
	 */
	SSLProcessor(SSLContext sslContext, boolean isClientMode, IMemoryManager memoryManager, EventHandler eventHandler) {
		this.isClientMode = isClientMode;
		this.memoryManager = memoryManager;
		this.eventHandler = eventHandler;
		
		sslEngine = sslContext.createSSLEngine();
		minAppBufferSize = sslEngine.getSession().getApplicationBufferSize();
		minNetBufferSize = sslEngine.getSession().getPacketBufferSize();
				
		sslEngine.setUseClientMode(isClientMode);
	}
	
	
	
	/**
	 * start ssl processing 
	 * 
	 * @param  inNetData  already received net data
	 * @throws IOException If some other I/O error occurs
	 */
	synchronized void start() throws IOException {
		try {
			sslEngine.beginHandshake();
		} catch (SSLException sslEx) {
			throw new RuntimeException(sslEx);
		}

		
		if (isClientMode) {
 			if (LOG.isLoggable(Level.FINE)) {
 				LOG.fine("initate ssl handshake");
 			}
 			processOutAppData();
		}
	}


	/**
	 * destroy this instance
	 *
	 */
	void destroy() {
		sslEngine.closeOutbound();
	}
	

	/**
	 * process incoming encrypted net data 
	 *  
	 * @param inNetBufferList   the data 
	 * @throws IOException if an io exction occurs 
	 * @throws ClosedConnectionException if the connection is already closed
	 */
	synchronized void processInNetData(LinkedList<ByteBuffer> inNetBufferList) throws IOException ,ClosedConnectionException {

		LinkedList<ByteBuffer> inAppDataList = new LinkedList<ByteBuffer>();
		
		try {
			int size = 0;
			for (ByteBuffer buffer : inNetBufferList) {
				size += buffer.remaining();
			}

			for (ByteBuffer inNetBuffer : inNetBufferList) {
				List<ByteBuffer> inAppData = unwrap(inNetBuffer);
				inAppDataList.addAll(inAppData);
				
				
				if (LOG.isLoggable(Level.FINE)) {
					int appDataSize = 0;
					ByteBuffer[] inAppDataListCopy = new ByteBuffer[inAppDataList.size()];
					for (int i = 0; i < inAppDataList.size(); i++) {
						appDataSize += inAppDataList.get(i).remaining();
						inAppDataListCopy[i] = inAppDataList.get(i).duplicate();
					}
					
					if ((size - appDataSize) > 0) {
						LOG.fine((size - appDataSize) + " ssl system packet data ");
					}
					
					if (appDataSize > 0) {
		 				LOG.fine(appDataSize + " app data extracted: " + DataConverter.toTextOrHexString(inAppDataListCopy, "UTF-8", 500));
					} 
				}
			}
			
			if (!inAppDataList.isEmpty()) {
				eventHandler.onInAppDataReceived(inAppDataList);
			}
		} catch (SSLException sslEx) {
			eventHandler.onSSLProcessorClosed();
		}
		
					
		if (sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
			processOutAppData();
		}
	}

	
	/**
	 * return true, if the connection is handshaking
	 * 
	 * @return true, if the connection is handshaking
	 */
	synchronized boolean isHandshaking() {
		HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();
		return !(handshakeStatus == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) 
		        || (handshakeStatus == SSLEngineResult.HandshakeStatus.FINISHED);
	}
	
	
	/**
	 * process outgoing plain app data 
	 *  
	 * @param outAppDataList  the app data 
	 * @throws IOException if an io exction occurs 
	 * @throws ClosedConnectionException if the connection is already closed
	 */
	void processOutAppData(LinkedList<ByteBuffer> outAppDataList) throws ClosedConnectionException, IOException{
		for (ByteBuffer outAppData : outAppDataList) {
			wrap(outAppData);
		}
	}

	/**
	 * process outgoing plain app data 
	 *   
	 * @throws IOException if an io exction occurs 
	 * @throws ClosedConnectionException if the connection is already closed
	 */
	void processOutAppData() throws ClosedConnectionException, IOException{
		ByteBuffer outAppData = ByteBuffer.allocate(0); 
		wrap(outAppData);
	}

	
	private synchronized LinkedList<ByteBuffer> unwrap(ByteBuffer inNetData) throws SSLException, ClosedConnectionException, IOException {
		LinkedList<ByteBuffer> inAppDataList = new LinkedList<ByteBuffer>();
		int minAppSize = minAppBufferSize;
			
		try {
			do {

				// perform unwrap
				ByteBuffer inAppData = memoryManager.acquireMemory(minAppSize);
				SSLEngineResult engineResult = sslEngine.unwrap(inNetData, inAppData);


				// handle result
				if ((engineResult.getStatus() == SSLEngineResult.Status.OK)
				     || (engineResult.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW)) {
					
					// exctract remaining encrypted inNetData
					if (inNetData.position() < inNetData.limit()) {
						inNetData = inNetData.slice();
					}
					
					// extract unwrapped app data
					inAppData.flip();
					inAppData = extractAndRecycleMemory(inAppData, minAppSize);
					if (inAppData.remaining() > 0) {
						inAppDataList.add(inAppData);
					}
					
					
				} else if (engineResult.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
					// hmmm? shouldn't occure, but handle it by expanding the min buffer size
					memoryManager.recycleMemory(inAppData, minAppSize);
					minAppSize += minAppSize;
					continue;
				
					
				} else if (engineResult.getStatus() == SSLEngineResult.Status.CLOSED) {
					memoryManager.recycleMemory(inAppData, minAppSize);
					throw new ClosedConnectionException("Couldn't unwrap, because connection is closed");
				}  
				

								
				// need task?
				if (sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
					Runnable task = null;
					while ((task = sslEngine.getDelegatedTask()) != null) {
					    task.run();
					}
				}
				

				// finished ?
				if (engineResult.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED) {
					notifyHandshakeFinished();
				}
				
				
			} while (inNetData.hasRemaining());
			
		} catch (SSLException ssles) {
			throw ssles;
		}

		return inAppDataList;
	}
	
	
	
	private synchronized void wrap(ByteBuffer outAppData) throws SSLException, ClosedConnectionException, IOException {
		int minNetSize = minNetBufferSize;

		do {

			// perform wrap
			ByteBuffer outNetData = memoryManager.acquireMemory(minNetSize);
			SSLEngineResult engineResult = sslEngine.wrap(outAppData, outNetData);
			
			
			// handle the engine result
		    if ((engineResult.getStatus() == SSLEngineResult.Status.OK) 
		    	|| (engineResult.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW)) {

				// extract remaining outAppData
				if (outAppData.position() < outAppData.limit()) {
					outAppData = outAppData.slice();
				}

				// extract encrypted net data
				outNetData.flip();
				outNetData = extractAndRecycleMemory(outNetData, minNetSize);
				if (outNetData.hasRemaining()) {
					eventHandler.onOutNetDataToWrite(outNetData);
				}

				
		    } else if (engineResult.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
				// hmmm? shouldn't occure, but handle it by expanding the min buffer size
				memoryManager.recycleMemory(outNetData, minNetSize);
				minNetSize += minNetSize;
				continue;

				
			} else if (engineResult.getStatus() == SSLEngineResult.Status.CLOSED) {
				memoryManager.recycleMemory(outNetData, minNetSize);
				throw new ClosedConnectionException("Couldn't wrap, because connection is closed");
			}
		    
		    
			// need task?
			if (sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
				Runnable task = null;
				while ((task = sslEngine.getDelegatedTask()) != null) {
				    task.run();
				}
			}

			
			// finished ?
			if (engineResult.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED) {
				notifyHandshakeFinished();
				break;
			}
		} while (outAppData.hasRemaining() || (sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_WRAP));
		
	}
	

	private void notifyHandshakeFinished() throws IOException {
		if (LOG.isLoggable(Level.FINE)) {
			if (isClientMode) {
				LOG.fine("handshake has been finished (clientMode)");
			} else {
				LOG.fine("handshake has been finished (serverMode)");
			}
		}
		
		processOutAppData();
		eventHandler.onHandshakeFinished();
	}
	
	
	private ByteBuffer extractAndRecycleMemory(ByteBuffer buffer, int minSize) {
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
			memoryManager.recycleMemory(unused, minSize);

			return slicedPart;
		}
	}
	
	
	/**
	 * SSLProcessor call back interface
	 * 
	 * @author grro
	 */
	static interface EventHandler {

		/**
		 * signals that the handshake has been finished
		 * 
	     * @throws IOException if an io exception occurs
		 */
		public void onHandshakeFinished() throws IOException;
		
		
		/**
		 * singals available app data 
		 * 
		 * @param appDataList the app data
		 */
		public void onInAppDataReceived(LinkedList<ByteBuffer> appDataList);
		
		
		/**
		 * signals that there is net data to write 
		 * 
		 * @param netData  the data to write
	     * @throws IOException if an io exception occurs
		 */
		public void onOutNetDataToWrite(ByteBuffer netData) throws IOException ;
		
		
		/**
		 * signals, that the SSLProcessor has been closed 
		 * 
	     * @throws IOException if an io exception occurs
		 */
		public void onSSLProcessorClosed() throws IOException;
	}
}
