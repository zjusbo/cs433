// $Id: DisconnectTest.java 1186 2007-04-27 09:35:49Z grro $
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
import java.nio.BufferUnderflowException;



import org.junit.Assert;
import org.junit.Test;
import org.xsocket.ClosedConnectionException;
import org.xsocket.QAUtil;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;




/**
*
* @author grro@xsocket.org
*/
public final class UncleanDisconnectTest {
	
	
	@Test 
	public void testBlockingClientServerClose() throws Exception {
		TestHandler hdl = new TestHandler();
		IMultithreadedServer server = new MultithreadedServer(hdl);
		StreamUtils.start(server);
		
		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(true);
		
		connection.write("test");

		server.close();
		QAUtil.sleep(1000);
		
		try {
			connection.write("rt");
			String msg = "testBlockingClientServerClose: an ClosedConnectionException should have been thrown";
			System.out.println(msg);
			Assert.fail(msg);
		} catch (ClosedConnectionException expected) { }		
	}


	@Test 
	public void testNonBlockingClientServerClose() throws Exception {
		TestHandler hdl = new TestHandler();
		IMultithreadedServer server = new MultithreadedServer(hdl);
		StreamUtils.start(server);
		
		ClientHandler cHdl = new ClientHandler();
		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort(), cHdl);
		connection.setAutoflush(true);
		
		connection.write("test");

		server.close();		
		QAUtil.sleep(1000);
		
		if (!cHdl.isDisconnected) {
			String msg = "testNonBlockingClientServerClose: hdl is not disconnected";
			System.out.println(msg);
			Assert.fail(msg);
		}
		
		try {
			connection.write("rt");
			String msg = "testNonBlockingClientServerClose: an ClosedConnectionException should have been thrown";
			System.out.println(msg);
			Assert.fail(msg);
		} catch (ClosedConnectionException expected) { }		
	}
	
	

	@Test 
	public void testClientClose() throws Exception {
		TestHandler hdl = new TestHandler();
		IMultithreadedServer server = new MultithreadedServer(hdl);
		StreamUtils.start(server);
		
		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(true);

		connection.write("test");
		connection.close();

		QAUtil.sleep(1000);
		
		if (!hdl.isDisconnected) {
			String msg = "error client close didn't detected";
			System.out.println(msg);
			Assert.fail(msg);
		}
		
		
		server.close();
	}
	
	
	private static final class ClientHandler implements IDisconnectHandler {
		private boolean isDisconnected = false;
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			isDisconnected = true;
			return true;
		}
	}
	
	
	

	private class TestHandler implements IDataHandler, IDisconnectHandler {
		
		private boolean isDisconnected = false;
		private String error = null;
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			connection.readAvailable();
			return true;
		}
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			isDisconnected = true;
			
			try {
				connection.write("shouldn't work");
				error = "ClosedConnectionException should have been thrown";
			} catch (ClosedConnectionException expected) { }
			return true;
		}
	}
}
