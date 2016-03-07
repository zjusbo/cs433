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

import java.io.Closeable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.ClosedException;
import org.xsocket.DataConverter;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IConnection.FlushMode;




/**
 * Base implementation of a data stream.   
 *  
 * @author grro@xsocket.org
 */
public abstract class AbstractNonBlockingStream implements WritableByteChannel, Closeable, Cloneable {

	private Logger LOG = Logger.getLogger(AbstractNonBlockingStream.class.getName());

	private boolean isOpen = true;

	private ReadQueue readQueue = new ReadQueue();
	private WriteQueue writeQueue = new WriteQueue();

	private String defaultEncoding = IConnection.INITIAL_DEFAULT_ENCODING;


	
	//flushing
	private boolean autoflush = IConnection.DEFAULT_AUTOFLUSH;
	private FlushMode flushmode = IConnection.DEFAULT_FLUSH_MODE;

		
	// attachment
	private Object attachment = null;


	/**
	 * Attaches the given object to this connection
	 *
	 * @param obj The object to be attached; may be null
	 * @return The previously-attached object, if any, otherwise null
	 */
	public final void setAttachment(Object obj) {
		attachment = obj;
	}


	/**
	 * Retrieves the current attachment.
	 *
	 * @return The object currently attached to this key, or null if there is no attachment
	 */
	public final Object getAttachment() {
		return attachment;
	}


	/**
	 * sets the default encoding 
	 * 
	 * @param defaultEncoding  the default encoding 
	 */
	public final void setEncoding(String defaultEncoding) {
		this.defaultEncoding = defaultEncoding;
	}


	/**
	 * gets the default encoding 
	 * 
	 * @return  the default encoding
	 */
	public final String getEncoding() {
		return defaultEncoding;
	}


	/**
	 * set the flush mode <br><br> By setting the flush mode with ASYNC (default is SYNC)
	 * the data will be transferred to the underlying connection in a asynchronous way.
	 * In most cases there are high performance improvements. If the {@link IReadWriteableConnection#write(ByteBuffer)}
	 * or {@link IReadWriteableConnection#write(ByteBuffer[])} method will be used, the flush mode
	 * {@link FlushMode#ASYNC} could have side-effects. Because the buffer will be
	 * written asynchronous, it could occur, that the passed-over buffers are not already
	 * written by returning from the write call.
	 *
	 * @param flushMode {@link FlushMode#ASYNC} if flush should be performed asynchronous,
	 *                  {@link FlushMode#SYNC} if flush should be perform synchronous
	 */
	public void setFlushmode(FlushMode flushMode) {
		this.flushmode = flushMode;
	}


	/**
	 * return the flush mode
	 * @return the flush mode
	 */
	public final FlushMode getFlushmode() {
		return flushmode;
	}


	/**
	 * returns the default chunk size for writing
	 * 
	 * @return write chunk size
	 */
	protected int getWriteTransferChunkeSize() {
		return 8196;
	}



	/**
	 * set autoflush. If autoflush is activated, each write call
	 * will cause a flush. <br><br>
	 *
	 * By default the autoflush is deactivated
	 *
	 * @param autoflush true if autoflush should be activated
	 */
	public final void setAutoflush(boolean autoflush) {
		this.autoflush = autoflush;
	}

	
	/**
	 * get autoflush
	 * 
	 * @return true, if autoflush is activated
	 */
	public final boolean isAutoflush() {
		return autoflush;
	}



	/**
	 * Returns the index of the first occurrence of the given string.
	 *
	 * @param str any string
	 * @return if the string argument occurs as a substring within this object, then
	 *         the index of the first character of the first such substring is returned;
	 *         if it does not occur as a substring, -1 is returned.
 	 * @throws IOException If some other I/O error occurs
	 */
	public int indexOf(String str) throws IOException {
		return indexOf(str, getEncoding());
	}


	/**
	 * Returns the index  of the first occurrence of the given string.
	 *
	 * @param str          any string
	 * @param encoding     the encoding to use
	 * @return if the string argument occurs as a substring within this object, then
	 *         the index of the first character of the first such substring is returned;
	 *         if it does not occur as a substring, -1 is returned.
 	 * @throws IOException If some other I/O error occurs
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found
	 */
	public  int indexOf(String str, String encoding) throws IOException, MaxReadSizeExceededException {
		return readQueue.retrieveIndexOf(str.getBytes(encoding), Integer.MAX_VALUE);
	}




	/**
	 * get the number of available bytes to read
	 * 
	 * @return the number of available bytes
	 * @throws IOException If some other I/O error occurs 
	 */
	public  int available() throws IOException {
		if (isReadBufferEmpty()) {
			if (isOpen()) {
				return 0;
			} else {
				return -1;
			}
		} else {
			return readQueue.getSize();
		}
	}
	
	
	
	/**
	 * get the version of read buffer. The version number increases, if
	 * the read buffer queue has been modified 
	 *
	 * @return the version of the read buffer
	 * @throws IOException If some other I/O error occurs 
	 */
	public int getReadBufferVersion() throws IOException {
		return readQueue.geVersion();
	}



	/**
	 * notification method which will be called after data has been read  
	 * 
 	 * @throws IOException If some other I/O error occurs
	 */
	protected void onPostRead() throws IOException {
		
	}

	

	/** 
	 * read a byte
	 * 
	 * @return the byte value
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available 
	 */
	public byte readByte() throws IOException, BufferUnderflowException {
 
		
		byte b = readSingleByteBuffer(1).get();
		
		onPostRead();
		return b;
	}


	/**
	 * read a short value
	 * 
	 * @return the short value
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available 
	 */
	public short readShort() throws IOException, BufferUnderflowException {
		short s = readSingleByteBuffer(2).getShort();
		
		onPostRead();
		return s;
	}

	
	/**
	 * read an int
	 * 
	 * @return the int value
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available 
	 */
	public int readInt() throws IOException, BufferUnderflowException {

		
		int i = readSingleByteBuffer(4).getInt();
		
		onPostRead();
		return i;
	}


	/**
	 * read a long
	 * 
	 * @return the long value
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available
	 */
	public long readLong() throws IOException, BufferUnderflowException {

		
		long l = readSingleByteBuffer(8).getLong();
		
		onPostRead();
		return l;
	}


	/**
	 * read a double
	 * 
	 * @return the double value
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available 
	 */
	public double readDouble() throws IOException, BufferUnderflowException {

		
		double d = readSingleByteBuffer(8).getDouble();
		
		onPostRead();
		return d;
	}


	
	/**
	 * see {@link ReadableByteChannel#read(ByteBuffer)}
	 */
	public int read(ByteBuffer buffer) throws IOException {

		
		int size = buffer.remaining();

		int available = available();
		
		if ((available == 0) && !isOpen()) {
			return -1;
		}
		
		if (available < size) {
			size = available;
		}

		if (size > 0) {
			ByteBuffer[] bufs = readByteBufferByLength(size);
			for (ByteBuffer buf : bufs) {
				while (buf.hasRemaining()) {
					buffer.put(buf);
				}
			}
		}

		onPostRead();
		return size;
	}


	/**
	 * read a ByteBuffer by using a delimiter. The default encoding will be used to decode the delimiter 
	 * To avoid memory leaks the {@link IReadWriteableConnection#readByteBufferByDelimiter(String, int)} method is generally preferable  
	 * <br> 
     * For performance reasons, the ByteBuffer readByteBuffer method is 
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter
	 * @return the ByteBuffer
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available 
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException, BufferUnderflowException {

		
		ByteBuffer[] buffers = readByteBufferByDelimiter(delimiter, getEncoding());
		
		onPostRead();
		return buffers;
	}


	/**
	 * read a ByteBuffer by using a delimiter 
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is 
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown	 
	 * @return the ByteBuffer
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found     
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available	 
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

		
		ByteBuffer[] buffers = readByteBufferByDelimiter(delimiter, getEncoding(), maxLength);
		
		onPostRead();
		return buffers;
	}


	/**
	 * read a ByteBuffer by using a delimiter 
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is 
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter
	 * @param encoding    the delimiter encoding
	 * @return the ByteBuffer
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found     
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available 
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException {

		
		ByteBuffer[] buffers = readByteBufferByDelimiter(delimiter, encoding, Integer.MAX_VALUE);
		
		onPostRead();
		return buffers;
	}


	/**
	 * read a ByteBuffer by using a delimiter 
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is 
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter
	 * @param encoding    the delimiter encoding
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown	 
	 * @return the ByteBuffer
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found     
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available 
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

		
		try {
			ByteBuffer[] buffers = readQueue.readByteBufferByDelimiter(delimiter.getBytes(encoding), maxLength);
			
			onPostRead();
			return buffers;

		} catch (MaxReadSizeExceededException mre) {
			if (isOpen()) {
				throw mre;

			} else {
				throw new ClosedException("data source is already closed");
			}
			
		} catch (BufferUnderflowException bue) {
			if (isOpen()) {
				throw bue;

			} else {
				throw new ClosedException("data source is already closed");
			}
		}
	}



	/**
	 * read a ByteBuffer  
	 * 
	 * @param length   the length could be negative, in this case a empty array will be returened
	 * @return the ByteBuffer
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available 
	 */
	public ByteBuffer[] readByteBufferByLength(int length) throws IOException, BufferUnderflowException {

		
		if (length <= 0) {
			
			onPostRead();
			return new ByteBuffer[0];
		}

		try {
			
			ByteBuffer[] buffers = readQueue.readByteBufferByLength(length);

			onPostRead();
			return buffers;
		} catch (BufferUnderflowException bue) {
			if (isOpen()) {
				throw bue;

			} else {
				throw new ClosedException("data source is already closed");
			}
		}
	}


	/**
	 * read a byte array by using a delimiter
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter  
	 * @return the read bytes
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available 
	 */		
	public byte[] readBytesByDelimiter(String delimiter) throws IOException, BufferUnderflowException {

		
		byte[] bytes = readBytesByDelimiter(delimiter, getEncoding());
		
		onPostRead();
		return bytes;
	}


	
	
	/**
	 * read a byte array by using a delimiter
	 *
     * For performance reasons, the ByteBuffer readByteBuffer method is
     * generally preferable to get bytes
	 *
	 * @param delimiter   the delimiter
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the read bytes
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found 	 
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available 
	 */
	public byte[] readBytesByDelimiter(String delimiter, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

		
		byte[] bytes = readBytesByDelimiter(delimiter, getEncoding(), maxLength);
		
		onPostRead();
		return bytes;
	}


	/**
	 * read a byte array by using a delimiter
	 *
     * For performance reasons, the ByteBuffer readByteBuffer method is
     * generally preferable to get bytes
	 *
	 * @param delimiter   the delimiter
	 * @param encoding    the delimiter encoding
	 * @return the read bytes
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found 	 
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available 
	 */
	public byte[] readBytesByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException {

		
		byte[] bytes = readBytesByDelimiter(delimiter, encoding, Integer.MAX_VALUE);
		
		onPostRead();
		return bytes;
	}


	/**
	 * read a byte array by using a delimiter
	 *
     * For performance reasons, the ByteBuffer readByteBuffer method is
     * generally preferable to get bytes
	 *
	 * @param delimiter   the delimiter
	 * @param encoding    the delimiter encoding
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the read bytes
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found 	 
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available 
	 */
	public byte[] readBytesByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

		
		byte[] bytes = DataConverter.toBytes(readByteBufferByDelimiter(delimiter, encoding, maxLength));
		
		onPostRead();
		return bytes;
	}

	

	/**
	 * read bytes by using a length definition 
	 *  
	 * @param length the amount of bytes to read  
	 * @return the read bytes
	 * @throws IOException If some other I/O error occurs
     * @throws IllegalArgumentException, if the length parameter is negative 
 	 * @throws BufferUnderflowException if not enough data is available 
	 */		
	public byte[] readBytesByLength(int length) throws IOException, BufferUnderflowException {

		
		byte[] bytes = DataConverter.toBytes(readByteBufferByLength(length));
		
		onPostRead();
		return bytes;
	}


	
	/**
	 * read a string by using a delimiter 
	 * 
	 * @param delimiter   the delimiter
	 * @return the string
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the default encoding is not supported
 	 * @throws BufferUnderflowException if not enough data is available
 	 */
	public String readStringByDelimiter(String delimiter) throws IOException, BufferUnderflowException, UnsupportedEncodingException {

		
		String s = readStringByDelimiter(delimiter, Integer.MAX_VALUE);
		
		onPostRead();
		return s;
	}



	/**
	 * read a string by using a delimiter
	 *
	 * @param delimiter   the delimiter
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the string
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found 	 
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException If the given encoding is not supported
 	 * @throws BufferUnderflowException if not enough data is available 
	 */
	public String readStringByDelimiter(String delimiter, int maxLength) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException {

		
		String s = readStringByDelimiter(delimiter, getEncoding(), maxLength);
		
		onPostRead();
		return s;
	}



	/**
	 * read a string by using a delimiter
	 *
	 * @param delimiter   the delimiter
	 * @param encoding    the encoding
	 * @return the string
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found 	 
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException If the given encoding is not supported
 	 * @throws BufferUnderflowException if not enough data is available 
	 */
	public String readStringByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException {

		
		String s = readStringByDelimiter(delimiter, encoding, Integer.MAX_VALUE);
		
		onPostRead();
		return s;
	}



	/**
	 * read a string by using a delimiter
	 *
	 * @param delimiter   the delimiter
	 * @param encoding    the encoding 
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the string
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found 	 
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException If the given encoding is not supported
 	 * @throws BufferUnderflowException if not enough data is available 
	 */
	public String readStringByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException {

		
		String s = DataConverter.toString(readByteBufferByDelimiter(delimiter, encoding, maxLength), encoding);
		
		onPostRead();
		return s;
	}


	
	/**
	 * read a string by using a length definition
	 * 
	 * @param length the amount of bytes to read  
	 * @return the string
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the given encoding is not supported 
     * @throws IllegalArgumentException, if the length parameter is negative 
 	 * @throws BufferUnderflowException if not enough data is available 
	 */
	public String readStringByLength(int length) throws IOException, BufferUnderflowException, UnsupportedEncodingException {

		
		String s = readStringByLength(length, getEncoding());
		
		onPostRead();
		return s;
	}


	
	/**
	 * read a string by using a length definition
	 * 
	 * @param length      the amount of bytes to read  
	 * @param encoding    the encoding 
	 * @return the string
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the given encoding is not supported 
     * @throws IllegalArgumentException, if the length parameter is negative 
 	 * @throws BufferUnderflowException if not enough data is available 
	 */
	public String readStringByLength(int length, String encoding) throws IOException, BufferUnderflowException, UnsupportedEncodingException {

		
		String s = DataConverter.toString(readByteBufferByLength(length), encoding);
		
		onPostRead();
		return s;
	}

	
	
	

	/**
	 * transfer the data of the this source channel to the given data sink
	 * 
	 * @param dataSink   the data sink
	 * @param length     the size to transfer
	 * 
	 * @return the number of transfered bytes
	 * @throws ClosedChannelException If either this channel or the target channel is closed
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available 
	 */
	public long transferTo(WritableByteChannel target, int length) throws IOException, ClosedChannelException, BufferUnderflowException {

		
		if (length > 0) {
			long written = 0;

			ByteBuffer[] buffers = readByteBufferByLength(length);
			for (ByteBuffer buffer : buffers) {
				while(buffer.hasRemaining()) {
					written += target.write(buffer);
				}
			}

			onPostRead();
			return written;

		} else {

			onPostRead();
			return 0;
		}
	}


	/**
	 * read a byte buffer by length. If the underlying data is fragmented over several ByteBuffer,
	 * the ByteBuffers will be merged
	 * 
	 * @param length   the length 
	 * @return the byte buffer
     * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available 
	 */
	protected ByteBuffer readSingleByteBuffer(int length) throws IOException, ClosedException, BufferUnderflowException {

		
		try {
			ByteBuffer buffer = readQueue.readSingleByteBuffer(length);
			
			onPostRead();
			return buffer;

		} catch (BufferUnderflowException bue) {

			if (isOpen()) {
				throw bue;

			} else {
				throw new ClosedException("data source is already closed");
			}
		}
	}




	/**
	 * writes a byte to the data sink
	 *  
	 * @param b   the byte to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available
	 * @throws IOException If some other I/O error occurs	 * 
	 */
	public final int write(byte b) throws IOException, BufferOverflowException {
		if (isOpen()) {
			writeQueue.append(DataConverter.toByteBuffer(b));
			onWriteDataInserted();

			return 1;

		} else {
			throw new ClosedException("data source is already closed");
		}
	}


	/**
	 * writes bytes to the data sink
	 *  
	 * @param bytes   the bytes to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	public final int write(byte... bytes) throws IOException, BufferOverflowException {
		if (isOpen()) {
			if (bytes.length > 0) {
				writeQueue.append(DataConverter.toByteBuffer(bytes));
				onWriteDataInserted();

				return bytes.length;
			} else {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("warning length of byte array to send is 0");
				}

				return 0;
			}

		} else {
			throw new ClosedException("data source is already closed");
		}
	}


	/**
	 * writes bytes to the data sink
	 *  
	 * @param bytes    the bytes to write
	 * @param offset   The offset of the sub array to be used; must be non-negative and no larger than array.length. The new buffer`s position will be set to this value.
	 * @param length   The length of the sub array to be used; must be non-negative and no larger than array.length - offset. The new buffer`s limit will be set to offset + length.
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	public final int write(byte[] bytes, int offset, int length) throws IOException, BufferOverflowException {
		if (isOpen()) {
			if (bytes.length > 0) {
				ByteBuffer buffer = DataConverter.toByteBuffer(bytes, offset, length);
				int written = buffer.remaining();

				writeQueue.append(buffer);
				onWriteDataInserted();

				return written;
			} else {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("warning length of buffer array to send is 0");
				}

				return 0;
			}

		} else {
			throw new ClosedException("data source is already closed");
		}
	}



	/**
	 * writes a short to the data sink
	 *  
	 * @param s   the short value to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	public final int write(short s) throws IOException, BufferOverflowException {
		if (isOpen()) {
			writeQueue.append(DataConverter.toByteBuffer(s));
			onWriteDataInserted();

			return 2;

		} else {
			throw new ClosedException("data source is already closed");
		}
	}
	
	

	/**
	 * writes a int to the data sink
	 *  
	 * @param i   the int value to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	public final int write(int i) throws IOException, BufferOverflowException {
		if (isOpen()) {
			writeQueue.append(DataConverter.toByteBuffer(i));
			onWriteDataInserted();

			return 4;

		} else {
			throw new ClosedException("data source is already closed");
		}
	}


	/**
	 * writes a long to the data sink
	 *  
	 * @param l   the int value to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	public final int write(long l) throws IOException, BufferOverflowException {
		if (isOpen()) {
			writeQueue.append(DataConverter.toByteBuffer(l));
			onWriteDataInserted();

			return 8;

		} else {
			throw new ClosedException("data source is already closed");
		}
	}


	
	/**
	 * writes a double to the data sink
	 *  
	 * @param d   the int value to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	public final int write(double d) throws IOException, BufferOverflowException {
		if (isOpen()) {
			writeQueue.append(DataConverter.toByteBuffer(d));
			onWriteDataInserted();

			return 8;

		} else {
			throw new ClosedException("data source is already closed");
		}
	}


	/**
	 * writes a message
	 * 
	 * @param message  the message to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	public final int write(String message) throws IOException, BufferOverflowException {
		return write(message, getEncoding());
	}


	/**
	 * writes a message
	 * 
	 * @param message   the message to write
	 * @param encoding  the encoding
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	public final int write(String message, String encoding) throws IOException, BufferOverflowException {
		if (isOpen()) {
			ByteBuffer buffer = DataConverter.toByteBuffer(message, encoding);
			int written = buffer.remaining();

			writeQueue.append(buffer);
			onWriteDataInserted();

			return written;

		} else {
			throw new ClosedException("data source is already closed");
		}
	}



	/**
	 * writes a list of bytes to the data sink
	 *  
	 * @param buffers    the bytes to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	public final long write(List<ByteBuffer> buffers) throws IOException, BufferOverflowException {
		if (buffers == null) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("warning buffer list to send is null");
			}
			return 0;
		}

		return write(buffers.toArray(new ByteBuffer[buffers.size()]));
	}





	
	/**
	 * see {@link GatheringByteChannel#write(ByteBuffer[])}
	 */
	public final long write(ByteBuffer[] buffers) throws IOException, BufferOverflowException {
		
		if ((buffers == null) || (buffers.length == 0)) {
			return 0;
		}
		
		
		if (isOpen()) {
			long written = 0;
			for (ByteBuffer buffer : buffers) {
				written += buffer.remaining();
				writeQueue.append(buffer);
			}
			onWriteDataInserted();

			return written;
 		} else {
			throw new ClosedException("coulnd not write " + buffers + " data source is already closed");
		}
	}


	/**
	 * see {@link GatheringByteChannel#write(ByteBuffer[], int, int)}
	 */
	public final long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
		if (srcs == null) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("warning buffer array to send is null");
			}
			return 0;
		}

		return write(DataConverter.toByteBuffers(srcs, offset, length));
	}



	/**
	 * {@link WritableByteChannel#write(ByteBuffer)}
	 */
	public final int write(ByteBuffer buffer) throws IOException, BufferOverflowException {
		if (isOpen()) {
			if (buffer == null) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("warning buffer is null");
				}
				return 0;
			}

			int written = buffer.remaining();
			writeQueue.append(buffer);
			onWriteDataInserted();

			return written;

		} else {
			throw new ClosedException("data source is already closed");
		}
	}


	/**
	 * transfer the data of the file channel to this data sink
	 * 
	 * @param fileChannel the file channel
	 * @return the number of transfered bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	public final long transferFrom(FileChannel fileChannel) throws ClosedException, IOException, SocketTimeoutException {
		
		if (getFlushmode() == FlushMode.SYNC) {
			return fileChannel.transferTo(fileChannel.position(), (fileChannel.size() - fileChannel.position()), this);
		
		} else {
			//return transferFrom((ReadableByteChannel) fileChannel);
			
			long transfered = 0;

			int chunkSize = getWriteTransferChunkeSize();
			int remaining = (int) fileChannel.size();
			int position = 0;
			
			
			do {
				int readSize = remaining; 
				if (remaining > chunkSize) {
					readSize = chunkSize;
				}
					
				ByteBuffer buffer = fileChannel.map(MapMode.READ_ONLY, position, readSize);
				transfered += write(buffer);
					
				position += readSize;
				remaining -= readSize;
				
			} while (remaining > 0);

			return transfered;
		}
	}



	/**
	 * transfer the data of the source channel to this data sink
	 * 
	 * @param source the source channel
	 * @return the number of transfered bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	public long transferFrom(ReadableByteChannel source) throws IOException, BufferOverflowException {
		return transferFrom(source, getWriteTransferChunkeSize());
	}


	
	/**
	 * transfer the data of the source channel to this data sink
	 * 
	 * @param source     the source channel
	 * @param chunkSize  the chunk size to use
	 * @return the number of transfered bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	public final long transferFrom(ReadableByteChannel source, int chunkSize) throws IOException, BufferOverflowException {
		if (isOpen()) {
			return transfer(source, this, chunkSize);

		} else {
			throw new ClosedException("data source is already closed");
		}
	}
	
	

	private static long transfer(ReadableByteChannel source, WritableByteChannel target, int chunkSize) throws IOException {
		long transfered = 0;

		int read = 0;
		do {
			ByteBuffer transferBuffer = ByteBuffer.allocate(chunkSize);
			read = source.read(transferBuffer);

			if (read > 0) {
				if (transferBuffer.remaining() == 0) {
					transferBuffer.flip();
					target.write(transferBuffer);

				} else {
					transferBuffer.flip();
					target.write(transferBuffer.slice());
				}

				transfered += read;
			}
		} while (read > 0);

		return transfered;
	}

	
	


	/**
	 * Marks the read position in the connection. Subsequent calls to resetToReadMark() will attempt
	 * to reposition the connection to this point.
	 *
	 */
	public final void markReadPosition() {
		readQueue.markReadPosition();
	}



	
	/**
	 * Marks the write position in the connection. 
	 */
	public final void markWritePosition() {
		if (isAutoflush()) {
			throw new UnsupportedOperationException("write mark is only supported for mode autoflush off");
		}

		writeQueue.markWritePosition();
	}


	/**
	 * Resets to the marked write position. If the connection has been marked,
	 * then attempt to reposition it at the mark.
	 *
	 * @return true, if reset was successful
	 */
	public final boolean resetToWriteMark() {
		return writeQueue.resetToWriteMark();
	}


	
	/**
	 * Resets to the marked read position. If the connection has been marked,
	 * then attempt to reposition it at the mark.
	 *
	 * @return true, if reset was successful
	 */
	public final boolean resetToReadMark() {
		return readQueue.resetToReadMark();
	}


	
	/**
	 * remove the read mark
	 */
	public final void removeReadMark() {
		readQueue.removeReadMark();
	}

	
	/**
	 * remove the write mark
	 */
	public final void removeWriteMark() {
		writeQueue.removeWriteMark();
	}


	/**
	 * resets the stream
	 * 
	 * @return true, if the stream has been reset
	 */
	protected boolean reset() {
		readQueue.reset();
		writeQueue.reset();
		
		defaultEncoding = IConnection.INITIAL_DEFAULT_ENCODING;
		autoflush = IConnection.DEFAULT_AUTOFLUSH;
		flushmode = IConnection.DEFAULT_FLUSH_MODE;
		attachment = null;

		return true;
	}
	

	/**
	 * return if the data source is open. Default is true
	 * @return true, if the data source is open
	 */
	public boolean isOpen() {
		return isOpen;
	}

	/**
	 * {@link Closeable#close()}
	 */
	public void close() throws IOException {
		isOpen = false;
	}


	/**
	 * notification, that data has been inserted 
	 * 
	 * @throws IOException  if an exception occurs 
	 * @throws ClosedException  if the stream is closed
	 */
	protected void onWriteDataInserted() throws IOException, ClosedException {

	}
	

	/**
	 * gets the write buffer size
	 * 
	 * @return the write buffer size 
	 */
	protected final int getWriteBufferSize() {
		return writeQueue.getSize();
	}

	
	/**
	 * returns if the write buffer is empty
	 * @return true, if the write buffer is empty
	 */
	protected final boolean isWriteBufferEmpty() {
		return writeQueue.isEmpty();
	}

	
	/**
	 * drains the write buffer 
	 * 
	 * @return the write buffer content
	 */
	protected ByteBuffer[] drainWriteQueue() {
		return writeQueue.drain();
	}

	
	/**
	 * returns if the read buffer is empty
	 * 
	 * @return  true, if the read buffer is empty
	 */
	protected final boolean isReadBufferEmpty() {
		return readQueue.isEmpty();
	}


	/**
	 * append data to the read buffer
	 * 
	 * @param data the data to append
	 */
	protected final void appendDataToReadBuffer(ByteBuffer[] data) {
		if (data == null) {
			return;
		}

		if (data.length == 0) {
			return;

		}

		readQueue.append(data);
	}




	/**
	 * prints the read buffer content
	 *  
	 * @param encoding the encoding
	 * @return the read buffer content
	 */
	protected final String printReadBuffer(String encoding) {
		return readQueue.asString(encoding);
	}

	/**
	 * prints the write buffer content 
	 * 
	 * @param encoding the encoding 
	 * @return the write buffer content
	 */
	protected final String printWriteBuffer(String encoding) {
		return writeQueue.asString(encoding);
	}
	
	@Override
	protected Object clone() throws CloneNotSupportedException {
		AbstractNonBlockingStream copy = (AbstractNonBlockingStream) super.clone();

		copy.readQueue = (ReadQueue) this.readQueue.clone(); 
		copy.writeQueue = (WriteQueue) this.writeQueue.clone();
		
		return copy;
	}
}
