// $Id: IConnection.java 1256 2007-05-17 14:21:06Z grro $
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

import java.io.Closeable;
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
import org.xsocket.MaxReadSizeExceededException;



/**
 * A connection (session) between two endpoints. It encapsulates the underlying socket channel. <br><br>
 * 
 * @author grro@xsocket.org
 */
public interface IConnection extends IDataSource, IDataSink, GatheringByteChannel, ReadableByteChannel, WritableByteChannel, Flushable, Closeable {
	
	public static final String INITIAL_DEFAULT_ENCODING = "UTF-8";
	public static final boolean INITIAL_AUTOFLUSH = true;
	
	public enum FlushMode { SYNC, ASYNC };


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
	 * flush the send buffer.
	 *
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public void flush() throws ClosedConnectionException, IOException;
	

	
	/**
	 * returns the local port
	 * 
	 * @return the local port
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
	 * @return the remote port
	 */
	public int getRemotePort();
	
	
	/**
	 * sets the default encoding for this connection (used by string related methods like readString...)
	 * 
	 * @param encoding the default encoding
	 */
	public void setDefaultEncoding(String encoding);
	
	
	/**
	 * gets the default encoding for this connection (used by string related methods like readString...)
	 *  
	 * @return the default encoding
	 */
	public String getDefaultEncoding();
	


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
	 * @return true, if autoflush is activated
	 */
	public boolean getAutoflush();
	
	


	/**
	 * ad hoc activation of a secured mode (SSL). By perfoming of this 
	 * method all remaining data to send will be flushed. 
	 * After this all data will be sent and received in the secured mode
	 * 
	 * @throws IOException If some other I/O error occurs
	 */
	public void activateSecuredMode() throws IOException;
	
	
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
	 * sends a byte buffer to the remote endpoint. In case of activated autoflush 
	 * (for default see {@link IConnection#INITIAL_AUTOFLUSH}) the behaviour is 
	 * according to the {@link WritableByteChannel} specification. In case of 
	 * user managed flushing (autoflush is off) the passed over buffers will 
	 * only be queued internally and written after flushing the connection.     
	 *  
	 * @param buffer   the bytes to send
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public int write(ByteBuffer buffer) throws ClosedConnectionException, IOException;
	
	
	/**
	 * sends an array of byte buffer to the remote endpoint. In case of activated autoflush 
	 * (for default see {@link IConnection#INITIAL_AUTOFLUSH}) the behaviour is 
	 * according to the {@link WritableByteChannel} specification. In case of 
	 * user managed flushing (autoflush is off) the passed over buffers will 
	 * only be queued internally and written after flushing the connection.     	 
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
	 * To avoid memory leaks the {@link IConnection#readByteBufferByDelimiter(String, int)} method is generally preferable  
	 * <br> 
     * For performance reasons, the ByteBuffer readByteBuffer method is 
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter
	 * @return the ByteBuffer
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException, ClosedConnectionException;
	
	
	
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
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter, int maxLength) throws IOException, ClosedConnectionException, MaxReadSizeExceededException;


	
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
	 * To avoid memory leaks the {@link IConnection#readBytesByDelimiter(String, int)} method is generally preferable  
     * <br> 
     * For performance reasons, the ByteBuffer readByteBuffer method is
     * generally preferable to get bytes 
	 * 
	 * @param delimiter    the delimiter  
	 * @return the read bytes
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */		
	public byte[] readBytesByDelimiter(String delimiter) throws IOException, ClosedConnectionException;


	
	/**
	 * read a byte array by using a delimiter
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is
     * generally preferable to get bytes 
	 * 
	 * @param delimiter    the delimiter  
	 * @param maxLength    the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the read bytes
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found    
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */		
	public byte[] readBytesByDelimiter(String delimiter, int maxLength) throws IOException, ClosedConnectionException, MaxReadSizeExceededException;
	

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
	 * To avoid memory leaks the {@link IConnection#readStringByDelimiter(String, int)} method is generally preferable  
     * 
	 * @param delimiter   the delimiter
	 * @return the string
	 * 
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the default encoding is not supported
	 */
	public String readStringByDelimiter(String delimiter) throws IOException, ClosedConnectionException, UnsupportedEncodingException;



	
	/**
	 * read a string by using a delimiter and the connection default encoding
	 * 
	 * @param delimiter   the delimiter
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the string
	 * 
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found   
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the default encoding is not supported
	 */
	public String readStringByDelimiter(String delimiter, int maxLength) throws IOException, ClosedConnectionException, UnsupportedEncodingException, MaxReadSizeExceededException;

	
	
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
	 * To avoid memory leaks the {@link IConnection#readStringByDelimiter(String, String, int)} method is generally preferable  
     *
	 * @param delimiter   the delimiter
	 * @param encoding    the encodin to use
	 * @return the string
	 * 
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException If the given encoding is not supported 
	 */
	public String readStringByDelimiter(String delimiter, String encoding) throws IOException, ClosedConnectionException, UnsupportedEncodingException;


	/**
	 * read a string by using a delimiter
	 * 
	 * @param delimiter   the delimiter
	 * @param encoding    the encodin to use
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the string
	 * 
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found  
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException If the given encoding is not supported 
	 */
	public String readStringByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, ClosedConnectionException, UnsupportedEncodingException, MaxReadSizeExceededException;

	
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
	 * Returns the index  of the first occurrence of the given string. 
	 * 
	 * @param str any string
	 * @return if the string argument occurs as a substring within this object, then the 
	 *         index of the first character of the first such substring is returned;
 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs          
	 */
	public int getIndexOf(String str) throws IOException, ClosedConnectionException;
	
	
	

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
	 */
	public int getIndexOf(String str, int maxLength) throws IOException, ClosedConnectionException, MaxReadSizeExceededException;
	

	/**
	 * Marks the present write position in the connection. Subsequent calls to resetToReadMark() will attempt
	 * to reposition the connection to this point. The write read become in valid by flushing
	 * the connection. <br><br>
	 * 
	 * <b>write mark is only supported for autoflush off.</b> E.g.
	 * <pre>
	 * ...
	 * con.setAutoflush(false);
	 * 
	 * con.markWritePosition();  // mark current position
	 * con.write((int) 0);       // write "emtpy" length field
	 * 
	 * int written = con.write("hello world");
	 * written += con.write(" it’s nice to be here");
	 * ...
	 * 
	 * con.resetToWriteMark();  // return to length field position
	 * con.write(written);      // and update it
	 * 
	 * con.flush(); // flush (marker will be removed implicit)
…    * </pre> 
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
	
	

	/**
	 * Attaches the given object to this connection
	 * 
	 * @param obj The object to be attached; may be null
	 * @return The previously-attached object, if any, otherwise null
	 */
	public Object attach(Object obj);
	

	/**
	 * Retrieves the current attachment.
	 * 
	 * @return The object currently attached to this key, or null if there is no attachment
	 */
	public Object attachment();
}
