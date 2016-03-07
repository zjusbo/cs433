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
package org.xsocket.connection.spi;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.IDispatcher;
import org.xsocket.IHandle;
import org.xsocket.DataConverter;


/**
 * Socket based io handler
 *
 * @author grro@xsocket.org
 */
final class IoSocketHandler extends ChainableIoHandler implements IHandle {

	private static final Logger LOG = Logger.getLogger(IoSocketHandler.class.getName());


	private static final int MAXSIZE_LOG_READ = 2000;

	@SuppressWarnings("unchecked")
	private static final Map<String, Class> SUPPORTED_OPTIONS = new HashMap<String, Class>();

	static {
		SUPPORTED_OPTIONS.put(IClientIoProvider.SO_RCVBUF, Integer.class);
		SUPPORTED_OPTIONS.put(IClientIoProvider.SO_SNDBUF, Integer.class);
		SUPPORTED_OPTIONS.put(IClientIoProvider.SO_REUSEADDR, Boolean.class);
		SUPPORTED_OPTIONS.put(IClientIoProvider.SO_KEEPALIVE, Boolean.class);
		SUPPORTED_OPTIONS.put(IClientIoProvider.TCP_NODELAY, Boolean.class);
		SUPPORTED_OPTIONS.put(IClientIoProvider.SO_LINGER, Integer.class);
	}


	// flag
	private boolean isLogicalOpen = true;
	private boolean isDisconnect = false;

	// socket
	private SocketChannel channel = null;


	// dispatcher
	private IoSocketDispatcher dispatcher = null;


	// memory management
	private IMemoryManager memoryManager = null;


	// receive & send queue
	private final IoQueue sendQueue = new IoQueue();


    // id
	private String id = null;

	
	// retry read
	private boolean isRetryRead = true;
	

	// timeouts
	private long idleTimeoutMillis = IClientIoProvider.DEFAULT_IDLE_TIMEOUT_MILLIS;
	private long idleTimeoutDateMillis = Long.MAX_VALUE;
	private long connectionTimeoutMillis = IClientIoProvider.DEFAULT_CONNECTION_TIMEOUT_MILLIS;
	private long connectionTimeoutDateMillis = Long.MAX_VALUE;


	// suspend flag
	private boolean suspendRead = false;


	// socket param
	private int soRcvbuf = 0;


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

    	soRcvbuf = (Integer) getOption(DefaultIoProvider.SO_RCVBUF);
	}


    public void init(IIoHandlerCallback callbackHandler) throws IOException, SocketTimeoutException {
    	setPreviousCallback(callbackHandler);

		blockUntilIsConnected();
		dispatcher.register(this, SelectionKey.OP_READ);
    }
    
    
    void setRetryRead(boolean isRetryRead) {
    	this.isRetryRead = isRetryRead;
    }


    /**
     * {@inheritDoc}
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


    void setMemoryManager(IMemoryManager memoryManager) {
    	this.memoryManager = memoryManager;
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
		DefaultIoProvider.setOption(channel.socket(), name, value);

		if (name.equals(DefaultIoProvider.SO_RCVBUF)) {
			soRcvbuf = (Integer) value;
		}
	}



	/**
	 * {@inheritDoc}
	 */
	public Object getOption(String name) throws IOException {
		return DefaultIoProvider.getOption(channel.socket(), name);
	}


	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Class> getOptions() {
		return Collections.unmodifiableMap(SUPPORTED_OPTIONS);
	}


    /**
     * {@inheritDoc}
     */
    public void setIdleTimeoutMillis(long timeoutMillis) {
		if (timeoutMillis <= 0) {
			LOG.warning("connection timeout " + timeoutMillis + " millis is invalid");
			return;
		}

		this.idleTimeoutMillis = timeoutMillis;
		this.idleTimeoutDateMillis = System.currentTimeMillis() + idleTimeoutMillis;
		
		if (idleTimeoutDateMillis < 0) {
			idleTimeoutDateMillis = Long.MAX_VALUE;
		}

		long period = idleTimeoutMillis;
		if (idleTimeoutMillis > 500) {
			period = idleTimeoutMillis / 5;
		}

		dispatcher.updateTimeoutCheckPeriod(period);
	}




	/**
	 * sets the connection timeout
	 *
	 * @param timeout the connection timeout
	 */
	public void setConnectionTimeoutMillis(long timeoutMillis) {

		if (timeoutMillis <= 0) {
			LOG.warning("connection timeout " + timeoutMillis + " millis is invalid");
			return;
		}

		this.connectionTimeoutMillis = timeoutMillis;
		this.connectionTimeoutDateMillis = System.currentTimeMillis() + connectionTimeoutMillis;

	
		long period = connectionTimeoutMillis;
		if (connectionTimeoutMillis > 500) {
			period = connectionTimeoutMillis / 5;
		}
		
		dispatcher.updateTimeoutCheckPeriod(period);
	}


	/**
	 * gets the connection timeout
	 *
	 * @return the connection timeout
	 */
	public long getConnectionTimeoutMillis() {
		return connectionTimeoutMillis;
	}


	/**
     * {@inheritDoc}
     */
	public long getIdleTimeoutMillis() {
		return idleTimeoutMillis;
	}





	/**
	 * check the  timeout
	 *
	 * @param currentMillis   the current time
	 * @return true, if the connection has been timed out
	 */
	boolean checkIdleTimeout(Long currentMillis) {
		if (getRemainingMillisToIdleTimeout(currentMillis) <= 0) {
			getPreviousCallback().onIdleTimeout();
			return true;
		}
		return false;
	}


	/**
	 * {@inheritDoc}
	 */
	public long getRemainingMillisToIdleTimeout() {
		return getRemainingMillisToIdleTimeout(System.currentTimeMillis());
	}


	private long getRemainingMillisToIdleTimeout(long currentMillis) {
		long remaining = idleTimeoutDateMillis - currentMillis;

		// time out received
		if (remaining > 0) {
			return remaining;

		// ... yes
		} else {

			// ... but check if meantime data has been received!
			return (lastTimeReceivedMillis + idleTimeoutMillis) - currentMillis;
		}
	}



	/**
	 * check if the underlying connection is timed out
	 *
	 * @param currentMillis   the current time
	 * @return true, if the connection has been timed out
	 */
	boolean checkConnectionTimeout(Long currentMillis) {
		if (getRemainingMillisToConnectionTimeout(currentMillis) <= 0) {
			getPreviousCallback().onConnectionTimeout();
			return true;
		}
		return false;
	}


	/**
	 * {@inheritDoc}
	 */
	public long getRemainingMillisToConnectionTimeout() {
		return getRemainingMillisToConnectionTimeout(System.currentTimeMillis());
	}


	private long getRemainingMillisToConnectionTimeout(long currentMillis) {
		return connectionTimeoutDateMillis - currentMillis;
	}


	/**
	 * check if the underyling connection is timed out
	 *
	 * @param current   the current time
	 * @return true, if the connection has been timed out
	 */
	void checkConnection() {
		if (!channel.isOpen()) {
			getPreviousCallback().onConnectionAbnormalTerminated();
		}
	}





	void onConnectEvent() throws IOException {
		getPreviousCallback().onConnect();
	}


	int onReadableEvent() throws IOException {
		assert (IoSocketDispatcher.isDispatcherThread()) : "receiveQueue can only be accessed by the dispatcher thread";

		int read = 0;

		try {
			// read data from socket
			ByteBuffer [] received  = readSocket();

			// handle the data
			if (received != null) {
				getPreviousCallback().onData(received);
			}

			// increase preallocated read memory if not sufficient
			checkPreallocatedReadMemory();

		} catch (ClosedChannelException ce) {
			close(false);

		} catch (Exception t) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + getId() + "] error occured by handling readable event. reason: " + t.toString());
			}
			close(false);

		} catch (Error e) {
			close(false);
			throw e;
		}


		return read;
	}



	int onWriteableEvent() throws IOException {
		assert (IoSocketDispatcher.isDispatcherThread());

		int sent = 0;

		if (suspendRead) {
			if (LOG.isLoggable(Level.FINEST)) {
				LOG.finest("[" + getId() + "] writeable event occured. update interested to none (because suspendRead is set) and write data to socket");
			}
			updateInterestedSetNonen();

		} else {
//			if (LOG.isLoggable(Level.FINEST)) {
//				LOG.finest("[" + getId() + "] writeable event occured. update interested to read and write data to socket");
//			}
			updateInterestedSetRead();
		}


		// write data to socket
		sent = writeSocket();


		// all data send? -> check for close
		if (sendQueue.isEmpty()) {
			if (shouldClosedPhysically()) {
				realClose();
			}

		// .. no, remaining data to send
		} else {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + id + "] remaining data to send. initiate sending of the remaining (" + DataConverter.toFormatedBytesSize(sendQueue.getSize()) + ")");
			}

			updateInterestedSetWrite();
		}


		if (LOG.isLoggable(Level.FINEST)) {
			LOG.finest("[" + getId() + "] writeable event handled");
		}

		return sent;
	}


	private void blockUntilIsConnected() throws IOException, SocketTimeoutException {
		// check/wait until channel is connected
		while (!getChannel().finishConnect()) {
			getChannel().configureBlocking(true);
			getChannel().finishConnect();
			getChannel().configureBlocking(false);
		}
	}


	private boolean shouldClosedPhysically() {
		// close handling (-> close() leads automatically to write, if there is data to write)
		if (!isLogicalOpen) {

			// send queue is emtpy -> close can be completed
			if (sendQueue.isEmpty()) {
				return true;
			}
		}

		return false;
	}


	/**
	 * {@inheritDoc}
	 */
	public void write(ByteBuffer[] buffers) throws IOException {
		if (buffers != null) {
			sendQueue.append(buffers);
			updateInterestedSetWrite();
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

			isLogicalOpen = false;
			updateInterestedSetWrite();
		}
	}


	private void realClose() {
		try {
			getDispatcher().deregister(this);
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



	void onDispatcherClose() {
		getPreviousCallback().onConnectionAbnormalTerminated();
	}

	private void updateInterestedSetWrite() throws ClosedChannelException {
		try {
			dispatcher.updateInterestSet(this, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("couldn`t update interested set to write data on socket. Reason: " + ioe.toString());
			}

			try {
				dispatcher.deregister(this);
			} catch (Exception ignore) { }

			throw new ClosedChannelException();
		}
	}

	private void updateInterestedSetRead() throws ClosedChannelException {
		try {
			dispatcher.updateInterestSet(this, SelectionKey.OP_READ);
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("couldn`t update interested set to read data. Reason: " + ioe.toString());
			}

			try {
				dispatcher.deregister(this);
			} catch (Exception ignore) { }

			throw new ClosedChannelException();
		}
	}




	private void updateInterestedSetNonen() throws ClosedChannelException {
		try {
			dispatcher.updateInterestSet(this, 0);
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("could not update interested set to nonen. Reason: " + ioe.toString());
			}

			try {
				dispatcher.deregister(this);
			} catch (Exception ignore) { }

			throw new ClosedChannelException();
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


	IDispatcher<IoSocketHandler> getDispatcher() {
		return dispatcher;
	}


	@Override
	public void suspendRead() throws IOException {
		suspendRead = true;

		// update to write (why?). Reason:
		//  * avoid race conditions in which current write need will be swallowed
		//  * write falls back to `none interested set`
		updateInterestedSetWrite();
	}



	@Override
	public void resumeRead() throws IOException {
		if (suspendRead) {
			suspendRead = false;

			// update to write (why not read?). Reason:
			//  * avoid race conditions in which current write need will be swallowed
			//  * write falls back to `read interested set` if there is no data to write
			updateInterestedSetWrite();
		}
	}


	/**
	 * reads socket into read queue
	 *
	 * @return the received data or <code>null</code>
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedChannelException if the underlying channel is closed
	 */
	private ByteBuffer[] readSocket() throws IOException {
		assert (IoSocketDispatcher.isDispatcherThread()) : "receiveQueue can only be accessed by the dispatcher thread";


		ByteBuffer[] received = null;


		int read = 0;
		lastTimeReceivedMillis = System.currentTimeMillis();


		if (isOpen() && !suspendRead) {

			assert (memoryManager instanceof UnsynchronizedMemoryManager);

			ByteBuffer readBuffer = memoryManager.acquireMemoryStandardSizeOrPreallocated(soRcvbuf);
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
			if (LOG.isLoggable(Level.FINEST)) {
				if (!isOpen()) {
					LOG.finest("["  + getId() + "] couldn't read socket because socket is already closed");
				}

				if (suspendRead) {
					LOG.finest("["  + getId() + "] read is suspended, do nothing");
				}
			}

			return null;
		}
	}


	/**
	 * check if preallocated read buffer size is sufficient. if not increase it
	 */
	private void checkPreallocatedReadMemory() {
		assert (IoSocketDispatcher.isDispatcherThread());

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
		assert (IoSocketDispatcher.isDispatcherThread());

		int sent = 0;

		////////////////////////////////////////////////////////////
		// Why hasn`t channel.write(ByteBuffer[]) been used??
		//
		// sendBytes += channel.write(data.toArray(new ByteBuffer[data.size()])) doesn`t
		// work correct under WinXP_SP2 & Sun JDK 1.6.0_01-b06 (and other configurations?).
		// The channel reports that x bytes have been written, but in some situations duplicated
		// data appears on the line (caused by the channel impl?!)
		// This behaviour doesn`t appear under Suse9.1/Intel & Sun JDK 1.5.0_08
		////////////////////////////////////////////////////////////


		if (isOpen()) {
			ByteBuffer[] buffers = sendQueue.drain();
			if (buffers == null) {
				return 0;
			}

			boolean hasUnwrittenBuffers = false;
			try {
				for (int i = 0; i < buffers.length; i++) {

					if (buffers[i] != null) {
		 				int writeSize = buffers[i].remaining();

		 				// data to write for this buffer?
		 				if (writeSize > 0) {
			 				if (LOG.isLoggable(Level.FINE)) {
					 			if (LOG.isLoggable(Level.FINE)) {
			 						LOG.fine("[" + id + "] sending (" + writeSize + " bytes): " + DataConverter.toTextOrHexString(buffers[i].duplicate(), "UTF-8", 500));
			 		 			}
			 				}

			 				// write to socket (internal out buffer)
			 				try {
				 				int written = channel.write(buffers[i]);
				 				sent += written;
				 				sendBytes += written;
	 				
				 				// all data written?
				 				if (written == writeSize) {
				 					try {
				 						// notify the io handler that data has been written
				 						getPreviousCallback().onWritten(buffers[i]);
				 					} catch (Exception e) {
				 						if (LOG.isLoggable(Level.FINE)) {
				 							LOG.fine("error occured by notifying that buffer has been written " + e.toString());
				 						}
				 					}

				 					buffers[i] = null;

				 				// ... no, return byte buffer to send queue
				 				} else {
				 					hasUnwrittenBuffers = true;  // see finally block

				 					if (LOG.isLoggable(Level.FINE)) {
				 						LOG.fine("[" + id + "] " + written + " of " + (writeSize - written) + " bytes has been sent (" + DataConverter.toFormatedBytesSize((writeSize - written)) + ")");
				 		 			}
				 					break;
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
					}
				}
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
	public void flushOutgoing() {

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
	   		       + ", lastReceived=" + DataConverter.toFormatedDate(lastTimeReceivedMillis)
	   		       + ", sendQueueSize=" + DataConverter.toFormatedBytesSize(sendQueue.getSize())
	   		       + " [" + id + "]";
   		} catch (Throwable e) {
   			return super.toString();
   		}
	}
}