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
import java.io.Serializable;

import org.xsocket.IDataSink;
import org.xsocket.IDataSource;



/**   
 *  
 * @author grro@xsocket.org
 */
final class MulticastMessageHeader implements Serializable {

	private static final long serialVersionUID = 8476515469885702677L;
	private static final Byte MAGIC_BYTE = 102;


	private String sourceAddress = null;
	private byte cmd = 0;
	 
	public MulticastMessageHeader(String sourceAddress, byte cmd) {
		this.sourceAddress = sourceAddress;
		this.cmd = cmd;
	}
	
	private MulticastMessageHeader(IDataSource dataSource) throws IOException {
		
		byte magicByte = dataSource.readByte();
		if (magicByte != MAGIC_BYTE) {
			throw new IOException("invalid protocol exception. Invalid magic byte: " + magicByte + "(expected: " + MAGIC_BYTE + ")");
		}
	
		cmd = dataSource.readByte();

		int addressLenth = dataSource.readInt();
		sourceAddress = new String(dataSource.readBytesByLength(addressLenth), "UTF-8");
	}
	
	
	public String getSourceAddress() {
		return sourceAddress;
	}
	
	public byte getCommand() {
		return cmd;
	}

	
	public static MulticastMessageHeader readFrom(IDataSource dataSource) throws IOException {
		return new MulticastMessageHeader(dataSource);
	}
	
	
	
	public int writeTo(IDataSink dataSink) throws IOException {
		int written = 0;
		
		written += dataSink.write(MAGIC_BYTE);
		written += dataSink.write(cmd);
		
		byte[] serializedAddress = sourceAddress.getBytes("UTF-8");
		written += dataSink.write(serializedAddress.length);
		written += dataSink.write(serializedAddress);
		
		return written;
	}	
	
	
	@Override
	public String toString() {
		return ("cmd=" + cmd + ", sourceAddress="  + sourceAddress);
	}
}
