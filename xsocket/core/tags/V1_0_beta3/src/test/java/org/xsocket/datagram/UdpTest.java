// $Id: UdpTest.java 764 2007-01-15 06:26:17Z grro $
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
import java.net.SocketAddress;
import java.nio.ByteBuffer;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.DataConverter;
import org.xsocket.TestUtil;
import org.xsocket.datagram.IDisconnectedEndpoint;
import org.xsocket.datagram.IDisconnectedEndpointHandler;
import org.xsocket.datagram.LocalDisconnectedEndpoint;




/**
*
* @author grro@xsocket.org
*/
public final class UdpTest {
	
	private int port = 7639;
	private int packageSize = 655;
	
	
	@Test public void testDefaultWorkerSize() throws Exception {
		
		IDisconnectedEndpoint serverEndpoint = new LocalDisconnectedEndpoint(port, packageSize, new EchoHandler());

		byte[] request = TestUtil.generatedByteArray(packageSize);
		DatagramSocket clientSocket = new DatagramSocket();
		DatagramPacket requestPackage = new DatagramPacket(request, packageSize, new InetSocketAddress("127.0.0.1", port));
		clientSocket.send(requestPackage);
		
		DatagramPacket responsePackage = new DatagramPacket(new byte[packageSize], packageSize);
		clientSocket.receive(responsePackage);
		byte[] response = responsePackage.getData();

		Assert.assertTrue(TestUtil.isEquals(request, response));

		serverEndpoint.close();
	}
	
	
	@Test public void testDedicatedWorkerSize() throws Exception {
		
		IDisconnectedEndpoint serverEndpoint = new LocalDisconnectedEndpoint(port + 1, packageSize, new EchoHandler(), 10);

		byte[] request = TestUtil.generatedByteArray(packageSize);
		DatagramSocket clientSocket = new DatagramSocket();
		DatagramPacket requestPackage = new DatagramPacket(request, packageSize, new InetSocketAddress("127.0.0.1", port + 1));
		clientSocket.send(requestPackage);
		
		DatagramPacket responsePackage = new DatagramPacket(new byte[packageSize], packageSize);
		clientSocket.receive(responsePackage);
		byte[] response = responsePackage.getData();

		Assert.assertTrue(TestUtil.isEquals(request, response));

		serverEndpoint.close();
	}

	
	@Test public void testClientMode() throws Exception {
		
		EchoHandler handler = new EchoHandler();
		IDisconnectedEndpoint serverEndpoint = new LocalDisconnectedEndpoint(port, packageSize, handler);
		
		IDisconnectedEndpoint clientEndpoint = new LocalDisconnectedEndpoint();
		
		byte[] data = TestUtil.generatedByteArray(packageSize);
		clientEndpoint.send(new InetSocketAddress("127.0.0.1", serverEndpoint.getLocalPort()), ByteBuffer.wrap(data));

		TestUtil.sleep(100);
		
		Assert.assertTrue(TestUtil.isEquals(data, handler.lastReceived)); 
		
		serverEndpoint.close();
	}

	
	
	private final class EchoHandler implements IDisconnectedEndpointHandler {
		
		private byte[] lastReceived = null;
		
		public boolean onData(IDisconnectedEndpoint endpoint, ByteBuffer data, SocketAddress remoteAddress) throws IOException {
			lastReceived = DataConverter.toArray(data);
			
			endpoint.send(remoteAddress, data);
			return true;
		}		
	}
}
