/*
 *  Copyright (c) xsocket.org, 2006-2008. All rights reserved.
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.IDisconnectHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.NonBlockingConnectionPool;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;




/**
*
* @author grro@xsocket.org
*/
public final class NonBlockingConnectionPoolHandlerTest {

//	private static final int WORKER_COUNT = 10;
	private static final int WORKER_COUNT = 5;

	private AtomicInteger running = new AtomicInteger(0);

	private final List<String> errors = new ArrayList<String>();



	@Test
	public void testNonThreadedDataHandler() throws Exception {
		System.out.println("test NonThreadedDataHandler ");
		errors.clear();

		final IServer server = new Server(9977, new EchoHandler());
		server.start();

		final NonBlockingConnectionPool pool = new NonBlockingConnectionPool();




		for (int i = 0; i < WORKER_COUNT ; i++) {
			Thread t = new Thread() {
				@Override
				public void run() {
					running.incrementAndGet();

					try {

						for (int i = 0; i < 5; i++) {
							NonThreadedClientHandler clientHdl = new NonThreadedClientHandler();

							INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server.getLocalPort(), clientHdl);
							con.setAutoflush(false);
							
							QAUtil.sleep(300);

							if (clientHdl.countConnected != 1) {
								System.out.println("Error: connect event didn't occur");
								errors.add("connect event didn't occur");
								continue;
							}

							Assert.assertEquals(Thread.currentThread().getName(), clientHdl.threadname);

							con.write("test" + EchoHandler.DELIMITER);
							con.flush();

							do {
								QAUtil.sleep(50);
							} while (clientHdl.response == null);


							if(!clientHdl.response.endsWith("test")) {
								System.out.println("Error: didn't get response. got " + clientHdl.response);
								errors.add("didn't get response. got " + clientHdl.response);
							}
							
							if (!clientHdl.threadname.startsWith("xDispatcherGlb#")) {
								System.out.println("Error thread name is not xDispatcherGlb#.. " + clientHdl.threadname);
								Assert.fail();
							}
							clientHdl.response = null;

							con.write("test" + EchoHandler.DELIMITER);
							con.flush();

							do {
								QAUtil.sleep(50);
							} while (clientHdl.response == null);

							if(!clientHdl.response.endsWith("test")) {
								System.out.println("Error didn't get response. got " + clientHdl.response);
								errors.add("didn't get response. got " + clientHdl.response);
							}
							clientHdl.response = null;

							try {
								con.close();

								if (clientHdl.countDisconnected != 1) {
									System.out.println("Error disconnect event didn't occur");
									errors.add("disconnect event didn't occur");
								}

							} catch (Exception ignore) { }
						}
					} catch (Exception e) {
						errors.add(e.toString());
					}

					running.decrementAndGet();
				}
			};

			t.start();
		}

		do {
			QAUtil.sleep(200);
		} while (running.get() > 0);


		for (String error : errors) {
			System.out.println("error: " + error);
		}

		Assert.assertTrue(errors.size() == 0);

		pool.close();
		server.close();

		System.out.println("test data handler OK");
	}


	@Test
	public void testMultiThreadedDataHandler() throws Exception {
		System.out.println("test MultiThreadedDataHandler");
		errors.clear();

		final IServer server = new Server(new EchoHandler());
		server.start();

		final NonBlockingConnectionPool pool = new NonBlockingConnectionPool();




		for (int i = 0; i < WORKER_COUNT ; i++) {
			Thread t = new Thread() {
				@Override
				public void run() {
					running.incrementAndGet();

					try {

						for (int i = 0; i < 10; i++) {
							MultiThreadedClientHandler clientHdl = new MultiThreadedClientHandler();

							INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server.getLocalPort(), clientHdl);
							con.setAutoflush(false);

							QAUtil.sleep(100);

							if (clientHdl.countConnected != 1) {
								errors.add("connect event didn't occur");
							}

							if (!clientHdl.threadname.startsWith("xNbcPool")) {
								System.out.println("Error: threadname is not xNbcPool: " + clientHdl.threadname);
								Assert.fail();
							}

							con.write("test" + EchoHandler.DELIMITER);
							con.flush();

							do {
								QAUtil.sleep(50);
							} while (clientHdl.response == null);


							if(!clientHdl.response.endsWith("test")) {
								System.out.println("Error: didn't get response. got " + clientHdl.response);
								errors.add("didn't get response. got " + clientHdl.response);
							}

							if (!clientHdl.threadname.startsWith("xNbcPool")) {
								System.out.println("Error: threadname is not xNbcPool: " + clientHdl.threadname);
								Assert.fail();
							}

							clientHdl.response = null;

							con.write("test" + EchoHandler.DELIMITER);
							con.flush();

							do {
								QAUtil.sleep(50);
							} while (clientHdl.response == null);

							if(!clientHdl.response.endsWith("test")) {
								System.out.println("Error didn't get response. got " + clientHdl.response);
								errors.add("didn't get response. got " + clientHdl.response);
							}
							clientHdl.response = null;

							try {
								con.close();

								QAUtil.sleep(100);

								if (clientHdl.countDisconnected != 1) {
									System.out.println("Error disconnect event didn't occur");
									errors.add("disconnect event didn't occur");
								}

							} catch (Exception ignore) { }
						}
					} catch (Exception e) {
						errors.add(e.toString());
					}

					running.decrementAndGet();
				}
			};

			t.start();
		}

		do {
			QAUtil.sleep(200);
		} while (running.get() > 0);


		for (String error : errors) {
			System.out.println("error: " + error);
		}

		Assert.assertTrue(errors.size() == 0);

		pool.close();
		server.close();

		System.out.println("test data handler OK");
	}






	@Test
	public void testUpdateHandler() throws Exception {
		System.out.println("test dUpdateHandler");
		errors.clear();

		IServer server = new Server(new EchoHandler());
		ConnectionUtils.start(server);

		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();

		NonThreadedClientHandler ch0 = new NonThreadedClientHandler();
		INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server.getLocalPort(), ch0);

		Assert.assertEquals(1, ch0.countConnected);

		NonThreadedClientHandler ch1 = new NonThreadedClientHandler();
		con.setHandler(ch1);

		con.write("test" + EchoHandler.DELIMITER);

		QAUtil.sleep(250);

		Assert.assertTrue("test".equals(ch1.response));
		con.close();





		Assert.assertEquals(0, ch0.countDisconnected);

		Assert.assertEquals(0, ch1.countConnected);
		Assert.assertEquals(1, ch1.countDisconnected);

		pool.close();
		server.close();
	}


	@Test
	public void testIdleTimeout() throws Exception {

		IServer testServer = new Server(new EchoHandler());
		ConnectionUtils.start(testServer);

		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();
		pool.setMaxActive(1);

		NonThreadedClientHandler clientHandler = new NonThreadedClientHandler();
		INonBlockingConnection connection = pool.getNonBlockingConnection("localhost", testServer.getLocalPort(), clientHandler);
		Assert.assertFalse(clientHandler.idleTimeoutOccured);

		connection.setAutoflush(true);
		connection.setIdleTimeoutMillis(1 * 1000);
		QAUtil.sleep(1500);

		Assert.assertTrue(clientHandler.idleTimeoutOccured);
		Assert.assertFalse(connection.isOpen());
		Assert.assertFalse(clientHandler.errorOccured);
		connection.close();


		clientHandler = new NonThreadedClientHandler();
		connection = pool.getNonBlockingConnection("localhost", testServer.getLocalPort(), clientHandler);
		Assert.assertFalse(clientHandler.idleTimeoutOccured);

		connection.setAutoflush(true);
		connection.setIdleTimeoutMillis(1 * 1000);
		QAUtil.sleep(1500);

		Assert.assertTrue(clientHandler.idleTimeoutOccured);
		Assert.assertFalse(connection.isOpen());
		Assert.assertFalse(clientHandler.errorOccured);
		connection.close();


		pool.close();
		testServer.close();
	}




	@Test
	public void testConnectionTimeout() throws Exception {
		IServer testServer = new Server(new EchoHandler());
		ConnectionUtils.start(testServer);

		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();
		pool.setMaxActive(1);

		NonThreadedClientHandler clientHandler = new NonThreadedClientHandler();
		INonBlockingConnection connection = pool.getNonBlockingConnection("localhost", testServer.getLocalPort(), clientHandler);
		Assert.assertFalse(clientHandler.idleTimeoutOccured);

		connection.setAutoflush(true);
		connection.setConnectionTimeoutMillis(1 * 1000);
		QAUtil.sleep(1500);

		Assert.assertTrue(clientHandler.connectionTimeoutOccured);
		Assert.assertFalse(connection.isOpen());
		Assert.assertFalse(clientHandler.errorOccured);
		connection.close();


		clientHandler = new NonThreadedClientHandler();
		connection = pool.getNonBlockingConnection("localhost", testServer.getLocalPort(), clientHandler);
		Assert.assertFalse(clientHandler.idleTimeoutOccured);

		connection.setAutoflush(true);
		connection.setConnectionTimeoutMillis(1 * 1000);
		QAUtil.sleep(1500);

		Assert.assertTrue(clientHandler.connectionTimeoutOccured);
		Assert.assertFalse(connection.isOpen());
		Assert.assertFalse(clientHandler.errorOccured);
		connection.close();


		pool.close();
		testServer.close();
	}


	@Execution(Execution.NONTHREADED)
	private static final class NonThreadedClientHandler implements IConnectHandler, IDataHandler, IDisconnectHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler {

		private String threadname = null;
		private boolean errorOccured = false;
		private String response = null;
		private int countConnected = 0;
		private int countDisconnected = 0;
		private boolean idleTimeoutOccured = false;
		private boolean connectionTimeoutOccured = false;

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			threadname = Thread.currentThread().getName();
			countConnected++;
			return true;
		}

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			threadname = Thread.currentThread().getName();
			response = connection.readStringByDelimiter(EchoHandler.DELIMITER);

			return true;
		}

		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			threadname = Thread.currentThread().getName();
			countDisconnected++;
			return true;
		}

		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
			threadname = Thread.currentThread().getName();
			connectionTimeoutOccured = true;
			return false;
		}

		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
			threadname = Thread.currentThread().getName();
			idleTimeoutOccured = true;
			return false;
		}
	}



	@Execution(Execution.MULTITHREADED)
	private static final class MultiThreadedClientHandler implements IConnectHandler, IDataHandler, IDisconnectHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler {

		private String threadname = null;
		private boolean errorOccured = false;
		private String response = null;
		private int countConnected = 0;
		private int countDisconnected = 0;
		private boolean idleTimeoutOccured = false;
		private boolean connectionTimeoutOccured = false;


		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			threadname = Thread.currentThread().getName();
			countConnected++;
			return true;
		}

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			threadname = Thread.currentThread().getName();
			response = connection.readStringByDelimiter(EchoHandler.DELIMITER);

			return true;
		}

		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			threadname = Thread.currentThread().getName();
			countDisconnected++;
			return true;
		}

		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
			threadname = Thread.currentThread().getName();
			connectionTimeoutOccured = true;
			return false;
		}

		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
			threadname = Thread.currentThread().getName();
			idleTimeoutOccured = true;
			return false;
		}
	}
}
