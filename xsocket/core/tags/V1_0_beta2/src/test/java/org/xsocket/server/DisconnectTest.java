// $Id: DisconnectTest.java 439 2006-12-06 06:43:30Z grro $
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


import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.xsocket.IBlockingConnection;
import org.xsocket.BlockingConnection;
import org.xsocket.INonBlockingConnection;




/**
*
* @author grro@xsocket.org
*/
public final class DisconnectTest extends AbstractServerTest {


	private int balance = 0;

	
	
	private IMultithreadedServer server = null;
	
	@Before public void setUp() {
		server = createServer(8282, new TestHandler()); 
	}
	

	@After public void tearDown() {
		server.shutdown();
	}





	@Test public void testSimple() throws Exception {
		setUp(); // maven bug work around

		for (int i = 0; i < 3; i++) {
			Thread t = new Thread() {
				public void run() {
					try {
						for (int j = 0; j < 2; j++) {
							IBlockingConnection con = new BlockingConnection("127.0.0.1", server.getPort());
							con.write("test");
							con.close();
						}
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				};
			};
			t.start();
		}



		try {
 			Thread.sleep(4 * 1000);
		} catch (InterruptedException ignore) {  }

		Assert.assertTrue(" unbalanced connects disconnect ratio " + balance, balance == 0);
		tearDown(); // maven bug work around
	}



	private class TestHandler implements IDataHandler, IConnectHandler, IDisconnectHandler {

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			balance++;
			return true;
		}

		public boolean onDisconnect(String connectionId) throws IOException {
			balance--;
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			connection.readAvailable();
			return true;
		}
	}
}
