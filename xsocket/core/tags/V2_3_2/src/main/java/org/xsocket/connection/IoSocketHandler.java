/*
 *  Copyright (c) xsocket.org, 2006 - 2009. All rights reserved.
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
	private final SocketChannel channel;


	// dispatcher
	private IoSocketDispatcher dispatcher;


	// memory management
	private AbstractMemoryManager memoryManager;


	// receive & send queue
	private final IoQueue sendQueue = new IoQueue();


	// write processor
	private int maxChunkSize = 0;
	private IWriteTask pendingWriteTask = null;
	
	
    // id
	private final String id;

	
	// retry read
	private boolean isRetryRead = true;
	


	// statistics
	private long openTime = -1;
	private long lastTimeReceivedMillis = System.currentTimeMillis();
	private long lastTimeSentMillis = System.currentTimeMillis();
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
			try {
			    getPreviousCallback().onConnect();
		     } catch (Exception e) {
	             // eat and log exception
		         if (LOG.isLoggable(Level.FINE)) {
		             LOG.fine("error occured by performing onConnect " + id + " reason: " + e.toString());
		         }
		     }
		}
	}
	
	
	void onRegisteredFailedEvent(IOException ioe) throws IOException {
	    if(!isConnected) {
            isConnected = true;
            try {
                getPreviousCallback().onConnectException(ioe);
             } catch (Exception e) {
                 // eat and log exception
                 if (LOG.isLoggable(Level.FINE)) {
                     LOG.fine("error occured by performing onConnectException " + id + " reason: " + e.toString());
                 }
             }
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


	
	void onDirectUnregisteredWriteEvent() throws IOException {
	    onWriteableEvent(false);
	}

	
	void onWriteableEvent() throws IOException {
	    onWriteableEvent(true);
	}
	

    private void onWriteableEvent(boolean isUnsetWriteSelector) throws IOException {
        assert (ConnectionUtils.isDispatcherThread());

        // write data to socket
        boolean isMoreDateToWrite = writeSocket();


        // more data to write?
        if (isMoreDateToWrite) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("[" + id + "] remaining data to send. initiate sending of the remaining (" + DataConverter.toFormatedBytesSize(sendQueue.getSize()) + ")");
            }
            dispatcher.initializeWrite(this, false, false);

        // .. no
        } else {
            if (isUnsetWriteSelector) {
                dispatcher.unsetWriteSelectionKeyNow(this, true);
            }
            
            // should be closed physically?
            if (isLogicalClosed) {
                realClose();
            }
        }

        if (LOG.isLoggable(Level.FINEST)) {
            LOG.finest("[" + getId() + "] writeable event handled");
        }
    }


	@Override
	public void write(ByteBuffer[] buffers) throws ClosedChannelException, IOException {
		addToWriteQueue(buffers);
	}
	
	
	void incSentBytes(int addSize) {
	    lastTimeSentMillis = System.currentTimeMillis();
	    sendBytes += addSize;
	    dispatcher.incSentBytes(addSize);
	    
	}
	
	@Override
	public void flush() throws IOException {
		if (!sendQueue.isEmpty()) {
			dispatcher.initializeWrite(this, true, true);
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
			dispatcher.initializeWrite(this, true, true);	
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
            // eat and log exception
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
            // eat and log exception
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by closing connection " + id + " reason: " + e.toString());
			}
		}


		if (!isDisconnect) {
			isDisconnect = true;
			try {
			    getPreviousCallback().onDisconnect();
		     } catch (Exception e) {
		         // eat and log exception
		         if (LOG.isLoggable(Level.FINE)) {
		             LOG.fine("error occured by calling onDisconnect " + id + " reason: " + e.toString());
		         }
		     }
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
	    IoSocketDispatcher.setBypassingWriteAllowed(false);
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
	 * @return true, if more date is to send
	 * 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedChannelException if the underlying channel is closed
	 */
	private boolean writeSocket() throws IOException {

		if (isOpen()) {

		    IWriteTask writeTask = null;
		    
		    
		    // does a pending write task exists (former write operation has been incomplete)? 
			if (pendingWriteTask != null) {
			    writeTask = pendingWriteTask;
			    
			// ... no, create a new one
			} else {
				writeTask = TaskFactory.newTask(sendQueue, maxChunkSize);
			} 
			
			
			
			// perform write task
			IWriteResult result = writeTask.write(this);
			
			
			
			// is write task complete?
			if (result.isAllWritten()) {
				pendingWriteTask = null;
				result.notifyWriteCallback();
				return !sendQueue.isEmpty();
				
			// no, save write task to process it again by the next writeable event
			} else {
			    pendingWriteTask = writeTask;
			    return true;
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
			
			return false; 
		}
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

	public long getLastTimeSendMillis() {
	    return lastTimeSentMillis;
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

   			StringBuilder sb = new StringBuilder();
   			sb.append("(" + channel.socket().getInetAddress().toString() + ":" + channel.socket().getPort()
			   + " -> " + channel.socket().getLocalAddress().toString() + ":" + channel.socket().getLocalPort() + ")");
   			
   			if (isReadSuspended()) {
   				sb.append(" SUSPENDED");
   			}
   			
			sb.append(" received=" + DataConverter.toFormatedBytesSize(receivedBytes)
		       + ", sent=" + DataConverter.toFormatedBytesSize(sendBytes)
		       + ", age=" + DataConverter.toFormatedDuration(System.currentTimeMillis() - openTime)
		       + ", lastReceived=" + df.format(new Date(lastTimeReceivedMillis))
		       + ", sendQueueSize=" + DataConverter.toFormatedBytesSize(sendQueue.getSize())
		       + " [" + id + "]");

   			
   			return sb.toString();
   		} catch (Throwable e) {
   			return super.toString();
   		}
	}
   	
   	
   	
   	private static final class TaskFactory {
   		
   	    private static final EmptyWriteTask EMPTY_WRITE_TASK = new EmptyWriteTask();
   	    
   	    // performance optimization -> reuse write task
   		private static ThreadLocal<MergingWriteTask> freeMergingWriteTaskThreadLocal = new ThreadLocal<MergingWriteTask>();
   		private static ThreadLocal<DirectWriteTask> freeDirectWriteTaskThreadLocal = new ThreadLocal<DirectWriteTask>();
   		
   		
   		static IWriteTask newTask(IoQueue sendQueue, int maxChunkSize) {
   			
   			ByteBuffer[] buffersToWrite = sendQueue.drain(maxChunkSize);

   			// if no data to write?
			if (buffersToWrite == null) {
				return createEmptyWriteTask();
			}
			
			
			// buffer array to write? 
			if (buffersToWrite.length > 1) {
				
				MergingWriteTask mergingWriteTask = createMergingWriteTask();
				boolean dataToWrite = mergingWriteTask.addData(buffersToWrite, maxChunkSize);
				if (dataToWrite) {
					return mergingWriteTask;
				} else {
					return createEmptyWriteTask();
				}
				
			// ... no, just a single byte buffer
			} else {

				DirectWriteTask directWriteTask = createDirectWriteTask();
				boolean dataToWrite = directWriteTask.addData(buffersToWrite[0]);
				if (dataToWrite) {
					return directWriteTask;
				} else {
					return createEmptyWriteTask();
				}
			}
   		}

   		
   	   static EmptyWriteTask createEmptyWriteTask() {
           return EMPTY_WRITE_TASK;
       }
       
       
       static MergingWriteTask createMergingWriteTask() {
           assert (ConnectionUtils.isDispatcherThread());
           
           // does a free merging write task exists?
           MergingWriteTask meringWriteTask = freeMergingWriteTaskThreadLocal.get();
           
           // ...yes, use it
           if (meringWriteTask != null) {
               freeMergingWriteTaskThreadLocal.remove();
               
           // .. no creat a new one                
           } else {
               meringWriteTask = new MergingWriteTask();
           }
           
           return meringWriteTask;
       }
       
       
       static DirectWriteTask createDirectWriteTask() {
           assert (ConnectionUtils.isDispatcherThread());
           
           // does a free direct write task exists?
           DirectWriteTask directWriteTask = freeDirectWriteTaskThreadLocal.get();
           
           // ...yes, use it
           if (directWriteTask != null) {
               freeDirectWriteTaskThreadLocal.remove();
               
           // .. no creat a new one                
           } else {
               directWriteTask = new DirectWriteTask();
           }
           
           return directWriteTask;
       }

       
       static void reuseWriteProcessor(MergingWriteTask meringWriteProcessor) {
           assert (ConnectionUtils.isDispatcherThread());
           freeMergingWriteTaskThreadLocal.set(meringWriteProcessor);
       }
       
       static void reuseWriteProcessor(DirectWriteTask directWriteProcessor) {
           assert (ConnectionUtils.isDispatcherThread());
           freeDirectWriteTaskThreadLocal.set(directWriteProcessor);
       }
   	}
   	
   	
   	
    
    private static interface IWriteTask {
        
        static final IncompleteWriteResult INCOMPLETE_WRITE_RESULT = new IncompleteWriteResult();
        static final IWriteResult EMPTY_WRITE_RESULT = new EmptyWriteResult();

        
        IWriteResult write(IoSocketHandler handler) throws IOException;
    }
    
   
	
   	private static final class EmptyWriteTask implements IWriteTask {

   		public IWriteResult write(IoSocketHandler handler) throws IOException {
   			return EMPTY_WRITE_RESULT;
   		}
   	}
   	
  	
    private static final class MergingWriteTask implements IWriteTask {

        private int writeBufferSize = 8192;
        private ByteBuffer writeBuffer = ByteBuffer.allocateDirect(writeBufferSize);
        private ByteBuffer[] buffersToWrite = null;

         
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
        
        
        
        public IWriteResult write(IoSocketHandler handler) throws IOException {
            
            try {
                int written = handler.channel.write(writeBuffer);
                handler.incSentBytes(written);  
                
                if (!writeBuffer.hasRemaining()) {
                    MultiBufferWriteResult writeResult = new MultiBufferWriteResult(handler, buffersToWrite);
                    release();
                    
                    return writeResult;
                    
                } else {
                    return INCOMPLETE_WRITE_RESULT;
                }
    
                
            } catch(final IOException ioe)  {
                // in case of exception write task will not be released (reused) 
                return new ErrorWriteResult(handler, ioe, buffersToWrite);
            }
        }
        
        
        private void release() {
            buffersToWrite = null;
            writeBuffer.clear();
            TaskFactory.reuseWriteProcessor(this);
        }
    }
   	
    
  
 	private static final class DirectWriteTask implements IWriteTask {

   		private ByteBuffer bufferToWrite = null;
   	
   		
   		boolean addData(ByteBuffer bufferToWrite) {
   			if (bufferToWrite.hasRemaining()) { 
   				this.bufferToWrite = bufferToWrite;
   				return true;
   				
   			} else {
   				return false;
   			}
   		}
   
   	
   		public IWriteResult write(IoSocketHandler handler) throws IOException {

   			try {
   				int written = handler.channel.write(bufferToWrite);
   				handler.incSentBytes(written);
		   			
   				if (!bufferToWrite.hasRemaining()) {
   				    SingleBufferWriteResult writeResult = new SingleBufferWriteResult(handler, bufferToWrite);
   				    release();
   				    
   				    return writeResult;
   				    
   				} else {
   					return INCOMPLETE_WRITE_RESULT;
   				}

	
   			} catch(final IOException ioe)  {
                // in case of exception write task will not be released (reused)
   			    return new ErrorWriteResult(handler, ioe, bufferToWrite);
   			}
   		}
   		
   	   
        private void release() {
            bufferToWrite = null;
            TaskFactory.reuseWriteProcessor(this);
        }
   	}
 	
  
    
    private static interface IWriteResult {
        
        boolean isAllWritten();
        
        void notifyWriteCallback();
    }
    
    
    private static final class IncompleteWriteResult implements IWriteResult {
        
        public boolean isAllWritten() {
            return false;
        }
        
        public void notifyWriteCallback() {
        }
    }
    
    
    private static final class EmptyWriteResult implements IWriteResult {
        
        public boolean isAllWritten() {
            return true;
        }
        
        public void notifyWriteCallback() {
        }
    }
    
    
    private static final class ErrorWriteResult implements IWriteResult {
        
        private final IOException ioe;
        private final ByteBuffer[] buffers; 
        private final IoSocketHandler handler;
        
        public ErrorWriteResult(IoSocketHandler handler, IOException ioe, ByteBuffer... buffers) {
            this.buffers = buffers;
            this.ioe = ioe;
            this.handler = handler;
        }
        
        public boolean isAllWritten() {
            return true;
        }

        public void notifyWriteCallback() {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("error " + ioe.toString() + " occured by writing " + ioe.toString());
            }
                 
            for (ByteBuffer buffer : buffers) {
                try {
                    handler.getPreviousCallback().onWriteException(ioe, buffer);
                } catch (Exception e) {
                    // eat and log exception
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine("error occured by notifying that write exception (" + e.toString() + ") has been occured " + e.toString());
                    }
                }
            }
        }
        
    }
    
    
    private static final class SingleBufferWriteResult implements IWriteResult {
        
        private final ByteBuffer buffer; 
        private final IoSocketHandler handler;
        
        public SingleBufferWriteResult(IoSocketHandler handler, ByteBuffer buffer) {
            this.buffer = buffer;
            this.handler = handler;
        }
        
        public boolean isAllWritten() {
            return true;
        }

        public void notifyWriteCallback() {
            try {
                handler.getPreviousCallback().onWritten(buffer);
            } catch (Exception e) {
                // eat and log exception
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("error occured by performing onWritten callback " + e.toString());
                }
            }
        }
    }
    
    
    private static final class MultiBufferWriteResult implements IWriteResult {
        
        private final ByteBuffer[] buffers; 
        private final IoSocketHandler handler;
        
        public MultiBufferWriteResult(IoSocketHandler handler, ByteBuffer... buffers) {
            this.buffers = buffers;
            this.handler = handler;
        }
        
        public boolean isAllWritten() {
            return true;
        }

        public void notifyWriteCallback() {
            for (ByteBuffer buffer : buffers) {
                try {
                    handler.getPreviousCallback().onWritten(buffer);
                } catch (Exception e) {
                    // eat and log exception
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine("error occured by performing onWritten callback " + e.toString());
                    }
                }
            }
        }
    }
}