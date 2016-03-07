// $Id: EndpointTest.java 1049 2007-03-21 16:42:48Z grro $
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
import java.net.SocketOptions;


import org.junit.Assert;
import org.junit.Test;





/**
*
* @author grro@xsocket.org
*/
public final class SocketOptionsTest {
	
	private int packageSize = 655;
	
	
	@Test 
	public void testNonConnectedEndpoint() throws Exception {
		DatagramSocketConfiguration socketConf = new DatagramSocketConfiguration();
		socketConf.setSO_SNDBUF(899);
		socketConf.setSO_RCVBUF(988);
		socketConf.setSO_REUSEADDR(true);
		
		Endpoint serverEndpoint = new Endpoint(socketConf, packageSize, new EchoHandler());
		Assert.assertTrue(serverEndpoint.getSocketOptions().getOption(SocketOptions.SO_REUSEADDR).equals(true));
	
 
		serverEndpoint.close();
	}
	

	@Test 
	public void testConnectedEndpoint() throws Exception {
		
		IEndpoint serverEndpoint = new Endpoint(packageSize, new EchoHandler());
		
		DatagramSocketConfiguration socketConf = new DatagramSocketConfiguration();
		socketConf.setSO_SNDBUF(899);
		socketConf.setSO_RCVBUF(988);
		socketConf.setSO_REUSEADDR(true);
		
		
		
		ConnectedEndpoint clientEndpoint = new ConnectedEndpoint(serverEndpoint.getLocalSocketAddress(), socketConf, packageSize, new EchoHandler());
		Assert.assertTrue(clientEndpoint.getSocketOptions().getOption(SocketOptions.SO_REUSEADDR).equals(true));
	
 
		clientEndpoint.close();
		serverEndpoint.close();
	}
	

	@Test 
	public void testMulticastEndpoint() throws Exception {
		DatagramSocketConfiguration socketConf = new DatagramSocketConfiguration();
		socketConf.setSO_SNDBUF(899);
		socketConf.setSO_RCVBUF(988);
		socketConf.setSO_REUSEADDR(true);
		
		MulticastEndpoint serverEndpoint = new MulticastEndpoint("233.128.0.95", 9988, socketConf, packageSize, new EchoHandler());
		Assert.assertTrue(serverEndpoint.getSocketOptions().getOption(SocketOptions.SO_REUSEADDR).equals(true));
	
 
		serverEndpoint.close();
	}

	
	private final class EchoHandler implements IDatagramHandler {
		
		public boolean onDatagram(IEndpoint localEndpoint) throws IOException {
			UserDatagram datagram = localEndpoint.receive();
			byte[] data = datagram.readBytes();
			
			
			UserDatagram response = new UserDatagram(datagram.getRemoteSocketAddress(), data);
			localEndpoint.send(response);
			return true;
		}		
	}
}
