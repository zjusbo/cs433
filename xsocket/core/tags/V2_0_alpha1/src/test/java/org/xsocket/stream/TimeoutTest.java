// $Id: TimeoutTest.java 1738 2007-09-13 07:02:26Z grro $
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
import org.xsocket.QAUtil;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.ITimeoutHandler;
import org.xsocket.stream.Server;



/**
*
* @author grro@xsocket.org
*/
public final class TimeoutTest {

	private static boolean connectionTimeoutOccured = false;
	private static boolean idleTimeoutOccured = false;

	
	

	@Test 
	public void testIdleTimeout() throws Exception {
		//TestUtil.setLogLevel(Level.FINE);
		
		IServer server = new Server(new TimeoutTestServerHandler());
		StreamUtils.start(server);

		connectionTimeoutOccured = false;
		idleTimeoutOccured = false;

		int idleTimeout = 1;
		System.out.println("set idle timeout " + idleTimeout + " sec");
		server.setIdleTimeoutSec(idleTimeout);
		Assert.assertTrue(server.getIdleTimeoutSec() == idleTimeout);

		server.setConnectionTimeoutSec(15);
		Assert.assertTrue(server.getConnectionTimeoutSec() == 15);
		

		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		con.write((int) 4);

		int sleepTime = 2000;
		QAUtil.sleep(sleepTime);
		
		con.close();

		Assert.assertTrue("idle timeout should have been occured (idle timeout=" + idleTimeout + ", elapsed=" + sleepTime, idleTimeoutOccured);
		Assert.assertFalse("connection timeout should have been occured", connectionTimeoutOccured);
		
		server.close();
	}


	@Test 
	public void testConnectionTimeout() throws Exception {
		IServer server = new Server(new TimeoutTestServerHandler());
		StreamUtils.start(server);
 
		connectionTimeoutOccured = false;
		idleTimeoutOccured = false;

		int conTimeout = 1;
		System.out.println("set con timeout " +conTimeout + " sec");
		server.setIdleTimeoutSec(15);
		server.setConnectionTimeoutSec(conTimeout);

		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		con.write((int) 4);

		int sleepTime = 2000;
		QAUtil.sleep(sleepTime);

		con.close();

		Assert.assertFalse(idleTimeoutOccured);
		Assert.assertTrue("connection timeout should have been occured (connection timeout=" + conTimeout + ", elapsed=" + sleepTime, connectionTimeoutOccured);
		server.close();
	}

	
	@Test 
	public void testIdleTimeoutWithoutSending() throws Exception {
		IServer server = new Server(new TimeoutTestServerHandler());
		StreamUtils.start(server);

		connectionTimeoutOccured = false;
		idleTimeoutOccured = false;

		int idleTimeout = 1;
		System.out.println("set idle timeout " + idleTimeout + " sec");
		server.setIdleTimeoutSec(idleTimeout);
		server.setConnectionTimeoutSec(20);

		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());

		int sleepTime = 2000;
		QAUtil.sleep(sleepTime);
		
		con.close();

		Assert.assertTrue("idle timeout should have been occured (idle timeout=" + idleTimeout + ", elapsed=" + sleepTime, idleTimeoutOccured);
		Assert.assertFalse(connectionTimeoutOccured);
		server.close();
	}

	
	
	@Test 
	public void testIdleTimeoutDefault() throws Exception {
		IServer server = new Server(new EchoHandler());
		StreamUtils.start(server);

		int idleTimeout = 1;
		System.out.println("set idle timeout " + idleTimeout + " sec");
		server.setIdleTimeoutSec(1);
		
		ClientHandler clientHdl = new ClientHandler();
		INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort(), clientHdl);
		con.write("rt");
		
		QAUtil.sleep(2000);
		
		Assert.assertTrue(clientHdl.isDisconnected);
		Assert.assertFalse(con.isOpen());
		server.close();
	}


	
	
	@Test 
	public void testIdleTimeoutDefault2() throws Exception {
		IServer server = new Server(new EchoHandler());
		StreamUtils.start(server);

		System.out.println("set connection timeout 1 sec");
		server.setConnectionTimeoutSec(1);
		
		ClientHandler clientHdl = new ClientHandler();
		INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort(), clientHdl);
		con.write("rt");
		
		QAUtil.sleep(2000);

		Assert.assertTrue(clientHdl.isDisconnected);
		Assert.assertFalse(con.isOpen());
		server.close();
	}

	
	

	private static final class TimeoutTestServerHandler implements ITimeoutHandler {


		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
			System.out.println("con time out");
			connectionTimeoutOccured = true;
			connection.close();
			return true;
		}


		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
			System.out.println("idle time out");
			idleTimeoutOccured = true;
			connection.close();
			return true;
		}
	}

	private static final class ClientHandler implements IDisconnectHandler {
		
		boolean isDisconnected = false;
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			isDisconnected = true;
			
			connection.close();
			return true;
		}
	}
	
	
	private static final class EchoHandler implements IDataHandler {
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			connection.write(connection.readAvailable());
			return true;
		}
	}
}
