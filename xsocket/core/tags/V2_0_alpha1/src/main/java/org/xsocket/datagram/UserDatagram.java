// $Id: UserDatagram.java 1691 2007-08-20 05:42:14Z grro $
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
package org.xsocket.datagram;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;


import org.xsocket.ClosedConnectionException;
import org.xsocket.DataConverter;
import org.xsocket.IDataSink;
import org.xsocket.IDataSource;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.stream.IConnection;




/**
 * a datagram packet 
 * 
 * 
 * @author grro@xsocket.org
 */
public final class UserDatagram implements IDataSource, IDataSink {
	
	private SocketAddress remoteSocketAddress = null;
	private ByteBuffer data = null;
	private String defaultEncoding = "UTF-8";
		
	
	/**
	 * constructor. creates an empty packet  
	 * 
	 * @param size         the packet size
	 */
	public UserDatagram(int size) {
		init(remoteSocketAddress, ByteBuffer.allocate(size), size);
	}
	
	
	/**
	 * constructor. creates an empty packet by setting the target remote address 
	 * 
	 * @param remoteHost   the destination hostname
	 * @param remotePort   the destination port number
	 * @param size         the packet size
	 */
	public UserDatagram(String remoteHost, int remotePort, int size) {
		init(new InetSocketAddress(remoteHost, remotePort), ByteBuffer.allocate(size), size);
	}
	
	
	/**
	 * constructor. creates an empty packet by setting the target remote address 
	 * 
	 * @param remoteAddress the destination address
	 * @param remotePort    the destination port number
	 * @param size         the packet size
	 */
	public UserDatagram(InetAddress remoteAddress, int remotePort, int size) {
		init(new InetSocketAddress(remoteAddress, remotePort), ByteBuffer.allocate(size), size);
	}
	
	/**
	 * constructor. creates an empty packet by setting the target remote address 
	 * 
	 * @param address      the destination address
	 * @param size         the packet size
	 */
	public UserDatagram(SocketAddress address, int size) {
		init(address, ByteBuffer.allocate(size), size);
	}
	
	
	/**
	 * constructor. creates packet, and sets the content with the given buffer
	 * 
	 * @param data    the data which will be written into the buffer
	 */
	public UserDatagram(ByteBuffer data) {
		this(null, data);
	}
	

	/**
	 * constructor. creates packet by setting the target remote address,
	 * and sets the content with the given buffer
	 * 
	 * @param remoteSocketAddress  the destination address
	 * @param data                 the data which will be written into the buffer
	 */
	public UserDatagram(SocketAddress remoteSocketAddress, ByteBuffer data) {
		this(remoteSocketAddress, data, "UTF-8");
	}

	
	/**
	 * constructor. creates packet by setting the target remote address,
	 * and sets the content with the given buffer
	 * 
	 * @param remoteSocketAddress  the destination address
	 * @param data                 the data which will be written into the buffer
	 * @param defaultEncoding      the default encoding to use
	 */
	UserDatagram(SocketAddress remoteSocketAddress, ByteBuffer data, String defaultEncoding) {
		init(remoteSocketAddress, data, data.limit());
		this.defaultEncoding = defaultEncoding;
	}

	
	/**
	 * constructor. creates packet sets the content with the given byte array
	 * 
	 * @param data                 the data which will be written into the buffer
	 */
	public UserDatagram(byte[] data) {
		this(null, data);
	}
		
	
	/**
	 * constructor. creates packet by setting the target remote address,
	 * and sets the content with the given byte array 
	 * 
	 * @param remoteHost   the destination hostname
	 * @param remotePort   the destination port number
	 * @param data         the data which will be written into the buffer
	 */
	public UserDatagram(String remoteHost, int remotePort, byte[] data) {
		this(new InetSocketAddress(remoteHost, remotePort), data);
	}
	
	

	
	/**
	 * constructor. creates packet by setting the target remote address,
	 * and sets the content with the given byte array 
	 * 
	 * @param remoteSocketAddress  the destination address
	 * @param data                 the data which will be written into the buffer
	 */
	public UserDatagram(SocketAddress remoteSocketAddress, byte[] data) {
		ByteBuffer buffer = ByteBuffer.wrap(data);
		buffer.position(buffer.limit());
		init(remoteSocketAddress, buffer, data.length);
	}
	
	
	
	private void init(SocketAddress remoteSocketAddress, ByteBuffer data, int packetSize) {
		this.remoteSocketAddress =remoteSocketAddress;
		this.data = data;
	}
	
	
	/**
	 * prepares the packet to send
	 *
	 */
	void prepareForSend() {
		data.clear();
	}
	

	/**
	 * set the remote socket address
	 * @param remoteSocketAddress   the remote socket address
	 */
	void setRemoteAddress(SocketAddress remoteSocketAddress) {
		this.remoteSocketAddress = remoteSocketAddress;
	}
	
	
	/**
	 * return the underlying data buffer
	 *  
	 * @return the underlying data buffer
	 */
	protected ByteBuffer getData() {
		return data;
	}

		
	/**
	 * Returns the socket address of the machine to which this packet is being sent 
	 * or from which the packet was received.
	 *  
	 * @return the socket address
	 */
	public SocketAddress getRemoteSocketAddress() {
		return remoteSocketAddress;
	}
	
	
	/**
     * Returns the address of the machine to which this packet is being sent 
	 * or from which the packet was received.
	 * 
	 * @return  the address
	 */
	public InetAddress getRemoteAddress() {
		if (remoteSocketAddress instanceof InetSocketAddress) {
			return ((InetSocketAddress) remoteSocketAddress).getAddress(); 
		} else {
			return null;
		}
	}
	
	
	/**
     * Returns the port number of the machine to which this packet is being sent 
	 * or from which the packet was received.
	 * 
	 * @return the port number
	 */
	public int getRemotePort() {
		if (remoteSocketAddress instanceof InetSocketAddress) {
			return ((InetSocketAddress) remoteSocketAddress).getPort(); 
		} else {
			return -1;
		}
	}
	
	
	/**
	 * read a byte
	 * 
	 * @return the byte value
 	 * @throws BufferUnderflowException if the buffer`s limit has been reached  
	 * @throws IOException If some other I/O error occurs
	 */	
	public byte readByte() throws IOException, BufferUnderflowException {
		return data.get();
	}
	
	

	
	/**
	 * read a ByteBuffer by using a length defintion 
     *
	 * @param length the amount of bytes to read
	 * @return the ByteBuffer
 	 * @throws BufferUnderflowException if the buffer`s limit has been reached  
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer readSingleByteBufferByLength(int length) throws IOException, BufferUnderflowException {
		int savedLimit = data.limit();
		int savedPosition = data.position();
		
		data.limit(data.position() + length);
		ByteBuffer sliced = data.slice();
		
		data.position(savedPosition + length);
		data.limit(savedLimit);
		return sliced;
	}
	
	

	
	/**
	 * read bytes by using a length defintion 
	 *  
	 * @param length the amount of bytes to read  
	 * @return the read bytes
 	 * @throws BufferUnderflowException if the buffer`s limit has been reached  
	 * @throws IOException If some other I/O error occurs
	 */		
	public byte[] readBytesByLength(int length) throws IOException, BufferUnderflowException {
		return DataConverter.toBytes(readByteBufferByLength(length));
	}

	
	
	
	/**
	 * read a string by using a length definition 
	 * 
	 * @param length the amount of bytes to read  
	 * @param encoding the encoding to use
	 * @return the string
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if the buffer`s limit has been reached  
 	 * @throws UnsupportedEncodingException if the given encoding is not supported 
	 */
	public String readStringByLength(int length, String encoding) throws IOException, BufferUnderflowException, UnsupportedEncodingException {
		return DataConverter.toString(readByteBufferByLength(length), encoding);
	}

	
	
	/**
	 * read a string by using a length definition and the connection default encoding
	 * 
	 * @param length the amount of bytes to read  
	 * @return the string
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if the buffer`s limit has been reached  
 	 * @throws UnsupportedEncodingException if the given encoding is not supported 
	 */
	public String readStringByLength(int length) throws IOException, BufferUnderflowException, UnsupportedEncodingException {
		return readStringByLength(length, defaultEncoding);
	}
	

	
	/**
	 * read a double
	 * 
	 * @return the double value
c	 * @throws IOException If some other I/O error occurs
	 */
	public double readDouble() throws IOException, BufferUnderflowException {
		return data.getDouble();
	}
	
	
	
	
	/**
	 * read an int
	 * 
	 * @return the int value
 	 * @throws BufferUnderflowException if the buffer`s limit has been reached  
	 * @throws IOException If some other I/O error occurs
	 */
	public int readInt() throws IOException, BufferUnderflowException {
		return data.getInt();
	}
	
	
	
	/**
	 * read an short
	 * 
	 * @return the short value
 	 * @throws BufferUnderflowException if the buffer`s limit has been reached  
	 * @throws IOException If some other I/O error occurs
	 */
	public short readShort() throws IOException, BufferUnderflowException {
		return data.getShort();
	}
	
	
	/**
	 * read a long
	 * 
	 * @return the long value
 	 * @throws BufferUnderflowException if the buffer`s limit has been reached  
	 * @throws IOException If some other I/O error occurs
	 */
	public long readLong() throws IOException, BufferUnderflowException {
		return data.getLong();
	}

		

	/**
	 * read all remaining data as ByteBuffer 
	 * 
	 * @return the ByteBuffer
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer readByteBuffer() throws IOException {
		ByteBuffer sliced = data.slice();
		data.position(data.limit());
		return sliced;
	}

	
	
	/**
	 * reads the remaining data as byte array 
	 * 
	 * @return the byte array
	 * @throws IOException If some other I/O error occurs
	 */
	public byte[] readBytes() throws IOException {
		return DataConverter.toBytes(readByteBuffer());
	}
	
	
	
	/**
	 * read the remaining data as String
	 * 
	 * @return the string
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the default encoding is not supported
	 */
	public String readString() throws IOException, UnsupportedEncodingException {
		return readString(defaultEncoding);
	}
	
	
	
	
	/**
	 * read the remaining data as String
	 * 
	 * @param encoding the encoding to use
	 * @return the string
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the default encoding is not supported
	 */
	public String readString(String encoding) throws IOException, UnsupportedEncodingException {
		return DataConverter.toString(readByteBuffer(), encoding);
	}

	
	/**
	 * read a string by using a delimiter and the connection default encoding
	 * 
	 * @param delimiter   the delimiter
	 * @return the string
	 * @throws IOException If some other I/O error occurs
  	 * @throws BufferUnderflowException if the buffer`s limit has been reached  
 	 * @throws UnsupportedEncodingException if the default encoding is not supported
	 */
	public String readStringByDelimiter(String delimiter) throws IOException, BufferUnderflowException, UnsupportedEncodingException {
		return readStringByDelimiter(delimiter, defaultEncoding, Integer.MAX_VALUE);
	}
	
	
	
	/**
	 * read a string by using a delimiter and the connection default encoding
	 * 
	 * @param delimiter   the delimiter
	 * @param maxLength   the max length of bytes that should be read. If the limit will be exceeded a MaxReadSizeExceededException will been thrown  
	 * @return the string
	 * @throws IOException If some other I/O error occurs
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found        
  	 * @throws BufferUnderflowException if the buffer`s limit has been reached  
 	 * @throws UnsupportedEncodingException if the default encoding is not supported
	 */
	public String readStringByDelimiter(String delimiter, int maxLength) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException {
		return readStringByDelimiter(delimiter, defaultEncoding, maxLength);
	}
	
	/**
	 * read a string by using a delimiter
	 * 
	 * @param delimiter   the delimiter
	 * @param encoding    the encodin to use
	 * @param maxLength   the max length of bytes that should be read. If the limit will be exceeded a MaxReadSizeExceededException will been thrown 
	 * @return the string
	 * @throws IOException If some other I/O error occurs
  	 * @throws BufferUnderflowException if the buffer`s limit has been reached
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found       
 	 * @throws UnsupportedEncodingException if the given encoding is not supported 
	 */
	public String readStringByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException {
		ByteBuffer buffer = readSingleByteBufferByDelimiter(delimiter, maxLength);
		return DataConverter.toString(buffer, encoding);
	}
	
	
	
	/**
	 * read a ByteBuffer by using a delimiter. The default encoing will be used to decode the delimiter 
	 * To avoid memory leaks the {@link IConnection#readByteBufferByDelimiter(String, int)} method is generally preferable  
	 * <br> 
     * For performance reasons, the ByteBuffer readByteBuffer method is 
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter
	 * @return the ByteBuffer
 	 * @throws ClosedConnectionException If the underlying socket is already closed
  	 * @throws BufferUnderflowException if the buffer`s limit has been reached
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException, BufferUnderflowException {
		return new ByteBuffer[] { readSingleByteBufferByDelimiter(delimiter) };
	}
	
	
	
	/**
	 * read a ByteBuffer by using a delimiter. The default encoing will be used to decode the delimiter 
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is 
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown	 
	 * @return the ByteBuffer
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found     
  	 * @throws BufferUnderflowException if the buffer`s limit has been reached 
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		return new ByteBuffer[] { readSingleByteBufferByDelimiter(delimiter, maxLength) };
	}
	



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
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found     
	 * @throws BufferUnderflowException if the buffer`s limit has been reached
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		return new ByteBuffer[] { readSingleByteBufferByDelimiter(delimiter, encoding, maxLength) };
	}

	
	/**
	 * read a ByteBuffer by using a length defintion 
	 * 
	 * @param length the amount of bytes to read
	 * @return the ByteBuffer
  	 * @throws BufferUnderflowException if the buffer`s limit has been reached 
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readByteBufferByLength(int length) throws IOException, BufferUnderflowException {
		return new ByteBuffer[] { readSingleByteBufferByLength(length) };
	}


	
	/**
	 * read a ByteBuffer by using a delimiter 
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is 
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter
	 * @return the ByteBuffer
  	 * @throws BufferUnderflowException if the buffer`s limit has been reached
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found    
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer readSingleByteBufferByDelimiter(String delimiter) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		return readSingleByteBufferByDelimiter(delimiter, Integer.MAX_VALUE);
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
  	 * @throws BufferUnderflowException if the buffer`s limit has been reached
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found    
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer readSingleByteBufferByDelimiter(String delimiter, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		return readSingleByteBufferByDelimiter(delimiter, defaultEncoding, maxLength);
	}

	
	
	/**
	 *  read a ByteBuffer by using a delimiter 
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is 
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter
	 * @param encoding    the encoding of the delimiter 
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown 
	 * @return the ByteBuffer
  	 * @throws BufferUnderflowException if the buffer`s limit has been reached
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found    
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer readSingleByteBufferByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		byte[] delimiterBytes = delimiter.getBytes(encoding);
		int startPos = findDelimiter(data, delimiterBytes, maxLength);
		if (startPos >= 0) {			
			int savedLimit = data.limit();
			data.limit(startPos);
			ByteBuffer result = data.slice();
			data.limit(savedLimit);
			data.position(startPos + delimiterBytes.length);

			return result; 
		} else {
			throw new BufferUnderflowException();
		}
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
  	 * @throws BufferUnderflowException if the buffer`s limit has been reached
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found        
	 * @throws IOException If some other I/O error occurs
	 */		
	public byte[] readBytesByDelimiter(String delimiter,int  maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		ByteBuffer buffer = readSingleByteBufferByDelimiter(delimiter, maxLength);
		return DataConverter.toBytes(buffer);
	}
	
	
	
	
	
	private static int findDelimiter(ByteBuffer buffer, byte[] delimiter, int maxLength) throws MaxReadSizeExceededException {
		int result = -1;
		
		int delimiterPosition = 0;
		
		// iterator over buffer content
		for (int pos = buffer.position(); pos < buffer.limit(); pos++) {
			
			byte b = buffer.get(pos);
			if (b == delimiter[delimiterPosition]) {
				delimiterPosition++;
				if (delimiterPosition == delimiter.length) {
					result = (pos - delimiterPosition + 1);
					break;
				}
			} else {
				delimiterPosition = 0;
			}
			
		}
		
		if (result > maxLength) {
			throw new MaxReadSizeExceededException();
		}
		
		return result;
	} 
	
	
	

	/**
	 * writes a byte to the packet
	 *  
	 * @param b   the byte to write
	 * @return the number of send bytes 
 	 * @throws BufferOverflowException if the buffer`s limit has been reached  
	 * @throws IOException If some other I/O error occurs
	 */
	public int write(byte b) throws IOException, BufferOverflowException {
		data.put(b);
		return 1;
	}
	

	/**
	 * writes a short to the packet
	 *  
	 * @param s   the short to write
	 * @return the number of send bytes 
 	 * @throws BufferOverflowException if the buffer`s limit has been reached  
	 * @throws IOException If some other I/O error occurs
	 */
	public int write(short s) throws IOException, BufferOverflowException {
		data.putShort(s);
		return 2;
	}
	
	
	/**
	 * writes bytes to the packet
	 *  
	 * @param bytes   the bytes to write
	 * @return the number of send bytes
 	 * @throws BufferOverflowException if the buffer`s limit has been reached   
	 * @throws IOException If some other I/O error occurs
	 */
	public int write(byte... bytes) throws IOException, BufferOverflowException {
		data.put(bytes);
		return bytes.length;
	}
	
	

	/**
	 * writes bytes to the packet
	 *  
	 * @param bytes    the bytes to write
	 * @param offset   The offset of the subarray to be used; must be non-negative and no larger than array.length. The new buffer`s position will be set to this value.
	 * @param length   The length of the subarray to be used; must be non-negative and no larger than array.length - offset. The new buffer`s limit will be set to offset + length.
	 * @return the number of send bytes 
 	 * @throws BufferOverflowException if the buffer`s limit has been reached  
	 * @throws IOException If some other I/O error occurs
	 */
	public int write(byte[] bytes, int offset, int length) throws IOException, BufferOverflowException {
		data.put(bytes, offset, length);
		return length;
	}
	
	
	/**
	 * writes a byte buffer to the packet
	 *  
	 * @param buffer   the bytes to write
	 * @return the number of send bytes 
 	 * @throws BufferOverflowException if the buffer`s limit has been reached  
	 * @throws IOException If some other I/O error occurs
	 */
	public int write(ByteBuffer buffer) throws IOException, BufferOverflowException {
		int length = buffer.remaining();
		data.put(buffer);
		return length;
	}
	
	
	
	/**
	 * writes a byte array to the packet
	 *  
	 * @param buffers   the bytes to write
	 * @return the number of send bytes 
 	 * @throws BufferOverflowException if the buffer`s limit has been reached  
	 * @throws IOException If some other I/O error occurs
	 */
	public long write(ByteBuffer[] buffers) throws IOException, BufferOverflowException {
		int length = 0;
		for (ByteBuffer buffer : buffers) {
			length += write(buffer);
		}
		return length;
	}
	
	

	
	/**
	 * writes a double to the packet
	 *  
	 * @param d   the int value to write
	 * @return the number of send bytes 
 	 * @throws BufferOverflowException if the buffer`s limit has been reached  
	 * @throws IOException If some other I/O error occurs
	 */
	public int write(double d) throws IOException, BufferOverflowException {
		data.putDouble(d);
		return 8;
	}
	
	
	/**
	 * writes a int to the packet
	 *  
	 * @param i   the int value to write
	 * @return the number of send bytes 
 	 * @throws BufferOverflowException if the buffer`s limit has been reached  
	 * @throws IOException If some other I/O error occurs
	 */
	public int write(int i) throws IOException, BufferOverflowException {
		data.putInt(i);
		return 4;
	}
	
	
	/**
	 * writes a long to the packet
	 *  
	 * @param l   the int value to write
	 * @return the number of send bytes 
 	 * @throws BufferOverflowException if the buffer`s limit has been reached  
	 * @throws IOException If some other I/O error occurs
	 */
	public int write(long l) throws IOException, BufferOverflowException {
		data.putLong(l);
		return 8;
	}
	
	
	/**
	 * write a message
	 * 
	 * @param message    the message to write
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferOverflowException if the buffer`s limit has been reached   
	 */
	public int write(String message) throws IOException, BufferOverflowException  {
		return write(message, defaultEncoding);
	}
	
	
	/**
	 * write a message
	 * 
	 * @param message    the message to write
	 * @param encoding   the encoding which should be used th encode the chars into byte (e.g. `US-ASCII` or `UTF-8`)
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferOverflowException if the buffer`s limit has been reached   
	 */
	public int write(String message, String encoding) throws IOException, BufferOverflowException  {
		byte[] bytes = message.getBytes(encoding);
		data.put(bytes);
		
		return bytes.length;
	}
	
	
	/**
	 * get the packet size
	 * 
	 * @return the packet size
	 */
	public int getSize() {
		return data.limit();
	}

	/**
	 * get the remaining, unwritten packet size
	 * 
	 * @return the remaining, unwritten packet size
	 */
	public int getRemaining() {
		return data.remaining();
	}

	
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		if (remoteSocketAddress != null) {
			sb.append("remoteAddress=" + remoteSocketAddress.toString() + " ");
		} else {
			sb.append("remoteAddress=null ");
		}
		
		if (data != null) {
			sb.append("data=" + DataConverter.toHexString(new ByteBuffer[] {data.duplicate()}, 500) + " ");
		} else {
			sb.append("data=null ");
		}		
		
		return sb.toString();
	}
}