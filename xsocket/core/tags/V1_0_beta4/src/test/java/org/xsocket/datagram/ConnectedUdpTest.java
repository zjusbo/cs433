// $Id: ConnectedUdpTest.java 919 2007-02-13 12:34:01Z grro $
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
import java.nio.ByteBuffer;
import java.util.logging.Logger;




import org.junit.Assert;
import org.junit.Test;
import org.xsocket.TestUtil;
import org.xsocket.datagram.IConnectedEndpoint;
import org.xsocket.datagram.NonBlockingConnectedEndpoint;
import org.xsocket.datagram.NonBlockingEndpoint;




/**
*
* @author grro@xsocket.org
*/
public final class ConnectedUdpTest {

	private Logger LOG = Logger.getLogger(ConnectedUdpTest.class.getName());

	private static final int ACCEPTOR_PAKET_SIZE = 4;
	
	private int countThreads = 3;
	private int runningThreads = 0;
	
	private Exception error = null;
		
	
	
	@Test public void testBlocking() throws Exception {
		final IEndpoint acceptorEndpoint = new NonBlockingEndpoint(new AcceptorHandler(), ACCEPTOR_PAKET_SIZE, 5);

		for (int i = 0; i < countThreads; i++) {
			Thread t = new Thread() {
				@Override
				public void run() {
					runningThreads++;
					try {
						clientCall(10, 600, acceptorEndpoint.getLocalAddress(), acceptorEndpoint.getLocalPort());
					} catch (Exception e) {
						error = e;
					}
					runningThreads--;
				}
			};
			
			t.start();
		}
		
		
	
		while (runningThreads > 0) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException ignore) { }
		}
	
		if (error != null) {
			Assert.fail("error occured: " + error.toString());
		}
		
		acceptorEndpoint.close();
	}
	
	
	private void clientCall(int callsCount, int exhangeDatasize, InetAddress acceptorAddress, int acceptorPort) throws Exception {
		DatagramSocket clientEndpoint = new DatagramSocket();
			
		ByteBuffer requestBB = ByteBuffer.allocate(ACCEPTOR_PAKET_SIZE);
		requestBB.putInt(exhangeDatasize);
		byte[] requestData = requestBB.array();
			
		DatagramPacket request = new DatagramPacket(requestData, requestData.length, new InetSocketAddress(acceptorAddress, acceptorPort));
		clientEndpoint.send(request);
			
		DatagramPacket serverAccept = new DatagramPacket(new byte[ACCEPTOR_PAKET_SIZE], ACCEPTOR_PAKET_SIZE);
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
		}
			
			
		clientEndpoint.close();
	}
	
	
	
	private final class AcceptorHandler implements IDatagramHandler {
		
		public boolean onData(IEndpoint localEndpoint, Packet packet) throws IOException {
			int exhangeDataSize = packet.readInt();
			
			WorkerHandler handler = new WorkerHandler();
			IConnectedEndpoint workerEndpoint = new NonBlockingConnectedEndpoint(packet.getRemoteSocketAddress(), handler, exhangeDataSize, 0);
			
			int port = workerEndpoint.getLocalPort();
			
			ByteBuffer buffer = ByteBuffer.allocate(ACCEPTOR_PAKET_SIZE);
			buffer.putInt(port);
			buffer.clear();
			
			Packet response = new Packet(packet.getRemoteSocketAddress(), buffer);
			localEndpoint.send(response);
		
			return true;
		}		
	}
	
	
	private static final class WorkerHandler implements IDatagramHandler {

		public boolean onData(IEndpoint localEndpoint, Packet packet) throws IOException {
			int exchangeDataSize = packet.getPacketSize();
			Packet response = new Packet(exchangeDataSize);
			localEndpoint.send(response);
			return true;
		}
	}
}
