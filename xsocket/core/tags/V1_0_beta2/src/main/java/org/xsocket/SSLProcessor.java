// $Id: SSLProcessor.java 448 2006-12-08 14:55:13Z grro $
/*
 *  Copyright (c) xsocket.org, 2006. All rights reserved.
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
package org.xsocket;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;

import org.xsocket.ClosedConnectionException;
import org.xsocket.util.TextUtils;





/**
 * A SSLProcessor 
 * 
 * 
 * @author grro@xsocket.org
 */
abstract class SSLProcessor {
	
	private static final Logger LOG = Logger.getLogger(SSLProcessor.class.getName());

	// the underlying connection
	private Connection connection = null;
	
	
	// ssl & memory
	private SSLEngine sslEngine = null;
	private SSLMemoryManager memoryManager = new SSLMemoryManager(65536);


	// buffer size
	private int minPacketBufferSize = 0;
	private int minAppBufferSize = 0;

	
	/**
	 * constructor
	 * 
	 * @param connection  the underlying connection
	 * @param clientMode  true, if the SSLProcessor ashould be run in the cleint mode 
	 * @param sslContext  the SSLContent to use
	 */
	SSLProcessor(Connection connection, boolean clientMode, SSLContext sslContext) {
		this.connection = connection;
		
		sslEngine = sslContext.createSSLEngine();
		minAppBufferSize = sslEngine.getSession().getApplicationBufferSize();
		minPacketBufferSize = sslEngine.getSession().getPacketBufferSize();
				
		sslEngine.setUseClientMode(clientMode);
	}
	
	
	/**
	 * factory method to create a new Processor object
	 * 
	 * @param connection  the underlying connection
	 * @param clientMode  true, if the SSLProcessor ashould be run in the cleint mode 
	 * @param sslContext  the SSLContent to use
	 * @return the create processor
	 */
	final static SSLProcessor newProcessor(Connection connection, boolean clientMode, SSLContext sslContext) {
		
		if (clientMode) {
			return new SSLClientProcessor(connection, sslContext);
		} else {
			return new SSLServerProcessor(connection, sslContext);
		}
	}
	
	
	/**
	 * return the associated connection
	 * 
	 * @return the associated connection
	 */
	protected final Connection getConnection() {
		return connection;
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
	}


	/**
	 * destroy this instance
	 *
	 */
	final void destroy() {
		sslEngine.closeOutbound();
	}

	
	/**
	 * write a array of ByteBuffer to send. The data will 
	 * be encrypted before sending 
	 * 
	 * @param buffers  the buffers to send
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	abstract void writeOutgoing(ByteBuffer[] buffers) throws ClosedConnectionException, IOException;
	

	/**
	 * read the incoming data. The data will be decrypted 
	 *  
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */	
	abstract int readIncoming() throws IOException, ClosedConnectionException;

	

	/**
	 * reads dta by using the underlying connection 
	 * 
	 * @return the size of read data 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	final int read() throws IOException, ClosedConnectionException {
		int readSize = 0; 
		ByteBuffer inNetbuffer = connection.readPhysical();
		
		if (inNetbuffer != null) {
			List<ByteBuffer> inAppDataList = unwrap(inNetbuffer);
			for (ByteBuffer inAppData : inAppDataList) {
				if (inAppData.remaining() > 0) {
					if (LOG.isLoggable(Level.FINE)) {
						getConnection().logFine("ssl -> data: " + TextUtils.toString(new ByteBuffer[] { inAppData.duplicate() }, "UTF-8", 500));
					}
					readSize += connection.appenToReadQueue(inAppData);
					
				}
			}
		}
		return readSize;
	}
	
	
	/**
	 * writes the data by using the underlying connection 
	 * 
	 * @param buffers the data to write
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	final void write(ByteBuffer[] buffers) throws ClosedConnectionException, IOException{
		if ((buffers.length > 0)) {
			for (ByteBuffer outAppData : buffers) {
				if (LOG.isLoggable(Level.FINE)) {
					if (outAppData.remaining() > 0) {
						getConnection().logFine("data -> ssl: " + TextUtils.toString(new ByteBuffer[] { outAppData.duplicate() }, "UTF-8", 500));
					}
				}
			
				List<ByteBuffer> outNetDataList = wrap(outAppData);
				if (outNetDataList.size() > 0) {
					ByteBuffer[] outBuffers = outNetDataList.toArray(new ByteBuffer[outNetDataList.size()]);
					getConnection().writePhysical(outBuffers);
				}
			}
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
	final List<ByteBuffer> unwrap(ByteBuffer inNetData) throws SSLException, ClosedConnectionException {
		List<ByteBuffer> inAppDataList = new ArrayList<ByteBuffer>();

		boolean repeat = false;
		int minSize = minAppBufferSize;

			
		try {
			do {
				repeat = false;
				SSLEngineResult engineResult = null;
				
				// processing received data
				ByteBuffer inAppData = memoryManager.acquireMemory(minSize);
				engineResult = sslEngine.unwrap(inNetData, inAppData);
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer(engineResult.toString());
				}
				
				if (engineResult.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
					LOG.warning("BUFFER_UNDERFLOW within unwrap shouldn't occur");
				} else if (engineResult.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
					memoryManager.recycleMemory(inAppData);
					repeat = true;
					minSize += minSize;
					continue;
				} else if (engineResult.getStatus() == SSLEngineResult.Status.CLOSED) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("ssl connection is closed. closing connection");
					}
					memoryManager.recycleMemory(inAppData);
					ClosedConnectionException cce = new ClosedConnectionException("Couldn't unwrap, because connection is closed");
					LOG.throwing(this.getClass().getName(), "unwrap", cce);
					throw cce;
				} else if (engineResult.getStatus() == SSLEngineResult.Status.OK) {
					// get remaining inNetData
					if (inNetData.position() < inNetData.limit()) {
						inNetData = inNetData.slice();
					}
						// extract  data
					inAppData.flip();
					inAppData = memoryManager.extractAndRecycleMemory(inAppData);
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
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer(sslEngine.getHandshakeStatus().toString());
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
					if (!connection.isConnected()) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("handshake finished (client side)");
						}
						
						connection.onConnect();
					}
				}

			} while (repeat);
		} catch (SSLException ssles) {
			ssles.printStackTrace();
			throw ssles;
		}

		return inAppDataList;
	}


	/**
	 * perform a unwrap
	 * 
	 * @param inNetData  the data to unwrap
	 * @return the unwrapped data  
	 * @throws SSLException if a ssl error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */

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
	final List<ByteBuffer> wrap(ByteBuffer outAppData) throws SSLException, ClosedConnectionException, IOException {
		List<ByteBuffer> outNetDataList = new ArrayList<ByteBuffer>();
		boolean repeat = false;
		int minSize = minPacketBufferSize;

		do {
			repeat = false;
			SSLEngineResult engineResult = null;

			// processing received data
			ByteBuffer outNetData = memoryManager.acquireMemory(minSize);
			engineResult = sslEngine.wrap(outAppData, outNetData);
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer(engineResult.toString());
			}

			if (engineResult.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
				memoryManager.recycleMemory(outNetData);
				repeat = true;
				minSize += minSize;
				continue;

			} else if (engineResult.getStatus() == SSLEngineResult.Status.CLOSED) {
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("ssl connection is closed. closing connection");
				}
				memoryManager.recycleMemory(outNetData);
				ClosedConnectionException cce = new ClosedConnectionException("Couldn't unwrap, because connection is closed");
				LOG.throwing(this.getClass().getName(), "wrap", cce);
				throw cce;

			} else if (engineResult.getStatus() == SSLEngineResult.Status.OK) {

				// get remaining outAppData
				if (outAppData.position() < outAppData.limit()) {
					outAppData = outAppData.slice();
				}

				// extract  data
				outNetData.flip();
				outNetData = memoryManager.extractAndRecycleMemory(outNetData);
				outNetDataList.add(outNetData);
			}

			// need task?
			if (sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
				Runnable task = null;
				while ((task = sslEngine.getDelegatedTask()) != null) {
				    task.run();
				}
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer(sslEngine.getHandshakeStatus().toString());
				}
			}


			// wrap
			if (sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
				repeat = true;
			}
			
			
			// finished ?
			if (engineResult.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED) {
				if (!connection.isConnected()) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("handshake finished (server side)");
					}

					connection.onConnect();
				}
				break;
			}
		} while (repeat);
		return outNetDataList;
	}
	
	
	private static final class SSLMemoryManager {
		
		private List<SoftReference<ByteBuffer>> memoryBuffer = new ArrayList<SoftReference<ByteBuffer>>();

		private int preallocationSize = 4096;
		
		
		SSLMemoryManager(int preallocationSize) {
			this.preallocationSize = preallocationSize;
		}
		
			
		public synchronized void recycleMemory(ByteBuffer buffer) {
			assert (buffer.position() == 0) : "buffer must be clean";
					
			memoryBuffer.add(new SoftReference<ByteBuffer>(buffer));
		}

			
		public synchronized ByteBuffer acquireMemory(int minSize) {		
			ByteBuffer buffer = null;
					
			if (!memoryBuffer.isEmpty()) {
				SoftReference<ByteBuffer> freeBuffer = memoryBuffer.remove(0);
				buffer = freeBuffer.get();

				if (buffer != null) {
					// size sufficient?
					if (buffer.limit() < minSize) {
						buffer = null;			
					}
				}
			} 
					
				
			if (buffer == null) {
				int size = preallocationSize;
				if (size < minSize) {
					size = minSize * 4;
				}
				
				buffer = ByteBuffer.allocateDirect(size);
			}
					
			return buffer;
		}	

		
		public ByteBuffer extractAndRecycleMemory(ByteBuffer buffer) {
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
				recycleMemory(unused);

				return slicedPart;
			}
		}

	}
}
