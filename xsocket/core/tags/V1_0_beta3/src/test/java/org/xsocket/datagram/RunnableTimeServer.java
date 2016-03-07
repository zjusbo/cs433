// $Id: RunnableTimeServer.java 765 2007-01-15 07:13:48Z grro $
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
import java.net.SocketAddress;
import java.nio.ByteBuffer;



import org.xsocket.JmxServer;
import org.xsocket.datagram.IDisconnectedEndpoint;
import org.xsocket.datagram.IDisconnectedEndpointHandler;
import org.xsocket.datagram.LocalDisconnectedEndpoint;



/**
*
* @author grro@xsocket.org
*/
public final class RunnableTimeServer {
	
	public static void main(String... args) throws IOException {
		if (args.length != 1) {
			System.err.println("usage java org.xsocket.server.RunnableTimeServer <port>");
			return;
		}
		new RunnableTimeServer().launch(Integer.parseInt(args[0]));
	}
	
	
	public void launch(int port) throws IOException {

		IDisconnectedEndpoint serverEndpoint = new LocalDisconnectedEndpoint(port, 8, new ServerHandler(), 10);

		new JmxServer().start("TestTimeSrv");

		while (serverEndpoint.isOpen()) {
			try {
				Thread.sleep(2000);
			} catch (InterruptedException ignore) { }
		}
	}
	
	
	private final class ServerHandler implements IDisconnectedEndpointHandler {
		
		
		public boolean onData(IDisconnectedEndpoint endpoint, ByteBuffer data, SocketAddress remoteAddress) throws IOException {
			data.putLong((System.currentTimeMillis() / 1000) + 2208988800L);
			data.position(4);
			ByteBuffer response = data.slice();
			 
			endpoint.send(remoteAddress, response);
			return true;
		}		
	}
}
