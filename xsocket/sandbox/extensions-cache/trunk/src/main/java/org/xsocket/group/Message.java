//$Id: AbstractChannelBasedEndpoint.java 1049 2007-03-21 16:42:48Z grro $
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
package org.xsocket.group;



import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

import org.xsocket.IDataSink;
import org.xsocket.IDataSource;



/**
 *   
 *  
 * @author grro@xsocket.org
 */
public abstract class Message {
	
	private static int nextId = 0;
	
	private int msgId = 0;
	
	private String classname = null;
	private ByteBuffer[] data = null;
	
	private Address sourceAddress = null;
	private int sourceAddressSuffix = 0;  
	private final Set<Address> destinationAddresses = new HashSet<Address>();
	
	
	protected Message(Address sourceAddress, int sourceAddressSuffix, String classname, ByteBuffer[] data) {
		msgId = getNextId();
		this.sourceAddress = sourceAddress;
		this.sourceAddressSuffix = sourceAddressSuffix;
		this.classname = classname;
		this.data = data;
	}

	protected Message() {

	}
	
	public final void addDestinationAddress(Address destination) {
		destinationAddresses.add(destination);
	}

	Set<Address> getDestinationAddresses() {
		return destinationAddresses;
	}
	
	public long getId() {
		return msgId;
	}
	
	public Address getSourceAddress() {
		return sourceAddress;
	}
	
	int getSourceAddressSuffix() {
		return sourceAddressSuffix;
	}
	
	final ByteBuffer[] getData() {
		return data;
	}	
	
	final String getClassname() {
		return classname;
	}
		
	synchronized int writeTo(IDataSink dataSink) throws IOException {
		int written = 0;
		
		// write the msg classname
		byte[] classnameBytes = classname.getBytes("UTF-8");
		written += dataSink.write(classnameBytes.length);
		written += dataSink.write(classnameBytes);

		// write the message id
		written += dataSink.write(msgId);
		
		// write the source address + suffic
		written += sourceAddress.writeTo(dataSink);
		written += dataSink.write(sourceAddressSuffix);
		
		// write destination addresses
		written += dataSink.write(destinationAddresses.size());
		for (Address destinationAddress : destinationAddresses) {
			written += destinationAddress.writeTo(dataSink);
		}

		// write the data
		int dataLength = 0; 
		ByteBuffer[] dataToSend = new ByteBuffer[data.length];
		for (int i = 0; i < data.length; i++) {
			dataToSend[i] = data[i].duplicate();
			dataLength += dataToSend[i].remaining();
		}
		written += dataSink.write(dataLength);
		written += dataSink.write(dataToSend);
		
		return written;
	}
	
	
	static Message readFrom(IDataSource dataSource) throws IOException {
		Message message = null;
		
		// read the classname
		int length = dataSource.readInt();
		String classname = dataSource.readStringByLength(length, "UTF-8");
		try {
			Class clazz = Class.forName(classname);
			message = (Message) clazz.newInstance();
			message.classname = classname;
		} catch (Exception e) {
			throw new IOException("error occured by instantiating the received message type. reason " + e.toString());
		}
		
		// read the message id
		message.msgId = dataSource.readInt();
		
		// read the sourceAddress
		message.sourceAddress = Address.readFrom(dataSource);
		message.sourceAddressSuffix = dataSource.readInt();

		
		// read destination addresses
		int count = dataSource.readInt();
		for (int i = 0; i < count; i++) {
			message.destinationAddresses.add(Address.readFrom(dataSource));
		}
		
		// read the data
		int dataLength = dataSource.readInt();
		message.data = new ByteBuffer[] { ByteBuffer.wrap(dataSource.readBytesByLength(dataLength)) };

		
		return message;
	}
	
	private static synchronized int getNextId() {
		nextId++;
		if (nextId < 0) {
			nextId = 1;
		}
		
		return nextId;
	}
}
