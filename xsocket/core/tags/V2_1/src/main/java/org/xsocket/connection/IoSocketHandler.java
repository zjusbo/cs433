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
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;


/**
 * Socket based io handler
 *
 * @author grro@xsocket.org
 */
final class IoSocketHandler extends IoChainableHandler {

	private static final Logger LOG = Logger.getLogger(IoSocketHandler.class.getName());
	
	private static final SimpleDateFormat DF = new SimpleDateFormat("HH:mm:ss,S");



	private static final int MAXSIZE_LOG_READ = 2000;

	@SuppressWarnings("unchecked")
	private static final Map<String, Class> SUPPORTED_OPTIONS = new HashMap<String, Class>();

	static {
		SUPPORTED_OPTIONS.put(IoProvider.SO_RCVBUF, Integer.class);
		SUPPORTED_OPTIONS.put(IoProvider.SO_SNDBUF, Integer.class);
		SUPPORTED_OPTIONS.put(IoProvider.SO_REUSEADDR, Boolean.class);
		SUPPORTED_OPTIONS.put(IoProvider.SO_KEEPALIVE, Boolean.class);
		SUPPORTED_OPTIONS.put(IoProvider.TCP_NODELAY, Boolean.class);
		SUPPORTED_OPTIONS.put(IoProvider.SO_LINGER, Integer.class);
	}


	// flag
	private boolean isConnected = false;
	private boolean isLogicalClosed = false;
	private boolean isDisconnect = false;
	private boolean isDetached = true;

	// socket
	private SocketChannel channel = null;


	// dispatcher
	private IoSocketDispatcher dispatcher = null;


	// memory management
	private AbstractMemoryManager memoryManager = null;


	// receive & send queue
	private final IoQueue sendQueue = new IoQueue();


    // id
	private String id = null;

	
	// retry read
	private boolean isRetryRead = true;
	


	// statistics
	private long openTime = -1;
	private long lastTimeReceivedMillis = System.currentTimeMillis();
//	private long lastTimeSent = System.currentTimeMillis();
	private long receivedBytes = 0;
	private long sendBytes = 0;


	/**
	 * constructor
	 *
	 * @param channel         the underlying channel
	 * @param idLocalPrefix   the id namespace prefix
	 * @param dispatcher      the dispatcher
	 * @throws IOException If some other I/O error occurs
	 */
    @SuppressWarnings("unchecked")
	IoSocketHandler(SocketChannel channel, IoSocketDispatcher dispatcher, String connectionId) throws IOException {
   	   	super(null);

    	assert (channel != null);
    	this.channel = channel;

    	openTime = System.currentTimeMillis();

		channel.configureBlocking(false);

		this.dispatcher = dispatcher;
    	this.id = connectionId;    	
	}


    public void init(IIoHandlerCallback callbackHandler) throws IOException, SocketTimeoutException {
    	setPreviousCallback(callbackHandler);

    	// remove this? shouldn't be necessary check this
		// will only block for client-side connections (a server-side connection is already connected and returns immediately)
    	// check & wait until channel is connected 
		while (!getChannel().finishConnect()) {
			getChannel().configureBlocking(true);
			getChannel().finishConnect();
			getChannel().configureBlocking(false);
		}
		
		updateDispatcher(dispatcher);
    }
    
    
    private void updateDispatcher(IoSocketDispatcher dispatcher) throws IOException {
		this.dispatcher = dispatcher;
		dispatcher.register(this, SelectionKey.OP_READ);	
	}
    
    
    public void setRetryRead(boolean isRetryRead) {
    	this.isRetryRead = isRetryRead;
    }


    /**
     * {@inheritDoc}b
     */
    public boolean reset() {
    	try {
	    	sendQueue.drain();	    	
	    	resumeRead();
	    	
			return super.reset();
    	} catch (Exception e) {
    		return false;
    	}
    }


    void setMemoryManager(AbstractMemoryManager memoryManager) {
    	this.memoryManager = memoryManager;
    }

    
    boolean isDetached() {
    	return isDetached;
    }
    
    @Override
    public String getId() {
    	return id;
    }
    
  

    /**
     * {@inheritDoc}
     */
    @Override
    public int getPendingWriteDataSize() {
    	return sendQueue.getSize() + super.getPendingWriteDataSize();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasDataToSend() {
    	return !sendQueue.isEmpty();
    }



	/**
	 * {@inheritDoc}
	 */
	public void setOption(String name, Object value) throws IOException {
		IoProvider.setOption(channel.socket(), name, value);
	}



	/**
	 * {@inheritDoc}
	 */
	public Object getOption(String name) throws IOException {
		return IoProvider.getOption(channel.socket(), name);
	}


	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Class> getOptions() {
		return Collections.unmodifiableMap(SUPPORTED_OPTIONS);
	}



	/**
	 * check if the underlying connection is timed out
	 *
	 * @param current   the current time
	 * @return true, if the connection has been timed out
	 */
	void checkConnection() {
		if (!channel.isOpen()) {
			getPreviousCallback().onConnectionAbnormalTerminated();
		}
	}



	void setDetached(boolean isDetached) {
		this.isDetached = isDetached;
	}


	void onRegisteredEvent() throws IOException {
		if(!isConnected) {
			isConnected = true;
			getPreviousCallback().onConnect();
		}
	}


	
	long onReadableEvent() throws IOException {
		assert (Thread.currentThread().getName().startsWith(IoSocketDispatcher.DISPATCHER_PREFIX)) : "receiveQueue can only be accessed by the dispatcher thread";

		long read = 0;

		// read data from socket
		ByteBuffer [] received  = readSocket();
		
		for (ByteBuffer byteBuffer : received) {
			read += byteBuffer.remaining();
		}

		// handle the data
		if (received != null) {
			getPreviousCallback().onData(received);
			getPreviousCallback().onPostData();
		}

		// increase preallocated read memory if not sufficient
		checkPreallocatedReadMemory();

		return read;
	}


	
	long onDirectUnregisteredWriteEvent() throws IOException {
		
		// Assert -> handler is not registered  
		
		int sent = 0;
		
		// write data to socket
		sent = writeSocket();


		// all data sent?
		if (sendQueue.isEmpty()) {
			
			// should be closed physically?
			if (isLogicalClosed) {
				realClose();
			}

		// .. no, remaining data to send
		} else {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + id + "] remaining data to send. initiate sending of the remaining (" + DataConverter.toFormatedBytesSize(sendQueue.getSize()) + ")");
			}
			
			dispatcher.register(this, SelectionKey.OP_WRITE);
		}

		
		return sent;
	}



	long onWriteableEvent() throws IOException {
		assert (Thread.currentThread().getName().startsWith(IoSocketDispatcher.DISPATCHER_PREFIX));

		int sent = 0;
		
		// write data to socket
		sent = writeSocket();


		// all data sent?
		if (sendQueue.isEmpty()) {
			dispatcher.unsetWriteSelectionKeyNow(this);
			
			// should be closed physically?
			if (isLogicalClosed) {
				realClose();
			}

		// .. no, remaining data to send
		} else {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + id + "] remaining data to send. initiate sending of the remaining (" + DataConverter.toFormatedBytesSize(sendQueue.getSize()) + ")");
			}
			
			dispatcher.setWriteSelectionKey(this);
		}


		if (LOG.isLoggable(Level.FINEST)) {
			LOG.finest("[" + getId() + "] writeable event handled");
		}
		
		return sent;
	}



	@Override
	public void write(ByteBuffer[] buffers) throws ClosedChannelException, IOException {
		addToWriteQueue(buffers);
	}
	
	@Override
	public void flush() throws IOException {
		if (!sendQueue.isEmpty()) {
			dispatcher.initializeWrite(this);
		}
	}

	
	@Override
	public void addToWriteQueue(ByteBuffer[] buffers) {
		if (buffers != null) {
			sendQueue.append(buffers);
		}
	}



	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public void close(boolean immediate) throws IOException {
		
		if (immediate || sendQueue.isEmpty()) {
			realClose();

		} else {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("postpone close until remaning data to write (" + sendQueue.getSize() + ") has been written");
			}
	
			isLogicalClosed = true;
			dispatcher.initializeWrite(this);	
		}
	}
	
	
	void closeSilence(boolean immediate) {
		try {
			close(immediate);
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by closing " + getId() + " " + DataConverter.toString(ioe));
			}
		}
	}




	private void realClose() {
		
		try {
			dispatcher.deregister(this);
		} catch (Exception e) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by deregistering connection " + id + " on dispatcher. reason: " + e.toString());
			}
		}

		try {
			channel.close();
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("connection " + id + " has been closed");
			}
		} catch (Exception e) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by closing connection " + id + " reason: " + e.toString());
			}
		}


		if (!isDisconnect) {
			isDisconnect = true;
			getPreviousCallback().onDisconnect();
		}
	}



	/**
	 * {@inheritDoc}
	 */
	public boolean isOpen() {
		return channel.isOpen();
	}



	/**
	 * return the underlying channel
	 *
	 * @return the underlying channel
	 */
	public SocketChannel getChannel() {
		return channel;
	}




	@Override
	public void suspendRead() throws IOException {
		dispatcher.suspendRead(this);	
	}



	@Override
	public void resumeRead() throws IOException {
		dispatcher.resumeRead(this);
	}
	
	@Override
	public boolean isReadSuspended() {
		return !dispatcher.isReadable(this);
	}



	private ByteBuffer[] readSocket() throws IOException {
		assert (Thread.currentThread().getName().startsWith(IoSocketDispatcher.DISPATCHER_PREFIX)) : "receiveQueue can only be accessed by the dispatcher thread";


		ByteBuffer[] received = null;


		int read = 0;
		lastTimeReceivedMillis = System.currentTimeMillis();


		if (isOpen()) {

			assert (memoryManager instanceof IoUnsynchronizedMemoryManager);

			ByteBuffer readBuffer = memoryManager.acquireMemoryStandardSizeOrPreallocated(8192);
			int pos = readBuffer.position();
			int limit = readBuffer.limit();

			// read from channel
			try {
				read = channel.read(readBuffer);
				
						
			// exception occured while reading
			} catch (IOException ioe) {
				readBuffer.position(pos);
				readBuffer.limit(limit);
				memoryManager.recycleMemory(readBuffer);

	 			if (LOG.isLoggable(Level.FINE)) {
	 				LOG.fine("[" + id + "] error occured while reading channel: " + ioe.toString());
	 			}
	 			
				throw ioe;
			}


			// handle read
			switch (read) {

				// end-of-stream has been reached -> throw an exception
				case -1:
					memoryManager.recycleMemory(readBuffer);
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + id + "] channel has reached end-of-stream (maybe closed by peer)");
					}
					ClosedChannelException cce = new ClosedChannelException();
					throw cce;

				// no bytes read recycle read buffer and do nothing
				case 0:
					memoryManager.recycleMemory(readBuffer);
					return null;

                // bytes available (read < -1 is not handled)
				default:					
					int remainingFreeSize = readBuffer.remaining();
					ByteBuffer dataBuffer = memoryManager.extractAndRecycleMemory(readBuffer, read);

					if (received == null) {
						received = new ByteBuffer[1];
						received[0] = dataBuffer;
					}


					receivedBytes += read;

					if (LOG.isLoggable(Level.FINE)) {
	 	 				LOG.fine("[" + id + "] received (" + (dataBuffer.limit() - dataBuffer.position()) + " bytes, total " + (receivedBytes + read) + " bytes): " + DataConverter.toTextOrHexString(new ByteBuffer[] {dataBuffer.duplicate() }, "UTF-8", MAXSIZE_LOG_READ));
		 			}


					// whole read buffer has been required -> repeat the read, because there could be more data to read
					if ((remainingFreeSize == 0) && isRetryRead) {
							
						// but just i case, if already read size is smaller than the preallocation size
						if (read < memoryManager.gettPreallocationBufferSize()) {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("[" + id + "] complete read buffer has been used, initiating repeated read");
							}

							ByteBuffer[] repeatedReceived = readSocket();
							if (repeatedReceived != null) {
								ByteBuffer[] newReceived = new ByteBuffer[received.length + 1];
								newReceived[0] = dataBuffer;
								System.arraycopy(repeatedReceived, 0, newReceived, 1, repeatedReceived.length);
								received = newReceived;

								return received;

							} else {
								return received;
							}

						} else  {
							return received;
						}
					}


					return received;
			}

		} else {
			return null;
		}
	}


	/**
	 * check if preallocated read buffer size is sufficient. if not increase it
	 */
	private void checkPreallocatedReadMemory() throws IOException {
		assert (Thread.currentThread().getName().startsWith(IoSocketDispatcher.DISPATCHER_PREFIX));

		memoryManager.preallocate();
	}

	/**
	 * writes the content of the send queue to the socket
	 *
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedChannelException if the underlying channel is closed
	 */
	@SuppressWarnings("unchecked")
	private int writeSocket() throws IOException {

		int sent = 0;


		if (isOpen()) {
			ByteBuffer[] buffers = sendQueue.drain();
			if (buffers == null) {
				return 0;
			}


			boolean hasUnwrittenBuffers = false;
			
			try {
				
				for (int i = 0; i < buffers.length; i++) {

	 				// data to write for this buffer?
					if ((buffers[i] != null) && buffers[i].hasRemaining()) {
		 			
						if (LOG.isLoggable(Level.FINE)) {
				 			if (LOG.isLoggable(Level.FINE)) {
		 						LOG.fine("[" + id + "] sending (" + buffers[i].remaining() + " bytes): " + DataConverter.toTextOrHexString(buffers[i].duplicate(), "UTF-8", 500));
		 		 			}
		 				}

						
		 				// write to socket (internal out buffer)
		 				try {
		 					
			 				int written = channel.write(buffers[i]);
			 				sent += written;
			 				sendBytes += written;
 								 				
			 				// more data to write? 
			 				if (buffers[i].hasRemaining()) {
			 					hasUnwrittenBuffers = true;  // see finally block
			 					break;


			 				// ... no, all data has been written
			 				} else {
			 					try {
			 						// notify the io handler that data has been written
			 						getPreviousCallback().onWritten(buffers[i]);
			 					} catch (Exception e) {
			 						if (LOG.isLoggable(Level.FINE)) {
			 							LOG.fine("error occured by notifying that buffer has been written " + e.toString());
			 						}
			 					}

			 					buffers[i] = null;
			 				}
			 				

		 				} catch(IOException ioe)  {

		 					if (LOG.isLoggable(Level.FINE)) {
		 						LOG.fine("error " + ioe.toString() + " occured by writing " + DataConverter.toTextOrHexString(buffers[i].duplicate(), "US-ASCII", 500));
		 					}
		 					
		 					try {
		 						getPreviousCallback().onWriteException(ioe, buffers[i]);
		 					} catch (Exception e) {
		 						if (LOG.isLoggable(Level.FINE)) {
		 							LOG.fine("error occured by notifying that write exception (" + e.toString() + ") has been occured " + e.toString());
		 						}
		 					}
		 					buffers[i] = null;

		 					return sent;
		 				}
	 				}
					
				} //  for (int i = 0; i < buffers.length; i++) {
				
			} finally {

				// not all data written -> return array into (head of) queue
				if (hasUnwrittenBuffers) {
					sendQueue.addFirst(buffers);
				}
			}

			
		} else {
			if (LOG.isLoggable(Level.FINEST)) {
				if (!isOpen()) {
					LOG.finest("["  + getId() + "] couldn't write send queue to socket because socket is already closed (sendQueuesize=" + DataConverter.toFormatedBytesSize(sendQueue.getSize()) + ")");
				}

				if (sendQueue.isEmpty()) {
					LOG.finest("["  + getId() + "] nothing to write, because send queue is empty ");
				}
			}
		}

		return sent;
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
		InetAddress addr = channel.socket().getInetAddress();
		return addr;
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public final int getRemotePort() {
		return channel.socket().getPort();
	}

	public long getLastTimeReceivedMillis() {
		return lastTimeReceivedMillis;
	}

	
	public long getNumberOfReceivedBytes() {
		return receivedBytes;
	}
	
	public long getNumberOfSendBytes() {
		return sendBytes;
	}
	
	public String getRegisteredOpsInfo() {
		return dispatcher.getRegisteredOpsInfo(this);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void hardFlush() throws IOException {
		flush();
	}


	/**
	 * {@inheritDoc}
	 */
   	@Override
	public String toString() {
   		try {
	   		return "(" + channel.socket().getInetAddress().toString() + ":" + channel.socket().getPort()
	   			   + " -> " + channel.socket().getLocalAddress().toString() + ":" + channel.socket().getLocalPort() + ")"
	   		       + " received=" + DataConverter.toFormatedBytesSize(receivedBytes)
	   		       + ", sent=" + DataConverter.toFormatedBytesSize(sendBytes)
	   		       + ", age=" + DataConverter.toFormatedDuration(System.currentTimeMillis() - openTime)
	   		       + ", lastReceived=" + DF.format(new Date(lastTimeReceivedMillis))
	   		       + ", sendQueueSize=" + DataConverter.toFormatedBytesSize(sendQueue.getSize())
	   		       + " [" + id + "]";
   		} catch (Throwable e) {
   			return super.toString();
   		}
	}
}