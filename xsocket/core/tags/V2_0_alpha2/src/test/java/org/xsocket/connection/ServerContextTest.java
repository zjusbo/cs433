/*
 *  Copyright (c) xsocket.org, 2006-2008. All rights reserved.
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
package org.xsocket.connection;

import java.io.IOException;
import java.net.InetAddress;
import java.util.logging.Level;



import org.junit.Assert;
import org.junit.Test;

import org.xsocket.ILifeCycle;
import org.xsocket.QAUtil;
import org.xsocket.Resource;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;




/**
*
* @author grro@xsocket.org
*/
public final class ServerContextTest {


	@Test
	public void testSimple() throws Exception {

		ServerHandler hdl = new ServerHandler();
		IServer server = new Server(0, hdl);
		ConnectionUtils.start(server);


		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.readStringByDelimiter("o", Integer.MAX_VALUE);
		QAUtil.sleep(100);
		Assert.assertTrue(hdl.srv.getNumberOfOpenConnections() == 1);

		IBlockingConnection connection2 = new BlockingConnection("localhost", server.getLocalPort());
		QAUtil.sleep(300);
		Assert.assertTrue(hdl.srv.getNumberOfOpenConnections() == 2);


		connection2.close();
		connection.close();
		server.close();
	}




	@Test
	public void testSimple2() throws Exception {

		ServerHandler2 hdl2 = new ServerHandler2();
		IServer server = new Server(hdl2);
		ConnectionUtils.start(server);
		
		QAUtil.sleep(500);

		Assert.assertNotNull(hdl2.getLocalAddress());

		server.close();
	}



	@Test
	public void testSimple3() throws Exception {

		ServerHandler3 hdl3 = new ServerHandler3();
		IServer server = new Server(hdl3);
		ConnectionUtils.start(server);
		
		QAUtil.sleep(500);

		Assert.assertNotNull(hdl3.getLocalAddress());

		server.close();
	}



	private static final class ServerHandler implements IConnectHandler {

		@Resource
		private IServer srv;

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.write("werwrwrer o");
			return true;
		}
	}



	private static final class ServerHandler2 implements IConnectHandler, ILifeCycle {

		@Resource
		private IServer srv;

		private InetAddress localAddress = null;

		public void onInit() {
			this.localAddress = srv.getLocalAddress();
		}

		public void onDestroy() {

		}

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.write("werwrwrer o");
			return true;
		}

		InetAddress getLocalAddress(){
			return localAddress;
		}
	}



	private static final class ServerHandler3 implements IConnectHandler, ILifeCycle {

		@Resource(type=IServer.class)
		private Object srv;

		private InetAddress localAddress = null;

		public void onInit() {
			this.localAddress = ((IServer) srv).getLocalAddress();
		}

		public void onDestroy() {

		}

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.write("werwrwrer o");
			return true;
		}

		InetAddress getLocalAddress(){
			return localAddress;
		}
	}
}
