// $Id: RunnableTimeServer.java 906 2007-02-12 09:37:57Z grro $
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
import java.nio.ByteBuffer;


import org.xsocket.JmxServer;
import org.xsocket.datagram.NonBlockingEndpoint;



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

		IEndpoint serverEndpoint = new NonBlockingEndpoint(port, new ServerHandler(), 10, 3);

		new JmxServer().start("TestTimeSrv");

		while (serverEndpoint.isOpen()) {
			try {
				Thread.sleep(2000);
			} catch (InterruptedException ignore) { }
		}
	}
	
	
	private final class ServerHandler implements IDatagramHandler {
		
		
		public boolean onData(IEndpoint localEndpoint, Packet packet) throws IOException {
			ByteBuffer data = ByteBuffer.allocate(8);
			data.putLong((System.currentTimeMillis() / 1000) + 2208988800L);
			data.position(4);
			ByteBuffer content = data.slice();
			 
			Packet response = new Packet(packet.getRemoteSocketAddress(), content); 
			localEndpoint.send(response);
			return true;
		}		
	}
}
