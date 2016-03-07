// $Id: INonBlockingConnection.java 776 2007-01-15 17:15:41Z grro $
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
	 * @param delimiter the delimiter
	 * @return the ByteBuffer
 	 * @throws ClosedConnectionException If the underlying socket is already closed
 	 * @throws BufferUnderflowException if the delimiter has not been found 
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException, ClosedConnectionException, BufferUnderflowException;


	
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
	 * @param delimiter the delimiter  
	 * @return the read bytes
 	 * @throws ClosedConnectionException If the underlying socket is already closed
 	 * @throws BufferUnderflowException if the delimiter has not been found 
	 * @throws IOException If some other I/O error occurs
	 */		
	public byte[] readBytesByDelimiter(String delimiter) throws IOException, ClosedConnectionException, BufferUnderflowException;
	

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
	 * @param delimiter the delimiter
	 * @return the string
 	 * @throws ClosedConnectionException If the underlying socket is already closed
 	 * @throws BufferUnderflowException if the delimiter has not been found  
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the default encoding is not supported
	 */
	public String readStringByDelimiter(String delimiter) throws IOException, ClosedConnectionException, BufferUnderflowException, UnsupportedEncodingException;

	
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
	 * @param delimiter the delimiter
	 * @param encoding the encodin to use
	 * @return the string
 	 * @throws ClosedConnectionException If the underlying socket is already closed
 	 * @throws BufferUnderflowException if the buffer's limit has been reached 
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the given encoding is not supported 
	 */
	public String readStringByDelimiter(String delimiter, String encoding) throws IOException, ClosedConnectionException, BufferUnderflowException, UnsupportedEncodingException;

	
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
}
