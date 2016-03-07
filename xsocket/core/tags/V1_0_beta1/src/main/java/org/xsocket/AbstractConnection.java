// $Id: AbstractConnection.java 47 2006-06-22 16:28:24Z grro $
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
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.xsocket.util.TextUtils;


/**
 * Implementation base of the <code>IConnection</code> interface. 
 * 
 * @author grro@xsocket.org 
 */
public abstract class AbstractConnection implements IConnection {

	private static final Logger LOG = Logger.getLogger(AbstractConnection.class.getName());
	
	private static final ByteBufferParser PARSER = new ByteBufferParser();	
	private static final int DEBUG_MAX_OUTPUT_SIZE = 200;
	
	private String id = null;
	
	private boolean isReceiving = true;

	private String defaultEncoding = "UTF-8";
	
	
	private final Queue receiveQueue = new Queue();
	
    // statistics
    private long connectionOpenedTime = -1;
    private long connectionEndTime = -1;
    private long lastTimeReceived = 0;
	private long bytesReceived = 0;
	private long bytesSend = 0;   

	
	
	/**
	 * constructor 
	 */
	public AbstractConnection() {		
		connectionOpenedTime = System.currentTimeMillis();
		lastTimeReceived = connectionOpenedTime;
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
	public abstract long write(ByteBuffer[] buffer) throws ClosedConnectionException, IOException;
	



	
	/**
	 * writes all btes of the send queue to the underlying channel 
	 * 
	 * @param buffersToWrite the buffers to write
	 * @return number of written bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ConnectionClosedException if the underlying channel is closed  
	 */
	protected final long writePhysical(ByteBuffer[] buffersToWrite) throws ClosedConnectionException, IOException {
		if (isOpen()) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + getId() + "] sending: " + printByteBuffersAsText(buffersToWrite));
				
				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("[" + getId() + "] " + printByteBuffersAsHex(buffersToWrite));
				}
			}
			
			try {
			long numberOfSendData = getAssignedSocketChannel().write(buffersToWrite);
			bytesSend += numberOfSendData;
			return numberOfSendData;
			} catch (IOException io) {
				io.printStackTrace();
				throw io;
			}
			
			
						
		} else {
			ClosedConnectionException cce = new ClosedConnectionException("[" + getId() + "] connection " + toCompactString() + " ist already closed. Couldn't write " + printData(buffersToWrite));
			LOG.throwing(this.getClass().getName(), "send(ByteBuffer[])", cce);
			throw cce;
		}
	}
	
	

	

	/**
	 * read from the underlying channel  
	 * 
	 * @return the read bytes
	 * @throws ClosedConnectionException if the connection has been already closed
	 * @throws IOException If some other I/O error occurs
	 */
	protected final ByteBuffer readPhysical() throws ClosedConnectionException, IOException {
		ByteBuffer result = null;
		
		ByteBuffer allocatedBufferBlock = acquireMemory();
		int pos = allocatedBufferBlock.position();
		int limit = allocatedBufferBlock.limit();

		int read = 0;
		if (isOpen() && isReceiving) {
			
			// read from channel
			try {
				read = getAssignedSocketChannel().read(allocatedBufferBlock);
			// exception occured while reading 
			} catch (IOException ioe) {				
				allocatedBufferBlock.position(pos);
				allocatedBufferBlock.limit(limit);
				recycleMemory(allocatedBufferBlock);

				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("[" + getId() + "] error occured while reading channel: " + ioe.toString());
				}
				
				throw ioe;
			}								

			
			// handle read
			switch (read) {
				
				// end-of-stream has been reached -> throw exception
				case -1:
					recycleMemory(allocatedBufferBlock);
					ClosedConnectionException cce = new ClosedConnectionException("[" + getId() + "] End of stream reached");
					LOG.throwing(this.getClass().getName(), "read()", cce);
					throw cce;
					
				// no bytes read					
				case 0:
					recycleMemory(allocatedBufferBlock);
					break;

                // bytes available (read < -1 is not handled)
				default:
					lastTimeReceived = System.currentTimeMillis();
					bytesReceived += read;
					allocatedBufferBlock.flip();				
					result = extractAndRecycleMemory(allocatedBufferBlock); 
					
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + getId() + "] received (" + (result.limit() - result.position()) + " bytes): " + printByteBuffersAsText(new ByteBuffer[] { result }));
						
						
						if (LOG.isLoggable(Level.FINER)) {
							LOG.finer("[" + getId() + "] " + printByteBuffersAsHex(new ByteBuffer[] { result }));
						}

					}
					break;
			}			
		} 				
		
		if (LOG.isLoggable(Level.FINEST)) {
			if (result != null) {
				LOG.finest("read: " + TextUtils.toByteString(result.duplicate()));
			} else {
				LOG.finest("read: ''");
			}
		}
		return result;
	}

	
	

	/**
	 * extract the used part of the ByteBuffer and recycle the remaining 
	 * @param buffer the ByteBuffer
	 * @return the extracted byteBuffer
	 */
	protected final ByteBuffer extractAndRecycleMemory(ByteBuffer buffer) {
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
	public synchronized void close() {
		if (getAssignedSocketChannel() != null) {
			try {
				getAssignedSocketChannel().close();
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + getId() + "] error occured while closing underlying channel: " + ioe.toString());
				}
			}				
			connectionEndTime = System.currentTimeMillis();
		}
	}
	
	
	/**
	 * return the assigned scoekt channel 
	 * 
	 * @return the socket channel
	 */
	protected abstract SocketChannel getAssignedSocketChannel();
	

	/**
	 * @see IConnection
	 */
	public final boolean isOpen() {
		if (getAssignedSocketChannel() == null) {
			return false;
		} else {
			return getAssignedSocketChannel().isOpen();
		}
	}
	

	
	/**
	 * @see IConnection
	 */
	public final int getRemotePort() {
		return getAssignedSocketChannel().socket().getPort();
	}
	
	

	/**
	 * @see IConnection
	 */
	public InetAddress getRemoteAddress() {
		return getAssignedSocketChannel().socket().getInetAddress();
	}
	
	/**
	 * @see IConnection
	 */	
	public InetAddress getLocalAddress() {
		return getAssignedSocketChannel().socket().getLocalAddress();
	}
	
	/**
	 * @see IConnection
	 */
	public long getConnectionOpenedTime() {
		return connectionOpenedTime;
	}

	/**
	 * @see IConnection
	 */
	public long getLastReceivingTime() {
		return lastTimeReceived;
	}


	/**
	 * @see IConnection
	 */
	public final int getLocalePort() {
		return getAssignedSocketChannel().socket().getLocalPort();
	}
	

	/**
	 * stop trhe receiving of data
	 *
	 */
	protected final void stopReading() {
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
	 * @see IConnection
	 */
	public final void setDefaultEncoding(String encoding) {
		this.defaultEncoding = encoding;
	}
	
	
	/**
	 * @see IConnection
	 */
	public final String getDefaultEncoding() {
		return defaultEncoding;
	}

	/**
	 * @see IConnection
	 */	
	public final int writeWord(String s) throws ClosedConnectionException, IOException {
		return writeWord(s, defaultEncoding);
	}

	
	/**
	 * @see IConnection
	 */	
	public final int writeWord(String s, String encoding) throws ClosedConnectionException, IOException {
		ByteBuffer buffer = TextUtils.toByteBuffer(s, encoding);
		return write(buffer);
	}
		
	/**
	 * @see IConnection
	 */	
	public final int writeByte(byte b) throws ClosedConnectionException, IOException {
		return write(ByteBuffer.allocate(1).put(b));
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
	public final int writeInt(int i) throws ClosedConnectionException, IOException {
		ByteBuffer buffer = ByteBuffer.allocate(4).putInt(i);
		buffer.flip();
		return (int) write(buffer);
	}

	/**
	 * @see IConnection
	 */	
	public final int writeLong(long l) throws ClosedConnectionException, IOException {
		ByteBuffer buffer = ByteBuffer.allocate(8).putLong(l);
		buffer.flip();
		return (int) write(buffer);
	}

	
	/**
	 * @see IConnection
	 */	
	public final int writeDouble(double d) throws ClosedConnectionException, IOException {
		ByteBuffer buffer = ByteBuffer.allocate(8).putDouble(d);
		buffer.flip();
		return (int) write(buffer);
	}
		

	/**
	 * @see IConnection
	 */		
	public final long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
		ByteBuffer[] bufs = new ByteBuffer[length];
		System.arraycopy(srcs, offset, bufs, 0, length);
		
		return write(bufs);
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
				  + ", receiveQueueSize=" + receiveQueue.getSize());
				//  + ", sendQueueSize=" + getSendQueueSize());
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
	
	
	protected final Queue getReceiveQueue() {
		return receiveQueue;
	}
	
	
	/**
	 * @see IConnection
	 */
	public String toCompactString() {
		return "id=" + getId() 
	       + ", caller=" + getRemoteAddress().getCanonicalHostName() + "(" + getRemoteAddress() + ":" + getRemotePort() + ")"
	       + ", opened=" + TextUtils.printFormatedDate(connectionOpenedTime); 
	}	

	
	
	private String printByteBuffersAsText(ByteBuffer[] buffers) {

		// first cut output if longer than max limit
		String postfix = "";
		int size = 0;
		List<ByteBuffer> copies = new ArrayList<ByteBuffer>();
		for (ByteBuffer buffer : buffers) {
			ByteBuffer copy = buffer.duplicate();
			if ((size + copy.limit()) > DEBUG_MAX_OUTPUT_SIZE) {
				copy.limit(DEBUG_MAX_OUTPUT_SIZE - size);
				copies.add(copy);
				postfix = " [logout put has been cut]";
				break;
			} else {
				copies.add(copy);
			}
		}

		StringBuilder result = new StringBuilder();

		// create text out put
		try {
			for (ByteBuffer buffer : copies) {
				result.append(TextUtils.toString(buffer, "UTF-8"));
				buffer.flip();
			}
		} catch (Exception ignore) { }	

		result.append(postfix);

		return result.toString();
	}
	
	
	private String printByteBuffersAsHex(ByteBuffer[] buffers) {

		// first cut output if longer than max limit
		String postfix = "";
		int size = 0;
		List<ByteBuffer> copies = new ArrayList<ByteBuffer>();
		for (ByteBuffer buffer : buffers) {
			ByteBuffer copy = buffer.duplicate();
			if ((size + copy.limit()) > DEBUG_MAX_OUTPUT_SIZE) {
				copy.limit(DEBUG_MAX_OUTPUT_SIZE - size);
				copies.add(copy);
				postfix = " [logout put has been cut]";
				break;
			} else {
				copies.add(copy);
			}
		}

		StringBuilder result = new StringBuilder();

	
		result.append("[hex:]");
		for (ByteBuffer buffer : copies) {
			result.append(TextUtils.toByteString(buffer));
		}
		
		result.append(postfix);
		
		return result.toString();
	}
	
	

	
	protected static final class Queue {
		private final Map<String, ByteBufferParser.Index> indexMap = new HashMap<String, ByteBufferParser.Index>();
		private LinkedList<ByteBuffer> bufferQueue = new LinkedList<ByteBuffer>();

		
		public Queue() {
			
		}
		
		/**
		 * @param buffer
		 * @return
		 * 
		 */
		public synchronized int append(ByteBuffer buffer) {
			bufferQueue.addLast(buffer);
			return buffer.limit() - buffer.position();			
		}
		

		public synchronized int getSize() {
			int i = 0;
			for (ByteBuffer buffer : bufferQueue) {
				i += buffer.limit() - buffer.position();
			}
				
			return i;
		}
		
		public synchronized LinkedList<ByteBuffer> drain() {
			LinkedList<ByteBuffer> result = bufferQueue;
			bufferQueue = new LinkedList<ByteBuffer>();
			return result;	
		}
		
		
		public synchronized void readRecord(String delimiter, WritableByteChannel outChannel) throws IOException {
			ByteBufferParser.Index index = null;
			
			byte[] delimiterBytes = delimiter.getBytes();
			
			if (!indexMap.isEmpty()) {
				if (indexMap.containsKey(delimiter)) {
					index = indexMap.get(delimiter);
				}
			}
		
			if (index == null) {			
				index = PARSER.find(bufferQueue, delimiterBytes);
			} else {
				index = PARSER.find(bufferQueue, index);
			}
				
			if (index.isDelimiterFound()) {
				indexMap.clear();
				PARSER.extract(bufferQueue, index, outChannel);
			} else {
				indexMap.put(delimiter, index);
				throw new BufferUnderflowException();
			}
		}
		 
		
		public synchronized boolean readAvailable(String delimiter, WritableByteChannel outChannel) throws IOException {
			ByteBufferParser.Index index = null;
			
			byte[] delimiterBytes = delimiter.getBytes();
			
			if (!indexMap.isEmpty()) {
				if (indexMap.containsKey(delimiter)) {
					index = indexMap.get(delimiter);
				}
			}
		
			if (index == null) {			
				index = PARSER.find(bufferQueue, delimiterBytes);
			} else {
				index = PARSER.find(bufferQueue, index);
			}
					
			PARSER.extractAvailable(bufferQueue, index, outChannel);
			indexMap.clear();
			
			return index.isDelimiterFound();
		}
			
			
			
			
		
		public synchronized int readInt() throws IOException {
			if (getAvailableBytesOfFirstBuffer() >= 4) {
				ByteBuffer buf = bufferQueue.removeFirst();
				int i = buf.getInt();
				bufferQueue.addFirst(buf.slice());
				return i;
			} else {
				return ByteBuffer.wrap(read(4)).getInt();
			}
			
		}
		
		public synchronized byte readByte() {
			if (getAvailableBytesOfFirstBuffer() >= 1) { 
				ByteBuffer buf = bufferQueue.removeFirst();
				byte b = buf.get();
				bufferQueue.addFirst(buf.slice());
				return b;
			}  else {
				throw new BufferUnderflowException();
			}
			
		}
		
		
		public synchronized double readDouble() throws IOException {
			if (getAvailableBytesOfFirstBuffer() >= 8) {
				ByteBuffer buf = bufferQueue.removeFirst();
				double d = buf.getDouble();
				bufferQueue.addFirst(buf.slice());
				return d;
	 
			} else {
				return ByteBuffer.wrap(read(8)).getDouble();
			}
		}
		
		
		public synchronized long readLong() throws IOException {
			if (getAvailableBytesOfFirstBuffer() >= 8) {
				ByteBuffer buf = bufferQueue.removeFirst();
				long l = buf.getLong();
				bufferQueue.addFirst(buf.slice());
				return l;
			} else {
				return ByteBuffer.wrap(read(8)).getLong();
			}
		}
		
		private boolean inputBytesAvailable(int size) {
			int l = 0;
				
			for (ByteBuffer buffer : bufferQueue) {
				l += buffer.limit() - buffer.position();
				if (l >= size) {
					return true;
				}
			}
				
			return false; 
		}
		
		
		private byte[] read(int length) throws IOException { 
			byte[] bytes = new byte[length];
			int pointer = 0;
			
			// enough bytes available ?
			if (!inputBytesAvailable(length)) {
				throw new BufferUnderflowException();
			}
				
			ByteBuffer buffer = bufferQueue.removeFirst();
		
			while (true) {					
				// read out the buffer 
				while(buffer.hasRemaining()) {
					bytes[pointer] = buffer.get();
					pointer++;
					if (pointer == length) {
						if (buffer.position() < buffer.limit()) {
							bufferQueue.addFirst(buffer.slice());
						}
						return bytes;
					}
				}
					
				buffer = bufferQueue.poll();
				if (buffer == null) {
					throw new IOException("unexpected Buffer underflow occured");
				}
			}		
		}

		private int getAvailableBytesOfFirstBuffer() {
			if (bufferQueue.size() < 1) {
				return 0;
			}
			
			ByteBuffer buffer = bufferQueue.getFirst();
			return buffer.limit() - buffer.position();
		}
		
		
		public String toString() {
			StringBuilder sb = new StringBuilder();
			
			for (ByteBuffer buffer : bufferQueue) {
				sb.append(TextUtils.toString(buffer.duplicate(), "UTF-8"));
			}
			
			return sb.toString();
		}
	}
	
	
	protected static final class ByteBufferArrayChannel implements WritableByteChannel {
		
		private boolean isOpen = true;
		private ByteBuffer[] buffers = new ByteBuffer[0];
		
		public ByteBufferArrayChannel ()  {
			
		}
		
		public boolean isOpen() {
			return isOpen;
		}
		
		public void close() throws IOException {
			isOpen = false;
		}
		
		public int write(ByteBuffer buffer) throws IOException {
			buffers = incArray(buffers, buffer);
			return buffer.limit() - buffer.position();
		}
		
		public ByteBuffer[] getContent() {
			return buffers;
		}
		
		private static ByteBuffer[] incArray(ByteBuffer[] original, ByteBuffer newElement) {
			ByteBuffer[] newArray =  (ByteBuffer[]) copyOf(original, original.length + 1, original.getClass());
			newArray[original.length] = newElement;
			
			return newArray;
		}
		
		
		/**
		 * @see Arrays (Java 1.6)
		 */
		@SuppressWarnings("unchecked")
		private static <T,U> T[] copyOf(U[] original, int newLength, Class<? extends T[]> newType) {
			 T[] copy = ((Object)newType == (Object)Object[].class)
			 				? (T[]) new Object[newLength]
			 				: (T[]) Array.newInstance(newType.getComponentType(), newLength);
	         System.arraycopy(original, 0, copy, 0, Math.min(original.length, newLength));
	     return copy;
	    }
	}

}
