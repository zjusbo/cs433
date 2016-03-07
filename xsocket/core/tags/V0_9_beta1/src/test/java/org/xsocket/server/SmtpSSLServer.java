// $Id$
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
package org.xsocket.server;


import javax.net.ssl.SSLContext;

import org.xsocket.server.IMultithreadedServer;
import org.xsocket.server.MultithreadedSSLServer;


/**
*
* @author grro@xsocket.org
*/
public final class SmtpSSLServer {

	private IMultithreadedServer sslTestServer = null;
	
	private SSLContext sslContext = null;
	
	public static void main(String... args) throws Exception {
		new SmtpSSLServer().launch(Integer.parseInt(args[0]));
	}
	
	public void launch(int port) throws Exception {
	
		sslTestServer = new MultithreadedSSLServer(port, "SSLTestSrv", new SSLTestContextFactory());
		sslTestServer.setDispatcherSize(1);
		sslTestServer.setDispatcherWorkerSize(1);
		sslTestServer.setReceiveBufferPreallocationSize(5120);

		sslTestServer.setHandler(new SmtpProtocolHandler("SSLTestSrv", "SmtpMonitor"));
				
		Thread server = new Thread(sslTestServer);
		server.start();
		
		do {
			try {
				Thread.sleep(555555250);
			} catch (InterruptedException ignore) { }
		} while (!sslTestServer.isRunning());
	}
}
