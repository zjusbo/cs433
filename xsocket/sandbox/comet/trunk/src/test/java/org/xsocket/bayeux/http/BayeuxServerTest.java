/*
 *  Copyright (c) xsocket.org, 2006 - 2008. All rights reserved.
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
package org.xsocket.bayeux.http;



import java.net.InetSocketAddress;
import java.util.logging.Level;

import org.junit.Test;
import org.mortbay.cometd.client.BayeuxClient;
import org.mortbay.thread.QueuedThreadPool;
import org.xsocket.connection.IServer;
import org.xsocket.connection.http.RequestHandlerChain;
import org.xsocket.connection.http.client.HttpClient;
import org.xsocket.connection.http.server.HttpServer;

import dojox.cometd.Bayeux;



/**
*
* @author grro@xsocket.org
*/
public final class BayeuxServerTest {

	
	@Test
	public void testSimple() throws Exception {
		
		QAUtil.setLogLevel("org.xsocket.bayeux.http.BayeuxRemoteClient", Level.FINE);

		RequestHandlerChain chain = new RequestHandlerChain();
		chain.addLast(new LogFilter());
		chain.addLast(new BayeuxHttpProtocolHandler());
		
		IServer server = new HttpServer(chain);
		server.start();

		
		 org.mortbay.jetty.client.HttpClient httpClient = new org.mortbay.jetty.client.HttpClient();
	        
		 httpClient.setConnectorType(org.mortbay.jetty.client.HttpClient.CONNECTOR_SELECT_CHANNEL);
		 httpClient.setMaxConnectionsPerAddress(20000);
	        
		 QueuedThreadPool pool = new QueuedThreadPool();
		 pool.setMaxThreads(500);
		 pool.setDaemon(true);
		 httpClient.setThreadPool(pool);
		 httpClient.start();
		
		BayeuxClient client = new BayeuxClient(httpClient, new InetSocketAddress("localhost", server.getLocalPort()), "http://localhost:" + server.getLocalPort() + "/Service");
		client.start();
		
		client.subscribe("/test");
		
		client.publish("/test", "{ \"name\" : \"hans\"}", "23");
		

		server.close();
	}
	
	
}
