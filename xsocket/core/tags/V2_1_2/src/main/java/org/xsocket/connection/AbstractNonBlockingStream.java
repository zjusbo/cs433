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
import java.nio.MappedByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IConnection.FlushMode;




/**
 * implementation base of a data stream.   
 * 
 * <br/><br/><b>This is a xSocket internal class and subject to change</b> 
 *  
 * @author grro@xsocket.org
 */
public abstract class AbstractNonBlockingStream implements WritableByteChannel, Closeable {

	private static Logger LOG = Logger.getLogger(AbstractNonBlockingStream.class.getName());

	private ReadQueue readQueue = new ReadQueue();
	private WriteQueue writeQueue = new WriteQueue();

	private String defaultEncoding = IConnection.INITIAL_DEFAULT_ENCODING;


	// open flag
	private final AtomicBoolean isOpen = new AtomicBoolean(true);
	
	
	//flushing
	private boolean autoflush = IConnection.DEFAULT_AUTOFLUSH;
	private FlushMode flushmode = IConnection.DEFAULT_FLUSH_MODE;

		
	// attachment
	private Object attachment = null;


	
	public void close() throws IOException {
		isOpen.set(false);
	}
	
	private void closeSilence() {
		try {
			close();
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by closing connection " + this + " " + ioe.toString());
			}
		}
	}
	
	
	
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
	 * returns true, if the underlying data source is open 
	 * 
	 * @return true, if the underlying data source is open 
	 */
	protected abstract boolean isMoreInputDataExpected();

	
	/**
	 * returns true, if the underlying data sink is open 
	 * 
	 * @return true, if the underlying data sink is open 
	 */
	protected abstract boolean isDataWriteable();
		

	/**
	 * Returns the index of the first occurrence of the given string.
	 *
	 * @param str any string
	 * @return if the string argument occurs as a substring within this object, then
	 *         the index of the first character of the first such substring is returned;
	 *         if it does not occur as a substring, -1 is returned.
 	 * @throws IOException If some other I/O error occurs
 	 * @throws ClosedChannelException If the stream is closed
	 */
	public int indexOf(String str) throws IOException, ClosedChannelException {
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
 	 * @throws ClosedChannelException If the stream is closed 
	 */
	public  int indexOf(String str, String encoding) throws IOException, ClosedChannelException {
		ensureStreamIsOpen();
			
		return readQueue.retrieveIndexOf(str.getBytes(encoding), Integer.MAX_VALUE);
	}


	
	
	/**
	 * get the number of available bytes to read
	 * 
	 * @return the number of available bytes or -1 if the end of stream is reached
	 * @throws IOException if an exception has been occurred 
	 */
	public int available() throws IOException {
		if (!isOpen.get()) {
			return -1;
		}
			
		int size = readQueue.getSize();
		if (size == 0) {
			if (isMoreInputDataExpected()) {
				return 0;
			} else {
				return -1;
			}
		} 
		
		return size;
	}
	
	/**
	 * returns the read queue size without additional check
	 * 
	 * @return the read queue size
	 */
	protected int getReadQueueSize() {
		return readQueue.getSize();
	}
	
	
	/**
	 * get the version of read buffer. The version number increases, if
	 * the read buffer queue has been modified 
	 *
	 */
	public int getReadBufferVersion()  {
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
 	 * @throws ClosedChannelException If the stream is closed 
	 */
	public byte readByte() throws IOException, BufferUnderflowException, ClosedChannelException { 		
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
 	 * @throws ClosedChannelException If the stream is closed  
	 */
	public short readShort() throws IOException, BufferUnderflowException, ClosedChannelException {
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
 	 * @throws ClosedChannelException If the stream is closed  
	 */
	public int readInt() throws IOException, BufferUnderflowException, ClosedChannelException {
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
 	 * @throws ClosedChannelException If the stream is closed 
	 */
	public long readLong() throws IOException, BufferUnderflowException, ClosedChannelException {
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
 	 * @throws ClosedChannelException If the stream is closed  
	 */
	public double readDouble() throws IOException, BufferUnderflowException, ClosedChannelException {
		double d = readSingleByteBuffer(8).getDouble();
		
		onPostRead();
		return d;
	}


	
	/**
	 * see {@link ReadableByteChannel#read(ByteBuffer)}
	 */
	public int read(ByteBuffer buffer) throws IOException, ClosedChannelException {
		ensureStreamIsOpen();
		
		int size = buffer.remaining();
		int available = available();
		
		if ((available == 0) && !isMoreInputDataExpected()) {
			closeSilence();
			return -1;
		}
		
		if (available < size) {
			size = available;
		}

		if (size > 0) {
			copyBuffers(readByteBufferByLength(size), buffer);
		}

		if (size == -1) {
			closeSilence();
		}
		
		onPostRead();
		return size;
	}
	
	
	private void copyBuffers(ByteBuffer[] source, ByteBuffer target) {
		for (ByteBuffer buf : source) {
			if (buf.hasRemaining()) {
				target.put(buf);
			}
		}
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
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException, BufferUnderflowException, ClosedChannelException {
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
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException, ClosedChannelException {
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
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException, ClosedChannelException {
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
 	 * @throws ClosedChannelException If the stream is closed  
 	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException, ClosedChannelException {
		ensureStreamIsOpen();
		
		try {
			ByteBuffer[] buffers = readQueue.readByteBufferByDelimiter(delimiter.getBytes(encoding), maxLength);
			
			onPostRead();
			return buffers;

		} catch (MaxReadSizeExceededException mre) {
			if (isMoreInputDataExpected()) {
				throw mre;

			} else {
				closeSilence();
				throw new ClosedChannelException();
			}
			
		} catch (BufferUnderflowException bue) {
			if (isMoreInputDataExpected()) {
				throw bue;

			} else {
				closeSilence();
				throw new ClosedChannelException();
			}
		}
	}
	
	


	/**
	 * read a ByteBuffer  
	 * 
	 * @param length   the length could be negative, in this case a empty array will be returned
	 * @return the ByteBuffer
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available 
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public ByteBuffer[] readByteBufferByLength(int length) throws IOException, BufferUnderflowException, ClosedChannelException {
		ensureStreamIsOpen();
		
		if (length <= 0) {
			if (!isMoreInputDataExpected()) {
				closeSilence();
				throw new ClosedChannelException();
			}
			
			onPostRead();
			return new ByteBuffer[0];
		}

		try {
			
			ByteBuffer[] buffers = readQueue.readByteBufferByLength(length);

			onPostRead();
			return buffers;
		} catch (BufferUnderflowException bue) {
			if (isMoreInputDataExpected()) {
				throw bue;

			} else {
				closeSilence();
				throw new ClosedChannelException();
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
 	 * @throws ClosedChannelException If the stream is closed    
	 */		
	public byte[] readBytesByDelimiter(String delimiter) throws IOException, BufferUnderflowException, ClosedChannelException {
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
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public byte[] readBytesByDelimiter(String delimiter, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException, ClosedChannelException {
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
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public byte[] readBytesByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException, ClosedChannelException {
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
 	 * @throws ClosedChannelException If the stream is closed    
	 */
	public byte[] readBytesByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException, ClosedChannelException {
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
 	 * @throws ClosedChannelException If the stream is closed   
	 */		
	public byte[] readBytesByLength(int length) throws IOException, BufferUnderflowException, ClosedChannelException {
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
 	 * @throws ClosedChannelException If the stream is closed   
 	 */
	public String readStringByDelimiter(String delimiter) throws IOException, BufferUnderflowException, UnsupportedEncodingException, ClosedChannelException {
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
 	 * @throws ClosedChannelException If the stream is closed    
	 */
	public String readStringByDelimiter(String delimiter, int maxLength) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException, ClosedChannelException {
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
 	 * @throws ClosedChannelException If the stream is closed    
	 */
	public String readStringByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException, ClosedChannelException {
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
 	 * @throws ClosedChannelException If the stream is closed    
	 */
	public String readStringByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException, ClosedChannelException {
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
 	 * @throws ClosedChannelException If the stream is closed    
	 */
	public String readStringByLength(int length) throws IOException, BufferUnderflowException, UnsupportedEncodingException, ClosedChannelException {
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
 	 * @throws ClosedChannelException If the stream is closed    
	 */
	public String readStringByLength(int length, String encoding) throws IOException, BufferUnderflowException, UnsupportedEncodingException, ClosedChannelException {
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
 	 * @throws ClosedChannelException If the stream is closed    
	 */
	public long transferTo(WritableByteChannel target, int length) throws IOException, ClosedChannelException, BufferUnderflowException, ClosedChannelException {
		
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
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	protected ByteBuffer readSingleByteBuffer(int length) throws IOException, ClosedChannelException, BufferUnderflowException, ClosedChannelException {
		ensureStreamIsOpen();
		
		try {
			ByteBuffer buffer = readQueue.readSingleByteBuffer(length);
			
			onPostRead();
			return buffer;

		} catch (BufferUnderflowException bue) {

			if (isMoreInputDataExpected()) {
				throw bue;

			} else {
				closeSilence();
				throw new ClosedChannelException();
			}
		}
	}




	/**
	 * writes a byte to the data sink
	 *  
	 * @param b   the byte to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available
	 * @throws IOException If some other I/O error occurs	 
 	 * @throws ClosedChannelException If the stream is closed    
	 */
	public final int write(byte b) throws IOException, BufferOverflowException, ClosedChannelException {
		ensureStreamIsOpenAndWritable();
		
		writeQueue.append(DataConverter.toByteBuffer(b));
		onWriteDataInserted();

		return 1;
	}


	/**
	 * writes bytes to the data sink
	 *  
	 * @param bytes   the bytes to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public final int write(byte... bytes) throws IOException, BufferOverflowException, ClosedChannelException {
		ensureStreamIsOpenAndWritable();

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
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public final int write(byte[] bytes, int offset, int length) throws IOException, BufferOverflowException, ClosedChannelException {
		ensureStreamIsOpenAndWritable();
		
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
	}



	/**
	 * writes a short to the data sink
	 *  
	 * @param s   the short value to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public final int write(short s) throws IOException, BufferOverflowException, ClosedChannelException {
		ensureStreamIsOpenAndWritable();
		
		writeQueue.append(DataConverter.toByteBuffer(s));
		onWriteDataInserted();

		return 2;
	}
	
	

	/**
	 * writes a int to the data sink
	 *  
	 * @param i   the int value to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public final int write(int i) throws IOException, BufferOverflowException, ClosedChannelException {
		ensureStreamIsOpenAndWritable();
		
		writeQueue.append(DataConverter.toByteBuffer(i));
		onWriteDataInserted();

		return 4;
	}


	/**
	 * writes a long to the data sink
	 *  
	 * @param l   the int value to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public final int write(long l) throws IOException, BufferOverflowException, ClosedChannelException {
		ensureStreamIsOpenAndWritable();
		
		writeQueue.append(DataConverter.toByteBuffer(l));
		onWriteDataInserted();

		return 8;
	}


	
	/**
	 * writes a double to the data sink
	 *  
	 * @param d   the int value to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public final int write(double d) throws IOException, BufferOverflowException, ClosedChannelException {
		ensureStreamIsOpenAndWritable();
			
		writeQueue.append(DataConverter.toByteBuffer(d));
		onWriteDataInserted();

		return 8;
	}


	/**
	 * writes a message
	 * 
	 * @param message  the message to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public final int write(String message) throws IOException, BufferOverflowException, ClosedChannelException {
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
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public final int write(String message, String encoding) throws IOException, BufferOverflowException, ClosedChannelException {
		ensureStreamIsOpenAndWritable();
		
		ByteBuffer buffer = DataConverter.toByteBuffer(message, encoding);
		int written = buffer.remaining();

		writeQueue.append(buffer);
		onWriteDataInserted();

		return written;
	}



	/**
	 * writes a list of bytes to the data sink
	 *  
	 * @param buffers    the bytes to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public final long write(List<ByteBuffer> buffers) throws IOException, BufferOverflowException, ClosedChannelException {
		if (buffers == null) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("warning buffer list to send is null");
			}
			return 0;
		}

		return write(buffers.toArray(new ByteBuffer[buffers.size()]));
	}






	/**
	 * see {@link GatheringByteChannel#write(ByteBuffer[], int, int)}
	 */
	public final long write(ByteBuffer[] srcs, int offset, int length) throws IOException, ClosedChannelException {
		if (srcs == null) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("warning buffer array to send is null");
			}
			return 0;
		}

		return write(DataConverter.toByteBuffers(srcs, offset, length));
	}



	
	/**
	 * see {@link GatheringByteChannel#write(ByteBuffer[])}
	 */
	public final long write(ByteBuffer[] buffers) throws IOException, BufferOverflowException, ClosedChannelException {
		ensureStreamIsOpenAndWritable();
		
		if ((buffers == null) || (buffers.length == 0)) {
			return 0;
		}
		
		
		long written = 0;
		for (ByteBuffer buffer : buffers) {
			int size = buffer.remaining();
			
			onPreWrite(size);
			writeQueue.append(buffer);

			written += size;
			onWriteDataInserted();
		}

		return written;
	}

	

	/**
	 * {@link WritableByteChannel#write(ByteBuffer)}
	 */
	public final int write(ByteBuffer buffer) throws IOException, BufferOverflowException, ClosedChannelException {
		ensureStreamIsOpenAndWritable();

		if (buffer == null) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("warning buffer is null");
			}
			return 0;
		}

		int size = buffer.remaining();
		
		onPreWrite(size);
		writeQueue.append(buffer);
		onWriteDataInserted();

		return size;
	}

	
	/**
	 * call back method which will be called before writing data into the write queue 
	 * 
	 * @param size the size to write 
	 * @throws BufferOverflowException  if the write buffer max size is exceeded 
	 */
	protected void onPreWrite(int size) throws BufferOverflowException {
		
	}

	

	/**
	 * transfer the data of the file channel to this data sink
	 * 
	 * @param fileChannel the file channel
	 * @return the number of transfered bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public final long transferFrom(FileChannel fileChannel) throws ClosedChannelException, IOException, SocketTimeoutException, ClosedChannelException {
		ensureStreamIsOpenAndWritable();
		
		if (getFlushmode() == FlushMode.SYNC) {
			MappedByteBuffer buffer = fileChannel.map(MapMode.READ_ONLY, 0, fileChannel.size());
			return write(buffer);
		
		} else {
			return transferFrom((ReadableByteChannel) fileChannel);
		}
	}



	/**
	 * transfer the data of the source channel to this data sink
	 * 
	 * @param source the source channel
	 * @return the number of transfered bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public long transferFrom(ReadableByteChannel source) throws IOException, BufferOverflowException, ClosedChannelException {
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
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public final long transferFrom(ReadableByteChannel source, int chunkSize) throws IOException, BufferOverflowException, ClosedChannelException {		
		return transfer(source, this, chunkSize);
	}
	
	

	private long transfer(ReadableByteChannel source, WritableByteChannel target, int chunkSize) throws IOException, ClosedChannelException {
		ensureStreamIsOpenAndWritable();
		
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
	 * notification, that data has been inserted 
	 * 
	 * @throws IOException  if an exception occurs 
	 * @throws ClosedChannelException  if the stream is closed
	 */
	protected void onWriteDataInserted() throws IOException, ClosedChannelException {

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
	 * drains the read buffer 
	 * 
	 * @return the read buffer content
	 */
	protected ByteBuffer[] drainReadQueue() {
		return readQueue.readAvailable();
	}

	/**
	 * copies the read buffer 
	 * 
	 * @return the read buffer content
	 */
	protected ByteBuffer[] copyReadQueue() {
		return readQueue.copyAvailable();
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
	protected final void appendDataToReadBuffer(ByteBuffer[] data, int size) {
		readQueue.append(data, size);
		onPostAppend();
	}
	

	/**
	 * @deprecated
	 */
	protected final void appendDataToReadBuffer(ByteBuffer[] data) {
		int size = 0;
		for (ByteBuffer byteBuffer : data) {
			size += byteBuffer.remaining();
		}
		
		appendDataToReadBuffer(data, size);
	}
	
	

	protected void onPostAppend() {
		
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
	
	

	private void ensureStreamIsOpen() throws ClosedChannelException {
		if (!isOpen.get()) {			
			throw new ClosedChannelException();
		}		
	}
	

	private void ensureStreamIsOpenAndWritable() throws ClosedChannelException {
		if (!isOpen.get()) {
			throw new ClosedChannelException();
		}		
		
		if(!isDataWriteable()) {
			closeSilence();
			throw new ClosedChannelException();
		}
	}
	
}
