// $Id: IoSocketHandler.java 785 2007-01-16 10:28:22Z grro $
/*
 *  Copyright (c) xsocket.org, 2006-2007. All rights reserved.
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
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.ClosedConnectionException;
import org.xsocket.Dispatcher;
import org.xsocket.IEventHandler;
import org.xsocket.IHandle;
import org.xsocket.DataConverter;
import org.xsocket.WorkerPool;



/**
 * Socket based io handler
 * 
 * @author grro@xsocket.org
 */
final class IoSocketHandler extends IoHandler implements IHandle {

	private static final Logger LOG = Logger.getLogger(IoSocketHandler.class.getName());
	
	
	private static final int DEFAULT_MEMORY_PREALLOCATION_SIZE = 65536;
		
	
	// defaults
	private static IMemoryManager defaultMemoryManager = null;
	private static Dispatcher<IoSocketHandler> defaultDispatcher = null;	
	public static final int DEFAULT_READ_MEMORY_MIN_SIZE = 128;

	// flag
	private boolean isLogicalOpen = true;
	private boolean isDisconnectNotified = false;
	
	// socket
	private SocketChannel channel = null; 
	
	// distacher
	private Dispatcher dispatcher = null;
	
	// handlers
	private IIOEventHandler ioEventHandler = null;
	
	// read & write queue
	private final ByteBufferQueue sendQueue = new ByteBufferQueue();
	private final ByteBufferQueue receiveQueue = new ByteBufferQueue();
	
	// worker pool
	private WorkerPool workerPool = null;
	
	// memory management
	private IMemoryManager memoryManager = null;
    
    // id
	private String id = null;
	private static long nextId = 0;
	private static String idPrefix = null;

	
	// statistics
	private long openTime = -1;
	private long lastTimeReceived = System.currentTimeMillis();
	private long receivedBytes = 0;
	private long sendBytes = 0;
	
	
    static {
    	String base = null;
    	try {
    		base = InetAddress.getLocalHost().getCanonicalHostName();
    	} catch (Exception e) {
    		base = "locale";
    	}
  
   		int random = 0;
   		do {
   			random = new Random().nextInt();
   		} while (random < 0);
   		idPrefix = Integer.toHexString(base.hashCode()) + "." + Long.toHexString(System.currentTimeMillis()) + "." + Integer.toHexString(random);
    }


	
	/**
	 * constructor 
	 * 
	 * @param channel        the underlying channel
	 * @param idLocalPrefix  the id namespace prefix
	 * @param memoryManager  the memory manager to use
	 * @param dispatcher     the assigned dispatcher
	 * @param workerPool     the worker pool to use
	 * @throws IOException If some other I/O error occurs
	 */
    @SuppressWarnings("unchecked")
	IoSocketHandler(SocketChannel channel, String idLocalPrefix, IMemoryManager memoryManager, Dispatcher<IoSocketHandler> dispatcher, WorkerPool workerPool) throws IOException {
   	   	super(null);
   	   	
    	assert (channel != null);
    	this.channel = channel;
    	
    	openTime = System.currentTimeMillis();
    	
    	if (memoryManager != null) {
    		this.memoryManager = memoryManager;
    	} else {
    		this.memoryManager = getDefaultMemoryManager();
    	}
    	
    	if (dispatcher != null) {
    		this.dispatcher = dispatcher;
    	} else {
    		this.dispatcher = getDefaultDispatcher();
    	}
    	
   		this.workerPool = workerPool;
		
		channel.configureBlocking(false);		
		
		id = idPrefix + "." + idLocalPrefix + nextLocalId();	
	}

    
	/**
	 * {@inheritDoc}
	 */
    @SuppressWarnings("unchecked")
	@Override
    void open() throws IOException {
    	assert (ioEventHandler != null) : "ioHandler hasn't been set";
    	
		dispatcher.register(this);    	
    }
    
     
    /**
     * return the assigned memory manager 
     * 
     * @return the assigned memory manager
     */
    IMemoryManager getMemoryManager() {
    	return memoryManager;
    }
    
    
	/**
	 * {@inheritDoc}
	 */
    @Override
    void setIOEventHandler(IIOEventHandler ioEventHandler) {
    	this.ioEventHandler = ioEventHandler;
    }
	
	/**
	 * {@inheritDoc}
	 */
    @Override
    IIOEventHandler getIOEventHandler() {
    	return ioEventHandler;
    }
    
    
	private synchronized long nextLocalId() {
		nextId++;
		if (nextId < 0) {
			nextId = 1;
		}
		return nextId;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	final String getId() {
		return id;
	}
	

	/**
	 * check if the underyling connection is idle timed out
	 * 
	 * @param current   the current time
	 * @param timeout   the timeout time
	 * @return true, if the connection has been timed out
	 */
	boolean checkIdleTimeout(Long current, long timeout) {
		boolean timeoutReached = (lastTimeReceived + timeout) < current;
		if (timeoutReached) {
			ioEventHandler.onIdleTimeout();
		}
		return timeoutReached;	 
	}
	
	
	/**
	 * check if the underyling connection is timed out
	 * 
	 * @param current   the current time
	 * @param timeout   the timeout time
	 * @return true, if the connection has been timed out
	 */
	boolean checkConnectionTimeout(Long current, long timeout) { 
		boolean timeoutReached = (openTime + timeout) < current;
		if (timeoutReached) {
			ioEventHandler.onConnectionTimeout();
		}
		return timeoutReached;	 
	}

	
	/**
	 * return the size of the read queue
	 * 
	 * @return the read queue size
	 */
	int getIncomingQueueSize() {
		return receiveQueue.getSize();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	@Override
	void writeOutgoing(ByteBuffer buffer) {
		if (buffer != null) {
			sendQueue.append(buffer);
			dispatcher.announceWriteNeed(this);
		}
	}

	
	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	@Override
	void writeOutgoing(LinkedList<ByteBuffer> buffers) {
		if (buffers != null) {
			sendQueue.append(buffers);
			dispatcher.announceWriteNeed(this);
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	LinkedList<ByteBuffer> drainIncoming() {
		return receiveQueue.drain();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	@Override
	void close() throws IOException {
		isLogicalOpen = false;
		dispatcher.announceWriteNeed(this);
	}

	
	/**
	 * {@inheritDoc}
	 */
	@Override
	boolean isOpen() {
		return channel.isOpen();
	}

	
	/**
	 * return the underlying channel
	 * 
	 * @return the underlying channel
	 */
	public SelectableChannel getChannel() {
		return channel;
	}
	

	/**
	 * reads socket into read queue
	 * 
	 * @return the number of read bytes
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	protected final int readSocketIntoReceiveQueue() throws IOException {
		int read = 0;
		lastTimeReceived = System.currentTimeMillis();

		
		if (isOpen()) {
			
			ByteBuffer readBuffer = memoryManager.acquireMemory(DEFAULT_READ_MEMORY_MIN_SIZE);
			int pos = readBuffer.position();
			int limit = readBuffer.limit();

			// read from channel
			try {
				read = channel.read( readBuffer);
			// exception occured while reading
			} catch (IOException ioe) {
				readBuffer.position(pos);
				readBuffer.limit(limit);
				memoryManager.recycleMemory(readBuffer);

	 			if (LOG.isLoggable(Level.FINE)) {
	 				LOG.fine("[" + getId() + "] error occured while reading channel: " + ioe.toString());
	 			}
				
				throw ioe;
			}


			// handle read
			switch (read) {

				// end-of-stream has been reached -> throw exception
				case -1:
					// end-of-stream has been reached -> do nothing
					memoryManager.recycleMemory(readBuffer);
					ClosedConnectionException cce = new ClosedConnectionException("[" + id + "] End of stream reached");
					throw cce;

				// no bytes read
				case 0:
					memoryManager.recycleMemory(readBuffer);
					break;

                // bytes available (read < -1 is not handled)
				default:					
					int savePos = readBuffer.position();
					int saveLimit = readBuffer.limit();
						
					readBuffer.position(savePos - read);
					readBuffer.limit(savePos);
	
					ByteBuffer readData = readBuffer.slice();
					receiveQueue.append(readData);


					if (readBuffer.hasRemaining()) {
						readBuffer.position(savePos);
						readBuffer.limit(saveLimit);
						memoryManager.recycleMemory(readBuffer);
					}
					
		 			if (LOG.isLoggable(Level.FINE)) {
		 				LOG.fine("[" + getId() + "] received (" + (readData.limit() - readData.position()) + " bytes): " + DataConverter.toTextOrHexString(new ByteBuffer[] {readData.duplicate() }, "UTF-8", 500));
		 			}
					break;
			}
		}		
		
		
		receivedBytes += read;
		
		return read;
	}

	/**
	 * writes the content of the send queue to the socket
	 * 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	@SuppressWarnings("unchecked")
	protected final void writeSendQueueDataToSocket() throws IOException {
				
		if (isOpen() && !sendQueue.isEmpty()) {

 			if (LOG.isLoggable(Level.FINE)) {
 				LOG.fine("[" + getId() + "] sending: " + sendQueue.toString());
 			}
			
			LinkedList<ByteBuffer> data = sendQueue.drain();
			sendBytes += channel.write(data.toArray(new ByteBuffer[data.size()]));
			
			// size to send was more than the socket 
			// buffer output size accepts
			ListIterator<ByteBuffer> it = data.listIterator(data.size());
			while (it.hasPrevious()) {
				ByteBuffer buf = it.previous();
				if (buf.hasRemaining()) {
					sendQueue.addFirst(buf);
				}
			}
			
			if (!sendQueue.isEmpty()) {
				dispatcher.announceWriteNeed(this);
			}	
		} 
	}

	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public final InetAddress getLocalAddress() {
		return channel.socket().getLocalAddress();
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final int getLocalPort() {
		return channel.socket().getLocalPort();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public final InetAddress getRemoteAddress() {
		return channel.socket().getInetAddress();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public final int getRemotePort() {
		return channel.socket().getPort();
	}

	
	/**
	 * {@inheritDoc}
	 */
	@Override
	void flushOutgoing() {
		
	}
	
	
	/**
	 * create a dispatcher 
	 * @param disptacherName  the disptacher name
	 * @return the dispatcher
	 */

    static Dispatcher<IoSocketHandler> createDispatcher(String disptacherName) {
   		return new Dispatcher<IoSocketHandler>(disptacherName, new DispatcherEventHandler());
    }
	
    
    private static Dispatcher<IoSocketHandler> getDefaultDispatcher() {
    	if (defaultDispatcher == null) {
    		defaultDispatcher = IoSocketHandler.createDispatcher("default");
   			new Thread(defaultDispatcher).start();
    	}
    	return defaultDispatcher;
    }

    
   private static IMemoryManager getDefaultMemoryManager() {
    	if (defaultMemoryManager == null) {
    		defaultMemoryManager = new MemoryManager(DEFAULT_MEMORY_PREALLOCATION_SIZE, true);
    	}
    	return defaultMemoryManager;
    }
   
   
	/**
	 * {@inheritDoc}
	 */
   	@Override
	public String toString() {
   		return channel.socket().getInetAddress().toString() + ":" + channel.socket().getPort()
   		       + " received=" + DataConverter.toFormatedBytesSize(receivedBytes)  
   		       + ", sent=" + DataConverter.toFormatedBytesSize(sendBytes) 
   		       + ", age=" + DataConverter.toFormatedDuration(System.currentTimeMillis() - openTime)
   		       + ", lastReceived=" + DataConverter.toFormatedDate(lastTimeReceived)
   		       + " [" + id + "]"; 
	}
			
   
	private static final class DispatcherEventHandler implements IEventHandler<IoSocketHandler> {
		
		public void onHandleReadableEvent(final IoSocketHandler socketIOHandler) throws IOException {			
			try {
				
				// read data from socket
				socketIOHandler.readSocketIntoReceiveQueue();	
				
				
				// handle it, if eventhandler is interested and data isavailable
				if (socketIOHandler.ioEventHandler.listenForData() && !socketIOHandler.receiveQueue.isEmpty()) {
					socketIOHandler.workerPool.execute(new Runnable() {
						public void run() {
							synchronized (socketIOHandler) {
								if (socketIOHandler.receiveQueue.getSize() > 0) {
									socketIOHandler.ioEventHandler.onDataEvent();
								}
							}
						}
					});
				}
			} catch (Throwable t) {
				socketIOHandler.close();
			}
		}
		
		
		@SuppressWarnings("unchecked")
		public void onHandleWriteableEvent(final IoSocketHandler socketIOHandler) throws IOException {
			
			// write data to socket 
			socketIOHandler.writeSendQueueDataToSocket();
			
			// close handling (-> close() leads automatically to write) 
			if (!socketIOHandler.isLogicalOpen) {
				
				// send queue is emtpy -> close can be completed
				if (socketIOHandler.sendQueue.isEmpty()) {
								
					// deregister handler and close SocketChannel 
					try {
						socketIOHandler.dispatcher.deregister(socketIOHandler);
						socketIOHandler.channel.close();
					} catch (Exception e) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("error occured by closing connection. reason: " + e.toString());
						}
					}

					// notify event handler if interested
					socketIOHandler.workerPool.execute(new Runnable() {
						public void run() {
							synchronized (socketIOHandler) {
								if (socketIOHandler.ioEventHandler.listenForDisconnect() & !socketIOHandler.isDisconnectNotified) {
									socketIOHandler.isDisconnectNotified = true;
									socketIOHandler.ioEventHandler.onDisconnectEvent();
								}
							}							
						}
					});

				// there are remaining data to send -> announce write demand  
				} else {
					socketIOHandler.dispatcher.announceWriteNeed(socketIOHandler);
				}
			}					
		}

		
		public void onDispatcherCloseEvent(final IoSocketHandler socketIOHandler) {

			socketIOHandler.workerPool.execute(new Runnable() {
				public void run() {
					synchronized (socketIOHandler) {
						try {
							socketIOHandler.close();
						} catch (IOException ignore) { }			
					}
				}
			});
		}

		
		public void onHandleRegisterEvent(final IoSocketHandler socketIOHandler) throws IOException {
			
			if (socketIOHandler.ioEventHandler.listenForConnect()) {
				socketIOHandler.workerPool.execute(new Runnable() {
					public void run() {
						synchronized (socketIOHandler) {
							socketIOHandler.ioEventHandler.onConnectEvent();
						}
					}
				});
			}
		}		
	}	
}
