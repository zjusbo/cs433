// $Id: ConnectionScopedTest.java 764 2007-01-15 06:26:17Z grro $
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
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IConnectHandler;
import org.xsocket.stream.IConnectionScoped;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;



/**
*
* @author grro@xsocket.org
*/
public final class ConnectionScopedTest  {

	private static int id = 0;
	
	


	@Test public void testMixed() throws Exception {
		//TestUtil.setLogLevel(Level.FINE);
		IMultithreadedServer server = new MultithreadedServer(new ServerHandler());
		new Thread(server).start(); 

		IBlockingConnection connection = new BlockingConnection("127.0.0.1", server.getLocalPort());
		connection.setAutoflush(false);
		
		int sessionId = connection.receiveInt();
		
		connection.write((long) 5);
		connection.flush();
		
		Assert.assertEquals(sessionId, connection.receiveInt());
		
		IBlockingConnection connection2 = new BlockingConnection("127.0.0.1", server.getLocalPort());
		int sessionId2 = connection2.receiveInt();
		Assert.assertFalse(sessionId == sessionId2);
		
		connection.write((long) 8);
		connection.flush();
		Assert.assertEquals(sessionId, connection.receiveInt());
		
		connection2.write((long) 34);
		connection2.flush();
		Assert.assertEquals(sessionId2, connection2.receiveInt());

		connection.close();
		connection2.close();

		server.shutdown();
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
