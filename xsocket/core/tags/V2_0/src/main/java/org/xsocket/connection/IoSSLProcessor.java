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
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;


import org.xsocket.DataConverter;



/**
 * ssl processor
 * 
 * @author grro@xsocket.org
 */
final class IoSSLProcessor {
	
	private static final Logger LOG = Logger.getLogger(IoSSLProcessor.class.getName());
	

	private SSLEngine sslEngine = null;
	
	private boolean isClientMode = false;
	private AbstractMemoryManager memoryManager = null;
	private EventHandler eventHandler = null;

	// buffer size
	private int minNetBufferSize = 0;
	private int minEncryptedBufferSize = 0;
	
	private ByteBuffer unprocessedEncryptedData = null; 

	
	/**
	 * constructor 
	 * 
	 * @param sslContext    the ssl context
	 * @param isClientMode  true, is ssl processor runs in client mode
	 */
	IoSSLProcessor(SSLContext sslContext, boolean isClientMode, AbstractMemoryManager memoryManager, EventHandler eventHandler) {
		this.isClientMode = isClientMode;
		this.memoryManager = memoryManager;
		this.eventHandler = eventHandler;
		
		sslEngine = sslContext.createSSLEngine();
		minEncryptedBufferSize = sslEngine.getSession().getApplicationBufferSize();
		minNetBufferSize = sslEngine.getSession().getPacketBufferSize();
		
		if (LOG.isLoggable(Level.FINE)) {
			if (isClientMode) {
				LOG.fine("initializing ssl processor (client mode)");
			} else {
				LOG.fine("initializing ssl processor (server mode)");
			}
			LOG.fine("app buffer size is " + minEncryptedBufferSize);
			LOG.fine("packet buffer size is " + minNetBufferSize);
		}
		
		
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
 			encrypt();
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
	 * decrypt  data
	 *  
	 * @param encryptedBufferList   the encrypted data 
	 * @throws IOException if an io exction occurs 
	 * @throws ClosedChannelException if the connection is already closed
	 */
	synchronized void decrypt(ByteBuffer[] encryptedBufferList) throws IOException ,ClosedChannelException {

		if (LOG.isLoggable(Level.FINEST)) {
			LOG.finest("decrypting " + encryptedBufferList.length + " buffers");
		}
		
		ArrayList<ByteBuffer> decryptedDataList = new ArrayList<ByteBuffer>(1);
		
		try {
			for (int i = 0; i < encryptedBufferList.length; i++) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("processing " + i + ".buffer (encrypted size " + encryptedBufferList[i].remaining() + ")");
				}
				List<ByteBuffer> inAppData = unwrap(encryptedBufferList[i]);
				decryptedDataList.addAll(inAppData);
				
				 
				if (LOG.isLoggable(Level.FINE)) {
					int decryptedDataSize = 0;
					ByteBuffer[] decryptedDataListCopy = new ByteBuffer[decryptedDataList.size()];
					for (int j = 0; j < decryptedDataList.size(); j++) {
						decryptedDataSize += decryptedDataList.get(j).remaining();
						decryptedDataListCopy[j] = decryptedDataList.get(j).duplicate();
					}
					
					if (decryptedDataSize > 0) {
		 				LOG.fine(decryptedDataSize + " decrypted data: " + DataConverter.toTextOrHexString(decryptedDataListCopy, "UTF-8", 500));
					} 
				}
			}
			
			if (!decryptedDataList.isEmpty()) {
				eventHandler.onDataDecrypted(decryptedDataList.toArray(new ByteBuffer[decryptedDataList.size()]));
			}
		} catch (SSLException sslEx) {
			eventHandler.onSSLProcessorClosed();
		}
		
					
		if (sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
			encrypt();
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
	 * encrypt data
	 *  
	 * @param plainBuffer  the plain data to encrypt 
	 * @throws IOException if an io exction occurs 
	 * @throws ClosedChannelException if the connection is already closed
	 */
	void encrypt(ByteBuffer[] plainBufferList) throws ClosedChannelException, IOException{
		for (ByteBuffer plainBuffer : plainBufferList) {
			wrap(plainBuffer);
		}
	}

	/**
	 * encrypt data
	 *   
	 * @throws IOException if an io exction occurs 
	 * @throws ClosedChannelException if the connection is already closed
	 */
	void encrypt() throws ClosedChannelException, IOException{
		ByteBuffer outAppData = ByteBuffer.allocate(0); 
		wrap(outAppData);
	}

	
	private synchronized ArrayList<ByteBuffer> unwrap(ByteBuffer inNetData) throws SSLException, ClosedChannelException, IOException {
		ArrayList<ByteBuffer> inAppDataList = new ArrayList<ByteBuffer>(1);
		int minAppSize = minEncryptedBufferSize;
		
		
		if (unprocessedEncryptedData != null) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("unprocessed encrypted data available. merging unprocessed encrypted data (size " 
						  + unprocessedEncryptedData.remaining() + ") with " + " new encrypted data (size " 
						  + inNetData.remaining() + ")");
			}
			
			inNetData = mergeBuffer(unprocessedEncryptedData, inNetData);
			unprocessedEncryptedData = null;

			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("new encrypted data size is " + inNetData.remaining());
			}
		}
		
		
		try {
			do {
				
				// perform unwrap
				ByteBuffer inAppData = memoryManager.acquireMemoryMinSize(minAppSize);
				SSLEngineResult engineResult = sslEngine.unwrap(inNetData, inAppData);


				if (engineResult.getStatus() == SSLEngineResult.Status.OK) {
						
					// exctract remaining encrypted inNetData
					if (inNetData.position() < inNetData.limit()) {
						inNetData = inNetData.slice();
					}
					
					
								
					// extract unwrapped app data
					inAppData = memoryManager.extractAndRecycleMemory(inAppData, engineResult.bytesProduced());
					if (inAppData.remaining() > 0) {
						inAppDataList.add(inAppData);
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest(inAppData.remaining() + " plain data has decrypted");
						}		
						
					} else {
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest("SSL System data (InNetData doesn't contain app data)");
						}		
					}
					
					
				} else if (engineResult.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
					/*
					 * There is not enough data on the input buffer to perform the operation. The application should read
					 * more data from the network. If the input buffer does not contain a full packet BufferUnderflow occurs
					 * (unwrap() can only operate on full packets)
					 */
					
					if (LOG.isLoggable(Level.FINEST)) {
						LOG.finest("BufferUnderflow occured (not enough InNet data)");
					}	
					
					unprocessedEncryptedData = inNetData;
					break; 

					
					
				} else if (engineResult.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
					// hmmm? shouldn`t occure, but handle it by expanding the min buffer size
					memoryManager.recycleMemory(inAppData);
					minAppSize += minAppSize;
					continue;
				
					
				} else if (engineResult.getStatus() == SSLEngineResult.Status.CLOSED) {
					memoryManager.recycleMemory(inAppData);
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("ssl engine closed");
					}
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
	
	
	
	private synchronized void wrap(ByteBuffer outAppData) throws SSLException, ClosedChannelException, IOException {
		int minNetSize = minNetBufferSize;

		do {

			// perform wrap
			ByteBuffer outNetData = memoryManager.acquireMemoryMinSize(minNetSize);
			SSLEngineResult engineResult = sslEngine.wrap(outAppData, outNetData);
			
			
			// handle the engine result
		    if ((engineResult.getStatus() == SSLEngineResult.Status.OK) 
		    	|| (engineResult.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW)) {

				// extract encrypted net data
				outNetData = memoryManager.extractAndRecycleMemory(outNetData, engineResult.bytesProduced());
				if (outNetData.hasRemaining()) {
					eventHandler.onDataEncrypted(outAppData, outNetData);
				}

				
		    } else if (engineResult.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
				// hmmm? shouldn`t occure, but handle it by expanding the min buffer size
				memoryManager.recycleMemory(outNetData);
				minNetSize += minNetSize;
				continue;

				
			} else if (engineResult.getStatus() == SSLEngineResult.Status.CLOSED) {
				memoryManager.recycleMemory(outNetData);
				throw new ClosedChannelException();
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
		
		encrypt();
		eventHandler.onHandshakeFinished();	
	}
	
	

	private ByteBuffer mergeBuffer(ByteBuffer first, ByteBuffer second) {
		ByteBuffer mergedBuffer = ByteBuffer.allocate(first.remaining() + second.remaining());
		mergedBuffer.put(first);
		mergedBuffer.put(second);
		mergedBuffer.flip();
		
		return mergedBuffer;
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
		 * singals data has been decrypted
		 * 
		 * @param decryptedBufferList the decrypted data
		 */
		public void onDataDecrypted(ByteBuffer[] decryptedBufferList);
		
		
		/**
		 * signals that data has been encrypted
		 * 
		 * @param plainData      the plain data
		 * @param encryptedData  the encrypted data
	     * @throws IOException if an io exception occurs
		 */
		public void onDataEncrypted(ByteBuffer plainData, ByteBuffer encryptedData) throws IOException ;
		
		
		/**
		 * signals, that the SSLProcessor has been closed 
		 * 
	     * @throws IOException if an io exception occurs
		 */
		public void onSSLProcessorClosed() throws IOException;
	}
}
