// $Id: Connection.java 1568 2007-07-26 06:47:32Z grro $
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
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketOptions;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;


import org.xsocket.ByteBufferQueue;
import org.xsocket.ClosedConnectionException;
import org.xsocket.DataConverter;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.stream.ByteBufferParser.Index;
import org.xsocket.stream.io.impl.IoProvider;
import org.xsocket.stream.io.spi.IClientIoProvider;
import org.xsocket.stream.io.spi.IHandlerIoProvider;
import org.xsocket.stream.io.spi.IIoHandler;
import org.xsocket.stream.io.spi.IIoHandlerCallback;
import org.xsocket.stream.io.spi.IIoHandlerContext;



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


	// io handler
	static final IoProvider DEFAULT_CLIENT_IO_PROVIDER = new IoProvider(); // will be removed
	private static IClientIoProvider clientIoProvider = null;
	private IHandlerIoProvider ioProvider = null;

	
	// closed flag
	private boolean isClosed = false;

	// read & write queue
	private final ByteBufferQueue writeQueue = new ByteBufferQueue();
	private final ByteBufferQueue readQueue = new ByteBufferQueue();


	// io handler
	private IIoHandler ioHandler = null;
	private IIoHandlerContext ioHandlerCtx = null;


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


	// timout
	private boolean idleTimeoutOccured = false;
	private boolean connectionTimeoutOccured = false;



	static {
		// IoHandlerManager
		String clientIoManagerClassname = System.getProperty(IClientIoProvider.PROVIDER_CLASSNAME_KEY, IoProvider.class.getName());
		try { 
			Class clientIoManagerClass = Class.forName(clientIoManagerClassname);
			clientIoProvider = (IClientIoProvider) clientIoManagerClass.newInstance();
		} catch (Exception e) {
			LOG.warning("error occured by creating ClientIoManager " + clientIoManagerClassname + ": " + e.toString());
			LOG.info("using default ClientIoManager " + DEFAULT_CLIENT_IO_PROVIDER.getClass().getName());
			clientIoProvider = DEFAULT_CLIENT_IO_PROVIDER;
		}
	}


	/**
	 * {@inheritDoc}
	 */
	public final int getPendingWriteDataSize() {
		return writeQueue.getSize() + ioHandler.getPendingWriteDataSize();
	}


	/**
	 * client-side constructor
	 *
	 */
	Connection(IIoHandlerContext ioHandlerCtx, InetSocketAddress remoteAddress, Map<String ,Object> options, SSLContext sslContext, boolean sslOn) throws IOException {
		this.ioHandlerCtx = ioHandlerCtx;

		if (sslContext != null) {
			ioHandler = ((IoProvider) clientIoProvider).createSSLClientIoHandler(ioHandlerCtx, remoteAddress, options, sslContext, sslOn);
		} else {
			ioHandler = clientIoProvider.createClientIoHandler(ioHandlerCtx, remoteAddress, options);
		}
		
		ioProvider = clientIoProvider;
	}


	/**
	 * server-side constructor
	 *
	 */
	Connection(IIoHandlerContext ioHandlerCtx, IIoHandler ioHandler, IHandlerIoProvider ioProvider) throws IOException {
		this.ioHandlerCtx = ioHandlerCtx;
		this.ioHandler = ioHandler;
		this.ioProvider = ioProvider;
	}

	
	protected final IHandlerIoProvider getIoProvider() {
		return ioProvider;
	}


	protected final IIoHandlerContext getIoHandlerContext() {
		return ioHandlerCtx;
	}

	protected final void init() throws IOException {
		ioHandler.init(new HandlerCallback());

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("connection " + getId() + " created. IoHandler: " + ioHandler.toString());
		}
	}

	protected final IIoHandlerContext getioHandlerCtx() {
		return ioHandlerCtx;
	}

	protected final void setIoHandler(IIoHandler ioHandler) {
		this.ioHandler = ioHandler;
	}

	/**
	 * reset the connection state (e.g. for connection reuse)
	 *
	 */
	void reset() throws IOException {
		writeQueue.drain();

		writeGuard = new Object();
		writeException = null;

		resumeRead();
		setIdleTimeoutSec(Integer.MAX_VALUE);
		setConnectionTimeoutSec(Integer.MAX_VALUE);
		setAutoflush(IBlockingConnection.INITIAL_AUTOFLUSH);
		setFlushmode(INITIAL_FLUSH_MODE);
		setDefaultEncoding(IBlockingConnection.INITIAL_DEFAULT_ENCODING);
		removeReadMark();
		removeWriteMark();
		resetCachedIndex();
		attachment = null;
		
		idleTimeoutOccured = false;
		connectionTimeoutOccured = false;

		ioHandler.drainIncoming();
		readQueue.drain();
	}


	/**
	 * return the ioHandler
	 * @return  the iohandler
	 */
	protected final IIoHandler getIoHandler() {
		return ioHandler;
	}



	/**
	 * @deprecated
	 */
	final SocketOptions getSocketOptions() {
		Map<String, Object> opt = new HashMap<String, Object>();
		
		Map<String, Class> setOpts = getOptions();
		for (String optionName : setOpts.keySet()) {
			try {
				opt.put(optionName, getOption(optionName));
			} catch (IOException ignore) { };
		}
		
		return StreamSocketConfiguration.fromOptions(opt);
	}


	/**
	 * return the read queue
	 *
	 * @return the read queue
	 */
	final ByteBufferQueue getReadQueue() {
		return readQueue;
	}



	/**
	 * {@inheritDoc}
	 */
	public final void close() throws IOException {
		if (isOpen() && !isClosed) {
			isClosed = true;

			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("closing connection -> flush all remaining data");
			}

			flushWriteQueue();
			ioHandler.close(false);
		}
	}


	/**
	 * {@inheritDoc}
	 */
	public final boolean isOpen() {
		if (isClosed) {
			return false;
		} else {
			return ioHandler.isOpen();
		}
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
	public void suspendRead() throws IOException {
		ioHandler.suspendRead();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void resumeRead() throws IOException {
		ioHandler.resumeRead();
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
			// flush write queue by using a write guard to wait until the onWrittenEvent has been occured
			syncFlush();
			
		} else {
			// just flush the queue and return 
			flushWriteQueue();
		}
	}


	/**
	 * set the flushmode
	 * @param flushMode the flushmode
	 */
	public final void setFlushmode(FlushMode flushMode) {
		this.flushmode = flushMode;
	}

	/**
	 * get the flushmode
	 *
	 * @return the flushmode
	 */
	public final FlushMode getFlushmode() {
		return flushmode;
	}


	/**
	 * {@inheritDoc}
	 * 
	 */
	public void setIdleTimeoutSec(int timeoutInSec) {
		getIoHandler().setIdleTimeoutSec(timeoutInSec);		
	}

	
	/**
	 * {@inheritDoc}
	 */
	public void setConnectionTimeoutSec(int timeoutSec) {
		getIoHandler().setConnectionTimeoutSec(timeoutSec);
	}


	/**
	 * {@inheritDoc}
	 */
	public int getConnectionTimeoutSec() {
		return getIoHandler().getConnectionTimeoutSec();
	}

	
	
	/**
	 * {@inheritDoc}
	 * 
	 */
	public int getIdleTimeoutSec() {
		return getIoHandler().getIdleTimeoutSec();
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
				// all data written?
				if (ioHandler.getPendingWriteDataSize() == 0) {
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
			LinkedList<ByteBuffer> buffers = writeQueue.drain();
			ioHandler.writeOutgoing(buffers);
		}
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
	public final void setAutoflush(boolean autoflush) {
		this.autoflush = autoflush;
	}


	/**
	 * {@inheritDoc}
	 */
	public final boolean getAutoflush() {
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
	public final InetAddress getLocalAddress() {
		return ioHandler.getLocalAddress();
	}



	/**
	 * {@inheritDoc}
	 */
	public final int getLocalPort() {
		return ioHandler.getLocalPort();
	}



	/**
	 * {@inheritDoc}
	 */
	public final InetAddress getRemoteAddress() {
		return ioHandler.getRemoteAddress();
	}



	/**
	 * {@inheritDoc}
	 */
	public final int getRemotePort() {
		return ioHandler.getRemotePort();
	}





	/**
	 * {@inheritDoc}
	 */
	public void activateSecuredMode() throws IOException {

		boolean isPrestarted = DEFAULT_CLIENT_IO_PROVIDER.preStartSecuredMode(ioHandler);

		if (isPrestarted) {
			internalFlush();
			DEFAULT_CLIENT_IO_PROVIDER.startSecuredMode(ioHandler, readQueue.drain());
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
	public final int write(short s) throws ClosedConnectionException, IOException {
		ByteBuffer buffer = ByteBuffer.allocate(2).putShort(s);
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
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn�t been found	 */
	protected final LinkedList<ByteBuffer> extractBytesByDelimiterFromReadQueue(byte[] delimiter, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

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
				onExtracted(extracted, delimiter);

				readQueue.addFirstSilence(buffers);
				resetCachedIndex();

				return extracted;

			// .. no -> return buffer
			} else {
				readQueue.addFirstSilence(buffers);
				cachedIndex = index;
			}
		}

		
		if (isOpen()) {
			throw new BufferUnderflowException();
			
		} else {
			// remaining reveived data of underlying io handler available? 
			int read = retrieveIoHandlerData();
			
			// got data? -> retry
			if (read > 0) {
				return extractBytesByDelimiterFromReadQueue(delimiter, maxLength);
				
			// .. no? -> throw exception
			} else {
				throw new ClosedConnectionException("connection " + getId() + " is already closed");
			}
		}
	}


	/**
	 * Returns the index within this string of the first occurrence of the specified substring
	 *
	 * @param str any string
	 * @return if the string argument occurs as a substring within this object, then the
	 *         index of the first character of the first such substring is returned;
	 *         if it does not occur as a substring, -1 is returned.
	 *
	 * @deprecated uses getIndexOf instead
	 */
	public int indexOf(String str) {

		int length = 0;

		if (!readQueue.isEmpty()) {
			try {
				LinkedList<ByteBuffer> buffers = readQueue.drain();
				ByteBufferParser.Index index = scanByDelimiter(buffers, str.getBytes(getDefaultEncoding()));

				// index found?
				if (index.hasDelimiterFound()) {
					length = index.getReadBytes() - str.length();

				// .. no
				} else {
					length = -1;
				}

				readQueue.addFirstSilence(buffers);
				cachedIndex = index;
			} catch (UnsupportedEncodingException uce) {
				throw new RuntimeException(uce);
			}
		}

		return length;
	}



	protected final int readIndexOf(byte[] bytes, int maxReadSize) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

		int length = 0;

		if (!readQueue.isEmpty()) {
			LinkedList<ByteBuffer> buffers = readQueue.drain();
			ByteBufferParser.Index index = scanByDelimiter(buffers, bytes);

			// index found?
			if (index.hasDelimiterFound()) {
				length = index.getReadBytes() - bytes.length;

			// .. no
			} else {
				length = -1;
			}

			readQueue.addFirstSilence(buffers);
			cachedIndex = index;
		} else {
			length = -1;
		}

		if (length < 0) {
			if (readQueue.getSize() >= maxReadSize) {
				throw new MaxReadSizeExceededException();
			}

			if (isOpen()) {
				throw new BufferUnderflowException();
			} else {
				throw new ClosedConnectionException("connection " + getId() + " is already closed");
			}
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

		if (length == 0) {
			return new LinkedList<ByteBuffer>();
		}

		// enough data?
		if (readQueue.getSize() >= length) {
			LinkedList<ByteBuffer> buffers = readQueue.drain();
			assert (buffers != null);

			LinkedList<ByteBuffer> extracted = PARSER.extract(buffers, length);
			onExtracted(extracted);

			readQueue.addFirstSilence(buffers);
			resetCachedIndex();

			return extracted;

		// .. no
		} else {
			if (isOpen()) {
				throw new BufferUnderflowException();
				
			} else {
				// remaining reveived data of underlying io handler available? 
				int read = retrieveIoHandlerData();
				
				// got data? -> retry
				if (read > 0) {
					return extractBytesByLength(length);
					
				// .. no? -> throw exception
				} else {
					throw new ClosedConnectionException("connection " + getId() + " is already closed");
				}
			}
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
	protected final boolean extractAvailableFromReadQueue(byte[] delimiter, WritableByteChannel outChannel) throws IOException {

		if (!readQueue.isEmpty()) {
			LinkedList<ByteBuffer> buffers = readQueue.drain();
			assert (buffers != null);

			ByteBufferParser.Index index = scanByDelimiter(buffers, delimiter);


			// delimiter found?
			if (index.hasDelimiterFound()) {
				LinkedList<ByteBuffer> extracted = PARSER.extract(buffers, index);
				onExtracted(extracted, delimiter);
				for (ByteBuffer buffer : extracted) {
					outChannel.write(buffer);
				}

				readQueue.addFirstSilence(buffers);
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

				readQueue.addFirstSilence(buffers);
				return false;
			}

		} else {
			return false;
		}
	}


	private ByteBufferParser.Index scanByDelimiter(LinkedList<ByteBuffer> buffers, byte[] delimiter) {

		// does index already exists (-> former scan)
		if (cachedIndex != null) {
			if (cachedIndex.isDelimiterEquals(delimiter)) {
				return PARSER.find(buffers, cachedIndex);
			} else {
				cachedIndex = null;
			}
		}

		return PARSER.find(buffers, delimiter);
	}


	private void onExtracted(LinkedList<ByteBuffer> buffers) {
		if (isReadMarked) {
			for (ByteBuffer buffer : buffers) {
				onExtracted(buffer);
			}
		}
	}


	private void onExtracted(LinkedList<ByteBuffer> buffers, byte[] delimiter) {
		if (isReadMarked) {
			for (ByteBuffer buffer : buffers) {
				onExtracted(buffer);
			}
			onExtracted(ByteBuffer.wrap(delimiter));
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
	 * extract a short from the queue
	 *
	 * @return the short value
 	 * @throws BufferUnderflowException if the buffer's limit has been reached
	 */
	protected final short extractShortFromReadQueue() throws BufferUnderflowException {
		resetCachedIndex();

		ByteBuffer buffer = readQueue.read(2);
		onExtracted(buffer);
		return buffer.getShort();
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
			getReadQueue().addFirstSilence(readMarkBuffer);
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


	protected int onDataEvent() {
		return retrieveIoHandlerData();
	}
	
	
	private int retrieveIoHandlerData() {

		try {

			LinkedList<ByteBuffer> buffers = getIoHandler().drainIncoming();

			int addSize = 0;
			for (ByteBuffer buffer : buffers) {
				addSize += buffer.remaining();
			}


			if (addSize > 0) {
				getReadQueue().append(buffers);
			}

			return addSize;

		} catch (RuntimeException e) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by transfering data to connection's read queue " + e.toString());
			}
			throw e;
		}
	}



	protected void onConnectEvent() {

	}


	protected void onDisconnectEvent() {

	}

	protected boolean onConnectionTimeoutEvent() {
		return false;
	}

	protected boolean onIdleTimeoutEvent() {
		return false;
	}


	
	public void onWritten() {
		if (flushmode == FlushMode.SYNC) {
			synchronized (writeGuard) {
				writeGuard.notifyAll();
			}
		}
	}
	
	
	public void onWriteException(IOException ioException) {
		if (flushmode == FlushMode.SYNC) {
			synchronized (writeGuard) {
				writeException = ioException;
				writeGuard.notify();
			}
		}
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final Object getOption(String name) throws IOException {
		return ioHandler.getOption(name);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final Map<String, Class> getOptions() {
		return ioHandler.getOptions();
	}

	
	/**
	 * {@inheritDoc}
	 */
	public IConnection setOption(String name, Object value) throws IOException {
		ioHandler.setOption(name, value);
		return this;
	}
	
	
	
	

	private void initiateClose() {
		try {
			close();
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + getId() + "] error occured closing connection. Reason: " + ioe.toString());
			}
		}
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



	private final class HandlerCallback implements IIoHandlerCallback {

		public void onWritten() {
			Connection.this.onWritten();	
		}
		
		
		public void onWriteException(IOException ioException) {
			Connection.this.onWriteException(ioException);
		}

		public void onDataRead() {
			Connection.this.onDataEvent();
		}


		public void onConnectionAbnormalTerminated() {
			Connection.this.initiateClose();
		}


		public void onConnect() {
			Connection.this.onConnectEvent();
		}


		public void onDisconnect() {
			Connection.this.onDisconnectEvent();
		}

		public void onConnectionTimeout() {
			if (!connectionTimeoutOccured) {
				connectionTimeoutOccured = true;
				boolean isHandled = Connection.this.onConnectionTimeoutEvent();
				if (!isHandled) {
					try {
						close();
					} catch (IOException ioe) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("[" + getId() + "] error occured closing connection caused by connection timeout. Reason: " + ioe.toString());
						}
					}
				}
			}
		}

		
		public void onIdleTimeout() {
			if (!idleTimeoutOccured) {
				idleTimeoutOccured = true;

				boolean isHandled = Connection.this.onIdleTimeoutEvent();
				if (!isHandled) {
					try {
						close();
					} catch (IOException ioe) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("[" + getId() + "] error occured closing connection caused by idle timeout. Reason: " + ioe.toString());
						}
					}
				}
			}
		}

	}
}
