// $Id: IConnection.java 882 2007-02-07 07:46:06Z grro $
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

import java.io.Flushable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import org.xsocket.ClosedConnectionException;
import org.xsocket.IDataSink;
import org.xsocket.IDataSource;



/**
 * A connection (session) between two endpoints. It encapsulates the underlying socket channel. <br><br>
 * 
 * @author grro@xsocket.org
 */
public interface IConnection extends IDataSource, IDataSink, GatheringByteChannel, ReadableByteChannel, WritableByteChannel, Flushable {
	
	public static final String INITIAL_DEFAULT_ENCODING = "UTF-8";
	public static final boolean INITIAL_AUTOFLUSH = true;
	

	/**
	 * returns the id
	 * 
	 * @return id
	 */
	public String getId();
	
	
	
	/**
	 * returns, if the connection is open 
	 * 
	 * 
	 * @return true if the connection is open
	 */
	public boolean isOpen();

	
	
	/**
	 * closes the endpoint
	 *
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public void close() throws ClosedConnectionException, IOException;
	
	
	/**
	 * flush the send buffer
	 *
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public void flush() throws ClosedConnectionException, IOException;
	

	
	/**
	 * returns the port of the local endpoint
	 * 
	 * @return the locale port number
	 */
	public int getLocalPort();
	
	

	/**
	 * returns the local address
	 * 
	 * @return the local IP address or InetAddress.anyLocalAddress() if the socket is not bound yet.
	 */
	public InetAddress getLocalAddress();
	

	

	/**
	 * returns the remote address
	 * 
	 * @return the remote address
	 */
	public InetAddress getRemoteAddress();
	

	/**
	 * returns the port of the remote endpoint
	 * 
	 * @return the remote port number
	 */
	public int getRemotePort();
	
	
	/**
	 * sets the dafault encoding used by this connection
	 * 
	 * @param encoding the default encoding
	 */
	public void setDefaultEncoding(String encoding);
	
	
	/**
	 * gets the dafault encoding used by this connection
	 *  
	 * @return the default encoding
	 */
	public String getDefaultEncoding();
	

	/**
	 * returns a compact string representation of the object.
	 *  
	 * @return compact string representation of the object.
	 */
	public String toCompactString();
	
	
	
	/**
	 * set autoflush. If atoflush is activated, each write call 
	 * will cause a flush. <br><br>
	 * 
	 * By default the autoflush is activated
	 *   
	 * @param autoflush true if autoflush should be activated 
	 */
	public void setAutoflush(boolean autoflush);
	
	
	/**
	 * get autoflush
	 * @return true, if autoflush is activated
	 */
	public boolean getAutoflush();
	
	


	/**
	 * ad hoc activation of ssl. By perfoming of this 
	 * method all remaining data to send will be flushed. 
	 * After this all data will be sent and received by 
	 * using ssl
	 * 
	 * @throws IOException If some other I/O error occurs
	 */
	public void startSSL() throws IOException;
	
	
	/**
	 * sends a message to the remote endpoint
	 * 
	 * @param message    the message to send 
	 * @param encoding   the encoding which should be used th encode the chars into byte (e.g. 'US-ASCII' or 'UTF-8')
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public int write(String message, String encoding) throws ClosedConnectionException, IOException;

	
	/**
	 * sends a message to the remote endpoint by using the connection default encoding 
	 * 
	 * @param message  the message to send 
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public int write(String message) throws ClosedConnectionException, IOException;

	
	/**
	 * sends a byte to the remote endpoint
	 *  
	 * @param b   the byte to send
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public int write(byte b) throws ClosedConnectionException, IOException;

	
	/**
	 * sends bytes to the remote endpoint
	 *  
	 * @param bytes   the bytes to send
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public int write(byte... bytes) throws ClosedConnectionException, IOException;
	

	/**
	 * sends bytes to the remote endpoint
	 *  
	 * @param bytes    the bytes to send
	 * @param offset   The offset of the subarray to be used; must be non-negative and no larger than array.length. The new buffer's position will be set to this value.
	 * @param length   The length of the subarray to be used; must be non-negative and no larger than array.length - offset. The new buffer's limit will be set to offset + length.
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public int write(byte[] bytes, int offset, int length) throws ClosedConnectionException, IOException;


	/**
	 * sends a byte buffer to the remote endpoint
	 *  
	 * @param buffer   the bytes to send
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public int write(ByteBuffer buffer) throws ClosedConnectionException, IOException;
	
	
	/**
	 * sends an array of byte buffer to the remote endpoint
	 *  
	 * @param buffers   the bytes to send
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public long write(ByteBuffer[] buffers) throws ClosedConnectionException, IOException;


	/**
	 * sends an int to the remote endpoint
	 *  
	 * @param i   the int value to send
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public int write(int i) throws ClosedConnectionException, IOException;


	/**
	 * sends a long to the remote endpoint
	 *  
	 * @param l   the int value to send
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public int write(long l) throws ClosedConnectionException, IOException;

	
	/**
	 * sends a double to the remote endpoint
	 *  
	 * @param d   the int value to send
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public int write(double d) throws ClosedConnectionException, IOException;

	
	/**
	 * read a byte
	 * 
	 * @return the byte value
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public byte readByte() throws IOException, ClosedConnectionException;
	
	
	/**
	 * read an int
	 * 
	 * @return the int value
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public int readInt() throws IOException, ClosedConnectionException;

	
	/**
	 * read a long
	 * 
	 * @return the long value
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public long readLong() throws IOException, ClosedConnectionException;

	
	/**
	 * read a double
	 * 
	 * @return the double value
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public double readDouble() throws IOException, ClosedConnectionException;
	
	
	
	
	/**
	 * read a ByteBuffer by using a delimiter 
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is 
     * generally preferable to get bytes 
	 * 
	 * @param delimiter the delimiter
	 * @return the ByteBuffer
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException, ClosedConnectionException;


	
	/**
	 * read a ByteBuffer by using a length defintion 
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is 
     * generally preferable to get bytes 
	 * 
	 * @param length the amount of bytes to read
	 * @return the ByteBuffer
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readByteBufferByLength(int length) throws IOException, ClosedConnectionException;

	
	
	
	/**
	 * read a byte array by using a delimiter
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is
     * generally preferable to get bytes 
	 * 
	 * @param delimiter the delimiter  
	 * @return the read bytes
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */		
	public byte[] readBytesByDelimiter(String delimiter) throws IOException, ClosedConnectionException;
	

	/**
	 * read bytes by using a length defintion 
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is
     * generally preferable to get bytes 
	 * 
	 * @param length the amount of bytes to read  
	 * @return the read bytes
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */		
	public byte[] readBytesByLength(int length) throws IOException, ClosedConnectionException;

	

	
	/**
	 * read a string by using a delimiter and the connection default encoding
	 * 
	 * @param delimiter the delimiter
	 * @return the string
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the default encoding is not supported
	 */
	public String readStringByDelimiter(String delimiter) throws IOException, ClosedConnectionException, UnsupportedEncodingException;

	
	/**
	 * read a string by using a length definition and the connection default encoding
	 * 
	 * @param length the amount of bytes to read  
	 * @return the string
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the given encoding is not supported 
	 */
	public String readStringByLength(int length) throws IOException, ClosedConnectionException, UnsupportedEncodingException;


	/**
	 * read a string by using a delimiter
	 * 
	 * @param delimiter the delimiter
	 * @param encoding the encodin to use
	 * @return the string
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the given encoding is not supported 
	 */
	public String readStringByDelimiter(String delimiter, String encoding) throws IOException, ClosedConnectionException, UnsupportedEncodingException;

	
	/**
	 * read a string by using a length definition 
	 * 
	 * @param length the amount of bytes to read  
	 * @param encoding the encodin to use
	 * @return the string
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the given encoding is not supported 
	 */
	public String readStringByLength(int length, String encoding) throws IOException, ClosedConnectionException, UnsupportedEncodingException;

	
	
	/**
	 * Marks the present write position in the connection. Subsequent calls to resetToReadMark() will attempt
	 * to reposition the connection to this point. The write read become in valid by flushing
	 * the connection 
	 * 
	 */
	public void markWritePosition();
	

	/**
	 * remove the mark the present write position in the connection.
	 */
	public void removeWriteMark();
	
	
	/**
	 * Resets to the marked write position. If the connection has been marked, 
	 * then attempt to reposition it at the mark. 
	 * 
	 * @return true, if reset was successful
	 */
	public boolean resetToWriteMark();

	
	/**
	 * Marks the present read position in the connection. Subsequent calls to resetToReadMark() will attempt
	 * to reposition the connection to this point.
	 * 
	 */
	public void markReadPosition();
	

	/**
	 * remove the mark the present read position in the connection.
	 */
	public void removeReadMark();
	
	
	/**
	 * Resets to the marked read position. If the connection has been marked, 
	 * then attempt to reposition it at the mark. 
	 * 
	 * @return true, if reset was successful
	 */
	public boolean resetToReadMark();
}
