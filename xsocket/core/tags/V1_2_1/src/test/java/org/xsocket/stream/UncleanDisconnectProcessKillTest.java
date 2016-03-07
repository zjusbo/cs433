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
import java.net.InetAddress;
import java.nio.BufferUnderflowException;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.ClosedConnectionException;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.stream.INonBlockingConnection;




/**
*
* @author grro@xsocket.org
*/
public final class UncleanDisconnectProcessKillTest {

	private static final String DELIMITER = "\r\n";

	
/*	@Test 
	public void testBlockingClientServerClose() throws Exception {	
		String host = "127.0.0.1";
		String port = "9955";
		
		// start the server
		JavaProcess jp = new JavaProcess();
		jp.start("org.xsocket.stream.TestServer", "http://xsocket.sourceforge.net/test.jar", host, port);
		QAUtil.sleep(500);
		
		IBlockingConnection connection = new BlockingConnection(host, Integer.parseInt(port));
		connection.setAutoflush(true);
		
		String request = "request";
		connection.write(request + DELIMITER);
		String response = connection.readStringByDelimiter(DELIMITER);

		Assert.assertTrue(request.endsWith(response));
		
		// kill server process
		jp.terminate();
		QAUtil.sleep(1000);
		
		try {
			connection.write("rt");
			String msg = "testBlockingClientServerClose: an ClosedConnectionException should have been thrown";
			System.out.println(msg);
			Assert.fail(msg);
		} catch (ClosedConnectionException expected) { }
	}
	*/
	 
	@Test 
	public void testNonBlockingClientServerClose() throws Exception {
		
		String host = "localhost";
		String port = "9956";
		
		// start the server
		JavaProcess jp = new JavaProcess();
		jp.start("org.xsocket.stream.TestServer", "http://xsocket.sourceforge.net/test.jar", host, port);
		QAUtil.sleep(1000);
		
		
		ClientHandler cHdl = new ClientHandler();
		INonBlockingConnection connection = new NonBlockingConnection(host, Integer.parseInt(port), cHdl);
		connection.setAutoflush(true);
		
		connection.write("test");

		
		// kill server process
		jp.terminate();
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

		String host = "localhost";
		String port = "9959";
		
		ServerHandler hdl = new ServerHandler();
		IMultithreadedServer server = new MultithreadedServer(host, Integer.parseInt(port), hdl);
		StreamUtils.start(server);

		
		// start the client
		JavaProcess jp = new JavaProcess();
		jp.start("org.xsocket.stream.TestClient", "http://xsocket.sourceforge.net/test.jar", host, port);
		QAUtil.sleep(500);

		if (hdl.isDisconnected) {
			String msg = "testClientClose: hdl should been connected";
			System.out.println(msg);
			Assert.fail(msg);
		}

		
		// kill the client
		jp.terminate();
		QAUtil.sleep(3000);
		
		if (!hdl.isDisconnected) {
			String msg = "testClientClose: hdl should been disconnected";
			System.out.println(msg);
			Assert.fail(msg);
		}

		server.close();
	}
		

	private static final class ServerHandler implements IDataHandler, IDisconnectHandler {
		
		private boolean isDisconnected = false;
		private int countReceived = 0;
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			isDisconnected = true;
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			connection.readStringByDelimiter(DELIMITER);
			countReceived++;
			return true;
		}
	}
	
	
	

	private static final class ClientHandler implements IDisconnectHandler {
		private boolean isDisconnected = false;
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			isDisconnected = true;
			return true;
		}
	}
}
