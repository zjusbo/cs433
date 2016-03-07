// $Id: FlushOnCloseTest.java 1017 2007-03-15 08:03:05Z grro $
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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;




/**
*
* @author grro@xsocket.org
*/
public final class NonBlockingConnectionPoolHandlerTest {

	private static final int WORKER_COUNT = 10;

	private int running = 0;

	private final List<String> errors = new ArrayList<String>();



	@Test
	public void testDataHandler() throws Exception {
		System.out.println("test data handler");
		errors.clear();

		final IMultithreadedServer server = new MultithreadedServer(InetAddress.getLocalHost(), 9977, new EchoHandler());
		StreamUtils.start(server);

		final NonBlockingConnectionPool pool = new NonBlockingConnectionPool(60 * 60 * 1000L);




		for (int i = 0; i < WORKER_COUNT ; i++) {
			Thread t = new Thread() {
				@Override
				public void run() {
					running++;

					try {

						for (int i = 0; i < 10; i++) {
							ClientHandler clientHdl = new ClientHandler();

							INonBlockingConnection con = pool.getNonBlockingConnection(server.getLocalAddress(), server.getLocalPort(), clientHdl);
							con.setAutoflush(false);

							if (!clientHdl.isConnected) {
								errors.add("connect event didn't occur");
							}

							con.write("test" + EchoHandler.DELIMITER);
							con.flush();

							do {
								QAUtil.sleep(50);
							} while (clientHdl.response == null);


							if(!clientHdl.response.endsWith("test")) {
								errors.add("didn't get response. got " + clientHdl.response);
							}
							clientHdl.response = null;

							con.write("test" + EchoHandler.DELIMITER);
							con.flush();

							do {
								QAUtil.sleep(50);
							} while (clientHdl.response == null);

							if(!clientHdl.response.endsWith("test")) {
								errors.add("didn't get response. got " + clientHdl.response);
							}
							clientHdl.response = null;

							try {
								con.close();
							} catch (Exception ignore) { }
						}
					} catch (Exception e) {
						errors.add(e.toString());
					}

					running--;
				}
			};

			t.start();
		}

		do {
			QAUtil.sleep(200);
		} while (running > 0);


		for (String error : errors) {
			System.out.println("error: " + error);
		}

		Assert.assertTrue(errors.size() == 0);

		pool.close();
		server.close();

		System.out.println("test data handler OK");
	}


	@Test
	public void testIdleTimeout() throws Exception {
		System.out.println("test idle timeout");
		errors.clear();

		final IMultithreadedServer server = new MultithreadedServer(new EchoHandler());
		StreamUtils.start(server);

		final NonBlockingConnectionPool pool = new NonBlockingConnectionPool(60 * 60 * 1000L);

		for (int i = 0; i < WORKER_COUNT ; i++) {
			Thread t = new Thread() {
				@Override
				public void run() {
					running++;

					try {

						for (int i = 0; i < 2; i++) {
							ClientHandler clientHdl = new ClientHandler();
							INonBlockingConnection connection = pool.getNonBlockingConnection(server.getLocalAddress(), server.getLocalPort(), clientHdl);
							connection.setIdleTimeoutSec(1);

							Assert.assertTrue(clientHdl.idleTimeouts == 0);
							Assert.assertTrue(clientHdl.connectionTimeouts == 0);

							QAUtil.sleep(1500);

							Assert.assertTrue(clientHdl.idleTimeouts == 1);
							Assert.assertTrue(clientHdl.connectionTimeouts == 0);


							connection.close();
						}
					} catch (Exception e) {
						errors.add(e.toString());
					}

					running--;
				}
			};

			t.start();
		}

		do {
			QAUtil.sleep(200);
		} while (running > 0);


		for (String error : errors) {
			System.out.println("error: " + error);
		}


		pool.close();
		server.close();

		System.out.println("test idle timeout OK");
	}


	@Test
	public void testConnectionTimeout() throws Exception {
		System.out.println("test connection timeout");
		errors.clear();

		final IMultithreadedServer server = new MultithreadedServer(new EchoHandler());
		StreamUtils.start(server);

		final NonBlockingConnectionPool pool = new NonBlockingConnectionPool(60 * 60 * 1000L);

		for (int i = 0; i < WORKER_COUNT ; i++) {
			Thread t = new Thread() {
				@Override
				public void run() {
					running++;

					try {

						for (int i = 0; i < 2; i++) {
							ClientHandler clientHdl = new ClientHandler();
							INonBlockingConnection connection = pool.getNonBlockingConnection(server.getLocalAddress(), server.getLocalPort(), clientHdl);
							connection.setConnectionTimeoutSec(1);

							Assert.assertTrue(clientHdl.idleTimeouts == 0);
							Assert.assertTrue(clientHdl.connectionTimeouts == 0);

							QAUtil.sleep(2000);

							Assert.assertTrue("idle timeout shouldn't have been occured", clientHdl.idleTimeouts == 0);
							Assert.assertTrue("connection timeout should have been occured", clientHdl.connectionTimeouts == 1);

							connection.close();
						}
					} catch (Exception e) {
						errors.add(e.toString());
					}

					running--;
				}
			};

			t.start();
		}

		do {
			QAUtil.sleep(200);
		} while (running > 0);


		for (String error : errors) {
			System.out.println("error: " + error);
		}


		pool.close();
		server.close();

		System.out.println("test connection timeout OK");
	}



	private static final class ClientHandler implements IConnectHandler, IDataHandler, ITimeoutHandler {

		private String response = null;
		private boolean isConnected = false;
		private int idleTimeouts = 0;
		private int connectionTimeouts = 0;

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			isConnected = true;
			return true;
		}

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			response = connection.readStringByDelimiter(EchoHandler.DELIMITER);

			return true;
		}

		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
			idleTimeouts++;
			return true;
		}

		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
			connectionTimeouts++;
			return true;
		}
	}
}
