// $Id: Connection.java 1276 2007-05-28 15:38:57Z grro $
/*
 *  Copyright (c) xsocket.org, 2006 - 2007. All rights reserved.
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
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketOptions;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;


import org.xsocket.ClosedConnectionException;
import org.xsocket.DataConverter;
import org.xsocket.DynamicWorkerPool;
import org.xsocket.IDispatcher;
import org.xsocket.IWorkerPool;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.stream.ByteBufferParser.Index;
import org.xsocket.stream.IoHandler.IIOEventHandler;


/**
 * Implementation base of the <code>IConnection</code> interface.
 * 
 *
 * @author grro@xsocket.org
 */
abstract class Connection implements IConnection {
	
	private static final Logger LOG = Logger.getLogger(Connection.class.getName());
	
	static final FlushMode INITIAL_FLUSH_MODE = FlushMode.SYNC;
	
	private static final long SEND_TIMEOUT = 60 * 1000;
	
	
	// parser
	private static final ByteBufferParser PARSER = new ByteBufferParser();
	
	
	// global entities
	private static IWorkerPool globalWorkerPool = null;
	private static IMemoryManager globalMemoryManager = null;
	private static IDispatcher<IoSocketHandler> globalDispatcher = null; 

	
	
	// read & write queue
	private final ByteBufferQueue writeQueue = new ByteBufferQueue();
	private final ByteBufferQueue readQueue = new ByteBufferQueue();

	// io handler
	private IoHandler ioHandler = null;
	
	// encoding
	private String defaultEncoding = INITIAL_DEFAULT_ENCODING;
	
	// autoflush
	private boolean autoflush = INITIAL_AUTOFLUSH;
	
	// flush mode
	private FlushMode flushmode = INITIAL_FLUSH_MODE;
	
	// index for extract method
	private Index cachedIndex = null;

	
	// attachment
	private Object attachment = null;
	

	// write thread handling
	private Object writeGuard = new Object();
	private IOException writeException = null;

	
	// mark support
	private LinkedList<ByteBuffer> readMarkBuffer = null;
	private boolean isReadMarked = false;
	
	private WriteMarkBuffer writeMarkBuffer = null;
	private boolean isWriteMarked = false;
	
	private IoSocketHandler socketHandler = null;
	
	
	Connection()  {
		
	}
	
	protected void init(IoSocketHandler socketHandler, SSLContext sslContext, boolean sslOn, boolean isClientMode, IMemoryManager sslMemoryManager) throws SocketException, IOException {
		this.socketHandler = socketHandler;
		
		// bind the connection's eventIOHandler on the socketHandler
		socketHandler.setIOEventHandler(new IOEventHandler());
		
		
		// ssl connection?
		if (sslContext != null) {
			if (sslOn) {
				setIOHandler(new IoSSLHandler(socketHandler, sslContext, isClientMode, sslMemoryManager));
			} else {
				setIOHandler(new IoActivateableSSLHandler(socketHandler, sslContext, isClientMode, sslMemoryManager));
			}
			
			open();

		// .. no 
		} else {
			setIOHandler(socketHandler);
			open();
		}
	}
	
	
	/**
	 * reset the connection state (e.g. for connection reuse)
	 *
	 */
	void reset() {
		writeQueue.drain();

		writeGuard = new Object();
		writeException = null;

		setAutoflush(IBlockingConnection.INITIAL_AUTOFLUSH);
		setFlushmode(INITIAL_FLUSH_MODE);
		setDefaultEncoding(IBlockingConnection.INITIAL_DEFAULT_ENCODING);
		removeReadMark();
		removeWriteMark();
		resetCachedIndex();
		attachment = null;

		ioHandler.drainIncoming();
		readQueue.drain();
	}
	
	
	/**
	 * return the socketHandler
	 * @return  the socket handler
	 */
	protected final IoSocketHandler getIoSocketHandler() {
		return socketHandler;
	}
         	
	/**
	 * open the connection
	 * 
	 * @throws IOException If some other I/O error occurs
	 */
	void open() throws IOException {
		ioHandler.open();
	}
	

	/**
	 * get the socket options of the underlying socket. Method is for test purposes only!
	 * @return
	 */
	final SocketOptions getSocketOptions() {
		return socketHandler.getSocketOptions();
	}
	
	
	/**
	 * return the read queue 
	 * 
	 * @return the read queue
	 */
	final ByteBufferQueue getReadQueue() {
		return readQueue;
	}
	
	String printInQueue() {
		StringBuilder sb = new StringBuilder();

		if (readMarkBuffer != null) {
			ByteBuffer[] copy = new ByteBuffer[readMarkBuffer.size()];
			for (int i = 0; i < copy.length; i++) {
				copy[i] = readMarkBuffer.get(i).duplicate();
			}
			sb.append("[" + DataConverter.toTextOrHexString(copy, "UTF-8", Integer.MAX_VALUE) + "] ");
		}
		sb.append(readQueue.toTextOrHexString());
		
		return sb.toString();
	}
	
	/**
	 * return the global worker pool
	 * 
	 * @return the global worker pool
	 */
	static final synchronized IWorkerPool getGlobalWorkerPool() {
		if (globalWorkerPool == null) {
			globalWorkerPool = new DynamicWorkerPool(0, 250);
		}
		return globalWorkerPool;
	}

	
	/**
	 * return the global memory manager
	 * 
	 * @return the global memory manager
	 */
	static synchronized IMemoryManager getGlobalMemoryManager() {
		if (globalMemoryManager == null) {
			globalMemoryManager = new MemoryManager(65536, true);
		}
		return globalMemoryManager;
	}

	

	static synchronized IDispatcher<IoSocketHandler> getGlobalDispatcher() {
		if (globalDispatcher == null) {
			globalDispatcher = newDispatcher("xGlobalDispatcher", new MemoryManager(65536, true));
		}
		return globalDispatcher;
	}
	

	static IoSocketDispatcher newDispatcher(String name, IMemoryManager memoryManager) {
		IoSocketDispatcher dispatcher = new IoSocketDispatcher(memoryManager);
		Thread t = new Thread(dispatcher);
		t.setName(name);
		t.setDaemon(true);
		t.start();
		
		return dispatcher;
	}

	
	static IoSocketHandler createClientIoSocketHandler(InetSocketAddress inetAddress, IMemoryManager memoryManager, IDispatcher<IoSocketHandler> dispatcher, StreamSocketConfiguration socketConfiguration) throws IOException {
		SocketChannel channel = SocketChannel.open();
		if (socketConfiguration != null) {
			socketConfiguration.setOptions(channel.socket());
		}
		
		channel.socket().connect(inetAddress);
		
		IoSocketHandler socketHdl = new IoSocketHandler(channel, "c.", dispatcher);
		socketHdl.setMemoryManager(memoryManager);
		
		while (!socketHdl.getChannel().finishConnect()) {
			try {
				Thread.sleep(25);
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("Waiting to finish connection");
				}
			} catch (InterruptedException ignore) { }
		}
		
		return socketHdl;
	}
	

	/**
	 * return the underlying io handler
	 * 
	 * @return the underlying io handler
	 */
	final IoHandler getIOHandler() {
		return ioHandler;
	}

	/**
	 * set the underlying io handler 
	 * 
	 * @param ioHdl  the underlying io handler
	 */
	final void setIOHandler(final IoHandler ioHdl) {
		this.ioHandler = ioHdl;
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void close() throws IOException {
		internalFlushStrong();
		ioHandler.close(false);		
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final boolean isOpen() {
		return ioHandler.isOpen();
	}
		
	/**
	 * write incoming data into the read buffer 
	 * 
	 * @param data the data to add
	 */
	final void writeIncoming(ByteBuffer data) {
		readQueue.append(data);
	}
	

	/**
	 * write outgoing data into the write buffer 
	 * 
	 * @param data the data to add
	 */
	final void writeOutgoing(ByteBuffer data) {
		writeQueue.append(data);
	}
	
	/**
	 * write outgoing datas into the write buffer
	 *  
	 * @param datas the data to add
	 */
	void writeOutgoing(LinkedList<ByteBuffer> datas) {
		writeQueue.append(datas);	
	}
	

	/**
	 * {@inheritDoc}
	 */ 
	public void flush() throws ClosedConnectionException, IOException {
		if (autoflush) {
			LOG.warning("flush has been called for a connection which is in autoflush mode (since xSocket V1.1 autoflush is activated by default)");
		}
		internalFlush();
	}	
	
	/**
	 * real flush. This method will only called by framework internal classes 
	 * 
	 * @throws ClosedConnectionException  if the connection has been closed
	 * @throws IOException if some io excpetion occurs
	 */
	void internalFlush() throws ClosedConnectionException, IOException {

		removeWriteMark();  
		if (flushmode == FlushMode.SYNC) {
			syncFlush();
		} else {
			flushWriteQueue();	
		}
	}
	
	
	/**
	 * set the flushmode 
	 * @param flushMode the flushmode
	 */
	void setFlushmode(FlushMode flushMode) {
		this.flushmode = flushMode;
	}
	
	/**
	 * get the flushmode
	 * 
	 * @return the flushmode
	 */
	FlushMode getFlushmode() {
		return flushmode;
	}
	
	
	/**
	 * flush, and wait until data has been written to channel
	 */
	private void syncFlush() throws ClosedConnectionException, IOException {
	
		long start = System.currentTimeMillis();
		long remainingTime = SEND_TIMEOUT;
			
		synchronized (writeGuard) {
			flushWriteQueue();

			do {
				// all buffers empty?
				if (getIOHandler().isChainSendBufferEmpty()) {
					return;
				
				// write exception occured?	
				} else if(writeException != null) {
					IOException ioe = writeException;
					writeException = null;
					throw ioe;
					
				// ... no -> wait
				} else {
					try {
						writeGuard.wait(remainingTime);
					} catch (InterruptedException ignore) { }
				}
				
				remainingTime = (start + SEND_TIMEOUT) - System.currentTimeMillis();	
			} while (remainingTime > 0);
		}
	}
	
	private void flushWriteQueue() throws ClosedConnectionException, IOException {
		if (!writeQueue.isEmpty()) {
			LinkedList<ByteBuffer> buffer = writeQueue.drain();
			ioHandler.writeOutgoing(buffer);
		}
	}
	
	
	private void internalFlushStrong() throws ClosedConnectionException, IOException {
		internalFlush();
		getIOHandler().flushOutgoing();
	}
	

	

	
	/**
	 * {@inheritDoc}
	 */
	public final String getDefaultEncoding() {
		return defaultEncoding;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void setDefaultEncoding(String defaultEncoding) {
		this.defaultEncoding = defaultEncoding;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void setAutoflush(boolean autoflush) {
		this.autoflush = autoflush;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public boolean getAutoflush() {
		return autoflush;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final String getId() {
		return ioHandler.getId();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public InetAddress getLocalAddress() {
		return ioHandler.getLocalAddress();
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getLocalPort() {
		return ioHandler.getLocalPort();
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public InetAddress getRemoteAddress() {
		return ioHandler.getRemoteAddress();
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getRemotePort() {
		return ioHandler.getRemotePort();
	}
	
		
	/**
	 * call back method for the idle timeout
	 *
	 */
	void onIdleTimeout() {
		try {
			close();
		} catch (IOException ignore) { }
	}
	

	/**
	 * call back method for connection timeout
	 *
	 */
	void onConnectionTimeout() {
		try {
			close();
		} catch (IOException ignore) { }
	}

	
	/**
	 * receive data 
	 *
	 */
	final void receive() {
		LinkedList<ByteBuffer> buffers = getIOHandler().drainIncoming();
		
		if (LOG.isLoggable(Level.FINER)) {
			int received = 0;
			for (ByteBuffer buffer : buffers) {
				received += buffer.remaining();
			}
			
			LOG.finer("appending " + received + " bytes to connection's read queue");
		}

		getReadQueue().append(buffers);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void activateSecuredMode() throws IOException {

		IoHandler ioHandler = getIOHandler();
		do {
			if (ioHandler instanceof IoActivateableSSLHandler) {
				break;
			}
			ioHandler = ioHandler.getSuccessor();
		} while (ioHandler != null);
		
		
		if (ioHandler != null) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("activating ssl");
			}
			IoActivateableSSLHandler sslHandler = (IoActivateableSSLHandler) ioHandler;
			
			sslHandler.stopProcessingIncoming();

			internalFlushStrong();

			sslHandler.startSSL(readQueue);
			
		} else {
			throw new IOException("couldn't startSSL, because no SSLHandler (SSLContext) is set");
		}
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
		ByteBuffer buffer = DataConverter.toByteBuffer(s, encoding);
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
	public final long write(ByteBuffer[] buffers) throws ClosedConnectionException, IOException {
		if (isOpen()) {
			long written = 0;
			for (ByteBuffer buffer : buffers) {
				written += buffer.limit() - buffer.position();
			}
	
			
			if (isWriteMarked) {
				for (ByteBuffer buffer : buffers) {
					writeMarkBuffer.add(buffer);
				}
				
			} else {
				for (ByteBuffer buffer : buffers) {
					writeQueue.append(buffer);
				}
			}
	
		
			if (autoflush) {
				internalFlush();
			}
			
			return written;
			
		} else {
			throw new ClosedConnectionException("connection " + getId() + " is already closed");
		}
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final int write(ByteBuffer buffer) throws ClosedConnectionException, IOException {
		if (isOpen()) {
			int written = buffer.limit() - buffer.position();
			
			if (isWriteMarked) {
				writeMarkBuffer.add(buffer);
				
			} else {
				writeQueue.append(buffer);
			}
			
			
			if (autoflush) {
				internalFlush();
			}
			
			return written;
		} else {
			throw new ClosedConnectionException("connection " + getId() + " is already closed");
		}

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
	public final long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
		ByteBuffer[] bufs = new ByteBuffer[length];
		System.arraycopy(srcs, offset, bufs, 0, length);

		return write(bufs);
	}

	
	/**
	 * extract all bytes from the queue 
	 * 
	 * @return all bytes of the queue
	 */
	protected final LinkedList<ByteBuffer> extractAvailableFromReadQueue() {
		resetCachedIndex();
		
		LinkedList<ByteBuffer> buffers = readQueue.drain();
		onExtracted(buffers);
		
		return buffers;
	}
	
	/**
	 * extract bytes by using a delimiter
	 * 
	 * @param delimiter   the delimiter
	 * @param maxLength   the max length of bytes that should be read. If the limit will be exceeded a MaxReadSizeExceededException will been thrown  
	 * @return the extracted data
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if the buffer's limit has been reached
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found	 */
	protected final LinkedList<ByteBuffer> extractBytesByDelimiterFromReadQueue(String delimiter, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		
		if (!readQueue.isEmpty()) {
			LinkedList<ByteBuffer> buffers = readQueue.drain();
			assert (buffers != null);
				
			ByteBufferParser.Index index = scanByDelimiter(buffers, delimiter);
	
			
			//	max Limit exceeded?
			if (index.getReadBytes() > maxLength) {
				throw new MaxReadSizeExceededException();
			}
			
			// index found?
			if (index.hasDelimiterFound()) {
				// delimiter found 
				LinkedList<ByteBuffer> extracted = PARSER.extract(buffers, index);
				onExtracted(extracted);
				
				readQueue.addFirst(buffers);
				resetCachedIndex();
				
				return extracted;	
					
			// .. no -> return buffer
			} else {
				readQueue.addFirst(buffers);
				cachedIndex = index;
			}
		}
		
		throw new BufferUnderflowException();
	}

	
	/**
	 * Returns the index within this string of the first occurrence of the specified substring
	 * 
	 * @param str any string
	 * @return if the string argument occurs as a substring within this object, then the 
	 *         index of the first character of the first such substring is returned; 
	 *         if it does not occur as a substring, -1 is returned.
	 */
	public int indexOf(String str) {
		
		int length = 0;
		
		if (!readQueue.isEmpty()) {
			LinkedList<ByteBuffer> buffers = readQueue.drain();
			ByteBufferParser.Index index = scanByDelimiter(buffers, str);
	
			// index found?
			if (index.hasDelimiterFound()) {
				length = index.getReadBytes() - str.length();
					
			// .. no 
			} else {
				length = -1;
			}
			
			readQueue.addFirst(buffers);
			cachedIndex = index;
		}
		
		return length;
	}
	
	

	protected final int readIndexOf(String str, int maxReadSize) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		
		int length = 0;
		
		if (!readQueue.isEmpty()) {
			LinkedList<ByteBuffer> buffers = readQueue.drain();
			ByteBufferParser.Index index = scanByDelimiter(buffers, str);
	
			// index found?
			if (index.hasDelimiterFound()) {
				length = index.getReadBytes() - str.length();
					
			// .. no 
			} else {
				length = -1;
			}
			
			readQueue.addFirst(buffers);
			cachedIndex = index;
		} else {
			length = -1;
		}
		
		if (length < 0) {
			if (readQueue.getSize() >= maxReadSize) {
				throw new MaxReadSizeExceededException();
			}
			throw new BufferUnderflowException();
		}
		
		return length;
	}
	
	
	/**
	 * extracts bytes by using 
	 * 
	 * @param length      the number of bytes to extract
	 * @return the exctracted data
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if the buffer's limit has been reached	
	 */
	protected final LinkedList<ByteBuffer> extractBytesByLength(int length) throws IOException, BufferUnderflowException {
		
		// enough data?
		if (readQueue.getSize() >= length) {
			LinkedList<ByteBuffer> buffers = readQueue.drain();
			assert (buffers != null);
			
			LinkedList<ByteBuffer> extracted = PARSER.extract(buffers, length);
			onExtracted(extracted);
			
			readQueue.addFirst(buffers);
			resetCachedIndex();
			
			return extracted;
			
		// .. no
		} else {
			throw new BufferUnderflowException();
		}
	}
	
	
	
	/**
	 * extract available bytes from the queue by using a delimiter
	 * 
	 * @param delimiter    the delimiter
	 * @param outChannel   the channel to write in
	 * @return true if the delimiter has been found
	 * @throws IOException If some other I/O error occurs 
	 */
	@SuppressWarnings("unchecked")
	protected final boolean extractAvailableFromReadQueue(String delimiter, WritableByteChannel outChannel) throws IOException {
		
		if (!readQueue.isEmpty()) {
			LinkedList<ByteBuffer> buffers = readQueue.drain();
			assert (buffers != null);

			ByteBufferParser.Index index = scanByDelimiter(buffers, delimiter);

			
			// delimiter found?
			if (index.hasDelimiterFound()) {
				LinkedList<ByteBuffer> extracted = PARSER.extract(buffers, index);
				onExtracted(extracted);
				for (ByteBuffer buffer : extracted) {
					outChannel.write(buffer);	
				}
								
				readQueue.addFirst(buffers);
				resetCachedIndex();	
				return true;
					
			// delimiter not found 	
			} else {
				// read only if not part of delimiter has been detected 
				if (index.getDelimiterPos() == 0) {
					int readBytes = index.getReadBytes();
					if (readBytes > 0) {
						int availableBytes = readBytes - index.getDelimiterPos();
						if (availableBytes > 0) {
							LinkedList<ByteBuffer> extracted = PARSER.extract(buffers, availableBytes);
							onExtracted(extracted);
							for (ByteBuffer buffer : extracted) {
								outChannel.write(buffer);	
							}
							
							resetCachedIndex();	
						}
					}
				} 
				
				readQueue.addFirst(buffers);
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
	
	
	private void onExtracted(LinkedList<ByteBuffer> buffers) {
		for (ByteBuffer buffer : buffers) {
			onExtracted(buffer);
		}
	}

	
	private void onExtracted(ByteBuffer buffer) {
		
		if (isReadMarked) {
			readMarkBuffer.addLast(buffer.duplicate());	
		}
	}
	

	
	
	/**
	 * extract bytes from the queue
	 * 
	 * @param length the number of bytes to extract
	 * @return the bytes
 	 * @throws BufferUnderflowException if the buffer's limit has been reached 
	 */
	protected final byte[] extractBytesFromReadQueue(int length) throws BufferUnderflowException {
		resetCachedIndex();
		
		ByteBuffer buffer = readQueue.read(length);
		onExtracted(buffer);
		return DataConverter.toBytes(buffer);
	}

	
	/**
	 * extract a int from the queue
	 * 
	 * @return the int value
 	 * @throws BufferUnderflowException if the buffer's limit has been reached
	 */
	protected final int extractIntFromReadQueue() throws BufferUnderflowException {
		resetCachedIndex();
		
		ByteBuffer buffer = readQueue.read(4);
		onExtracted(buffer);
		return buffer.getInt();
	}
	
	
	/**
	 * extract a byte value from the queue
	 * 
	 * @return the byte value
 	 * @throws BufferUnderflowException if the buffer's limit has been reached
	 */
	protected final byte extractByteFromReadQueue() throws BufferUnderflowException {
		resetCachedIndex();
		
		ByteBuffer buffer = readQueue.read(1);
		onExtracted(buffer);
		
		return buffer.get();
	}
	
	
	/**
	 * extract a double value from the queue 
	 * 
	 * @return the double value
 	 * @throws BufferUnderflowException if the buffer's limit has been reached
	 */
	protected final double extractDoubleFromReadQueue() throws BufferUnderflowException {
		resetCachedIndex();

		ByteBuffer buffer = readQueue.read(8);
		onExtracted(buffer);

		return buffer.getDouble();
	}
	
	

	/**
	 * extract a long value from the queue
	 * 
	 * @return the long value
 	 * @throws BufferUnderflowException if the buffer's limit has been reached 
	 */
	protected final long extractLongFromReadQueue() throws BufferUnderflowException {
		resetCachedIndex();
		
		ByteBuffer buffer = readQueue.read(8);
		onExtracted(buffer);

		return buffer.getLong();
	}

	
	private void resetCachedIndex() {
		cachedIndex = null;
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public final Object attach(Object obj) {
		Object old = attachment;
		attachment = obj;
		return old;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final Object attachment() {
		return attachment;
	}

	
	/**
	 * {@inheritDoc}
	 */
	public void markReadPosition() {
		removeReadMark();
		
		isReadMarked = true; 
		readMarkBuffer = new LinkedList<ByteBuffer>();
	}

	
	/**
	 * {@inheritDoc}
	 */	
	public void markWritePosition() {
		if (getAutoflush()) {
			throw new UnsupportedOperationException("write mark is only supported for mode autoflush off");
		}
		removeWriteMark();
		
		isWriteMarked = true;
		writeMarkBuffer = new WriteMarkBuffer();
	}

	
	/**
	 * {@inheritDoc}
	 */
	public boolean resetToWriteMark() {
		if (isWriteMarked) {
			writeMarkBuffer.resetWritePosition();
			return true;
			
		} else {
			return false;
		}
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public boolean resetToReadMark() {
		if (isReadMarked) {
			getReadQueue().addFirst(readMarkBuffer);
			removeReadMark();
			return true;
			
		} else {
			return false;
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 */	
	public void removeReadMark() {
		isReadMarked = false;
		readMarkBuffer = null;
	}

	
	/**
	 * {@inheritDoc}
	 */
	public void removeWriteMark() {
		if (isWriteMarked) {
			isWriteMarked = false;
			writeQueue.append(writeMarkBuffer.drain());
			writeMarkBuffer = null;
		}
	}

	
	protected void onDataEvent() {
		
	}
	
	
	protected void onConnectEvent() {
		
	}

	
	protected void onDisconnectEvent() {
		
	}
	
	protected void onConnectionTimeoutEvent() {

	}
	
	protected void onIdleTimeoutEvent() {
		
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
			try {
			if (isOpen()) {
				return "id=" + getId() + ", remote=" + getRemoteAddress().getCanonicalHostName() + "(" + getRemoteAddress() + ":" + getRemotePort() + ")";
			} else {
				return "id=" + getId() + " (closed)";
			}
		} catch (Exception e) {
			return super.toString();
		}		
	}
	
	
	
	
	private static final class WriteMarkBuffer {
		private final Entry head = new Entry(null, null);
		private Entry tail = head;

		
		public LinkedList<ByteBuffer> drain() {
			LinkedList<ByteBuffer> result = new LinkedList<ByteBuffer>();
			
			Entry entry = head;
			do {
				entry = entry.next;
				if (entry!= null) {
					result.add(entry.element);
				}
				
			} while (entry != null);
			
			head.next = null;	
			tail = head;
			
			return result;
		}
		
		public void add(ByteBuffer data) {
			int size = data.remaining();
			
			if (size == 0) {
				return;
			}
			
			
			// add entry on tail
			Entry entry = new Entry(data, tail.next);
			tail.next = entry;
			tail = entry;


			// tail is not last entry -> reamove written size on leading	
			while (size > 0) {
				if (tail.next != null) {
					int nextSize = tail.next.element.remaining();
					
					// next size =< (written) size -> remove it
					if (nextSize <= size) {
						size = size - nextSize;
						
						tail.next = tail.next.next;
						if (tail.next == null) {
							break;
						}
											
					// next size > (written) size -> slice and remove written size
					} else {
						ByteBuffer buffer = tail.next.element;
						buffer.position(buffer.position() + size);
						ByteBuffer sliced = buffer.slice();
						
						Entry slicedEntry = new Entry(sliced, tail.next.next);
						tail.next = slicedEntry;
						break;
					}
				} else {
					break;
				}
			}
		}
				
	
		
		public void resetWritePosition() {
			tail = head;
		}
	}
	
	private static class Entry {
		private ByteBuffer element = null;
		private Entry next = null;

		Entry(ByteBuffer element, Entry next) {
			this.element = element;
			this.next = next;
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			
			if (element != null) {
				sb.append(DataConverter.toHexString(new ByteBuffer[] {element}, 100000));
			}

			if (next != null) {
				sb.append(next.toString());
			}
			
			return sb.toString();
		}
	}
	
	
	
	private final class IOEventHandler implements IIOEventHandler {
	
		public boolean listenForWritten() {
			return true;
		}
		
		public void onWrittenEvent() {
			synchronized (writeGuard) {
				writeGuard.notify();
			}		
		}
		
		public void onWriteExceptionEvent(IOException ioe) {
			synchronized (writeGuard) {
				writeException = ioe;
				writeGuard.notify();
			}
		}
		
		public void onDataEvent() {
			Connection.this.onDataEvent();
		}
		
		public void initiateClose() {
			try {
				close();
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + getId() + "] error occured closing connection. Reason: " + ioe.toString());
				}
			}					
		}

		public boolean listenForConnect() {
			return true;
		}
		
		public void onConnectEvent() {
			Connection.this.onConnectEvent();
		}

		public boolean listenForDisconnect() {
			return true;
		}
		
		public void onDisconnectEvent() {
			Connection.this.onDisconnectEvent();
		}
		
		public void onConnectionTimeout() {
			Connection.this.onConnectionTimeoutEvent();
		}
		
		public void onIdleTimeout() {
			Connection.this.onIdleTimeoutEvent();
		}
	}
}
