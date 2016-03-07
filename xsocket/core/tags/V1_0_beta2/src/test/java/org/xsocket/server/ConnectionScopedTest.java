// $Id: ConnectionScopedTest.java 439 2006-12-06 06:43:30Z grro $
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

import java.io.IOException;
import java.nio.BufferUnderflowException;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import org.xsocket.IBlockingConnection;
import org.xsocket.BlockingConnection;
import org.xsocket.INonBlockingConnection;


/**
*
* @author grro@xsocket.org
*/
public final class ConnectionScopedTest extends AbstractServerTest {

	
	private static IMultithreadedServer server = null;
	private static int id = 0;
	
	
	@BeforeClass public static void setUp() {
		server = createServer(6372, new ServerHandler()); 
	}
	

	@AfterClass public static void tearDown() {
		server.shutdown();
	}



	@Test public void testMixed() throws Exception {
		setUp();  // workaround -> current version of maven doesn't support junit4

		IBlockingConnection connection = new BlockingConnection("127.0.0.1", server.getPort());
		int sessionId = connection.receiveInt();
		
		connection.write((long) 5);
		Assert.assertEquals(sessionId, connection.receiveInt());
		
		IBlockingConnection connection2 = new BlockingConnection("127.0.0.1", server.getPort());
		int sessionId2 = connection2.receiveInt();
		Assert.assertFalse(sessionId == sessionId2);
		
		connection.write((long) 8);
		Assert.assertEquals(sessionId, connection.receiveInt());
		
		connection2.write((long) 34);
		Assert.assertEquals(sessionId2, connection2.receiveInt());

		connection.close();
		connection2.close();

		tearDown(); 	 // workaround -> current version of maven doesn't support junit4
	}



	private static final class ServerHandler implements IDataHandler, IConnectHandler, IConnectionScoped {
		private int sessionId = 0;
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			sessionId = ++id;
			connection.write((int) sessionId);
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			connection.readAvailable();
			connection.write((int) sessionId);
			return true;
		}
		
		@Override
		public Object clone() throws CloneNotSupportedException {
			return super.clone();
		}
	}
}
