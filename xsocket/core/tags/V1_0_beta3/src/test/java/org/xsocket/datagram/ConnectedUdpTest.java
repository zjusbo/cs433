// $Id: ConnectedUdpTest.java 764 2007-01-15 06:26:17Z grro $
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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.logging.Logger;




import org.junit.Assert;
import org.junit.Test;
import org.xsocket.TestUtil;
import org.xsocket.datagram.IConnectedEndpoint;
import org.xsocket.datagram.IConnectedEndpointHandler;
import org.xsocket.datagram.IDisconnectedEndpoint;
import org.xsocket.datagram.IDisconnectedEndpointHandler;
import org.xsocket.datagram.LocalConnectedEndpoint;
import org.xsocket.datagram.LocalDisconnectedEndpoint;




/**
*
* @author grro@xsocket.org
*/
public final class ConnectedUdpTest {

	private Logger LOG = Logger.getLogger(ConnectedUdpTest.class.getName());
	
	
	private int acceptorPort = 6567;
	private int acceptorPackageSize = 4;
	
	private int countThreads = 1;
	private int runningThreads = 0;
		
	
	
	@Test public void testBlocking() throws Exception {
		IDisconnectedEndpoint acceptorEndpoint = new LocalDisconnectedEndpoint(acceptorPort, acceptorPackageSize, new AcceptorHandler(), 5);

		for (int i = 0; i < countThreads; i++) {
			Thread t = new Thread() {
				@Override
				public void run() {
					runningThreads++;
					clientCall(5, 600);
					runningThreads--;
				}
			};
			
			t.start();
		}
		
		
		TestUtil.sleep(500);

		
		while (runningThreads > 0) {
			try {
				Thread.sleep(2000);
			} catch (InterruptedException ignore) { }
		}
	
		acceptorEndpoint.close();
	}
	
	
	private void clientCall(int callsCount, int exhangeDatasize)  {
		try {
			DatagramSocket clientEndpoint = new DatagramSocket();
			
			ByteBuffer requestBB = ByteBuffer.allocate(acceptorPackageSize);
			requestBB.putInt(exhangeDatasize);
			byte[] requestData = requestBB.array();
			
			DatagramPacket request = new DatagramPacket(requestData, requestData.length, new InetSocketAddress("127.0.0.1", acceptorPort));
			clientEndpoint.send(request);
			
			DatagramPacket serverAccept = new DatagramPacket(new byte[acceptorPackageSize], acceptorPackageSize);
			clientEndpoint.receive(serverAccept);
			int serverHandlerPort = ByteBuffer.wrap(serverAccept.getData()).getInt();
			InetAddress serverHandlerAddress = serverAccept.getAddress();
			
			for (int i = 0; i < callsCount; i++) {
				LOG.fine("send data to " + serverHandlerPort);
				byte[] dataRequestBytes = TestUtil.generatedByteArray(exhangeDatasize);
				DatagramPacket dataRequest = new DatagramPacket(dataRequestBytes, dataRequestBytes.length, serverHandlerAddress, serverHandlerPort);
				clientEndpoint.send(dataRequest);
				
				byte[] dataResponseBytes = new byte[dataRequestBytes.length];
				DatagramPacket dataResponse = new DatagramPacket(dataResponseBytes, dataRequestBytes.length);
				clientEndpoint.receive(dataResponse);
				
				Assert.assertTrue(TestUtil.isEquals(dataResponseBytes, dataResponseBytes));
				System.out.print(".");
			}
			
			
			clientEndpoint.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	
	
	private final class AcceptorHandler implements IDisconnectedEndpointHandler {
		
		public boolean onData(IDisconnectedEndpoint endpoint, ByteBuffer data, SocketAddress remoteAddress) throws IOException {
			
			WorkerHandler handler = new WorkerHandler();
			IConnectedEndpoint workerEndpoint = new LocalConnectedEndpoint(0, 500, handler, remoteAddress);
			
			int port = workerEndpoint.getLocalPort();
			workerEndpoint.setReceivePacketSize(data.getInt());
			
			ByteBuffer buffer = ByteBuffer.allocate(acceptorPackageSize);
			buffer.putInt(port);
			buffer.clear();
			
			endpoint.send(remoteAddress, buffer);
			
			return true;
		}		
	}
	
	
	private static final class WorkerHandler implements IConnectedEndpointHandler {

		public boolean onData(IConnectedEndpoint endpoint, ByteBuffer data) throws IOException {
			endpoint.send(data);
			return false;
		}
	}
}
