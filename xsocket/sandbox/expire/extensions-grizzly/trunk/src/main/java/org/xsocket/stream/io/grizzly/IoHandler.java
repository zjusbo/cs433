// $Id: IoHandler.java 1017 2007-03-15 08:03:05Z grro $
/*
 *  Copyright (c) xsocket.org, 2007. All rights reserved.
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
package org.xsocket.stream.io.grizzly;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.ClosedConnectionException;
import org.xsocket.DataConverter;
import org.xsocket.stream.io.spi.IClientIoProvider;
import org.xsocket.stream.io.spi.IIoHandler;
import org.xsocket.stream.io.spi.IIoHandlerCallback;
import org.xsocket.stream.io.spi.IIoHandlerContext;

import com.sun.grizzly.CallbackHandler;
import com.sun.grizzly.Context;
import com.sun.grizzly.IOEvent;
import com.sun.grizzly.TCPSelectorHandler;



/**
 * Grizzly-based IoHandler
 *
 * @author grro@xsocket.org
 */
public final class IoHandler implements IIoHandler, CallbackHandler<Context> {

	private static final Logger LOG = Logger.getLogger(IoHandler.class.getName());
	
	private static final Map<String ,Class> SUPPORTED_OPTIONS = new HashMap<String, Class>();
	
	static {
		SUPPORTED_OPTIONS.put(IClientIoProvider.SO_RCVBUF, Integer.class);
		SUPPORTED_OPTIONS.put(IClientIoProvider.SO_SNDBUF, Integer.class);
		SUPPORTED_OPTIONS.put(IClientIoProvider.SO_REUSEADDR, Boolean.class);
		SUPPORTED_OPTIONS.put(IClientIoProvider.SO_KEEPALIVE, Boolean.class);
		SUPPORTED_OPTIONS.put(IClientIoProvider.TCP_NODELAY, Boolean.class);
		SUPPORTED_OPTIONS.put(IClientIoProvider.SO_LINGER, Integer.class);
	}

	
	
	private String id = null; 
	private int idleTimeout = 0;
	private int connectionTimeout = 0;
	

	
	// queues
	private final LinkedList<ByteBuffer> sendQueue = new LinkedList<ByteBuffer>();
	private LinkedList<ByteBuffer> readQueue = new LinkedList<ByteBuffer>(); 

	
	// grizzly
	private SocketChannel socketChannel = null;
	private TCPSelectorHandler selectorHandler = null;
	private SelectionKey key = null;
	

	// xSocket
	private IIoHandlerCallback callback = null;
	private IIoHandlerContext handlerContext = null;

	
	
	private IMemoryManager memoryManager = null;
	private boolean suspendRead = false;
	private int socketReadBufferSize = 0;
	
	
	
	
	public IoHandler(IIoHandlerContext handlerContex, String id, TCPSelectorHandler selectorHandler, SocketChannel socketChannel, IMemoryManager memoryManager) throws IOException {
		this.handlerContext = handlerContex;
		this.id = id;
		this.selectorHandler = selectorHandler;
		this.socketChannel = socketChannel;
		this.memoryManager = memoryManager;   
		
		key = socketChannel.keyFor(selectorHandler.getSelector());
		
		socketReadBufferSize = socketChannel.socket().getReceiveBufferSize();
	}
	
	
	

	///////////////////////////////////////////////////
	// Grizzly CallbackHandler methods
	
	public void onConnect(IOEvent<Context> ioEvent) {
		System.out.println("on connect");
	}

	
	public void onRead(IOEvent<Context> ioEvent) {
		
		if (!suspendRead) {
			
			// TODO: better (sync free?) solution
			// the memory manager used here is a global memory manager with synchronized methods. A
			// specific memory manager for each (worker!) thread would end in preallocation (to much?) memory. 
			// In which way will a high efficient memory management supported by grizzly (to replace this solution)  
	        ByteBuffer readBuffer = memoryManager.acquireMemory(32);  // min 32 byte
	        
	        
	        try {
	            int size = socketChannel.read(readBuffer);
	
	            if (size > 0) {
		            readBuffer.flip();
		            ByteBuffer read = readBuffer.slice();
		            
		            readBuffer.position(readBuffer.limit());
		            readBuffer.limit(readBuffer.capacity());
		            ByteBuffer remaining = readBuffer.slice();
		            
		            memoryManager.recycleMemory(remaining, (socketReadBufferSize * 10));
		            
		            
		            	
		            if (LOG.isLoggable(Level.FINE)) {
		            	LOG.fine("[" + getId() + "] received " + DataConverter.toTextOrHexString(read.duplicate(),"US-ASCII", 100));
		            }
		
		            synchronized (readQueue) {
		        		readQueue.add(read);
		        	}

		            
		            if (handlerContext.isAppHandlerListenForDataEvent()) {
		            	if (handlerContext.isAppHandlerThreadSafe()) {
		            		callback.onDataRead();
		            	} else {
		            		synchronized (this) {
		            			callback.onDataRead();
							}
		            	}
		            }

		        	
		            // enable OP_READ 
		            key.attach(this);
		            selectorHandler.register(key, SelectionKey.OP_READ);

		        	
	            } else if (size == 0) {
		            memoryManager.recycleMemory(readBuffer, socketReadBufferSize);
	            	
		            // enable OP_READ 
		            key.attach(this);
		            selectorHandler.register(key, SelectionKey.OP_READ);

		            
	            } else if (size == -1) {
		            memoryManager.recycleMemory(readBuffer, socketReadBufferSize);
	            	close(true);
	            }
	
	            
	        } catch (IOException ex) {
	        	try {
	        		close(true);
	        	} catch (IOException ignore) { }
	        }
	        
	        
		} 
	}
	
	
	public void onWrite(IOEvent<Context> ioEvent) {
		
    	if (LOG.isLoggable(Level.FINE)) {
    		int size = 0;
    		List<ByteBuffer> copy = new ArrayList<ByteBuffer>();
    		synchronized (sendQueue) {
    			for (ByteBuffer buffer : sendQueue) {
					copy.add(buffer.duplicate());
					size += buffer.remaining();
				}
			}
    		
    		LOG.fine("[" + getId() + "] try to write " + DataConverter.toFormatedBytesSize(size) + ": "+ DataConverter.toTextAndHexString(copy.toArray(new ByteBuffer[copy.size()]), "US-ASCII", 100));
    	}

		
		while (!sendQueue.isEmpty()) {
			ByteBuffer byteBuffer = null;
			synchronized (sendQueue) {
				byteBuffer = sendQueue.removeFirst();
			}
			
			try {
				byteBuffer = writeBuffer(byteBuffer);
				
				// not all data written?
				if (byteBuffer.remaining() > 0) {
					synchronized (sendQueue) {
						sendQueue.addFirst(byteBuffer);
					}
					
					selectorHandler.register(key, SelectionKey.OP_WRITE);
					break; 
					
				// all data written	
				} else {
					callback.onWritten();
				}
				
				
			} catch (IOException ioe) {
				
				// if current buffer has remaining -> return to send queue 
				if (byteBuffer.remaining() > 0) {
					synchronized (sendQueue) {
						sendQueue.addFirst(byteBuffer);
					}
				}
				
				callback.onWriteException(ioe);
			}
		}
	}
	
	
	private ByteBuffer writeBuffer(ByteBuffer byteBuffer) throws IOException {
		int nWrite = 1;
		int totalWriteBytes = 0;
		
		while (nWrite > 0 && byteBuffer.hasRemaining()){
			nWrite = socketChannel.write(byteBuffer);
			totalWriteBytes += nWrite;
			
        	if (LOG.isLoggable(Level.FINE)) {
        		LOG.fine("[" + getId() + "] " + nWrite + " bytes written");
        	}
		}
	            
		if (totalWriteBytes == 0 && byteBuffer.hasRemaining()){
			key.attach(this);
			selectorHandler.register(key, SelectionKey.OP_WRITE);
			
		} 
		
		return byteBuffer;
	}
	
	
	// Grizzly
	///////////////////////////////////////////////////

	
	
	/**
	 * {@inheritDoc}
	 */
	public void init(IIoHandlerCallback callback) throws IOException {
		this.callback = callback;
		
		// TODO use a worker thread to perform callback method  
		if (handlerContext.isAppHandlerListenForConnectEvent()) {
			if (handlerContext.isAppHandlerThreadSafe()) {
				callback.onConnect();
			} else {
				synchronized (this) {
					callback.onConnect();
				}
			}
		}
	}

	
	
	/**
	 * {@inheritDoc}
	 */
	public boolean isOpen() {
		return socketChannel.isOpen();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void close(boolean immediate) throws IOException {
		
		if (immediate) {
			realClose();
		}
	}
	
	
	private void realClose() {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + getId() + "] closing socketChannel");
		}
		try {
			socketChannel.close();
		} catch (Exception ignore) { }
	}


	/**
	 * {@inheritDoc}
	 */
	public LinkedList<ByteBuffer> drainIncoming() {
		LinkedList<ByteBuffer> result = null;
		
		synchronized (readQueue) {
			result = readQueue;
			readQueue = new LinkedList<ByteBuffer>();
		}
		
		return result;
	}


	/**
	 * {@inheritDoc}
	 */
	public void writeOutgoing(ByteBuffer buffer) throws ClosedConnectionException, IOException {

		synchronized (sendQueue) {
 			sendQueue.offer(buffer);
 		}
		
		selectorHandler.register(key, SelectionKey.OP_WRITE);
	}

	
	/**
	 * {@inheritDoc}
	 */
	public void writeOutgoing(LinkedList<ByteBuffer> buffers) throws ClosedConnectionException, IOException {
		
		synchronized (sendQueue) {
			sendQueue.addAll(buffers);
 		}

		selectorHandler.register(key, SelectionKey.OP_WRITE);
	}


	/**
	 * {@inheritDoc}
	 */
	public int getPendingWriteDataSize() {
		int size = 0;
		synchronized (sendQueue) {
			for (ByteBuffer buffer : sendQueue) {
				size += buffer.remaining();
			}
		}
		
		return size;
	}
	

	
	/**
	 * {@inheritDoc}
	 */
	public InetAddress getLocalAddress() {
		return socketChannel.socket().getLocalAddress();
	}

	
	/**
	 * {@inheritDoc}
	 */
	public int getLocalPort() {
		return socketChannel.socket().getLocalPort();
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public InetAddress getRemoteAddress() {
		return socketChannel.socket().getInetAddress();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getRemotePort() {
		return socketChannel.socket().getPort();
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public void resumeRead() throws IOException {
		suspendRead = false;
		selectorHandler.register(key, SelectionKey.OP_READ);
	}

	
	/**
	 * {@inheritDoc}
	 */
	public void suspendRead() throws IOException {
		suspendRead = true;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public String getId() {
		return id;
	}	


	
    
	/**
	 * {@inheritDoc}
	 */
	public void setOption(String name, Object value) throws IOException {
	
		if (name.equals(IClientIoProvider.SO_SNDBUF)) {
			socketChannel.socket().setSendBufferSize((Integer) value);
			
		} else if (name.equals(IClientIoProvider.SO_REUSEADDR)) {
			socketChannel.socket().setReuseAddress((Boolean) value);

		} else if (name.equals(IClientIoProvider.SO_RCVBUF)) {
			socketChannel.socket().setReceiveBufferSize((Integer) value);

		} else if (name.equals(IClientIoProvider.SO_KEEPALIVE)) {
			socketChannel.socket().setKeepAlive((Boolean) value);

		} else if (name.equals(IClientIoProvider.SO_LINGER)) {
			if (value instanceof Integer) {
				socketChannel.socket().setSoLinger(true, (Integer) value);
			} else if (value instanceof Boolean) {
				if (((Boolean) value).equals(Boolean.FALSE)) {
					socketChannel.socket().setSoLinger(Boolean.FALSE, 0);
				}
			}

		} else if (name.equals(IClientIoProvider.TCP_NODELAY)) {
			socketChannel.socket().setTcpNoDelay((Boolean) value);

			
		} else {
			LOG.warning("option " + name + " is not supproted for " + this.getClass().getName());
		}
	}
	
    
    

	/**
	 * {@inheritDoc}
	 */	
	public Object getOption(String name) throws IOException {

		if (name.equals(IClientIoProvider.SO_SNDBUF)) {
			return socketChannel.socket().getSendBufferSize();
			
		} else if (name.equals(IClientIoProvider.SO_REUSEADDR)) {
			return socketChannel.socket().getReuseAddress();
			
		} else if (name.equals(IClientIoProvider.SO_RCVBUF)) {
			return socketChannel.socket().getReceiveBufferSize();

		} else if (name.equals(IClientIoProvider.SO_KEEPALIVE)) {
			return socketChannel.socket().getKeepAlive();
			
		} else if (name.equals(IClientIoProvider.TCP_NODELAY)) {
			return socketChannel.socket().getTcpNoDelay();

		} else if (name.equals(IClientIoProvider.SO_LINGER)) {
			return socketChannel.socket().getSoLinger();

			
		} else {
			LOG.warning("option " + name + " is not supproted for " + this.getClass().getName());
			return null;
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public Map<String, Class> getOptions() {
		return Collections.unmodifiableMap(SUPPORTED_OPTIONS);
	}
  

	
	
	/**
	 * {@inheritDoc}
	 */
	public void setConnectionTimeoutSec(int timeout) {
		this.connectionTimeout = timeout;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getConnectionTimeoutSec() {
		return connectionTimeout;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void setIdleTimeoutSec(int timeout) {
		this.idleTimeout = timeout;
	}

	
	/**
	 * {@inheritDoc}
	 */
	public int getIdleTimeoutSec() {
		return idleTimeout;
	}	
}
