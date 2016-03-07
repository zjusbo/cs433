// $Id: PacketSizeTest.java 905 2007-02-12 07:57:45Z grro $
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
import java.nio.BufferOverflowException;
import java.util.ArrayList;
import java.util.List;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.TestUtil;



/**
*
* @author grro@xsocket.org
*/
public final class PacketModificationTest {

	private static final int SIZE = 10;
	
	
	
	@Test public void testSizeByteArray() throws Exception {		
		Handler hdl = new Handler();
		IEndpoint serverEndpoint = new NonBlockingEndpoint(hdl, SIZE, 3);
		IConnectedEndpoint clientEndpoint = new NonBlockingConnectedEndpoint(serverEndpoint.getLocalSocketAddress());
		

		Packet packet = new Packet(SIZE);
		Assert.assertTrue(packet.getPacketSize() == SIZE);
		Assert.assertTrue(packet.getRemoteAddress() == null);
		Assert.assertTrue(packet.getRemoteSocketAddress() == null);
		
		packet.write((int) 4); 
		packet.write((int) 5); 
		
		
		
		try {
			packet.write((int) 7);
			Assert.fail("BufferOverflow should have been occured");
		} catch (BufferOverflowException shouldOccur) { }
		
		
		clientEndpoint.send(packet);
		
		TestUtil.sleep(500);
		
		Assert.assertTrue(hdl.received.get(0).length == SIZE);
		
		clientEndpoint.close();
		serverEndpoint.close();
	}
	

	@Test public void testSizeByteArray2() throws Exception {		
		Handler hdl = new Handler();
		IEndpoint serverEndpoint = new NonBlockingEndpoint(hdl, SIZE, 3);
		IEndpoint clientEndpoint = new NonBlockingEndpoint();

		
		Packet packet = new Packet(serverEndpoint.getLocalSocketAddress(), SIZE);
		Assert.assertTrue(packet.getPacketSize() == SIZE);
		Assert.assertTrue(packet.getRemoteAddress().getHostName().equals(serverEndpoint.getLocalAddress().getHostName()));
		Assert.assertTrue(packet.getRemoteSocketAddress().equals(serverEndpoint.getLocalSocketAddress()));
		
		packet.write((int) 4); 
		packet.write((int) 5); 
		
		try {
			packet.write((int) 7);
			Assert.fail("BufferOverflow should have been occured");
		} catch (BufferOverflowException shouldOccur) { }
		
		
		clientEndpoint.send(packet);
		
		TestUtil.sleep(500);
		
		Assert.assertTrue(hdl.received.get(0).length == SIZE);
		
		clientEndpoint.close();
		serverEndpoint.close();
	}
	
	
	
	@Test public void testSizeByteArray3() throws Exception {		
		Handler hdl = new Handler();
		IEndpoint serverEndpoint = new NonBlockingEndpoint(hdl, SIZE, 3);
		IEndpoint clientEndpoint = new NonBlockingEndpoint();

		
		Packet packet = new Packet(serverEndpoint.getLocalAddress().getHostName(), serverEndpoint.getLocalPort(), SIZE);
		Assert.assertTrue(packet.getPacketSize() == SIZE);
		Assert.assertTrue(packet.getRemoteAddress().getHostName().equals(serverEndpoint.getLocalAddress().getHostName()));
		Assert.assertTrue(packet.getRemoteSocketAddress().equals(serverEndpoint.getLocalSocketAddress()));
		
		packet.write((int) 4); 
		packet.write((int) 5); 
		
		try {
			packet.write((int) 7);
			Assert.fail("BufferOverflow should have been occured");
		} catch (BufferOverflowException shouldOccur) { }
		
		
		clientEndpoint.send(packet);
		
		TestUtil.sleep(500);
		
		Assert.assertTrue(hdl.received.get(0).length == SIZE);
		
		clientEndpoint.close();
		serverEndpoint.close();
	}
	
	
	@Test public void testSizeByteArray4() throws Exception {		
		Handler hdl = new Handler();
		IEndpoint serverEndpoint = new NonBlockingEndpoint(hdl, SIZE, 3);
		IEndpoint clientEndpoint = new NonBlockingEndpoint();

		byte[] data = TestUtil.generatedByteArray(SIZE);
		Packet packet = new Packet(serverEndpoint.getLocalAddress().getHostName(), serverEndpoint.getLocalPort(), data);
		Assert.assertTrue(packet.getPacketSize() == SIZE);
		Assert.assertTrue(packet.getRemoteAddress().getHostName().equals(serverEndpoint.getLocalAddress().getHostName()));
		Assert.assertTrue(packet.getRemoteSocketAddress().equals(serverEndpoint.getLocalSocketAddress()));
		
		try {
			packet.write((int) 7);
			Assert.fail("BufferOverflow should have been occured");
		} catch (BufferOverflowException shouldOccur) { }
		
		
		clientEndpoint.send(packet);
		
		TestUtil.sleep(500);
		
		Assert.assertTrue(hdl.received.get(0).length == SIZE);
		
		clientEndpoint.close();
		serverEndpoint.close();
	}
	
	
	
	private final class Handler implements IDatagramHandler {
		
		private List<byte[]> received = new ArrayList<byte[]>();
		
		public boolean onData(IEndpoint localEndpoint, Packet packet) throws IOException {
			
			received.add(packet.readBytes());
			
			return true;
		}		
	}
}
