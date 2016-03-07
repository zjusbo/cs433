// $Id: DisconnectTest.java 764 2007-01-15 06:26:17Z grro $
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
import java.util.logging.Level;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.TestUtil;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IConnectHandler;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IDisconnectHandler;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;




/**
*
* @author grro@xsocket.org
*/
public final class DisconnectTest {


	private int balance = 0;

	
	
	@Test public void testSimple() throws Exception {
		//TestUtil.setLogLevel(Level.FINE);
		final IMultithreadedServer server = new MultithreadedServer(new TestHandler());
		new Thread(server).start();


		for (int i = 0; i < 3; i++) {
			Thread t = new Thread() {
				public void run() {
					try {
						for (int j = 0; j < 2; j++) {
							IBlockingConnection con = new BlockingConnection("127.0.0.1", server.getLocalPort());
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
 			Thread.sleep(1 * 1000);
		} catch (InterruptedException ignore) {  }

		Assert.assertTrue(" unbalanced connects disconnect ratio " + balance, balance == 0);
		server.shutdown();
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
