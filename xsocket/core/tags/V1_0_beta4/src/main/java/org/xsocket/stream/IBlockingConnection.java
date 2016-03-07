// $Id: IBlockingConnection.java 806 2007-01-18 07:47:43Z grro $
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
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

import org.xsocket.ClosedConnectionException;


/**
 * A connection which uses the underlying channel in a blocking manner. Every I/O operation
 * will block until it completes. <br><br>
 * 
 * @author grro@xsocket.org
 */
public interface IBlockingConnection extends IConnection {

	public static final long INITIAL_RECEIVE_TIMEOUT = 1 * 60 * 1000; 
	
	
	/**
	 * set the timeout for calling receive methods in millis 
	 * 
	 * @param timeout  the timeout in millis
	 */
	public void setReceiveTimeout(long timeout);
	
	
	
	/**
	 * receive a string. the method will block, until the delimiter has been read.
	 * For the encoding the default encoding of the connection will be used  
	 * 
	 * @param delimiter the delimiter  
	 * @return the received string
	 * @throws SocketTimeoutException If the receive timeout has been reached
 	 * @throws ClosedConnectionException If the underlying socket is already closed  
	 * @throws IOException If some other I/O error occurs
	 */	
	public String readStringByDelimiter(String delimiter) throws IOException, ClosedConnectionException, SocketTimeoutException;
	
	
	/**
	 * receive a string. the method will block, until the delimiter has been read  
	 * 
	 * @param delimiter the delimiter
	 * @param encoding the encoding   
	 * @return the received string
	 * @throws SocketTimeoutException If the receive timeout has been reached 
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public String readStringByDelimiter(String delimiter, String encoding) throws IOException, ClosedConnectionException, SocketTimeoutException;
	
	
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
	 * 
     * For performance reasons, the ByteBuffer receiveByteBuffer method is generally preferable to get bytes 
	 * 
	 * @param delimiter the delimiter  
	 * @return the received ByteBuffer
	 * @throws SocketTimeoutException If the receive timeout has been reached
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */		
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException, ClosedConnectionException, SocketTimeoutException;
	
	
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
	 * 
     * For performance reasons, the ByteBuffer receiveByteBuffer method is generally preferable to get bytes
     *  
	 * @param delimiter the delimiter  
	 * @return the received byte array
	 * @throws SocketTimeoutException If the receive timeout has been reached 
 	 * @throws ClosedConnectionException If the underlying socket is already closed 
	 * @throws IOException If some other I/O error occurs
	 */		
	public byte[] readBytesByDelimiter(String delimiter) throws IOException, ClosedConnectionException, SocketTimeoutException;
	
	
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
}
