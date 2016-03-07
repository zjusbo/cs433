// $Id: HandlerContextTest.java 1037 2007-03-20 06:45:01Z grro $
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
import java.net.InetAddress;


import org.junit.Assert;
import org.junit.Test;

import org.xsocket.ILifeCycle;
import org.xsocket.Resource;
import org.xsocket.QAUtil;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IConnectHandler;
import org.xsocket.stream.IConnectionScoped;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;




/**
*
* @author grro@xsocket.org
*/
public final class ServerContextTest {
	

	@Test 
	public void testSimple() throws Exception {

		ServerHandler hdl = new ServerHandler();
		IMultithreadedServer server = new MultithreadedServer(hdl);
		StreamUtils.start(server);


		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.readStringByDelimiter("o", Integer.MAX_VALUE);
		QAUtil.sleep(100);
		Assert.assertTrue(hdl.ctx.getNumberOfOpenConnections() == 1);

		IBlockingConnection connection2 = new BlockingConnection("localhost", server.getLocalPort());
		QAUtil.sleep(300);
		Assert.assertTrue(hdl.ctx.getNumberOfOpenConnections() == 2);


		connection2.close();
		connection.close();
		server.close();
	}

	
	

	
	private static final class ServerHandler implements IConnectHandler, IConnectionScoped {

		@Resource
		private IServerContext ctx;
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.write("werwrwrer o");
			return true;
		}

		@Override
		public Object clone() throws CloneNotSupportedException {
			return super.clone();
		}
	}
	
	
	
	private static final class ServerHandler2 implements IConnectHandler, ILifeCycle {

		@Resource
		private IServerContext ctx;
		
		private InetAddress localAddress = null; 
		
		public void onInit() {
			this.localAddress = ctx.getLocaleAddress();
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
