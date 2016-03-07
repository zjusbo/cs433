// $Id: Packet.java 910 2007-02-12 16:56:19Z grro $
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




/**
 * a datagram packet 
 * 
 * 
 * @author grro
 */
public final class Packet implements IDataSource, IDataSink {
	
	private SocketAddress remoteSocketAddress = null;
	private ByteBuffer data = null;
	private String defaultEncoding = "UTF-8";
	
	private int packetSize = 0;
	
	
	
	/**
	 * constructor 
	 * 
	 * @param size         the packet size
	 */
	public Packet(int size) {
		init(remoteSocketAddress, ByteBuffer.allocate(size), size);
	}
	
	
	/**
	 * constructor 
	 * 
	 * @param remoteHost   the destination hostname
	 * @param remotePort   the destination port number
	 * @param size         the packet size
	 */
	public Packet(String remoteHost, int remotePort, int size) {
		init(new InetSocketAddress(remoteHost, remotePort), ByteBuffer.allocate(size), size);
	}
	
	
	/**
	 * constructor
	 * 
	 * @param address      the destination address
	 * @param size         the packet size
	 */
	public Packet(SocketAddress address, int size) {
		init(address, ByteBuffer.allocate(size), size);
	}
	
	
	/**
	 * constructor
	 * 
	 * @param data    the data which will be written into the buffer
	 */
	public Packet(ByteBuffer data) {
		this(null, data);
	}
	

	/**
	 * constructor
	 * 
	 * @param remoteSocketAddress  the destination address
	 * @param data                 the data which will be written into the buffer
	 */
	public Packet(SocketAddress remoteSocketAddress, ByteBuffer data) {
		this(remoteSocketAddress, data, "UTF-8");
	}

	
	/**
	 * constructor
	 * 
	 * @param remoteSocketAddress  the destination address
	 * @param data                 the data which will be written into the buffer
	 * @param defaultEncoding      the default encoding to use
	 */
	Packet(SocketAddress remoteSocketAddress, ByteBuffer data, String defaultEncoding) {
		init(remoteSocketAddress, data.duplicate(), data.limit());
		this.defaultEncoding = defaultEncoding;
	}

	
	/**
	 * constructor
	 * 
	 * @param data                 the data which will be written into the buffer
	 */
	public Packet(byte[] data) {
		this(null, data);
	}
		
	
	/**
	 * constructor
	 * 
	 * @param remoteHost   the destination hostname
	 * @param remotePort   the destination port number
	 * @param data         the data which will be written into the buffer
	 */
	public Packet(String remoteHost, int remotePort, byte[] data) {
		this(new InetSocketAddress(remoteHost, remotePort), data);
	}
	
	
	/**
	 * constructor
	 * 
	 * @param remoteSocketAddress  the destination address
	 * @param data                 the data which will be written into the buffer
	 */
	public Packet(SocketAddress remoteSocketAddress, byte[] data) {
		ByteBuffer buffer = ByteBuffer.wrap(data);
		buffer.position(buffer.limit());
		init(remoteSocketAddress, buffer, data.length);
	}
	
	
	
	private void init(SocketAddress remoteSocketAddress, ByteBuffer data, int packetSize) {
		this.remoteSocketAddress =remoteSocketAddress;
		this.data = data;
		this.packetSize = packetSize;

	}
	
	
	/**
	 * prepares the packet to send
	 *
	 */
	void prepareforSend() {
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
 	 * @throws BufferUnderflowException if the buffer's limit has been reached  
	 * @throws IOException If some other I/O error occurs
	 */	
	public byte readByte() throws IOException, ClosedConnectionException {
		return data.get();
	}
	
	

	
	/**
	 * read a ByteBuffer by using a length defintion 
     *
	 * @param length the amount of bytes to read
	 * @return the ByteBuffer
 	 * @throws BufferUnderflowException if the buffer's limit has been reached  
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer readByteBufferByLength(int length) throws IOException, ClosedConnectionException {
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
 	 * @throws BufferUnderflowException if the buffer's limit has been reached  
	 * @throws IOException If some other I/O error occurs
	 */		
	public byte[] readBytesByLength(int length) throws IOException, ClosedConnectionException {
		return DataConverter.toBytes(readByteBufferByLength(length));
	}

	
	
	
	/**
	 * read a string by using a length definition 
	 * 
	 * @param length the amount of bytes to read  
	 * @param encoding the encoding to use
	 * @return the string
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if the buffer's limit has been reached  
 	 * @throws UnsupportedEncodingException if the given encoding is not supported 
	 */
	public String readStringByLength(int length, String encoding) throws IOException, ClosedConnectionException, UnsupportedEncodingException {
		return DataConverter.toString(readByteBufferByLength(length), encoding);
	}

	
	
	/**
	 * read a string by using a length definition and the connection default encoding
	 * 
	 * @param length the amount of bytes to read  
	 * @return the string
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if the buffer's limit has been reached  
 	 * @throws UnsupportedEncodingException if the given encoding is not supported 
	 */
	public String readStringByLength(int length) throws IOException, ClosedConnectionException, UnsupportedEncodingException {
		return DataConverter.toString(readByteBufferByLength(length), defaultEncoding);
	}
	

	
	/**
	 * read a double
	 * 
	 * @return the double value
 	 * @throws BufferUnderflowException if the buffer's limit has been reached  
	 * @throws IOException If some other I/O error occurs
	 */
	public double readDouble() throws IOException, ClosedConnectionException {
		return data.getDouble();
	}
	
	
	
	
	/**
	 * read an int
	 * 
	 * @return the int value
 	 * @throws BufferUnderflowException if the buffer's limit has been reached  
	 * @throws IOException If some other I/O error occurs
	 */
	public int readInt() throws IOException, ClosedConnectionException {
		return data.getInt();
	}
	
	
	/**
	 * read a long
	 * 
	 * @return the long value
 	 * @throws BufferUnderflowException if the buffer's limit has been reached  
	 * @throws IOException If some other I/O error occurs
	 */
	public long readLong() throws IOException, ClosedConnectionException {
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
	 * writes a byte to the packet
	 *  
	 * @param b   the byte to write
	 * @return the number of send bytes 
 	 * @throws BufferOverflowException if the buffer's limit has been reached  
	 * @throws IOException If some other I/O error occurs
	 */
	public int write(byte b) throws IOException, BufferOverflowException {
		data.put(b);
		return 1;
	}
	
	
	/**
	 * writes bytes to the packet
	 *  
	 * @param bytes   the bytes to write
	 * @return the number of send bytes
 	 * @throws BufferOverflowException if the buffer's limit has been reached   
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
	 * @param offset   The offset of the subarray to be used; must be non-negative and no larger than array.length. The new buffer's position will be set to this value.
	 * @param length   The length of the subarray to be used; must be non-negative and no larger than array.length - offset. The new buffer's limit will be set to offset + length.
	 * @return the number of send bytes 
 	 * @throws BufferOverflowException if the buffer's limit has been reached  
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
 	 * @throws BufferOverflowException if the buffer's limit has been reached  
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
 	 * @throws BufferOverflowException if the buffer's limit has been reached  
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
 	 * @throws BufferOverflowException if the buffer's limit has been reached  
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
 	 * @throws BufferOverflowException if the buffer's limit has been reached  
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
 	 * @throws BufferOverflowException if the buffer's limit has been reached  
	 * @throws IOException If some other I/O error occurs
	 */
	public int write(long l) throws IOException, BufferOverflowException {
		data.putLong(l);
		return 8;
	}
	
	
	/**
	 * get the packet size
	 * 
	 * @return the packet size
	 */
	public int getPacketSize() {
		return packetSize;
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