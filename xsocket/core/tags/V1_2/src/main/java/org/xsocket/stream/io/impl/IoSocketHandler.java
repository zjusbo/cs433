// $Id: IoSocketHandler.java 1316 2007-06-10 08:51:18Z grro $
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
package org.xsocket.stream.io.impl;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.ByteBufferQueue;
import org.xsocket.ClosedConnectionException;
import org.xsocket.IDispatcher;
import org.xsocket.IHandle;
import org.xsocket.DataConverter;
import org.xsocket.stream.io.spi.IClientIoProvider;
import org.xsocket.stream.io.spi.IIoHandlerCallback;
import org.xsocket.stream.io.spi.IIoHandlerContext;




/**
 * Socket based io handler
 *
 * @author grro@xsocket.org
 */
final class IoSocketHandler extends ChainableIoHandler implements IHandle {

	private static final Logger LOG = Logger.getLogger(IoSocketHandler.class.getName());


	private static final int MIN_READ_BUFFER_SIZE = 64;  // if smaller than this new buffer has to be allocated

	private static final Map<String ,Class> SUPPORTED_OPTIONS = new HashMap<String, Class>();
	
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


	// distacher
	private IoSocketDispatcher dispatcher = null;


	// memory management
	private IMemoryManager memoryManager = null;


	// read & write queue
	private final ByteBufferQueue sendQueue = new ByteBufferQueue();
	private final ByteBufferQueue receiveQueue = new ByteBufferQueue();


    // id
	private String id = null;


	// timeouts
	private long idleTimeout = Long.MAX_VALUE;
	private long connectionTimeout = Long.MAX_VALUE;

	
	// suspend flag
	private boolean suspendRead = false;
	

	// statistics
	private long openTime = -1;
	private long lastTimeReceived = System.currentTimeMillis();
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
	IoSocketHandler(SocketChannel channel, IoSocketDispatcher dispatcher, IIoHandlerContext ctx, String connectionId) throws IOException {
   	   	super(null);

    	assert (channel != null);
    	this.channel = channel;

    	openTime = System.currentTimeMillis();

		channel.configureBlocking(false);

		this.dispatcher = dispatcher;
    	this.id = connectionId;
	}


    public void init(IIoHandlerCallback callbackHandler) throws IOException {
    	setPreviousCallback(callbackHandler);
    	
		blockUntilIsConnected();
		dispatcher.register(this, SelectionKey.OP_READ);
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


    int getPendingReceiveDataSize() {
    	return receiveQueue.getSize() + super.getPendingReceiveDataSize();
    }

    
	/**
	 * {@inheritDoc}
	 */
	public void setOption(String name, Object value) throws IOException {
	
		if (name.equals(IClientIoProvider.SO_SNDBUF)) {
			channel.socket().setSendBufferSize((Integer) value);
			
		} else if (name.equals(IClientIoProvider.SO_REUSEADDR)) {
			channel.socket().setReuseAddress((Boolean) value);

		} else if (name.equals(IClientIoProvider.SO_RCVBUF)) {
			channel.socket().setReceiveBufferSize((Integer) value);

		} else if (name.equals(IClientIoProvider.SO_KEEPALIVE)) {
			channel.socket().setKeepAlive((Boolean) value);

		} else if (name.equals(IClientIoProvider.SO_LINGER)) {
			if (value instanceof Integer) {
				channel.socket().setSoLinger(true, (Integer) value);
			} else if (value instanceof Boolean) {
				if (((Boolean) value).equals(Boolean.FALSE)) {
					channel.socket().setSoLinger(Boolean.FALSE, 0);
				}
			}

		} else if (name.equals(IClientIoProvider.TCP_NODELAY)) {
			channel.socket().setTcpNoDelay((Boolean) value);

			
		} else {
			LOG.warning("option " + name + " is not supproted for " + this.getClass().getName());
		}
	}
	
    
    

	/**
	 * {@inheritDoc}
	 */	
	public Object getOption(String name) throws IOException {

		if (name.equals(IClientIoProvider.SO_SNDBUF)) {
			return channel.socket().getSendBufferSize();
			
		} else if (name.equals(IClientIoProvider.SO_REUSEADDR)) {
			return channel.socket().getReuseAddress();
			
		} else if (name.equals(IClientIoProvider.SO_RCVBUF)) {
			return channel.socket().getReceiveBufferSize();

		} else if (name.equals(IClientIoProvider.SO_KEEPALIVE)) {
			return channel.socket().getKeepAlive();
			
		} else if (name.equals(IClientIoProvider.TCP_NODELAY)) {
			return channel.socket().getTcpNoDelay();

		} else if (name.equals(IClientIoProvider.SO_LINGER)) {
			return channel.socket().getSoLinger();

			
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
    public void setIdleTimeoutSec(int timeout) {
		long timeoutMillis = ((long) timeout) * 1000L;
		this.idleTimeout = timeoutMillis;

		dispatcher.updateTimeoutCheckPeriod((long) timeout * 250L);
	}


    

	/**
	 * sets the connection timout
	 *
	 * @param timeout the connection timeout
	 */
	public void setConnectionTimeoutSec(int timeout) {
		long timeoutMillis = ((long) timeout) * 1000L;
		this.connectionTimeout = timeoutMillis;

		dispatcher.updateTimeoutCheckPeriod((long) timeout * 250L);
	}


	/**
	 * gets the connection timeout
	 *
	 * @return the connection timeout
	 */
	public int getConnectionTimeoutSec() {
		return (int) (connectionTimeout / 1000);
	}


	/**
     * {@inheritDoc}
     */
	public int getIdleTimeoutSec() {
		return (int) (idleTimeout / 1000);
	}


	
	public int getReceiveQueueSize() {
		return receiveQueue.getSize();
	}


	int getSendQueueSize() {
		return sendQueue.getSize();
	}

	/**
	 * check the  timeout 
	 *
	 * @param current   the current time
	 * @return true, if the connection has been timed out
	 */
	boolean checkIdleTimeout(Long current) {
		long maxTime = lastTimeReceived + idleTimeout;
		if (maxTime < 0) {
			maxTime = Long.MAX_VALUE;
		}
		boolean timeoutReached = maxTime < current;
		
		if (timeoutReached) {
			getPreviousCallback().onIdleTimeout();
		}
		return timeoutReached;
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



	/**
	 * check if the underyling connection is timed out
	 *
	 * @param current   the current time
	 * @return true, if the connection has been timed out
	 */
	boolean checkConnectionTimeout(Long current) {
		long maxTime = openTime + connectionTimeout;
		if (maxTime < 0) {
			maxTime = Long.MAX_VALUE;
		}
		boolean timeoutReached = maxTime < current;
		if (timeoutReached) {
			getPreviousCallback().onConnectionTimeout();
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
	
	
	void onConnectEvent() throws IOException {	
		getPreviousCallback().onConnect();
	}
	
	
	void onReadableEvent() throws IOException {
		assert (IoSocketDispatcher.isDispatcherThread());
		
		try {
			// read data from socket
			readSocketIntoReceiveQueue();	
			
			// handle the data

			if (getReceiveQueueSize() > 0) {
				getPreviousCallback().onDataRead();
			}
					
			// increase preallocated read memory if not sufficient
			checkPreallocatedReadMemory();
			
			
		} catch (ClosedConnectionException ce) {
			close(false);
			
		} catch (Throwable t) {
			close(false);
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by handling readable event. reason: " + t.toString());
			}
		}		
	}
	
	
	
	void onWriteableEvent() throws IOException {
		assert (IoSocketDispatcher.isDispatcherThread());
		
		if (suspendRead) {
			updateInterestedSetNonen();
			
		} else {
			updateInterestedSetRead();
		}
			
		// updates interestSet with read & write data to socket 
		try {
			writeSendQueueDataToSocket();
				
			// notify the io handler that data has been written
			getPreviousCallback().onWritten();
		} catch(IOException ioe)  {
			getPreviousCallback().onWriteException(ioe);
		}
				
			
		// are there remaining data to send -> announce write demand 
		if (getSendQueueSize() > 0) {
			getDispatcher().updateInterestSet(this, SelectionKey.OP_WRITE);
				
		// all data send -> check for close
		} else {
			if (shouldClosedPhysically()) {
				realClose();
			}				
		}
	}
	

	private void blockUntilIsConnected() throws IOException {
		// check/wait until channel is connected  
		while (!getChannel().finishConnect()) {
			try {
				Thread.sleep(25);
			} catch (InterruptedException ignore) { }
		}
	}


	private boolean shouldClosedPhysically() {
		// close handling (-> close() leads automatically to write)
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
	@SuppressWarnings("unchecked")
	public void writeOutgoing(ByteBuffer buffer) throws IOException {
		if (buffer != null) {
			sendQueue.append(buffer);
			updateInterestedSetWrite();
		}
	}


	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public void writeOutgoing(LinkedList<ByteBuffer> buffers) throws IOException {
		if (buffers != null) {
			sendQueue.append(buffers);
			updateInterestedSetWrite();
		}
	}


	/**
	 * {@inheritDoc}
	 */
	public LinkedList<ByteBuffer> drainIncoming() {
		return receiveQueue.drain();
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

	private void updateInterestedSetWrite() throws ClosedConnectionException {
		try {
			dispatcher.updateInterestSet(this, SelectionKey.OP_WRITE);
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("couldn't update interested set to write data on socket. Reason: " + ioe.toString());
			}

			try {
				dispatcher.deregister(this);
			} catch (Exception ignore) { }

			throw new ClosedConnectionException("connection " + id + " is already closed", ioe);
		}
	}
	
	private void updateInterestedSetRead() throws ClosedConnectionException {
		try {
			dispatcher.updateInterestSet(this, SelectionKey.OP_READ);
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("couldn't update interested set to read data. Reason: " + ioe.toString());
			}

			try {
				dispatcher.deregister(this);
			} catch (Exception ignore) { }

			throw new ClosedConnectionException("connection " + id + " is already closed", ioe);
		}
	}
	


	
	private void updateInterestedSetNonen() throws ClosedConnectionException {
		try {
			dispatcher.updateInterestSet(this, 0);
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("couldn't update interested set to nonen. Reason: " + ioe.toString());
			}

			try {
				dispatcher.deregister(this);
			} catch (Exception ignore) { }

			throw new ClosedConnectionException("connection " + id + " is already closed", ioe);
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
		updateInterestedSetNonen();
	}
	
	
	@Override
	public void resumeRead() throws IOException {
		suspendRead = false;
		
		// update to write (why not read?). Reason:
		//  * avoid race conditions in which current write need will be swallowed
		//  * write falls back to read interested set if no more data to write exist
		updateInterestedSetWrite();
	}

	
	/**
	 * reads socket into read queue
	 *
	 * @return the number of read bytes
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed
	 */
	private int readSocketIntoReceiveQueue() throws IOException {
		assert (IoSocketDispatcher.isDispatcherThread());

		int read = 0;
		lastTimeReceived = System.currentTimeMillis();


		if (isOpen()) {

			assert (memoryManager instanceof UnsynchronizedMemoryManager);

			ByteBuffer readBuffer = memoryManager.acquireMemory(MIN_READ_BUFFER_SIZE);
			int pos = readBuffer.position();
			int limit = readBuffer.limit();

			// read from channel
			try {
				read = channel.read( readBuffer);
			// exception occured while reading
			} catch (IOException ioe) {
				readBuffer.position(pos);
				readBuffer.limit(limit);
				memoryManager.recycleMemory(readBuffer, MIN_READ_BUFFER_SIZE);

	 			if (LOG.isLoggable(Level.FINE)) {
	 				LOG.fine("[" + id + "] error occured while reading channel: " + ioe.toString());
	 			}

				throw ioe;
			}


			// handle read
			switch (read) {

				// end-of-stream has been reached -> throw an exception
				case -1:
					memoryManager.recycleMemory(readBuffer, MIN_READ_BUFFER_SIZE);
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + id + "] channel has reached end-of-stream (maybe closed by peer)");
					}
					ClosedConnectionException cce = new ClosedConnectionException("[" + id + "] End of stream reached");
					throw cce;

				// no bytes read recycle read buffer and do nothing
				case 0:
					memoryManager.recycleMemory(readBuffer, MIN_READ_BUFFER_SIZE);
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
						memoryManager.recycleMemory(readBuffer, MIN_READ_BUFFER_SIZE);
					}
		 			if (LOG.isLoggable(Level.FINE)) {
	 	 				LOG.fine("[" + id + "] received (" + (readData.limit() - readData.position()) + " bytes, total " + (receivedBytes + read) + " bytes): " + DataConverter.toTextOrHexString(new ByteBuffer[] {readData.duplicate() }, "UTF-8", 500));
		 			}
					break;
			}
		}


		receivedBytes += read;

		return read;
	}


	/**
	 * check if preallocated read buffer size is sufficient. if not increaese it
	 */
	private void checkPreallocatedReadMemory() {
		assert (IoSocketDispatcher.isDispatcherThread());

		memoryManager.preallocate(MIN_READ_BUFFER_SIZE);
	}

	/**
	 * writes the content of the send queue to the socket
	 *
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed
	 */
	@SuppressWarnings("unchecked")
	private void writeSendQueueDataToSocket() throws IOException {
		assert (IoSocketDispatcher.isDispatcherThread());


		////////////////////////////////////////////////////////////
		// Why hasn't channel.write(ByteBuffer[]) been used??
		//
		// sendBytes += channel.write(data.toArray(new ByteBuffer[data.size()])) doesn't
		// work correct under WinXP_SP2 & Sun JDK 1.6.0_01-b06 (and other configurations?).
		// The channel reports that x bytes have been written, but in some situations duplicated
		// data appears on the line (caused by the channel impl?!)
		// This behaviour doesn't appear under Suse9.1/Intel & Sun JDK 1.5.0_08
		////////////////////////////////////////////////////////////


		if (isOpen() && !sendQueue.isEmpty()) {
			
//			lastTimeSent = System.currentTimeMillis();

			// write buffer after buffer
			ByteBuffer buffer = null;
			
			do {
				buffer = sendQueue.removeFirst();

				if (buffer != null) {
	 				int writeSize = buffer.remaining();

	 				if (writeSize > 0) {
		 				if (LOG.isLoggable(Level.FINE)) {
				 			if (LOG.isLoggable(Level.FINE)) {
		 						LOG.fine("[" + id + "] sending (" + writeSize + " bytes): " + DataConverter.toTextOrHexString(buffer.duplicate(), "UTF-8", 500));
		 		 			}
		 				}

		 				// write to socket (internal out buffer)
		 				int written = channel.write(buffer);
		 				sendBytes += written;


		 				// not all data written?
		 				if (written != writeSize) {
		 					if (LOG.isLoggable(Level.FINE)) {
		 						LOG.fine("[" + id + "] " + written + " of " + (writeSize - written) + " bytes has been sent. initiate sending of the remaining (total sent " + sendBytes + " bytes)");
		 		 			}

		 					sendQueue.addFirst(buffer);
		 					updateInterestedSetWrite();
		 					break;
		 				}
	 				}
				}
				
			} while (buffer != null);
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
	   		       + ", lastReceived=" + DataConverter.toFormatedDate(lastTimeReceived)
	   		       + ", sendQueueSize=" + DataConverter.toFormatedBytesSize(sendQueue.getSize())
	   		       + " [" + id + "]";
   		} catch (Exception e) {
   			return super.toString();
   		}
	}
}