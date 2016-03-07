/*
 *  Copyright (c) xsocket.org, 2006 - 2009. All rights reserved.
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

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.NonBlockingConnection;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;



/**
*
* @author grro@xsocket.org
*/
public final class NonBlockingConnnectionResetTest  {
	

	@Test 
	public void testIdleTimout() throws Exception {
		
		IServer server = new Server(new EchoHandler());
		ConnectionUtils.start(server);
		
		
		Handler hdl = new Handler();
		NonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort(), hdl);
		
		con.setConnectionTimeoutMillis(60 * 60  * 1000);
		con.setIdleTimeoutMillis(2 * 1000);
		
		con.write("test" + EchoHandler.DELIMITER);
		QAUtil.sleep(300);
		
		Assert.assertEquals("test", hdl.data);
		Assert.assertFalse(hdl.connectionTimeoutOccurred);
		Assert.assertFalse(hdl.idleTimeoutOccurred);
		hdl.data = null;
		
		
		con.reset();
		
		con.setHandler(hdl);
		
		con.write("test2" + EchoHandler.DELIMITER);
		QAUtil.sleep(300);
		
		Assert.assertEquals("test2", hdl.data);
		Assert.assertFalse(hdl.connectionTimeoutOccurred);
		Assert.assertFalse(hdl.idleTimeoutOccurred);
		hdl.data = null;
		
		
		QAUtil.sleep(2000);

		Assert.assertFalse(hdl.connectionTimeoutOccurred);
		Assert.assertTrue(hdl.idleTimeoutOccurred);
		QAUtil.assertTimeout(hdl.elapsed, 2000, 1800, 3000);
		
		
		con.close();
		server.close();
	}
	
	
	@Test 
	public void testConnectionTimeout() throws Exception {
		
		IServer server = new Server(new EchoHandler());
		ConnectionUtils.start(server);
		
		
		Handler hdl = new Handler();
		NonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort(), hdl);
		
		con.setConnectionTimeoutMillis(2 * 1000);
		con.setIdleTimeoutMillis(60 * 60  * 1000);
		
		con.write("test" + EchoHandler.DELIMITER);
		QAUtil.sleep(300);
		
		Assert.assertEquals("test", hdl.data);
		Assert.assertFalse(hdl.connectionTimeoutOccurred);
		Assert.assertFalse(hdl.idleTimeoutOccurred);
		hdl.data = null;
		
		
		con.reset();
		
		con.setHandler(hdl);

		
		con.write("test2" + EchoHandler.DELIMITER);
		QAUtil.sleep(300);
		
		Assert.assertEquals("test2", hdl.data);
		Assert.assertFalse(hdl.connectionTimeoutOccurred);
		Assert.assertFalse(hdl.idleTimeoutOccurred);
		hdl.data = null;
		
		
		QAUtil.sleep(2000);

		Assert.assertTrue(hdl.connectionTimeoutOccurred);
		Assert.assertFalse(hdl.idleTimeoutOccurred);
		QAUtil.assertTimeout(hdl.elapsed, 2000, 1800, 3000);
		
		
		con.close();
		server.close();
	}
	
	
	
	@Execution(Execution.NONTHREADED)
	private static final class Handler implements IConnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler {
		
		private String data = null;
		
		private boolean connectionTimeoutOccurred = false;
		private boolean idleTimeoutOccurred = false;
		
		private long elapsed = 0;
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			elapsed = System.currentTimeMillis();
			return true;
		}
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			data = connection.readStringByDelimiter(EchoHandler.DELIMITER);
			return true;
		}
		
		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
			elapsed = System.currentTimeMillis() - elapsed;
			connectionTimeoutOccurred = true;
			return true;
		}
		
		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
			elapsed = System.currentTimeMillis() - elapsed;
			idleTimeoutOccurred = true;
			return true;
		}
		
	}
}
