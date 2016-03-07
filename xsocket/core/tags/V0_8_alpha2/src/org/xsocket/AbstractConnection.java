// $Id$
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
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.util.TextUtils;


/**
 * Implementation base of the <code>IConnection</code> interface 
 * 
 * @author grro@xsocket.org 
 */
public abstract class AbstractConnection implements IConnection {

	private static final Logger LOG = Logger.getLogger(AbstractConnection.class.getName());
	
	
	private String id = null;
	private String defaultEncoding = "UTF-8";
	
	
	// socket
	private SocketChannel channel = null;
	private InetAddress remoteAddress = null;
	private int remotePort = 0;
	private int localePort = 0;
	
	
	// queues
	private ByteBufferQueue receiveQueue = new ByteBufferQueue();
	private ByteBufferQueue sendQueue = new ByteBufferQueue();
	private boolean isReceiving = true;

	
    // statistics
    private long connectionOpenedTime = -1;
    private long connectionEndTime = -1;
    private long lastTimeReceived = 0;
	private long bytesReceived = 0;
	private long bytesSend = 0;   



	/**
	 * constructor 
	 * @param channel  the underlying socket channel
	 * @throws IOException if the channel can not be configured ain a non-blocking mode
	 */
	public AbstractConnection(SocketChannel channel) throws IOException {		
		this.channel = channel;

		connectionOpenedTime = System.currentTimeMillis();
		lastTimeReceived = connectionOpenedTime;

		remoteAddress = channel.socket().getInetAddress();
		remotePort = channel.socket().getPort();		
		localePort = channel.socket().getLocalPort();		
	}
	

	/**
	 * @see IConnection
	 */		
	public final void setId(String id) {
		this.id = id;
	}

	
	/**
	 * @see IConnection
	 */
	public final String getId() {
		return id;
	}


	/**
	 * @see IConnection
	 */
	public void setDefaultEncoding(String encoding) {
		this.defaultEncoding = encoding;
	}
	
	
	/**
	 * @see IConnection
	 */
	public String getDefaultEncoding() {
		return defaultEncoding;
	}

	
	
	/**
	 * @see IConnection
	 */	
	public final int write(String s) throws ClosedConnectionException, IOException {
		return write(s, defaultEncoding);
	}

	
	/**
	 * @see IConnection
	 */	
	public final int write(String s, String encoding) throws ClosedConnectionException, IOException {
		ByteBuffer buffer = TextUtils.toByteBuffer(s, encoding);
		return write(buffer);
	}
	
	/**
	 * @see IConnection
	 */	
	public final int write(ByteBuffer buffer) throws ClosedConnectionException, IOException {
		return (int) write(new ByteBuffer[] {buffer});
	}
	
	/**
	 * @see IConnection
	 */	
	public abstract long write(ByteBuffer[] buffer) throws ClosedConnectionException, IOException;
	

	
	/**
	 * writes a buffer to the send queue 
	 * 
	 * @param buffer the buffer to write
	 * @return the number of written bytes
	 */
	protected final int writeToSendQueue(ByteBuffer buffer) {
		sendQueue.offer(buffer);
		return buffer.limit() - buffer.position();
	}
	
	/**
	 * writes the content of trhe send queue to the underlying channel 
	 * 
	 * @return the written bytes
	 * @throws IOException If some other I/O error occurs
	 * @throws ConnectionClosedException if the underlying channel is closed  
	 */
	protected synchronized final long writeSendQueuePhysical() throws ClosedConnectionException, IOException {
		return writePhysical(sendQueue.drain());
	}

	
	/**
	 * flag to check if the send queue is emtpy
	 *  
	 * @return true if the send queue is empty
	 */
	protected final boolean isSendQueueEmpty() {
		return sendQueue.isEmpty();
	}


	/**
	 * flag to check if the receive queue is emtpy
	 *  
	 * @return true if the receive queue is empty
	 */
	protected final boolean isReceiveQueueEmpty() {
		return receiveQueue.isEmpty();
	}
	

	
	/**
	 * writes all btes of the send queue to the underlying channel 
	 * 
	 * @param buffersToWrite the buffers to write
	 * @return number of written bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ConnectionClosedException if the underlying channel is closed  
	 */
	protected synchronized final long writePhysical(ByteBuffer[] buffersToWrite) throws ClosedConnectionException, IOException {
		if (isOpen()) {			
			if (LOG.isLoggable(Level.FINE)) {
				List<ByteBuffer> copies = new ArrayList<ByteBuffer>();
				for (ByteBuffer buffer : buffersToWrite) {
					copies.add(buffer.duplicate());
				}
				LOG.fine("[" + getId() + "] sending: " + TextUtils.toString(copies, "UTF-8"));
			}

			long numberOfSendData = getChannel().write(buffersToWrite);
			bytesSend += numberOfSendData;
			
			return numberOfSendData;			
		} else {
			ClosedConnectionException cce = new ClosedConnectionException("[" + getId() + "] connection " + toCompactString() + " ist already closed. Couldn't write " + printData(buffersToWrite));
			LOG.throwing(this.getClass().getName(), "send(ByteBuffer[])", cce);
			throw cce;
		}
	}
	
	
	/**
	 * read all available from the receive queue
	 *  
	 * @return all available bytes of the receive queue 
	 * @throws IOException If some other I/O error occurs
	 */
	protected final synchronized ByteBuffer[] readAvailableFromReceiveQueue() throws IOException {
		return receiveQueue.drain();
	}
	
	/**
	 * reads a record from the receive queue. the record is terminted by the delimiter 
	 * 
	 * @param delimiter the record delimiter
	 * @return the record, or null if the delimiter has not been found
	 * @throws IOException If some other I/O error occurs
	 */
	protected final synchronized ByteBuffer[] readRecordFromReceiveReceiveQueue(String delimiter) throws IOException {
		Index index = ByteBufferUtils.find(receiveQueue, delimiter.getBytes());
		if (index != null) {
			ByteBuffer[] buffers = ByteBufferUtils.extract(receiveQueue, index);
			return buffers;
		} else {
			return null;
		}
	}
	
	
	/**
	 * reads a word from the receive queue. the record is terminted by the delimiter 
	 * 
	 * @param delimiter the record delimiter
	 * @param encoding the encoding to use
	 * @return the word, or null if the delimiter has not been found
	 * @throws IOException If some other I/O error occurs
	 */
	protected final String readWordFromReceiveQueue(String delimiter, String encoding) throws IOException {
		ByteBuffer[] buffer = readRecordFromReceiveReceiveQueue(delimiter);
		if (buffer.length > 0) {
			return TextUtils.toString(buffer, encoding);
		} else {
			return null;
		}
	}
	
	
	/**
	 * reads from the underlying channel into the receiver queue
	 * @return number of read bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ConnectionClosedException if the underlying channel is closed  
	 */
	protected synchronized final int readPhysical() throws ClosedConnectionException, IOException {
		int read = 0;
		boolean moreDataAvailable = true;
		
		while (moreDataAvailable) {
			ByteBuffer allocatedBufferBlock = acquireMemory();

			// try to read
			int pos = allocatedBufferBlock.position();
			int limit = allocatedBufferBlock.limit();
		
			moreDataAvailable = false;
			
			try {
				read = readPhysical(allocatedBufferBlock);
			
			} catch (IOException ioe) {
				allocatedBufferBlock.position(pos);
				allocatedBufferBlock.limit(limit);
				recycleMemory(allocatedBufferBlock);
				
				throw ioe;
			}								
	
			// are bytes available?
			if (read > 0) {
				lastTimeReceived = System.currentTimeMillis();
				bytesReceived += read;
				
				allocatedBufferBlock.flip();
				
				// all bytes used?
				if (allocatedBufferBlock.limit() == allocatedBufferBlock.capacity()) {
					receiveQueue.offer(allocatedBufferBlock);
					moreDataAvailable = true;
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + getId() + "] read '" + printData(new ByteBuffer[] {allocatedBufferBlock}) + "'");
					}

				// not all bytes used -> slice used part
				} else {
			   		int savedLimit = allocatedBufferBlock.limit();
			   		ByteBuffer slicedPart = allocatedBufferBlock.slice();
			   		receiveQueue.offer(slicedPart);		

			   		if (LOG.isLoggable(Level.FINE)) {
			   			LOG.fine("[" + getId() + "] read '" + printData(new ByteBuffer[] {slicedPart}) + "'");
			   		}
			   		
			   		// .. and return the remaining buffer for reuse
		   			allocatedBufferBlock.position(savedLimit);
		   			allocatedBufferBlock.limit(allocatedBufferBlock.capacity());
					ByteBuffer unused = allocatedBufferBlock.slice();
					recycleMemory(unused);					
				}
				   
			// no bytes read
			} else {
				recycleMemory(allocatedBufferBlock);
				LOG.fine("[" + getId() + "] block returned");
			}
		}
		
		return read;
	}

	

	private int readPhysical(ByteBuffer buffer) throws ClosedConnectionException, IOException {
		int read = 0;
		
		if (isOpen() && isReceiving) {
			try {
				read = getChannel().read(buffer);
				
			} catch (IOException ioe) {				
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("[" + getId() + "] error occured while reading channel: " + ioe.toString());
				}
				throw ioe;
			}								

			
			// end-of-stream has been reached -> throw exception
			if (read == -1) {
				ClosedConnectionException cce = new ClosedConnectionException("[" + getId() + "] End of stream reached");
				LOG.throwing(this.getClass().getName(), "read()", cce);
				throw cce;
			}
				
		}
		
		return read;
	}

	

	/**
	 * returns a unused buffer from the pool.
	 *  
	 * @return the ByteBufffer
	 */
	protected abstract ByteBuffer acquireMemory();
	
	
	
	/**
	 * Recycles a buffer back into the pool. Once a buffer has been
	 * recycled, the buffer must not modified anymore
	 * 
	 * @param buffer the buffer to put into the pool.
	 */
	protected abstract void recycleMemory(ByteBuffer buffer);
	
	
	

	/**
	 * @see IConnection
	 */
	public final synchronized void close() {
		if (channel != null) {
			try {
				channel.close();
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + getId() + "] error occured while closing underlying channel: " + ioe.toString());
				}
			}
				
				
			channel = null;
				
			connectionEndTime = System.currentTimeMillis();
		}
	}
	

	/**
	 * @see IConnection
	 */
	public synchronized final boolean isOpen() {
		if (channel == null) {
			return false;
		} else {
			return channel.isOpen();
		}
	}
	

	
	/**
	 * @see IConnection
	 */
	public final int getRemotePort() {
		return remotePort;
	}
	
	

	/**
	 * @see IConnection
	 */
	public InetAddress getRemoteAddress() {
		return remoteAddress;
	}
	
	
	public long getConnectionOpenedTime() {
		return connectionOpenedTime;
	}
	
	public long getLastReceivingTime() {
		return lastTimeReceived;
	}


	/**
	 * @see IConnection
	 */
	public final int getLocalePort() {
		return localePort;
	}


	/**
	 * returns the underlying socket channel 
	 * 
	 * @return the underlying socket channel
	 */
	protected synchronized final SocketChannel getChannel() {
		return channel;
	}
	

	/**
	 * stop trhe receiving of data
	 *
	 */
	protected  synchronized final void stopReading() {
		isReceiving = false;
	}


	
	private String printData(ByteBuffer[] buffers) {
		String postfix = "";
		StringBuilder sb = new StringBuilder();
		
		int read = 0;
		for (ByteBuffer buffer : buffers) {
			ByteBuffer duplicated = buffer.duplicate();
			
			if ( ((duplicated.limit() - duplicated.position()) + read) > 400) {
				duplicated.limit(duplicated.position() + (400 - read));
				postfix = "[...remaining has been cut for log output]";
				break;
			}
			sb.append(TextUtils.toString(duplicated, "UTF-8"));
		}
		
		return (sb.toString() + postfix);
	}
	
	
	/**
	 * @see Object
	 */	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(toCompactString());
		if (connectionEndTime != -1) {
			sb.append(", lifetime=" + TextUtils.printFormatedDuration(connectionEndTime - connectionOpenedTime));
		}
		sb.append(", lastTimeReceived=" + TextUtils.printFormatedDate(lastTimeReceived) 
				  + ", received=" + TextUtils.printFormatedBytesSize(bytesReceived)
				  + ", send=" + TextUtils.printFormatedBytesSize(bytesSend)
				  + ", receiveQueueSize=" + receiveQueue.size()
				  + ", sendQueueSize=" + sendQueue.size());
		
		return sb.toString();
	}

	
	/**
	 * @see Object
	 */
	@Override
	public int hashCode() {
		return id.hashCode();
	}
	
	/**
	 * @see Object
	 */
	@Override
	public boolean equals(Object other) {
		if (other instanceof AbstractConnection) {
			return ((AbstractConnection) other).id.equals(this.id);
		} else {
			return false;
		}
	}
	
	/**
	 * @see IConnection
	 */
	public String toCompactString() {
		return "id=" + getId() 
	       + ", caller=" + getRemoteAddress().getCanonicalHostName() + "(" + getRemotePort() + ":" + remotePort + ")"
	       + ", opened=" + TextUtils.printFormatedDate(connectionOpenedTime); 
	}	

}
