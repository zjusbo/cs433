// $Id: Connection.java 449 2006-12-09 07:02:10Z grro $
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
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;


import org.xsocket.ByteBufferParser.Index;
import org.xsocket.util.TextUtils;



/**
 * Implementation base of the <code>IConnection</code> interface.
 * 
 *
 * @author grro@xsocket.org
 */
abstract class Connection implements IConnection {
	
	private static final Logger LOG = Logger.getLogger(Connection.class.getName());
	
	private static final int DEBUG_MAX_OUTPUT_SIZE = 200;
	public static final int DEFAULT_PREALLOCATION_SIZE = 4096;

	
	// parser
	private static final ByteBufferParser PARSER = new ByteBufferParser();

	
	// connected flag
	private boolean isConnected = false;
	

	// socket
	private SocketChannel channel = null;
	private String id = null;
	
	// receive queue
	private final ReadQueue readQueue = new ReadQueue();

	
	// ssl
	private SSLContext sslContext = null;
	private boolean sslOn = false;
	private boolean isClientMode = true;
	private SSLProcessor sslProcessor = null;
	
	
	// encoding
	private String defaultEncoding = "UTF-8";
	
	
	// id
	private static long nextId = 0;
	private static String idPrefix = null;

	
	// memory management
	private ByteBuffer readBuffer = null;


	// index for extract method
	private Index cachedIndex = null;

	
	// statistics
    private long lastTimeRead = 0;
	private long bytesRead = 0;
	private long bytesWritten = 0;
    private long connectionOpenedTime = -1;
    private long connectionEndTime = -1;

    
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
	 */
	Connection(SocketChannel channel, String localId, boolean clientMode, SSLContext sslContext, boolean sslOn) throws IOException {
		if (channel == null) {
			throw new NullPointerException("parameter channel is not set");
		}

		this.channel = channel;
		this.isClientMode = clientMode;
		this.sslContext = sslContext;
		this.sslOn = sslOn;

		
		if (localId == null) {
			id = idPrefix + "." + nextLocalId();	
		} else {
			id = idPrefix + "." + localId;
		}
		
		connectionOpenedTime = System.currentTimeMillis();
		lastTimeRead = connectionOpenedTime;
		

		if (sslOn) {
			sslProcessor = SSLProcessor.newProcessor(this, clientMode, sslContext);
			sslProcessor.start();
		} 
	}
	
	
	/**
	 * initialization of this connection
	 *
	 */
	protected void init() {
		if (!sslOn) {
			onConnect();
		}
	}
	

	/**
	 * callback method to signal that 
	 * the connection has been established
	 *
	 */
	protected void onConnect() {
		isConnected = true;
	}


	

	/**
	 * {@inheritDoc}
	 */
	public final void startSSL() throws IOException {	
		if (sslContext == null) {
			throw new IOException("sslContext is missing (for client connections this has to be set within the connection constructor");
		}
		
		if (!sslOn) {
			// flush buffer
			flushOutgoing();

			// activate ssl
			sslProcessor = SSLProcessor.newProcessor(this, isClientMode, sslContext);
			sslProcessor.start();
			sslOn = true;
		} 
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
	public final String getId() {
		return id;
	}
 

	/**
	 * {@inheritDoc}
	 */
	public boolean isOpen() {
		if (channel == null) {
			return false;
		} else {
			return channel.isOpen();
		}
	}

		
	/**
	 * {@inheritDoc}
	 */
	public void close() {
		flushOutgoing();
		
		connectionEndTime = System.currentTimeMillis();
		
		if (channel != null) {
			try {
				channel.close();
			} catch (Exception ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + id + "] error occured while closing underlying channel: " + ioe.toString());
				}
			}
		}
	}

	
	
	/**
	 * returns if connection is connected
	 * 
	 * @return true, if connected
	 */
	final boolean isConnected() {
		return isConnected;
	}
	
	
	

	/**
	 * {@inheritDoc}
	 */
	public final int getRemotePort() {
		return channel.socket().getPort();
	}



	/**
	 * {@inheritDoc}
	 */
	public final InetAddress getRemoteAddress() {
		return channel.socket().getInetAddress();
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final InetAddress getLocalAddress() {
		return channel.socket().getLocalAddress();
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final long getConnectionOpenedTime() {
		return connectionOpenedTime;
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final long getLastReceivingTime() {
		return lastTimeRead;
	}


	/**
	 * {@inheritDoc}
	 */
	public final int getLocalePort() {
		return channel.socket().getLocalPort();
	}
	
	
	/**
	 * converts a ByteBuffer array to a byte array
	 * 
	 * @param buffers the ByteBuffer array to convert
	 * @return the byte array
	 */
	protected final byte[] toArray(ByteBuffer[] buffers) {
		byte[] result = null;

		if (buffers == null) {
			return null;
		}

		for (ByteBuffer buffer : buffers) {
			if (result == null) {
				byte[] bytes = toArray(buffer);
				if (bytes.length > 0) {
					result = bytes;
				}
			} else {
				byte[] additionalBytes = toArray(buffer);
				byte[] newResult = new byte[result.length + additionalBytes.length];
				System.arraycopy(result, 0, newResult, 0, result.length);
				System.arraycopy(additionalBytes, 0, newResult, result.length, additionalBytes.length);
				result = newResult;
			}
		}
		return result;
	}

	
	private byte[] toArray(ByteBuffer buffer) {

		byte[] array = new byte[buffer.limit() - buffer.position()];

		if (buffer.hasArray()) {
			int offset = buffer.arrayOffset();
			byte[] bufferArray = buffer.array();
			System.arraycopy(bufferArray, offset, array, 0, array.length);

			return array;
		} else {
			buffer.get(array);
			return array;
		}
	}



	/**
	 * {@inheritDoc}
	 */
	public final void setDefaultEncoding(String encoding) {
		this.defaultEncoding = encoding;
	}


	/**
	 * {@inheritDoc}
	 */
	public final String getDefaultEncoding() {
		return defaultEncoding;
	}
	
	
	/**
	 * return if ssl is activated
	 * 
	 * @return truue, if ssl is activated
	 */
	protected final boolean isSSLOn() {
		return sslOn;
	}

	/**
	 * {@inheritDoc}
	 */
	public final int write(String s) throws ClosedConnectionException, IOException {
		return write(s, defaultEncoding);
	}


	/**
	 * {@inheritDoc}
	 */
	public final int write(String s, String encoding) throws ClosedConnectionException, IOException {
		ByteBuffer buffer = TextUtils.toByteBuffer(s, encoding);
		return write(buffer);
	}

	/**
	 * {@inheritDoc}
	 */
	public final int write(byte b) throws ClosedConnectionException, IOException {
		ByteBuffer buffer = ByteBuffer.allocate(1).put(b);
		buffer.flip();
		return write(buffer);
	}

	/**
	 * {@inheritDoc}
	 */
	public final int write(byte... bytes) throws ClosedConnectionException, IOException {
		return write(ByteBuffer.wrap(bytes));
	}

	/**
	 * {@inheritDoc}
	 */
	public final int write(byte[] bytes, int offset, int length) throws ClosedConnectionException, IOException {
		return write(ByteBuffer.wrap(bytes, offset, length));
	}

	

	/**
	 * {@inheritDoc}
	 */
	public long write(ByteBuffer[] buffers) throws ClosedConnectionException, IOException {
		long written = 0;
		for (ByteBuffer buffer : buffers) {
			written += buffer.limit() - buffer.position();
		}

		writeOutgoing(buffers);
		return written;
	}

	
	/**
	 * {@inheritDoc}
	 */
	public int write(ByteBuffer buffer) throws ClosedConnectionException, IOException {
		int written = buffer.limit() - buffer.position();
		writeOutgoing(new ByteBuffer[] {buffer});
		
		return written;
	}
	

	/**
	 * {@inheritDoc}
	 */
	public final int write(int i) throws ClosedConnectionException, IOException {
		ByteBuffer buffer = ByteBuffer.allocate(4).putInt(i);
		buffer.flip();
		return (int) write(buffer);
	}

	/**
	 * {@inheritDoc}
	 */
	public final int write(long l) throws ClosedConnectionException, IOException {
		ByteBuffer buffer = ByteBuffer.allocate(8).putLong(l);
		buffer.flip();
		return (int) write(buffer);
	}


	/**
	 * {@inheritDoc}
	 */
	public final int write(double d) throws ClosedConnectionException, IOException {
		ByteBuffer buffer = ByteBuffer.allocate(8).putDouble(d);
		buffer.flip();
		return (int) write(buffer);
	}


	/**
	 * {@inheritDoc}
	 */
	public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
		ByteBuffer[] bufs = new ByteBuffer[length];
		System.arraycopy(srcs, offset, bufs, 0, length);

		return write(bufs);
	}
	
	
	/**
	 * write a array of ByteBuffer to send. If SSL is 
	 * activated, the data will be encrypted 
	 * 
	 * @param buffers  the buffers to send
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	protected final void writeOutgoing(ByteBuffer[] buffers) throws IOException, ClosedConnectionException {
		
		// ssl connection
		if (sslOn) {
			sslProcessor.writeOutgoing(buffers);
			
		// plain connection
		} else {
			
			do {
				// write physical
				buffers = writePhysical(buffers);
				
			// loop until all bytes are written (writePhysical methods returns unwritten bytes, if socket buffer is to small) 
			} while (buffers != null);
		}
	}
		

	/**
	 * writes the given byte to the socket out channel
	 * 
	 * @param buffers   the buffers to write
	 * @return unwritten buffers (in case of 'buffers size' > 'socket output buffer size') 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	protected synchronized ByteBuffer[] writePhysical(ByteBuffer[] buffers) throws ClosedConnectionException, IOException {
		
		if (isOpen()) {
			if (buffers.length > 0) {
				
				// calculate the size to send
				int sizeToSend = 0;
				for (ByteBuffer buffer : buffers) {
					sizeToSend += buffer.remaining();
				}
				
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + id + "] sending (" + sizeToSend + " bytes): " + TextUtils.toTextOrHexString(buffers, "UTF-8", DEBUG_MAX_OUTPUT_SIZE));
				}
	

				long numberOfSendData = channel.write(buffers);
				bytesWritten += numberOfSendData;

				// size to send was more than the socket 
				// buffer output size accepts
				if (numberOfSendData < sizeToSend) {
					List<ByteBuffer> unwritten = new ArrayList<ByteBuffer>();
					for (ByteBuffer buffer : buffers) {
						if (buffer.remaining() > 0) {
							unwritten.add(buffer);
						}
					}
					return unwritten.toArray(new ByteBuffer[unwritten.size()]);
				}
			}
			
			return null;
		} else {
			ClosedConnectionException cce = new ClosedConnectionException("[" + id + "] connection " + id + " ist already closed. Couldn't write");
			LOG.throwing(this.getClass().getName(), "send(ByteBuffer[])", cce);
			throw cce;
		}
	}
	
	
	

	protected final LinkedList<ByteBuffer> extractAvailableFromReadQueue() {
		resetCachedIndex();
		return readQueue.drain();
	}
	
	
	
	
	protected final void extractRecordByLength(int length, WritableByteChannel outChannel) throws IOException {
		
		// enough data?
		if (readQueue.getSize() >= length) {
			LinkedList<ByteBuffer> buffers = readQueue.drain();
			assert (buffers != null);
			
			PARSER.extract(buffers, length, outChannel);
			readQueue.addFirst(buffers);
			resetCachedIndex();
			
		// .. no
		} else {
			throw new BufferUnderflowException();
		}
	}
	
	
	int appenToReadQueue(ByteBuffer buffer) {
		return readQueue.append(buffer);
	}



	protected int getReadQueueSize() {
		return readQueue.getSize();
	}
	
	protected final void extractRecordByDelimiterFromReadQueue(String delimiter, WritableByteChannel outChannel) throws IOException {
		
		if (!readQueue.isEmtpy()) {
			LinkedList<ByteBuffer> buffers = readQueue.drain();
			assert (buffers != null);
				
			ByteBufferParser.Index index = scanByDelimiter(buffers, delimiter);
	
			// index found?
			if (index.hasDelimiterFound()) {
				// delimiter found 
				PARSER.extract(buffers, index, outChannel);
				readQueue.addFirst(buffers);
				resetCachedIndex();
				return;	
					
			// .. no -> return buffer
			} else {
				readQueue.addFirst(buffers);
				cachedIndex = index;
			}
		}
		
		throw new BufferUnderflowException();
	}

	

	
	
	@SuppressWarnings("unchecked")
	protected final boolean extractAvailableFromReadQueue(String delimiter, WritableByteChannel outChannel) throws IOException {
		
		if (!readQueue.isEmtpy()) {
			LinkedList<ByteBuffer> buffers = readQueue.drain();
			assert (buffers != null);

			ByteBufferParser.Index index = scanByDelimiter(buffers, delimiter);
	
			// delimiter found?
			if (index.hasDelimiterFound()) {
				PARSER.extract(buffers, index, outChannel);
				readQueue.addFirst(buffers);
				resetCachedIndex();	
				return true;
					
			// delimiter not found 	
			} else {
				int readBytes = index.getReadBytes();
				if (readBytes > 0) {
					int availableBytes = readBytes - index.getDelimiterPos();
					if (availableBytes > 0) {
						PARSER.extract(buffers, availableBytes, outChannel);
						readQueue.addFirst(buffers);
						resetCachedIndex();	
					}
				}
				
				return false;
			}
			
		} else {
			return false;
		}
	}

	
	private ByteBufferParser.Index scanByDelimiter(LinkedList<ByteBuffer> buffers, String delimiter) {			

		// does index already exists (from former scan) 
		if (cachedIndex != null) {
			if (cachedIndex.getDelimiter().equals(delimiter)) {
				return PARSER.find(buffers, cachedIndex);
			} else {
				cachedIndex = null;
			}
		}

		return PARSER.find(buffers, delimiter);
	}
	
	


	
	protected final int extractIntFromReadQueue() throws BufferUnderflowException {
		resetCachedIndex();
		
		if (readQueue.getFirstBufferSize() >= 4) {
			ByteBuffer buf = readQueue.removeFirst();
			assert (buf != null);
			
			int i = buf.getInt();
			readQueue.addFirst(buf.slice());
			return i;
			
		} else {
			return ByteBuffer.wrap(readQueue.read(4)).getInt();
		}
	}
	
	
	protected final byte extractByteFromReadQueue() throws BufferUnderflowException {
		resetCachedIndex();
		
		if (readQueue.getFirstBufferSize() >= 1) {
			ByteBuffer buf = readQueue.removeFirst();
			assert (buf != null);
			
			byte b = buf.get();
			readQueue.addFirst(buf.slice());
			return b;
		}  else {
			throw new BufferUnderflowException();
		}
	}
	
	
	protected final double extractDoubleFromReadQueue() throws BufferUnderflowException {
		resetCachedIndex();
		
		if (readQueue.getFirstBufferSize() >= 8) {
			ByteBuffer buf = readQueue.removeFirst();
			assert (buf != null);
			
			double d = buf.getDouble();
			readQueue.addFirst(buf.slice());
			return d;
		} else {
			return ByteBuffer.wrap(readQueue.read(8)).getDouble();
		}
	}
	
	


	protected final long extractLongFromReadQueue() throws BufferUnderflowException {
		resetCachedIndex();
		
		if (readQueue.getFirstBufferSize() >= 8) {
			ByteBuffer buf = readQueue.removeFirst();
			assert (buf != null);
			
			long l = buf.getLong();
			readQueue.addFirst(buf.slice());
			return l;
		} else {
			return ByteBuffer.wrap(readQueue.read(8)).getLong();
		}
	}

	
	private void resetCachedIndex() {
		cachedIndex = null;
	}
	
	
	
	/**
	 * read the incoming data. If SSL is 
	 * activated, the data will be decrypted 
	 *  
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */	
	protected int readIncoming() throws IOException, ClosedConnectionException {
		int readSize = 0;
			
		// ssl connection
		if (sslOn) {
			readSize = sslProcessor.readIncoming();
	
		// plain connection
		} else {
			ByteBuffer buffer = readPhysical();
			readSize = readQueue.append(buffer);
		}
	
		return readSize;
	}
	
	
	/**
	 * aquires a "empty" ByteBuffer, which will 
	 * be used to perform the read operation of 
	 * the socket    
	 * 
	 * @return "free" ByteBuffer
	 */
	protected ByteBuffer acquireIOReadMemory() {
		if (readBuffer == null) {
			readBuffer = ByteBuffer.allocate(8192);
		}
		ByteBuffer result = readBuffer;
		readBuffer = null;
		return result;
	}

	
	/**
	 * recycles a aquired ByteBuffer, which have
	 * free space  
	 * 
	 * @param buffer the ByteBuffer to recycle
	 */
	protected void recycleIOReadMemory(ByteBuffer buffer) {
		if (buffer.hasRemaining()) {
			readBuffer = buffer;
		}
	}

	
	/**
	 * reads form the socket in channel
	 * 
	 * @return the ByteBuffer with the read bytes
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	final ByteBuffer readPhysical() throws ClosedConnectionException, IOException {
		ByteBuffer result = null;

		if (isOpen()) {
			int read = 0;
			
			ByteBuffer readBuffer = acquireIOReadMemory();
			int pos = readBuffer.position();
			int limit = readBuffer.limit();

			// read from channel
			try {
				read = channel.read( readBuffer);
			// exception occured while reading
			} catch (IOException ioe) {
				readBuffer.position(pos);
				readBuffer.limit(limit);
				recycleIOReadMemory(readBuffer);

				if (LOG.isLoggable(Level.FINER)) {
					LOG.finer("[" + id + "] error occured while reading channel: " + ioe.toString());
				}

				throw ioe;
			}


			// handle read
			switch (read) {

				// end-of-stream has been reached -> throw exception
				case -1:
					recycleIOReadMemory(readBuffer);
					ClosedConnectionException cce = new ClosedConnectionException("[" + id + "] End of stream reached");
					LOG.throwing(this.getClass().getName(), "read()", cce);
					throw cce;

				// no bytes read
				case 0:
					recycleIOReadMemory(readBuffer);
					break;

                // bytes available (read < -1 is not handled)
				default:
					lastTimeRead = System.currentTimeMillis();
					bytesRead += read;
					
					int savePos = readBuffer.position();
					int saveLimit = readBuffer.limit();
					
					readBuffer.position(savePos - read);
					readBuffer.limit(savePos);

					result = readBuffer.slice();
					
					readBuffer.position(savePos);
					readBuffer.limit(saveLimit);
					recycleIOReadMemory(readBuffer);
					
						if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + id + "] received (" + (result.limit() - result.position()) + " bytes): " + TextUtils.toTextOrHexString(new ByteBuffer[] {result.duplicate() }, "UTF-8", DEBUG_MAX_OUTPUT_SIZE));
					}
					break;
			}
		}
		return result;
	}


	
	
	
	/**
	 * flush the send buffer
	 *
	 */
	protected abstract void flushOutgoing();

	
	/**
	 * return the assoiated channel
	 * @return the assiosiated channel
	 */
	protected SocketChannel getChannel() {
		return channel;
	}
	
	



	/**
	 * log a fine msg 
	 * 
	 * @param msg the log message
	 */
	final protected void logFine(String msg) {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + getId() + "] " + msg);
		}
	}
	
	
	/**
	 * log a finer msg 
	 * 
	 * @param msg the log message
	 */
	final protected void logFiner(String msg) {
		if (LOG.isLoggable(Level.FINER)) {
			LOG.finer("[" + getId() + "] " + msg);
		}
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return id.hashCode();
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object other) {
		if (other instanceof Connection) {
			return ((Connection) other).id.equals(this.id);
		} else {
			return false;
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public String toCompactString() {
		return "id=" + getId()
	       + ", caller=" + getRemoteAddress().getCanonicalHostName() + "(" + getRemoteAddress() + ":" + getRemotePort() + ")"
	       + ", opened=" + TextUtils.printFormatedDate(connectionOpenedTime);
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(toCompactString());
		if (connectionEndTime != -1) {
			sb.append(", lifetime=" + TextUtils.printFormatedDuration(connectionEndTime - connectionOpenedTime));
		}
		sb.append(", lastTimeReceived=" + TextUtils.printFormatedDate(lastTimeRead)
				  + ", received=" + TextUtils.printFormatedBytesSize(bytesRead)
				  + ", send=" + TextUtils.printFormatedBytesSize(bytesWritten)
				  + ", receiveQueueSize=" + readQueue.getSize());
		return sb.toString();
	}	
	
	
	
	

	private final static class ReadQueue {
		private LinkedList<ByteBuffer> bufferQueue = null;

		
		/**
		 * check if queue is empty
		 * 
		 * @return true, if the queue is empty
		 */
		public synchronized boolean isEmtpy() {
			if (bufferQueue == null) {
				return true;
			}
			return bufferQueue.isEmpty();
		}
		
		

		/**
		 * append a ByteBuffer 
		 * 
		 * @param buffer the ByteBuffer to append 
		 * @return the appended size 
		 */
		public synchronized int append(ByteBuffer buffer) {		
			if (bufferQueue == null) {
				bufferQueue = new LinkedList<ByteBuffer>();
			}
			
			if (buffer != null) {
				int length = buffer.remaining();
				if (length > 0) {
					bufferQueue.addLast(buffer);
				}
				return length;
			} else {
				return 0;
			}
		}
		
		
		/**
		 * drain the queue
		 * 
		 * @return  the queue content
		 */
		public synchronized LinkedList<ByteBuffer> drain() {
			if (bufferQueue == null) {
				return null;
			}
			LinkedList<ByteBuffer> result = bufferQueue;
			bufferQueue = null;
			return result;
		}

		
		/**
		 * get the size of the first ByteBuffer in queue
		 * 
		 * @return the size of the first ByteBuffer
		 */
		public synchronized int getFirstBufferSize() {
			if (bufferQueue == null) {
				return 0;
			}  

			if (bufferQueue.size() < 1) {
				return 0;
			}
	
			ByteBuffer buffer = bufferQueue.getFirst();
			return buffer.limit() - buffer.position();
		}
		
		
		/**
		 * remove the first ByteBuffer 
		 * @return the first ByteBuffer
		 */
		public synchronized ByteBuffer removeFirst() {
			if (bufferQueue == null) {
				return null;
			}
			return bufferQueue.removeFirst();
		}
		
		
		/**
		 * add a ByteBuffer on the first position 
		 * @param buffer the ByteBuffer to add
		 */
		public synchronized void addFirst(ByteBuffer buffer) {
			if (bufferQueue == null) {
				bufferQueue = new LinkedList<ByteBuffer>();
			}

			if (buffer.hasRemaining()) {
				bufferQueue.addFirst(buffer);
			}
		}

		
		
		/**
		 * add a list of ByteBuffer on the first position. 
		 * Caution! the passed over list object will be reused internally  
		 * 
		 * @param buffers the list of ByteBuffer to add
		 */
		public synchronized void addFirst(LinkedList<ByteBuffer> buffers) {
			if (bufferQueue == null) {
				bufferQueue = buffers;
				return;
			} 
			
			if (bufferQueue.isEmpty()) {
				bufferQueue = buffers;
				return;
			}
				
			for (int i = (buffers.size() - 1); i >= 0; i--) {
				ByteBuffer buffer = buffers.get(i);
				if (buffer.hasRemaining()) {
					bufferQueue.addFirst(buffer);
				}
			}
		}
		
		
		/**
		 * read bytes 
		 * 
		 * @param length  the length
		 * @return the read bytes
		 * @throws BufferUnderflowException if the delimiter has not been found  
		 */
		public synchronized byte[] read(int length) throws BufferUnderflowException {
			if (bufferQueue == null) {
				throw new BufferUnderflowException();
			}
			
			
			byte[] bytes = new byte[length];
			int pointer = 0;

			// enough bytes available ?
			if (!isSizeEqualsOrLargerThan(length)) {
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
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("Warning unexpected Buffer underflow occured");
					}
					throw new BufferUnderflowException();
				}
			}
		}

		
		private boolean isSizeEqualsOrLargerThan(int size) {
			if (bufferQueue == null) {
				return false;
			}
			
			int l = 0;

			for (ByteBuffer buffer : bufferQueue) {
				l += buffer.limit() - buffer.position();
				if (l >= size) {
					return true;
				}
			}

			return false;
		}
		

		/**
		 * get the queue size
		 * @return the queue size
		 */
		public synchronized int getSize() {
			if (bufferQueue == null) {
				return 0;
			}
			
			int size = 0;
			
			int bufferSize = bufferQueue.size();
			for (int i = 0; i < bufferSize; i++) {
				size += bufferQueue.get(i).remaining();
			}
			return size;
		}
		
			

		@Override
		public String toString() {
			if (bufferQueue == null) {
				return "";
			} else {
				return TextUtils.toTextOrHexString(bufferQueue.toArray(new ByteBuffer[bufferQueue.size()]), "US-ASCII", 500);
			}
		}
	}
}
