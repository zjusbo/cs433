// $Id: UdpTest.java 910 2007-02-12 16:56:19Z grro $
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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.TestUtil;
import org.xsocket.datagram.NonBlockingEndpoint;




/**
*
* @author grro@xsocket.org
*/
public final class UdpTest {
	
	private int packageSize = 655;
	
	
	@Test public void testDefaultWorkerSize() throws Exception {
		
		IEndpoint serverEndpoint = new NonBlockingEndpoint(0, new EchoHandler(), packageSize, 3);

		byte[] request = TestUtil.generatedByteArray(packageSize);
		DatagramSocket clientSocket = new DatagramSocket();
		DatagramPacket requestPackage = new DatagramPacket(request, packageSize, new InetSocketAddress(serverEndpoint.getLocalAddress(), serverEndpoint.getLocalPort()));
		clientSocket.send(requestPackage);
		
		DatagramPacket responsePackage = new DatagramPacket(new byte[packageSize], packageSize);
		clientSocket.receive(responsePackage);
		byte[] response = responsePackage.getData();

		Assert.assertTrue(TestUtil.isEquals(request, response));

		serverEndpoint.close();
	}
	
	
	@Test public void testDedicatedWorkerSize() throws Exception {
		
		IEndpoint serverEndpoint = new NonBlockingEndpoint(0, new EchoHandler(), packageSize, 10);

		byte[] request = TestUtil.generatedByteArray(packageSize);
		DatagramSocket clientSocket = new DatagramSocket();
		DatagramPacket requestPackage = new DatagramPacket(request, packageSize, new InetSocketAddress(serverEndpoint.getLocalAddress(), serverEndpoint.getLocalPort()));
		clientSocket.send(requestPackage);
		
		DatagramPacket responsePackage = new DatagramPacket(new byte[packageSize], packageSize);
		clientSocket.receive(responsePackage);
		byte[] response = responsePackage.getData();

		Assert.assertTrue(TestUtil.isEquals(request, response));

		serverEndpoint.close();
	}

	
	@Test public void testClientMode() throws Exception {
		
		EchoHandler handler = new EchoHandler();
		IEndpoint serverEndpoint = new NonBlockingEndpoint(0, handler, packageSize, 3);
		
		IEndpoint clientEndpoint = new NonBlockingEndpoint();
		
		byte[] data = TestUtil.generatedByteArray(packageSize);
		
		Packet packet = new Packet(serverEndpoint.getLocalSocketAddress(), data);
		
		clientEndpoint.send(packet);

		TestUtil.sleep(100);
		
		Assert.assertTrue(TestUtil.isEquals(data, handler.lastReceived)); 
		
		clientEndpoint.close();
		serverEndpoint.close();
	}

	

	@Test public void testConnected() throws Exception {
		
		EchoHandler handler = new EchoHandler();
		IEndpoint serverEndpoint = new NonBlockingEndpoint(0, handler, packageSize, 3);
		
		IConnectedEndpoint clientEndpoint = new NonBlockingConnectedEndpoint(serverEndpoint.getLocalSocketAddress());
		
		byte[] data = TestUtil.generatedByteArray(packageSize);
		
		Packet packet = new Packet(data);
		clientEndpoint.send(packet);

		TestUtil.sleep(100);
		
		Assert.assertTrue(TestUtil.isEquals(data, handler.lastReceived)); 
		
		clientEndpoint.close();
		serverEndpoint.close();
	}
	
	
	
	@Test public void testMulticast() throws Exception {
		
		IConnectedEndpoint mcEndpoint1 = new NonBlockingMulticastEndpoint("233.128.0.195", 4433, new ConsumerHandler(), packageSize, 0);
		
		ConsumerHandler handler = new ConsumerHandler();
		IConnectedEndpoint mcEndpoint2 = new NonBlockingMulticastEndpoint("233.128.0.195", 4433, handler, packageSize, 0);
		
		
		byte[] data = TestUtil.generatedByteArray(packageSize);
		
		Packet packet = new Packet(data);
		mcEndpoint1.send(packet);

		TestUtil.sleep(500);
		
		Assert.assertTrue(TestUtil.isEquals(data, handler.lastReceived)); 
		
		mcEndpoint1.close();
		mcEndpoint2.close();
	}
	
		
	private final class EchoHandler implements IDatagramHandler {
		
		private byte[] lastReceived = null;
		
		public boolean onData(IEndpoint localEndpoint, Packet packet) throws IOException {
			lastReceived = packet.readBytes();
			
			
			Packet response = new Packet(packet.getRemoteSocketAddress(), lastReceived);
			localEndpoint.send(response);
			return true;
		}		
	}
	
	
	
	
	private final class ConsumerHandler implements IDatagramHandler {
		
		private byte[] lastReceived = null;
		
		public boolean onData(IEndpoint localEndpoint, Packet packet) throws IOException {
			lastReceived = packet.readBytes();
			
			
			return true;
		}		
	}

}
