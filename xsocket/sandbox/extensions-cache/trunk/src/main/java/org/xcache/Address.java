// $Id: DataConverter.java 1546 2007-07-23 06:07:56Z grro $

/*
 *  Copyright (c) xcache.org, 2007. All rights reserved.
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
 * The latest copy of this software may be found on http://www.xcache.org/
 */
package org.xcache;


import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import org.xsocket.IDataSink;
import org.xsocket.IDataSource;



/**
 *   
 *  
 * @author grro@xsocket.org
 */
final class Address {

	private InetAddress address = null;
	private int port = 0;
	
	 
	public Address(InetAddress address, int port) {
		this.address = address;
		this.port = port;
	}
	

	private Address(IDataSource dataSource) throws IOException {
		int length = dataSource.readInt();
		byte[] bytes = dataSource.readBytesByLength(length);
		deserialize(ByteBuffer.wrap(bytes));
	}
	
	
	
	private void deserialize(ByteBuffer buffer) throws UnknownHostException {
		int packetSize = buffer.getInt();
		
		port = buffer.getInt();
		
		byte[] addressBytes = new byte[packetSize - 4 - 4];
		buffer.get(addressBytes, 0, addressBytes.length);
		address = InetAddress.getByAddress(addressBytes);
	}

	
	private ByteBuffer serialize() {
		int contentSize = 0;

		byte[] addressBytes = address.getAddress();
		if (addressBytes.length == 4) {
			contentSize = 4 + 4 + 4;
		} else {
			contentSize = 4 + 4 + 16;
		}
		
		ByteBuffer buffer = ByteBuffer.allocate(contentSize);
		serialize(buffer, contentSize, addressBytes);
		
		buffer.clear();
		return buffer;
	}
	
	
	private void serialize(ByteBuffer buffer, int contentSize, byte[] addressBytes) {		
		buffer.putInt(contentSize);
		buffer.putInt(port);
		buffer.put(addressBytes);
	}
	
	
	
	public InetAddress getAddress() {
		return address;
	}

	public int getPort() {
		return port;
	}
	
	InetSocketAddress toSocketAddress() {
		return new InetSocketAddress(address, port);
	}
	
	
	static Address readFrom(IDataSource dataSource) throws IOException {
		return new Address(dataSource);
	}
	
	int writeTo(IDataSink dataSink) throws IOException {
		ByteBuffer buffer = serialize();
		
		int written = dataSink.write(buffer.remaining());
		written += dataSink.write(buffer);
		
		return written;
	}
	
	
	@Override
	public String toString() {
		return "/" + address + ":" + port;
	}
	
	
	@Override
	public int hashCode() {
		return address.hashCode() ^ new Integer(port).hashCode();
	}
	
	
	@Override
	public boolean equals(Object other) {
		if (other instanceof Address) {
			Address nodeAddress = (Address) other;
			if (nodeAddress.getPort() == this.getPort()) {
				return nodeAddress.address.equals(this.address);
			}
		} 
		return false;
	}
}