/*
 *  Copyright (c) xsocket.org, 2006 - 2008. All rights reserved.
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
import java.net.InetAddress;
import java.nio.ByteBuffer;



/**
 * the global unique id 
 *  
 * @author grro@xsocket.org
 */
final class GUID {

	private String hostAddress = null;               // unique
	private Integer port = 0;                        // ...

	private String time = null;                      // ...over time
		 
	
	public GUID(InetAddress address, Integer port) {
		hostAddress = address.getHostAddress();
		this.port = port;
		
		long l = System.currentTimeMillis();
		ByteBuffer buffer = ByteBuffer.allocate(8);
		buffer.putLong(l);
		buffer.position(4);
		buffer.limit(8);
		int i = buffer.getInt();
		
		this.time = Integer.toHexString(i);
	}
	

	public GUID(String serialized) throws IOException {
		String[] parts = serialized.split("_");
		hostAddress = parts[0];
		port = Integer.parseInt(parts[1]);
		time = parts[2];
	}
	
	
	
	public InetAddress getInetAddress() throws IOException {
		return InetAddress.getByName(hostAddress);
	}
	
	public int getPort() {
		return port;
	}

	
	@Override
	public String toString() {
		return hostAddress + "_" + port + "_" + time;
	}
	
	
	@Override
	public int hashCode() {
		return  hostAddress.hashCode() ^ port.hashCode() ^ time.hashCode();
	}
	
	
	@Override
	public boolean equals(Object other) {
		if (!(other instanceof GUID)) {
			return false;
		}
		
		GUID otherGroupMemberInfo = (GUID) other;
		if (!otherGroupMemberInfo.port.equals(this.port)) {
			return false;
		}
		
		if (!otherGroupMemberInfo.time.equals(this.time)) {
			return false;
		} 	

		if (!otherGroupMemberInfo.hostAddress.equals(this.hostAddress)) {
			return false;
		} 	

		
		return true;
	}
}