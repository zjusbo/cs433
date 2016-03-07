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
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;


import org.junit.Assert;
import org.junit.Test;

import org.xsocket.Execution;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.IDisconnectHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;



/**
*
* @author grro@xsocket.org
*/
public final class NoWorkerPoolTest  {

	private static final String MAIN_THREAD_NAME = "myMainThread";


	@Test 
	public void testUnlimited() throws Exception {
		Handler hdl = new Handler(); 
		IServer server = new Server(hdl);
		ConnectionUtils.start(server);

		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);
		
		connection.write("test");
		connection.flush();
		
		
		connection.close();
		server.close();
		
		Assert.assertTrue(hdl.errors.size() == 0);
	}

	
	@Execution(Execution.Mode.NONTHREADED)
	private static final class Handler implements IConnectHandler, IDataHandler, IDisconnectHandler {
		
		private List<String> errors = new ArrayList<String>();
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			if (!Thread.currentThread().getName().startsWith(ConnectionUtils.SERVER_TRHREAD_PREFIX)) {
				errors.add("onConnect should be executed within main thread");
			}
			connection.setAutoflush(false);
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			if (!Thread.currentThread().getName().startsWith("xDispatcher")) {
				errors.add("onData should be executed within dispatcher thread");
			}
			return true;
		}
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			if (!Thread.currentThread().getName().startsWith("xDispatcher")) {
				errors.add("onDisconnect should be executed within dispatcher thread");
			}
			return true;
		}
	}
	
}
