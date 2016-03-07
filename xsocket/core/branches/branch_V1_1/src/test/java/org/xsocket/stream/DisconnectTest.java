// $Id: DisconnectTest.java 1281 2007-05-29 19:48:07Z grro $
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
import java.io.OutputStream;
import java.net.Socket;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.List;



import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;
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
	private int errors = 0;

	private int runningTreads = 0;
	
	
	@Test public void testxSocket() throws Exception {
		//TestUtil.setLogLevel(Level.FINE);
		TestHandler hdl = new TestHandler();
		final IMultithreadedServer server = new MultithreadedServer(hdl);
		StreamUtils.start(server);


		for (int i = 0; i < 3; i++) {
			Thread t = new Thread() {
				public void run() {
					runningTreads++;
					try {
						for (int j = 0; j < 2; j++) {
							IBlockingConnection con = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
							con.write("test");
							con.close();
						}
					} catch (Exception e) {
						errors++;
					}
					
					runningTreads--;
				};
			};
			t.start();
		}

		QAUtil.sleep(200);


		do {
			QAUtil.sleep(100);
		} while (runningTreads > 0);

		Assert.assertTrue(" unbalanced connects disconnect ratio " + balance, balance == 0);
		Assert.assertTrue("error occured", errors == 0);
		
		Assert.assertTrue("handler error occured", hdl.errors.isEmpty());
		
		server.close();
	}

	
	@Test public void testNative() throws Exception {
		//TestUtil.setLogLevel(Level.FINE);
		TestHandler hdl = new TestHandler();
		final IMultithreadedServer server = new MultithreadedServer(hdl);
		StreamUtils.start(server);


		for (int i = 0; i < 3; i++) {
			Thread t = new Thread() {
				public void run() {
					runningTreads++;
					try {
						for (int j = 0; j < 2; j++) {
							Socket socket = new Socket(server.getLocalAddress(), server.getLocalPort());
							OutputStream os = socket.getOutputStream();
							os.write("test".getBytes());
							os.flush();
							os.close();
							socket.close();
						}
					} catch (Exception e) {
						errors++;
					}
					
					runningTreads--;
				};
			};
			t.start();
		}

		QAUtil.sleep(200);


		do {
			QAUtil.sleep(100);
		} while (runningTreads > 0);

		Assert.assertTrue(" unbalanced connects disconnect ratio " + balance, balance == 0);
		Assert.assertTrue("error occured", errors == 0);
		
		Assert.assertTrue("handler error occured", hdl.errors.isEmpty());
		
		server.close();
	}




	private class TestHandler implements IDataHandler, IConnectHandler, IDisconnectHandler {

		private List<String> errors = new ArrayList<String>();
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);

			balance++;
			return true;
		}

		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			
			// shouldn't thrown an exception!
			try {
				connection.toString();
			} catch (NullPointerException npe) {
				errors.add("Nullpointerexception occured");
			}
		
			balance--;
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			connection.readAvailable();
			return true;
		}
	}
}
