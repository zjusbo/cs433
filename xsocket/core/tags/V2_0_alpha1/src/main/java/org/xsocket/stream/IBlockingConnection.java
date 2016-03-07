// $Id: IBlockingConnection.java 1722 2007-09-03 15:57:17Z grro $
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
import java.nio.ByteBuffer;

import org.xsocket.ClosedConnectionException;
import org.xsocket.MaxReadSizeExceededException;


/**
 * A connection which uses the underlying channel in a blocking manner. Every I/O operation
 * will block until it completes. <br><br>
 *
 * @author grro@xsocket.org
 */
public interface IBlockingConnection extends IConnection {

	public static final int INITIAL_RECEIVE_TIMEOUT = Integer.MAX_VALUE;


	/**
	 * set the timeout for calling read methods in millis
	 *
	 * @param timeout  the timeout in millis
	 * @throws IOException If some other I/O error occurs
	 */
	public void setReceiveTimeoutMillis(int timeout) throws IOException;


	/**
	 * receive a string. the method will block, until the delimiter has been read.
	 * For the encoding the default encoding of the connection will be used.
	 * To avoid memory leaks the {@link IBlockingConnection#readStringByDelimiter(String, int)} method is generally preferable
	 *
	 * @param delimiter   the delimiter
	 * @return the string
	 *
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 * @throws SocketTimeoutException If the receive timeout has been reached
 	 * @throws UnsupportedEncodingException if the default encoding is not supported
	 */
	public String readStringByDelimiter(String delimiter) throws IOException, ClosedConnectionException, UnsupportedEncodingException, SocketTimeoutException;




	/**
	 * receive a string. the method will block, until the delimiter has been read.
	 * For the encoding the default encoding of the connection will be used
	 *
	 * @param delimiter   the delimiter
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the string
	 *
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 * @throws SocketTimeoutException If the receive timeout has been reached
 	 * @throws UnsupportedEncodingException if the default encoding is not supported
	 */
	public String readStringByDelimiter(String delimiter, int maxLength) throws IOException, ClosedConnectionException, UnsupportedEncodingException, SocketTimeoutException, MaxReadSizeExceededException;



	/**
	 * receive a string. the method will block, until the delimiter has been read
	 * To avoid memory leaks the {@link IBlockingConnection#readStringByDelimiter(String, String)} method is generally preferable
	 *
	 * @param delimiter   the delimiter
	 * @param encoding    the encoding to use
	 * @return the string
	 *
	 * @throws SocketTimeoutException If the receive timeout has been reached
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException If the given encoding is not supported
	 */
	public String readStringByDelimiter(String delimiter, String encoding) throws IOException, ClosedConnectionException, UnsupportedEncodingException, SocketTimeoutException;




	/**
	 * receive a string. the method will block, until the delimiter has been read
	 *
	 * @param delimiter   the delimiter
	 * @param encoding    the encoding to use
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the string
	 *
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws SocketTimeoutException If the receive timeout has been reached
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException If the given encoding is not supported
	 */
	public String readStringByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, ClosedConnectionException, UnsupportedEncodingException, SocketTimeoutException, MaxReadSizeExceededException;




	/**
	 * receive a string.  the method will block, until the required amount of bytes has been received
	 *
	 * @param length the number of bytes to read
	 *@return the received string
	 * @throws SocketTimeoutException If the receive timeout has been reached
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public String readStringByLength(int length) throws IOException, ClosedConnectionException, SocketTimeoutException;


	/**
	 * receive a string.  the method will block, until the required amount of bytes has been received
	 *
     * For performance reasons, the ByteBuffer receiveRecord method is generally preferable to get bytes
	 *
	 * @param length the number of bytes to read
	 * @param encoding the encoding
	 * @return the received string
	 * @throws SocketTimeoutException If the receive timeout has been reached
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public String readStringByLength(int length, String encoding) throws IOException, ClosedConnectionException, SocketTimeoutException;



	/**
	 * receive a ByteBuffer. the method will block, until the delimiter has been read.
	 * To avoid memory leaks the {@link IBlockingConnection#readByteBufferByDelimiter(String, int)} method is generally preferable
	 * <br>
     * For performance reasons, the ByteBuffer receiveByteBuffer method is generally preferable to get bytes
     *
	 * @param delimiter   the delimiter
	 * @return the ByteBuffer
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found
	 * @throws SocketTimeoutException If the receive timeout has been reached
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException, ClosedConnectionException, SocketTimeoutException;




	/**
	 * receive a ByteBuffer. the method will block, until the delimiter has been read.
	 *
     * For performance reasons, the ByteBuffer receiveByteBuffer method is generally preferable to get bytes
     *
	 * @param delimiter   the delimiter
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the ByteBuffer
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found
	 * @throws SocketTimeoutException If the receive timeout has been reached
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter, int maxLength) throws IOException, ClosedConnectionException, SocketTimeoutException, MaxReadSizeExceededException;



	/**
	 * receive a ByteBuffer. the method will block, until the required amount of bytes has been received
	 *
     * For performance reasons, the ByteBuffer receiveByteBuffer method is generally preferable to get bytes
	 *
	 * @param length the number of bytes to read
	 * @return the received ByteBuffer
	 * @throws SocketTimeoutException If the receive timeout has been reached
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readByteBufferByLength(int length) throws IOException, ClosedConnectionException, SocketTimeoutException;



	/**
	 * receive a byte array. the method will block, until the delimiter has been read.
	 * To avoid memory leaks the {@link IBlockingConnection#readBytesByDelimiter(String, int)} method is generally preferable
	 * <br>
     * For performance reasons, the ByteBuffer receiveByteBuffer method is generally preferable to get bytes
 	 *
	 * @param delimiter   the delimiter
	 * @return the read bytes
	 * @throws SocketTimeoutException If the receive timeout has been reached
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public byte[] readBytesByDelimiter(String delimiter) throws IOException, ClosedConnectionException, SocketTimeoutException;



	/**
	 * receive a byte array. the method will block, until the delimiter has been read.
	 *
     * For performance reasons, the ByteBuffer receiveByteBuffer method is generally preferable to get bytes
 	 *
	 * @param delimiter   the delimiter
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the read bytes
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found
	 * @throws SocketTimeoutException If the receive timeout has been reached
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public byte[] readBytesByDelimiter(String delimiter, int maxLength) throws IOException, ClosedConnectionException, SocketTimeoutException, MaxReadSizeExceededException;




	/**
	 * receive a byte array. the method will block, until the required amount of bytes has been received
	 *
     * For performance reasons, the ByteBuffer receiveByteBuffer method is generally preferable to get bytes
	 *
	 * @param length the number of bytes to read
	 * @return the received byte array
	 * @throws SocketTimeoutException If the receive timeout has been reached
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public byte[] readBytesByLength(int length) throws IOException, ClosedConnectionException, SocketTimeoutException;



	/**
	 * receive an int. the method will block, until data is available
	 *
	 * @return the received int
	 * @throws SocketTimeoutException If the receive timeout has been reached
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public int readInt() throws IOException, ClosedConnectionException, SocketTimeoutException;


	/**
	 * receive a long. the method will block, until data is available
	 *
	 * @return the received long
	 * @throws SocketTimeoutException If the receive timeout has been reached
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public long readLong() throws IOException, ClosedConnectionException, SocketTimeoutException;


	/**
	 * receive a double. the method will block, until data is available
	 *
	 * @return the received double
	 * @throws SocketTimeoutException If the receive timeout has been reached
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public double readDouble() throws IOException, ClosedConnectionException, SocketTimeoutException;



	/**
	 * receive a byte. the method will block, until data is available
	 *
	 * @return the received byte
	 * @throws SocketTimeoutException If the receive timeout has been reached
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public byte readByte() throws IOException, ClosedConnectionException, SocketTimeoutException;


	/**
	 * sends a message to the remote endpoint
	 *
	 * @param message    the message to send
	 * @param encoding   the encoding which should be used th encode the chars into byte (e.g. `US-ASCII` or `UTF-8`)
	 * @return the number of send bytes
	 * @throws IOException If some other I/O error occurs
	 * @throws SocketTimeoutException If the receive timeout has been reached (will only been thrown if autoflush = true)
	 * @throws ClosedConnectionException if the underlying channel is closed
	 */
	public int write(String message, String encoding) throws ClosedConnectionException, IOException, SocketTimeoutException;


	/**
	 * sends a message to the remote endpoint by using the connection default encoding
	 *
	 * @param message  the message to send
	 * @return the number of send bytes
	 * @throws IOException If some other I/O error occurs
	 * @throws SocketTimeoutException If the receive timeout has been reached (will only been thrown if autoflush = true)
	 * @throws ClosedConnectionException if the underlying channel is closed
	 */
	public int write(String message) throws ClosedConnectionException, IOException, SocketTimeoutException;


	/**
	 * sends a byte to the remote endpoint
	 *
	 * @param b   the byte to send
	 * @return the number of send bytes
	 * @throws IOException If some other I/O error occurs
	 * @throws SocketTimeoutException If the receive timeout has been reached (will only been thrown if autoflush = true)
	 * @throws ClosedConnectionException if the underlying channel is closed
	 */
	public int write(byte b) throws ClosedConnectionException, IOException, SocketTimeoutException;


	/**
	 * sends bytes to the remote endpoint
	 *
	 * @param bytes   the bytes to send
	 * @return the number of send bytes
	 * @throws IOException If some other I/O error occurs
	 * @throws SocketTimeoutException If the receive timeout has been reached (will only been thrown if autoflush = true)
	 * @throws ClosedConnectionException if the underlying channel is closed
	 */
	public int write(byte... bytes) throws ClosedConnectionException, IOException, SocketTimeoutException;


	/**
	 * sends bytes to the remote endpoint
	 *
	 * @param bytes    the bytes to send
	 * @param offset   The offset of the subarray to be used; must be non-negative and no larger than array.length. The new buffer`s position will be set to this value.
	 * @param length   The length of the subarray to be used; must be non-negative and no larger than array.length - offset. The new buffer`s limit will be set to offset + length.
	 * @return the number of send bytes
	 * @throws IOException If some other I/O error occurs
	 * @throws SocketTimeoutException If the receive timeout has been reached (will only been thrown if autoflush = true)
	 * @throws ClosedConnectionException if the underlying channel is closed
	 */
	public int write(byte[] bytes, int offset, int length) throws ClosedConnectionException, IOException, SocketTimeoutException;


	/**
	 * sends a byte buffer to the remote endpoint
	 *
	 * @param buffer   the bytes to send
	 * @return the number of send bytes
	 * @throws IOException If some other I/O error occurs
	 * @throws SocketTimeoutException If the receive timeout has been reached (will only been thrown if autoflush = true)
	 * @throws ClosedConnectionException if the underlying channel is closed
	 */
	public int write(ByteBuffer buffer) throws ClosedConnectionException, IOException, SocketTimeoutException;


	/**
	 * sends an array of byte buffer to the remote endpoint
	 *
	 * @param buffers   the bytes to send
	 * @return the number of send bytes
	 * @throws IOException If some other I/O error occurs
	 * @throws SocketTimeoutException If the receive timeout has been reached (will only been thrown if autoflush = true)
	 * @throws ClosedConnectionException if the underlying channel is closed
	 */
	public long write(ByteBuffer[] buffers) throws ClosedConnectionException, IOException, SocketTimeoutException;


	/**
	 * sends an int to the remote endpoint
	 *
	 * @param i   the int value to send
	 * @return the number of send bytes
	 * @throws IOException If some other I/O error occurs
	 * @throws SocketTimeoutException If the receive timeout has been reached (will only been thrown if autoflush = true)
	 * @throws ClosedConnectionException if the underlying channel is closed
	 */
	public int write(int i) throws ClosedConnectionException, IOException, SocketTimeoutException;


	/**
	 * sends a long to the remote endpoint
	 *
	 * @param l   the int value to send
	 * @return the number of send bytes
	 * @throws IOException If some other I/O error occurs
	 * @throws SocketTimeoutException If the receive timeout has been reached (will only been thrown if autoflush = true)
	 * @throws ClosedConnectionException if the underlying channel is closed
	 */
	public int write(long l) throws ClosedConnectionException, IOException, SocketTimeoutException;


	/**
	 * sends a double to the remote endpoint
	 *
	 * @param d   the int value to send
	 * @return the number of send bytes
	 * @throws IOException If some other I/O error occurs
	 * @throws SocketTimeoutException If the receive timeout has been reached (will only been thrown if autoflush = true)
	 * @throws ClosedConnectionException if the underlying channel is closed
	 */
	public int write(double d) throws ClosedConnectionException, IOException, SocketTimeoutException;



	/**
	 * flush the send buffer. The method call will block until
	 * the outgoing data has been flushed into the underlying
	 * os-specific send buffer.
	 *
	 * @throws IOException If some other I/O error occurs
	 * @throws SocketTimeoutException If the timeout has been reached
	 * @throws ClosedConnectionException if the underlying channel is closed
	 */
	public void flush() throws ClosedConnectionException, IOException, SocketTimeoutException;




	/**
	 * sets the value of a option
	 *
	 * @param name   the name of the option
	 * @param value  the value of the option
	 * @return the IConnection
	 * @throws IOException In an I/O error occurs
	 */
	public IBlockingConnection setOption(String name, Object value) throws IOException;
}
