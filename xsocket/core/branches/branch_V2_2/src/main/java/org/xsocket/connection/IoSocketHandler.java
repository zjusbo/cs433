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


	// write processor
	private int maxChunkSize = 0;
	private AbstractWriteTask writeTask = null;
	
	
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
	IoSocketHandler(SocketChannel channel, IoSocketDispatcher dispatcher, String connectionId) throws IOException {
   	   	super(null);

    	assert (channel != null);
    	this.channel = channel;

    	openTime = System.currentTimeMillis();

		channel.configureBlocking(false);

		this.dispatcher = dispatcher;
    	this.id = connectionId;    	
    	
    	maxChunkSize = channel.socket().getSendBufferSize();
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
	public boolean isSecure() {
		return false;
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
		

		// handle the data
		if (received != null) {
			int size = 0;
			for (ByteBuffer byteBuffer : received) {
				size += byteBuffer.remaining();
			}
			read += size;
			
			getPreviousCallback().onData(received, size);
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
		

		// remaining data to write
		if (writeTask != null) {
			dispatcher.initializeWrite(this, false);
		}
		

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
			
			dispatcher.initializeWrite(this, false);
		}

		
		return sent;
	}



	long onWriteableEvent() throws IOException {
		assert (Thread.currentThread().getName().startsWith(IoSocketDispatcher.DISPATCHER_PREFIX));

		int sent = 0;
		
		// write data to socket
		sent = writeSocket();


		// all data sent?
		if (sendQueue.isEmpty() && (writeTask == null)) {
			dispatcher.unsetWriteSelectionKeyNow(this, true);
			
			// should be closed physically?
			if (isLogicalClosed) {
				realClose();
			}

		// .. no, remaining data to send
		} else {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + id + "] remaining data to send. initiate sending of the remaining (" + DataConverter.toFormatedBytesSize(sendQueue.getSize()) + ")");
			}
			
			dispatcher.initializeWrite(this, false);
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
			dispatcher.initializeWrite(this, true);
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
	public void close(boolean immediate) throws IOException {
		
		if (immediate || sendQueue.isEmpty()) {
			realClose();

		} else {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("postpone close until remaning data to write (" + sendQueue.getSize() + ") has been written");
			}
	
			isLogicalClosed = true;
			dispatcher.initializeWrite(this, true);	
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

					received = new ByteBuffer[1];
					received[0] = dataBuffer;

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
	private int writeSocket() throws IOException {

		int sent = 0;

		if (isOpen()) {
			
			if (writeTask == null) {
				writeTask = TaskFactory.newTask(sendQueue, maxChunkSize);
			}
			
			int written = writeTask.write(this);
			sent += written;
			sendBytes += written;
				
			if (!writeTask.hasUnwrittenData()) {
				writeTask = null;
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
	public InetAddress getLocalAddress() {
		return channel.socket().getLocalAddress();
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getLocalPort() {
		return channel.socket().getLocalPort();
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public InetAddress getRemoteAddress() {
		InetAddress addr = channel.socket().getInetAddress();
		return addr;
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getRemotePort() {
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
   			SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss,S");

	   		return "(" + channel.socket().getInetAddress().toString() + ":" + channel.socket().getPort()
	   			   + " -> " + channel.socket().getLocalAddress().toString() + ":" + channel.socket().getLocalPort() + ")"
	   		       + " received=" + DataConverter.toFormatedBytesSize(receivedBytes)
	   		       + ", sent=" + DataConverter.toFormatedBytesSize(sendBytes)
	   		       + ", age=" + DataConverter.toFormatedDuration(System.currentTimeMillis() - openTime)
	   		       + ", lastReceived=" + df.format(new Date(lastTimeReceivedMillis))
	   		       + ", sendQueueSize=" + DataConverter.toFormatedBytesSize(sendQueue.getSize())
	   		       + " [" + id + "]";
   		} catch (Throwable e) {
   			return super.toString();
   		}
	}
   	
   	
   	
   	private static final class TaskFactory {
   		
   		private static ThreadLocal<EmptyWriteTask> emptyWriteTaskThreadLocal = new ThreadLocal<EmptyWriteTask>();
   		private static ThreadLocal<MergingWriteProcessor> mergingWriteTaskThreadLocal = new ThreadLocal<MergingWriteProcessor>();
   		private static ThreadLocal<DirectWriteProcessor> directWriteTaskThreadLocal = new ThreadLocal<DirectWriteProcessor>();
   		
   		
   		static EmptyWriteTask getEmptyWriteTask() {
   			EmptyWriteTask emptyWriteTask = emptyWriteTaskThreadLocal.get();
   			if (emptyWriteTask == null) {
   				emptyWriteTask = new EmptyWriteTask();
   				emptyWriteTaskThreadLocal.set(emptyWriteTask);
   			}
   			
   			return emptyWriteTask;
   		}
   		
   		static MergingWriteProcessor getMergingWriteProcessor() {
   			MergingWriteProcessor meringWriteProcessor = mergingWriteTaskThreadLocal.get();
   			if (meringWriteProcessor == null) {
   				meringWriteProcessor = new MergingWriteProcessor();
   				mergingWriteTaskThreadLocal.set(meringWriteProcessor);
   			}
   			
   			return meringWriteProcessor;
   		}
   		
   		
   		static DirectWriteProcessor getDirectWriteProcessor() {
   			DirectWriteProcessor directWriteProcessor = directWriteTaskThreadLocal.get();
   			if (directWriteProcessor == null) {
   				directWriteProcessor = new DirectWriteProcessor();
   				directWriteTaskThreadLocal.set(directWriteProcessor);
   			}
   			
   			return directWriteProcessor;
   		}
   		
   		static void detachMergingWriteProcessor() {
   			mergingWriteTaskThreadLocal.set(null);
   		}
   		
   		static void detachDirectWriteProcessor() {
   			directWriteTaskThreadLocal.set(null);
   		}
   		
   		
   		static AbstractWriteTask newTask(IoQueue sendQueue, int maxChunkSize) {
   			
   			ByteBuffer[] buffersToWrite = sendQueue.drain(maxChunkSize);

   			// got data?
			if (buffersToWrite == null) {
				return getEmptyWriteTask();
			}
			
			if (buffersToWrite.length > 1) {
				
				MergingWriteProcessor mergingWriteProcessor = getMergingWriteProcessor();
				boolean dataToWrite = mergingWriteProcessor.addData(buffersToWrite, maxChunkSize);
				if (dataToWrite) {
					return mergingWriteProcessor;
				} else {
					return getEmptyWriteTask();
				}
				
			} else {

				DirectWriteProcessor directWriteProcessor = getDirectWriteProcessor();
				boolean dataToWrite = directWriteProcessor.addData(buffersToWrite[0]);
				if (dataToWrite) {
					return directWriteProcessor;
				} else {
					return getEmptyWriteTask();
				}
			}
   			
   		}
   		
   	}
   	
   	
   	private static abstract class AbstractWriteTask {
   		
   		abstract boolean hasUnwrittenData();
   		 
   		abstract int write(IoSocketHandler handler) throws IOException;
   		
   		final void hasWritten(IoSocketHandler handler, ByteBuffer buffer) {
   			handler.getPreviousCallback().onWritten(buffer);
   		}
   		
   		final void writeErrorOccured(IoSocketHandler handler, IOException ioe, ByteBuffer buffer) {
   			if (LOG.isLoggable(Level.FINE)) {
   				LOG.fine("error " + ioe.toString() + " occured by writing " + ioe.toString());
   			}
					
   			try {
   				handler.getPreviousCallback().onWriteException(ioe, buffer);
   			} catch (Exception e) {
   				if (LOG.isLoggable(Level.FINE)) {
   					LOG.fine("error occured by notifying that write exception (" + e.toString() + ") has been occured " + e.toString());
   				}
   			}
   		}
   	}
   	
   	
   	
   	private static final class EmptyWriteTask extends AbstractWriteTask {
   		
   		
   		@Override
   		boolean hasUnwrittenData() {
   			return false;
   		}
   		
   		@Override
   		int write(IoSocketHandler handler) throws IOException {
   			return 0;
   		}
   	}
   	
   	
   	
   	private static final class MergingWriteProcessor extends AbstractWriteTask {

   		private int writeBufferSize = 8192;
   		private ByteBuffer writeBuffer = ByteBuffer.allocateDirect(writeBufferSize);
   		private boolean hasUnwrittenData = false; 
   		private ByteBuffer[] buffersToWrite = null;

   		
	
   		
   		boolean hasUnwrittenData() {
   			return hasUnwrittenData;  
   		}


   		boolean addData(ByteBuffer[] buffers, int maxChunkSize) {
   			
   			buffersToWrite = buffers;
   			
   			if (writeBufferSize < maxChunkSize) {
   				writeBufferSize = maxChunkSize;
   				writeBuffer = ByteBuffer.allocateDirect(writeBufferSize);
   			}
   			
			// copying data
			int countBuffers = buffersToWrite.length; 
			for (int i = 0; i < countBuffers; i++) {
				writeBuffer.put(buffersToWrite[i]);
			}
   			
   			// nothing to write?
   			if (writeBuffer.position() == 0) {
   				return false;
   			}
				
   			writeBuffer.flip();	
   
   			return true;
   		}
   		
   		
   		
   		int write(IoSocketHandler handler) throws IOException {
   			
   			int written = 0;
   			
   			try {
   				written = handler.channel.write(writeBuffer);
		   			
   				if (!writeBuffer.hasRemaining()) {
   		   			for (ByteBuffer buffer : buffersToWrite) {
   		   				hasWritten(handler, buffer);
   		   			}
   					
   					buffersToWrite = null;
   					writeBuffer.clear();
   					hasUnwrittenData = false;
   					
   				} else {
   					hasUnwrittenData = true;
   					TaskFactory.detachMergingWriteProcessor();
   				}
	
   			} catch(IOException ioe)  {
   				
   				writeBuffer.clear();
   				hasUnwrittenData = false;
   				
   				for (ByteBuffer buffer : buffersToWrite) {
   					writeErrorOccured(handler, ioe, buffer);
   				}
   				
   				buffersToWrite = null;
   				
   			}
	   			
   			return written;
   		}
   	}
   	
  
 	private static final class DirectWriteProcessor extends AbstractWriteTask {

   		private boolean hasUnwrittenData = false; 
   		private ByteBuffer bufferToWrite = null;
   	
   		
   		boolean addData(ByteBuffer bufferToWrite) {
   			if (bufferToWrite.hasRemaining()) { 
   				this.bufferToWrite = bufferToWrite;
   				return true;
   				
   			} else {
   				return false;
   			}
   		}
   
   		
   		boolean hasUnwrittenData() {
   			return hasUnwrittenData;  
   		}

   		
   		
   		int write(IoSocketHandler handler) throws IOException {
   			
   			int written = 0;
   			
   			try {
   				written = handler.channel.write(bufferToWrite);
		   			
   				if (!bufferToWrite.hasRemaining()) {
   					hasWritten(handler, bufferToWrite);
   					
   					bufferToWrite = null;
   					hasUnwrittenData = false;
		   				
   				} else {
   					hasUnwrittenData = true;
   					TaskFactory.detachDirectWriteProcessor();
   					
   				}
	
   			} catch(IOException ioe)  {
   				
   				bufferToWrite = null;
   				hasUnwrittenData = false;
   				
   				writeErrorOccured(handler, ioe, bufferToWrite);
   			}
	   			
   			return written;
   		}
   	}
}