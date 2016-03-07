// $Id: INonBlockingConnection.java 1017 2007-03-15 08:03:05Z grro $
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

	
	
	/**
	 * get the number of available bytes to read
	 * 
	 * @return the umber of available bytes
	 */
	public int getNumberOfAvailableBytes();

	

	/**
	 * Returns the index within this string of the first occurrence of the specified substring
	 * 
	 * @param str any string
	 * @return if the string argument occurs as a substring within this object, then the 
	 *         index of the first character of the first such substring is returned; 
	 *         if it does not occur as a substring, -1 is returned.
	 */
	public int indexOf(String str);
	

	
	/**
	 * read all received bytes
	 * 
	 * @return the received bytes 
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readAvailable() throws IOException, ClosedConnectionException;


	
	/**
	 * read all received byte buffers. In the case the delimiter has been
	 * found, the behavior is equals to the readRecordByDelimiter method. 
	 * In this case the method returns true. <br> 
	 * If the delimiter hasn't been reached, behavior is equals to the readAvailable
	 * method. In this case the method returns false;
	 * 
 	 * @param delimiter        the delimiter to use
	 * @param outputChannel    the output channel to write the available bytes
	 * @return true, if the delimiter has been found
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public boolean readAvailableByDelimiter(String delimiter, WritableByteChannel outputChannel) throws IOException, ClosedConnectionException;

		
	
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
 	 * @throws BufferUnderflowException if the buffer's limit has been reached 
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readByteBufferByLength(int length) throws IOException, ClosedConnectionException, BufferUnderflowException;

	

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
 	 * @throws BufferUnderflowException if the buffer's limit has been reached 
	 * @throws IOException If some other I/O error occurs
	 */		
	public byte[] readBytesByLength(int length) throws IOException, ClosedConnectionException, BufferUnderflowException;

	
	
	
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
 	 * @throws BufferUnderflowException if the buffer's limit has been reached  
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the given encoding is not supported 
	 */
	public String readStringByLength(int length) throws IOException, ClosedConnectionException, BufferUnderflowException, UnsupportedEncodingException;


	
	/**
	 * read a string by using a delimiter
	 * 
	 * @param delimiter   the delimiter
	 * @param encoding    the encodin to use
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the string
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if the buffer's limit has been reached  
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
 	 * @throws BufferUnderflowException if the buffer's limit has been reached 
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the given encoding is not supported 
	 */
	public String readStringByLength(int length, String encoding) throws IOException, ClosedConnectionException, BufferUnderflowException, UnsupportedEncodingException;

	
	/**
	 * read an int
	 * 
	 * @return the int value
 	 * @throws ClosedConnectionException If the underlying socket is already closed
 	 * @throws BufferUnderflowException if the buffer's limit has been reached 
	 * @throws IOException If some other I/O error occurs
	 */
	public int readInt() throws IOException, ClosedConnectionException, BufferUnderflowException;

	
	/**
	 * read a long
	 * 
	 * @return the long value
 	 * @throws ClosedConnectionException If the underlying socket is already closed
 	 * @throws BufferUnderflowException if the buffer's limit has been reached 
	 * @throws IOException If some other I/O error occurs
	 */
	public long readLong() throws IOException, ClosedConnectionException, BufferUnderflowException;

	
	/**
	 * read a double
	 * 
	 * @return the double value
 	 * @throws ClosedConnectionException If the underlying socket is already closed
 	 * @throws BufferUnderflowException if the buffer's limit has been reached 
	 * @throws IOException If some other I/O error occurs
	 */
	public double readDouble() throws IOException, ClosedConnectionException, BufferUnderflowException;
	
	
	/**
	 * read a byte
	 * 
	 * @return the byte value
 	 * @throws ClosedConnectionException If the underlying socket is already closed
 	 * @throws BufferUnderflowException if the buffer's limit has been reached 
	 * @throws IOException If some other I/O error occurs
	 */
	public byte readByte() throws IOException, ClosedConnectionException, BufferUnderflowException;
		
	
	/**
	 * set the send delay time. Data which has been written 
	 * by the write methods will be sent within a background
	 * process based on the given write rate.
	 * The write methods will <b>not</b> block for this time. <br>
	 * 
	 * By default the write transfer rate is set with UNLIMITED
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
	 * returns the idle timeout in sec. 
	 * 
	 * @return idle timeout in sec
	 */
	public int getIdleTimeoutSec();
	
	
	/**
	 * sets the idle timeout in sec 
	 * 
	 * @param timeoutInSec idle timeout in sec
	 */
	public void setIdleTimeoutSec(int timeoutInSec);
	
	
	/**
	 * gets the connection timeout
	 * 
	 * @return connection timeout
	 */
	public int getConnectionTimeoutSec();
	
	
	/**
	 * sets the max time for a connections. By 
	 * exceeding this time the connection will be
	 * terminated
	 * 
	 * @param timeoutSec the connection timeout in sec
	 */
	public void setConnectionTimeoutSec(int timeoutSec);
}
