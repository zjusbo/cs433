// $Id: DatagramReadWriteDataTest.java 1748 2007-09-17 08:19:23Z grro $
/*
 *  Copyright (c) xsocket.org, 2006. All rights reserved.
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
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;



/**
*
* @author grro@xsocket.org
*/
public final class DatagramReadWriteDataTest {

	private static final int SIZE = 600;
	private static final String DELIMITER = "f";
	
	
	
	
		
	
	@Test
	public void testDataTypes() throws Exception {		
		Handler hdl = new Handler();
		IEndpoint serverEndpoint = new Endpoint(SIZE, hdl);
		IConnectedEndpoint clientEndpoint = new ConnectedEndpoint(new InetSocketAddress("localhost", serverEndpoint.getLocalPort()));
		
		UserDatagram packet = new UserDatagram(SIZE);
		
		
		packet.write((byte) 56);
		packet.write(45.9);
		packet.write(45);
		packet.write(446456456555L);
		byte[] data = QAUtil.generateByteArray(10);
		packet.write(data);
		byte[] data2 = QAUtil.generateByteArray(17);
		ByteBuffer buffer2 = ByteBuffer.wrap(data2);
		packet.write(buffer2);
		buffer2.flip();
		packet.write(new ByteBuffer[] {buffer2});
		buffer2.flip();
		packet.write("hello datagram\r\n");
		packet.write(34);
		packet.write("\r\n");
		packet.write(4);
		packet.write("hello endpoint");
		byte[] data3 = QAUtil.generateByteArray(4);
		packet.write(data3);
		packet.write("this is the end");
		
		clientEndpoint.send(packet);
		QAUtil.sleep(500);
		
		UserDatagram serverSidePacket = hdl.lastReceived;
		
		Assert.assertTrue(serverSidePacket.readByte() == ((byte) 56));
		Assert.assertTrue(serverSidePacket.readDouble() == 45.9);
		Assert.assertTrue(serverSidePacket.readInt() == 45);
		Assert.assertTrue(serverSidePacket.readLong() == 446456456555L);
		
		Assert.assertTrue(QAUtil.isEquals(serverSidePacket.readBytesByLength(data.length), data));
		Assert.assertTrue(QAUtil.isEquals(serverSidePacket.readByteBufferByLength(data2.length), buffer2));
		Assert.assertTrue(QAUtil.isEquals(serverSidePacket.readByteBufferByLength(data2.length), buffer2));
		Assert.assertTrue(serverSidePacket.readStringByDelimiter("\r\n", Integer.MAX_VALUE).equals("hello datagram"));
		Assert.assertTrue(serverSidePacket.readInt() == 34);
		Assert.assertTrue(serverSidePacket.readStringByDelimiter("\r\n", Integer.MAX_VALUE).equals(""));
		Assert.assertTrue(serverSidePacket.readInt() == 4);
		Assert.assertTrue(serverSidePacket.readStringByLength(14).equals("hello endpoint"));
		Assert.assertTrue(QAUtil.isEquals(serverSidePacket.readBytesByLength(4), data3));
		try {
			serverSidePacket.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			Assert.fail("BufferUnderflowException should haven been occured");
		} catch (BufferUnderflowException expected) { } 
		
		clientEndpoint.close();
		serverEndpoint.close();
	}
	
	
	
	@Test 
	public void testString() throws Exception {		
		Handler hdl = new Handler();
		IEndpoint serverEndpoint = new Endpoint(SIZE, hdl);
		IEndpoint clientEndpoint = new Endpoint();
		
		String msg = "hello";
		String msg2 = "hello2";
		
		UserDatagram packet = new UserDatagram("localhost", serverEndpoint.getLocalPort(), msg.getBytes().length + msg2.getBytes().length);
		
		packet.write(msg);
		packet.write(msg2);
		
		clientEndpoint.send(packet);
		QAUtil.sleep(500);
		
		UserDatagram serverSidePacket = hdl.lastReceived;
		
		Assert.assertTrue(serverSidePacket.readStringByLength(msg.getBytes().length).equals("hello"));
		Assert.assertTrue(serverSidePacket.readString().equals(msg2));
		
		clientEndpoint.close();
		serverEndpoint.close();
	}
	
	
	
	@Test 
	public void testMaxReadSize() throws Exception {		
		Handler hdl = new Handler();
		IEndpoint serverEndpoint = new Endpoint(SIZE, hdl);
		IEndpoint clientEndpoint = new Endpoint();
		
		UserDatagram packet = new UserDatagram("localhost", serverEndpoint.getLocalPort(), SIZE);
		packet.write(QAUtil.generateByteArray(10));
		packet.write(DELIMITER);
		
		clientEndpoint.send(packet);
		QAUtil.sleep(500);
		
		UserDatagram serverSidePacket = hdl.lastReceived;
		
		try {
			serverSidePacket.readBytesByDelimiter(DELIMITER, 5);
			Assert.fail("max read size exception expected");
		} catch (MaxReadSizeExceededException expected) { }
		
		clientEndpoint.close();
		serverEndpoint.close();
	}
	
	
	
	private final class Handler implements IDatagramHandler {
		
		private UserDatagram lastReceived = null;

		public boolean onDatagram(IEndpoint localEndpoint) throws IOException {
			lastReceived = localEndpoint.receive();
			return true;
		}		
	}
}
