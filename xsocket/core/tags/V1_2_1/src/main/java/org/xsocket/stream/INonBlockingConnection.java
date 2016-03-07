// $Id: INonBlockingConnection.java 1798 2007-10-05 05:39:23Z grro $
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
import java.net.SocketTimeoutException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import org.xsocket.ClosedConnectionException;
import org.xsocket.MaxReadSizeExceededException;


/**
 * A connection which uses the underlying channel in a non-blocking manner. <br><br>
 * 
 * @author grro@xsocket.org
 */
public interface INonBlockingConnection extends IConnection {

	public static final int UNLIMITED = Integer.MAX_VALUE;
	public static final FlushMode INITIAL_FLUSH_MODE = FlushMode.SYNC;
	
	
	public static final class TransferResult {
		
		private boolean delimiterFound = false;
		private long written = 0;
		
		TransferResult(boolean delimiterFound, long written) {
			this.delimiterFound = delimiterFound;
			this.written = written;
		}

		
		public boolean isDelimiterFound() {
			return delimiterFound;
		}
		public long getWritten() {
			return written;
		}	
	}
	
	
	/**
	 * get the number of available bytes to read
	 * 
	 * @return the umber of available bytes
	 */
	public int getNumberOfAvailableBytes();

	

	/**
	 * Returns the index  of the first occurrence of the given string.
	 * 
	 * @param str any string
	 * @return if the string argument occurs as a substring within this object, then the 
	 *         index of the first character of the first such substring is returned; 
	 *         if it does not occur as a substring, -1 is returned.
	 * @deprecated uses getIndexOf instead
	 */
	public int indexOf(String str);
	
	
	/**
	 * Returns the index  of the first occurrence of the given string. 
	 * 
	 * @param str any string
	 * @return if the string argument occurs as a substring within this object, then the 
	 *         index of the first character of the first such substring is returned;
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if the given string has not been found             
	 */
	public int getIndexOf(String str) throws IOException, ClosedConnectionException, BufferUnderflowException;

	
	/**
	 * Returns the index  of the first occurrence of the given string. 
	 * 
	 * @param str any string
     * @param maxLength    the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown  
	 * @return if the string argument occurs as a substring within this object, then the 
	 *         index of the first character of the first such substring is returned;
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found
 	 * @throws BufferUnderflowException if the given string has not been found                             
	 */
	public int getIndexOf(String str, int maxLength) throws IOException, ClosedConnectionException, BufferUnderflowException, MaxReadSizeExceededException;
	

	
	
	/**
	 * read all received bytes
	 * 
	 * @return the received bytes 
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readAvailable() throws IOException, ClosedConnectionException;


	
	/**
	 * @deprecated use {@link INonBlockingConnection#transferToAvailableByDelimiter(String, WritableByteChannel)} instead
	 */
	public boolean readAvailableByDelimiter(String delimiter, WritableByteChannel outputChannel) throws IOException, ClosedConnectionException;


	/**
	 * @deprecated use {@link INonBlockingConnection#transferToAvailableByDelimiter(String, String, WritableByteChannel)} instead
	 */
	public boolean readAvailableByDelimiter(String delimiter, String encoding, WritableByteChannel outputChannel) throws IOException, ClosedConnectionException;

	

	/**
	 * transfers bytes from this connection to the given writable byte channel. All 
	 * available bytes buffers terminated by the delimiter will be transfered.
	 * 
	 * if the delimiter has been found the {@link TransferResult#delimiterFound} is
	 * set with true. The {@link TransferResult#written} contains the The number of bytes,
	 * possibly zero, that were actually transferred  <br>
	 * The default encoding will be used to decode the delimiter
	 *
	 *
 	 * @param delimiter        the delimiter to use
	 * @param outputChannel    the output channel to write the available bytes
	 * @return transfer result 
	 * @throws IOException If some other I/O error occurs
	 * @throws SocketTimeoutException If the receive timeout has been reached (will only been thrown if autoflush = true)
	 * @throws ClosedConnectionException if the underlying channel is closed
	 */
	public TransferResult transferToAvailableByDelimiter(String delimiter, WritableByteChannel outputChannel) throws ClosedConnectionException, IOException, SocketTimeoutException;


	/**
	 * transfers bytes from this connection to the given writable byte channel. All 
	 * available bytes buffers terminated by the delimiter will be transfered.
	 * 
	 * if the delimiter has been found the {@link TransferResult#delimiterFound} is
	 * set with true. The {@link TransferResult#written} contains the The number of bytes,
	 * possibly zero, that were actually transferred  
	 *
 	 * @param delimiter        the delimiter to use
 	 * @param encoding         the encoding of the delimiter
	 * @param outputChannel    the output channel to write the available bytes
	 * @return transfer the transfer result
	 * @throws IOException If some other I/O error occurs
	 * @throws SocketTimeoutException If the receive timeout has been reached (will only been thrown if autoflush = true)
	 * @throws ClosedConnectionException if the underlying channel is closed
	 */
	public TransferResult transferToAvailableByDelimiter(String delimiter, String encoding, WritableByteChannel outputChannel) throws ClosedConnectionException, IOException, SocketTimeoutException;



	
	
	
	/**
	 * read a ByteBuffer by using a delimiter 
	 * To avoid memory leaks the {@link INonBlockingConnection#readByteBufferByDelimiter(String, int)} method is generally preferable  
	 * <br>
     * For performance reasons, the ByteBuffer readByteBuffer method is 
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter
	 * @return the ByteBuffer
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if the delimiter has not been found  
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException, ClosedConnectionException, BufferUnderflowException;

		
	
	/**
	 * read a ByteBuffer by using a delimiter 
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is 
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown	 
	 * @return the ByteBuffer
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if the delimiter has not been found  
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter, int maxLength) throws IOException, ClosedConnectionException, MaxReadSizeExceededException, BufferUnderflowException;



	
	/**
	 * read a ByteBuffer by using a length defintion 
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is 
     * generally preferable to get bytes 
	 * 
	 * @param length the amount of bytes to read
	 * @return the ByteBuffer
 	 * @throws ClosedConnectionException If the underlying socket is already closed
 	 * @throws BufferUnderflowException if the buffer`s limit has been reached 
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readByteBufferByLength(int length) throws IOException, ClosedConnectionException, BufferUnderflowException;

	
	/**
	 * read a byte array by using a delimiter
	 * To avoid memory leaks the {@link INonBlockingConnection#readBytesByDelimiter(String, int)} method is generally preferable  
	 * <br>
     * For performance reasons, the ByteBuffer readByteBuffer method is
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter  
	 * @return the read bytes
     * @throws ClosedConnectionException If the underlying socket is already closed
 	 * @throws BufferUnderflowException if the delimiter has not been found  
	 * @throws IOException If some other I/O error occurs
	 */		
	public byte[] readBytesByDelimiter(String delimiter) throws IOException, ClosedConnectionException, BufferUnderflowException;

	

	/**
	 * read a byte array by using a delimiter
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter  
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the read bytes
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found 	 * @throws ClosedConnectionException If the underlying socket is already closed
 	 * @throws BufferUnderflowException if the delimiter has not been found  
	 * @throws IOException If some other I/O error occurs
	 */		
	public byte[] readBytesByDelimiter(String delimiter, int maxLength) throws IOException, ClosedConnectionException, MaxReadSizeExceededException, BufferUnderflowException;
	

	
	
	/**
	 * read bytes by using a length defintion 
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is
     * generally preferable to get bytes 
	 * 
	 * @param length the amount of bytes to read  
	 * @return the read bytes
 	 * @throws ClosedConnectionException If the underlying socket is already closed
 	 * @throws BufferUnderflowException if the buffer`s limit has been reached 
	 * @throws IOException If some other I/O error occurs
	 */		
	public byte[] readBytesByLength(int length) throws IOException, ClosedConnectionException, BufferUnderflowException;


	
	/**
	 * read a string by using a delimiter and the connection default encoding
	 * To avoid memory leaks the {@link INonBlockingConnection#readStringByDelimiter(String, int)} method is generally preferable
	 *   
	 * @param delimiter   the delimiter
	 * @return the string
	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if the delimiter has not been found   
 	 * @throws UnsupportedEncodingException if the default encoding is not supported
	 */
	public String readStringByDelimiter(String delimiter) throws IOException, ClosedConnectionException, BufferUnderflowException, UnsupportedEncodingException;

	
	
	/**
	 * read a string by using a delimiter and the connection default encoding
	 * 
	 * @param delimiter   the delimiter
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the string
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found 	
	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if the delimiter has not been found   
 	 * @throws UnsupportedEncodingException if the default encoding is not supported
	 */
	public String readStringByDelimiter(String delimiter, int maxLength) throws IOException, ClosedConnectionException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException;

	
	
	/**
	 * read a string by using a length definition and the connection default encoding
	 * 
	 * @param length the amount of bytes to read  
	 * @return the string
 	 * @throws ClosedConnectionException If the underlying socket is already closed
 	 * @throws BufferUnderflowException if the buffer`s limit has been reached  
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the given encoding is not supported 
	 */
	public String readStringByLength(int length) throws IOException, ClosedConnectionException, BufferUnderflowException, UnsupportedEncodingException;

	
	/**
	 * read a string by using a delimiter
	 * To avoid memory leaks the {@link INonBlockingConnection#readStringByDelimiter(String, String, int)} method is generally preferable 
	 * 
	 * @param delimiter   the delimiter
	 * @param encoding    the encodin to use
	 * @return the string
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if the buffer`s limit has been reached  
 	 * @throws UnsupportedEncodingException If the given encoding is not supported 
	 */
	public String readStringByDelimiter(String delimiter, String encoding) throws IOException, ClosedConnectionException, BufferUnderflowException, UnsupportedEncodingException;


	
	/**
	 * read a string by using a delimiter
	 * 
	 * @param delimiter   the delimiter
	 * @param encoding    the encodin to use
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the string
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if the buffer`s limit has been reached  
 	 * @throws UnsupportedEncodingException If the given encoding is not supported 
	 */
	public String readStringByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, ClosedConnectionException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException;

	
	
	
	/**
	 * read a string by using a length definition 
	 * 
	 * @param length the amount of bytes to read  
	 * @param encoding the encodin to use
	 * @return the string
 	 * @throws ClosedConnectionException If the underlying socket is already closed
 	 * @throws BufferUnderflowException if the buffer`s limit has been reached 
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the given encoding is not supported 
	 */
	public String readStringByLength(int length, String encoding) throws IOException, ClosedConnectionException, BufferUnderflowException, UnsupportedEncodingException;

	
	/**
	 * read an int
	 * 
	 * @return the int value
 	 * @throws ClosedConnectionException If the underlying socket is already closed
 	 * @throws BufferUnderflowException if the buffer`s limit has been reached 
	 * @throws IOException If some other I/O error occurs
	 */
	public int readInt() throws IOException, ClosedConnectionException, BufferUnderflowException;

	
	/**
	 * read a long
	 * 
	 * @return the long value
 	 * @throws ClosedConnectionException If the underlying socket is already closed
 	 * @throws BufferUnderflowException if the buffer`s limit has been reached 
	 * @throws IOException If some other I/O error occurs
	 */
	public long readLong() throws IOException, ClosedConnectionException, BufferUnderflowException;

	
	/**
	 * read a double
	 * 
	 * @return the double value
 	 * @throws ClosedConnectionException If the underlying socket is already closed
 	 * @throws BufferUnderflowException if the buffer`s limit has been reached 
	 * @throws IOException If some other I/O error occurs
	 */
	public double readDouble() throws IOException, ClosedConnectionException, BufferUnderflowException;
	
	
	/**
	 * read a byte
	 * 
	 * @return the byte value
 	 * @throws ClosedConnectionException If the underlying socket is already closed
 	 * @throws BufferUnderflowException if the buffer`s limit has been reached 
	 * @throws IOException If some other I/O error occurs
	 */
	public byte readByte() throws IOException, ClosedConnectionException, BufferUnderflowException;
		
	
	/**
	 * set the send delay time. WData to write will be buffered
	 * internally and be written to the underlying subsystem
	 * based on the given write rate.
	 * The write methods will <b>not</b> block for this time. <br>
	 * 
	 * By default the write transfer rate is set with UNLIMITED <br><br>
	 * 
	 * Reduced write transfer is only supported for FlushMode.ASYNC. see
	 * {@link INonBlockingConnection#setFlushmode(org.xsocket.stream.IConnection.FlushMode)}
	 * 
	 * @param bytesPerSecond the transfer rate of the outgoing data 
	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs 
	 */
	public void setWriteTransferRate(int bytesPerSecond) throws ClosedConnectionException, IOException;
	
	
	/**
	 * flush the send buffer. The method call returns immediately. 
	 * The outgoing data will be flushed into the underlying 
	 * os-specific send buffer asynchronously in the background 
	 *
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public void flush() throws ClosedConnectionException, IOException;
		
	

	/**
	 * sends a byte buffer to the remote endpoint. In case of <i>activated autoflush</i> 
	 * (for default see {@link IConnection#INITIAL_AUTOFLUSH}) and <i>flushmode SYNC</i>
	 * (for default see {@link INonBlockingConnection#INITIAL_FLUSH_MODE} the behaviour is 
	 * according to the {@link WritableByteChannel} specification. <br> In case of 
	 * user managed flushing (autoflush is off) the passed over buffers will 
	 * only be queued internally and written after flushing the connection.     
	 * 
	 * @see IConnection#setAutoflush(boolean)
 	 * @see INonBlockingConnection#setFlushmode(org.xsocket.stream.IConnection.FlushMode)
	 *  
	 * @param buffer   the bytes to send
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public int write(ByteBuffer buffer) throws ClosedConnectionException, IOException;
	

	/**
	 * sends an array of byte buffer to the remote endpoint. In case of <i>activated autoflush</i> 
	 * (for default see {@link IConnection#INITIAL_AUTOFLUSH}) and <i>flushmode SYNC</i>
	 * (for default see {@link INonBlockingConnection#INITIAL_FLUSH_MODE} the behaviour is 
	 * according to the {@link WritableByteChannel} specification. <br> In case of 
	 * user managed flushing (autoflush is off) the passed over buffers will 
	 * only be queued internally and written after flushing the connection.     
	 * 
	 * @see IConnection#setAutoflush(boolean)
 	 * @see INonBlockingConnection#setFlushmode(org.xsocket.stream.IConnection.FlushMode)
	 *  
	 * @param buffers   the bytes to send
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public long write(ByteBuffer[] buffers) throws ClosedConnectionException, IOException;
 
	
	
	/**
	 * set the flush mode <br><br> By setting the flushmode with ASYNC (default is SYNC)
	 * the data will be transferred to the underlying connection in a asynchronous way. 
	 * In most cases there are high performance improvements. If the {@link IConnection#write(ByteBuffer)} 
	 * or {@link IConnection#write(ByteBuffer[])} method will be used, the flushmode 
	 * {@link FlushMode#ASYNC} could have side-effects. Because the buffer will be 
	 * written asynchronous, it could occur, that the passed-over buffers are not already 
	 * written by returning from the write call.
	 * 
	 * @param flushMode {@link FlushMode#ASYNC} if flush should be performed asynchronous, 
	 *                  {@link FlushMode#SYNC} if flush should be perform synchronous
	 */
	public void setFlushmode(FlushMode flushMode);
	
	/**
	 * return the flushmode
	 * @return the flushmode
	 */
	public FlushMode getFlushmode();
	
	
	/**
	 * sets the value of a option 
	 *  
	 * @param name   the name of the option
	 * @param value  the value of the option
	 * @return the IConnection 
	 * @throws IOException In an I/O error occurs
	 */
	public INonBlockingConnection setOption(String name, Object value) throws IOException;

}
