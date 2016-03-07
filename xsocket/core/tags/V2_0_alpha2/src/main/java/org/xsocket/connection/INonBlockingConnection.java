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

import java.io.Flushable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.Executor;

import org.xsocket.ClosedException;
import org.xsocket.IDataSink;
import org.xsocket.IDataSource;
import org.xsocket.MaxReadSizeExceededException;



/**
 * A connection which accesses the underlying channel in a non-blocking manner. <br><br>
 *
 * @author grro@xsocket.org
 */
public interface INonBlockingConnection extends IConnection, IDataSource, IDataSink, GatheringByteChannel, ReadableByteChannel, WritableByteChannel, Flushable {

	public static final int UNLIMITED = Integer.MAX_VALUE;


	/**
	 * set the connection handler 
	 * 
	 * @param handler the handler 
	 */
	public void setHandler(IHandler handler);



	/**
	 * gets the encoding (used by string related methods like write(String) ...)
	 *
	 * @return the encoding
	 */
	public String getEncoding();
	
	

	
	/**
	 * sets the encoding  (used by string related methods like write(String) ...)
	 *
	 * @param encoding the encoding
	 */
	public void setEncoding(String encoding);
	
	
	/**
	 * set autoflush. If autoflush is activated, each write call
	 * will cause a flush. <br><br>
	 *
	 * By default the autoflush is deactivated
	 *
	 * @param autoflush true if autoflush should be activated
	 */
	public void setAutoflush(boolean autoflush);



	/**
	 * get autoflush
	 * 
	 * @return true, if autoflush is activated
	 */
	public boolean isAutoflush();
	
	

	
	/**
	 * flush the send buffer. The method call will block until
	 * the outgoing data has been flushed into the underlying
	 * os-specific send buffer.
	 *
	 * @throws IOException If some other I/O error occurs
	 * @throws SocketTimeoutException If the timeout has been reached
	 * @throws ClosedException if the underlying channel is closed
	 */
	public void flush() throws ClosedException, IOException, SocketTimeoutException;




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
	public void setFlushmode(FlushMode flushMode);

	
	/**
	 * return the flush mode
	 * @return the flush mode
	 */
	public FlushMode getFlushmode();
	
	
	/**
	 * ad hoc activation of a secured mode (SSL). By performing of this
	 * method all remaining data to send will be flushed.
	 * After this all data will be sent and received in the secured mode
	 *
	 * @throws IOException If some other I/O error occurs
	 */
	public void activateSecuredMode() throws IOException;
	
	


	/**
	 * returns the size of the data which have already been written, but not
	 * yet transferred to the underlying socket.
	 *
	 * @return the size of the pending data to write
	 */
	public int getPendingWriteDataSize();



	/**
	 * suspend reading data from the underlying subsystem
	 *
	 * @throws IOException If some other I/O error occurs
	 */
	public void suspendRead() throws IOException;


	/**
	 * resume reading data from the underlying subsystem
	 *
	 * @throws IOException If some other I/O error occurs
	 */
	public void resumeRead() throws IOException;
	


	
	/**
	 * write a message
	 * 
	 * @param message    the message to write
	 * @param encoding   the encoding which should be used th encode the chars into byte (e.g. `US-ASCII` or `UTF-8`)
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	public int write(String message, String encoding) throws IOException, BufferOverflowException;

	/**
	 * read a ByteBuffer by using a delimiter. The default encoding will be used to decode the delimiter 
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is 
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter
	 * @param encoding    the encoding to use	 
	 * @return the ByteBuffer
	 * @throws BufferUnderflowException If not enough data is available     
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException;


	
	/**
	 * read a ByteBuffer by using a delimiter 
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is 
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter
	 * @param encoding    the encoding of the delimiter
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown	 
	 * @return the ByteBuffer
	 * @throws BufferUnderflowException If not enough data is available 
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found     
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException;

	
	/**
	 * read a byte array by using a delimiter
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter  
	 * @param encoding    the encoding to use	  
	 * @return the read bytes
	 * @throws BufferUnderflowException If not enough data is available   
	 * @throws IOException If some other I/O error occurs
	 */		
	public byte[] readBytesByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException;
	
	
	

	/**
	 * read a byte array by using a delimiter
	 *
     * For performance reasons, the ByteBuffer readByteBuffer method is
     * generally preferable to get bytes
	 *
	 * @param delimiter   the delimiter
	 * @param encoding    the encoding to use 
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the read bytes
	 * @throws BufferUnderflowException If not enough data is available 
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public byte[] readBytesByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException;




	/**
	 * read a string by using a delimiter
	 * 
	 * @param delimiter   the delimiter
	 * @param encoding    the encoding to use
	 * @return the string
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found   
	 * @throws IOException If some other I/O error occurs
	 * @throws BufferUnderflowException If not enough data is available 
 	 * @throws UnsupportedEncodingException if the given encoding is not supported 
	 */
	public String readStringByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException;

	

	/**
	 * read a string by using a delimiter
	 *
	 * @param delimiter   the delimiter
	 * @param encoding    the encoding to use
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the string
	 * @throws BufferUnderflowException If not enough data is available 
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException If the given encoding is not supported
	 */
	public String readStringByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException;




	/**
	 * read a string by using a length definition 
	 * 
	 * @param length the amount of bytes to read.  
	 * @param encoding the encoding to use
	 * @return the string
	 * @throws IOException If some other I/O error occurs
	 * @throws BufferUnderflowException If not enough data is available 
 	 * @throws UnsupportedEncodingException if the given encoding is not supported 
     * @throws IllegalArgumentException, if the length parameter is negative
	 */
	public String readStringByLength(int length, String encoding) throws IOException, BufferUnderflowException, UnsupportedEncodingException;
	
	
	/**
	 * transfer the data of the file channel to this data sink
	 * 
	 * @param source the source channel
	 * @return the number of transfered bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	public long transferFrom(FileChannel source) throws IOException, BufferOverflowException;

	/**
	 * Returns the index  of the first occurrence of the given string.
	 *
	 * @param str any string
	 * @return if the string argument occurs as a substring within this object, then
	 *         the index of the first character of the first such substring is returned;
	 *         if it does not occur as a substring, -1 is returned.
 	 * @throws IOException If some other I/O error occurs
	 */
	public int indexOf(String str) throws IOException;


	
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
	public int indexOf(String str, String encoding) throws IOException;


	/**
	 * read available received bytes
	 *
	 * @return the received bytes
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readAvailableByteBuffer() throws IOException;

	


	
	
	
	/**
	 * set the send delay time. WData to write will be buffered
	 * internally and be written to the underlying subsystem
	 * based on the given write rate.
	 * The write methods will <b>not</b> block for this time. <br>
	 *
	 * By default the write transfer rate is set with UNLIMITED <br><br>
	 *
	 * Reduced write transfer is only supported for FlushMode.ASYNC. see
	 * {@link INonBlockingConnection#setFlushmode(org.xsocket.stream.IReadWriteableConnection.FlushMode)}
	 *
	 * @param bytesPerSecond the transfer rate of the outgoing data
	 * @throws ClosedException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public void setWriteTransferRate(int bytesPerSecond) throws ClosedException, IOException;
		




	/**
	 * get the number of available bytes to read
	 * 
	 * @return the number of available bytes
	 */
	public int available();

	
	
	/**
	 * get the version of read buffer. The version number increases, if
	 * the read buffer queue has been modified 
	 *
	 * @return the version of the read buffer
	 */
	public int getReadBufferVersion();
	
	
	/**
	 * return if the data source is open. Default is true
	 * @return true, if the data source is open
	 */
	public boolean isOpen();
	
	
	/**
	 * return the worker pool which is used to process the call back methods
	 *
	 * @return the worker pool
	 */
	public Executor getWorkerpool();

	

	/**
	 * Resets to the marked write position. If the connection has been marked,
	 * then attempt to reposition it at the mark.
	 *
	 * @return true, if reset was successful
	 */
	public boolean resetToWriteMark();

	
	
	/**
	 * Resets to the marked read position. If the connection has been marked,
	 * then attempt to reposition it at the mark.
	 *
	 * @return true, if reset was successful
	 */
	public boolean resetToReadMark();

	
	/**
	 * Marks the write position in the connection. 
	 */
	public void markWritePosition();
	

	/**
	 * Marks the read position in the connection. Subsequent calls to resetToReadMark() will attempt
	 * to reposition the connection to this point.
	 *
	 */
	public void markReadPosition();

	
	/**
	 * remove the read mark
	 */
	public void removeReadMark();

	
	/**
	 * remove the write mark
	 */
	public void removeWriteMark();
}
