// $Id: RunnableSSLEchoServer.java 829 2007-01-24 16:03:50Z grro $
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
package org.xsocket.stream;

import java.io.IOException;

import javax.net.ssl.SSLContext;

import org.xsocket.JmxServer;
import org.xsocket.SSLTestContextFactory;
import org.xsocket.stream.EchoHandler;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.MultithreadedServer;



/**
*
* @author grro@xsocket.org
*/
public final class RunnableSSLEchoServer {

	
	public static void main(String... args) throws IOException {
		if (args.length != 1) {
			System.err.println("usage java org.xsocket.server.RunnableEchoServer <port>");
			return;
		}
		new RunnableSSLEchoServer().launch(Integer.parseInt(args[0]));
		
	}
	
	public void launch(int port) throws IOException {
		SSLContext sslCtx = new SSLTestContextFactory().getSSLContext();
		System.out.println("got ssl context for " + sslCtx.getProtocol());
		IMultithreadedServer server = new MultithreadedServer(port, new EchoHandler(), "SSLEchoServer", true, sslCtx);
		
		server.setReceiveBufferPreallocationSize(65536);
		server.setIdleTimeoutSec(10 * 60);
			
		Thread t = new Thread(server);
		t.start();

		System.out.println("EchoServer started on " + port);

		new JmxServer().start("TestSrv");
		
		while (true) {
			try {
				Thread.sleep(2000);
			} catch (InterruptedException ignore) { }
		}
	}
}
