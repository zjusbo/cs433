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
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;



import org.junit.Assert;
import org.junit.Test;
import org.xsocket.DataConverter;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.WaitTimeoutException;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.BlockingConnectionPool;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.IConnection.FlushMode;




/**
*
* @author grro@xsocket.org
*/
public final class BlockingConnectionPoolTest {

	private static final String DELIMITER = System.getProperty("line.separator");
	private static final int LOOPS = 10;

	private int running = 0;

	private final List<String> errors = new ArrayList<String>();



	public static void main(String[] args) throws Exception {
		IServer server = new org.xsocket.connection.Server(new EchoHandler());
		ConnectionUtils.start(server);

		new BlockingConnectionPoolTest().callPooled("localhost", server.getLocalPort(), 40000000);
	}


	@Test
	public void testSimple() throws Exception {

		BlockingConnectionPool pool = new BlockingConnectionPool();
		Server server = new Server(50);
		new Thread(server).start();
		
		ConnectionUtils.registerMBean(pool);

		IBlockingConnection con = null;
		for (int i = 0;  i < 50; i++) {
			try {
				// retrieve a connection (if no connection is in pool, a new one will be created)
				con = pool.getBlockingConnection("localhost", server.getLocalPort());
				con.write("Hello" + DELIMITER);
				Assert.assertEquals("OK", con.readStringByDelimiter(DELIMITER));

				con.close();
			} catch (IOException e) {
				if (con != null) {
					try {
						 // if the connection is invalid -> destroy it (it will not return into the pool)
						pool.destroy(con);
					 } catch (Exception ignore) { }
				}
			}
		}


		pool.close();
		server.close();
	}


	@Test
	public void testSimplePerformanceCompare() throws Exception {

		//QAUtil.setLogLevel(NonBlockingConnectionPool.class.getName(), Level.FINE);

		IServer server = new org.xsocket.connection.Server(new BlackHoleHandler());
		server.setFlushMode(FlushMode.ASYNC);
		ConnectionUtils.start(server);

		// warm up
		callPooled("localhost", server.getLocalPort(), 20);
		callUnPooled("localhost", server.getLocalPort(), 20);

		long elapsedPooled = callPooled("localhost", server.getLocalPort(), 1000);
		long elapsedUnpooled = callUnPooled("localhost", server.getLocalPort(), 1000);


		System.out.println("\r\npooled " + DataConverter.toFormatedDuration(elapsedPooled) +  " " +
				           " unpooled " + DataConverter.toFormatedDuration(elapsedUnpooled));

		server.close();

		Assert.assertTrue(elapsedPooled < elapsedUnpooled);
	}


	private long callPooled(String hostname, int port, int loops) throws IOException {
		long elapsed = 0;

		BlockingConnectionPool pool = new BlockingConnectionPool();

		for (int i = 0; i < loops; i++) {
			long start = System.nanoTime();
			IBlockingConnection con = pool.getBlockingConnection(hostname, port);
			con.setFlushmode(FlushMode.ASYNC);

			con.write("Hello" + EchoHandler.DELIMITER);
			elapsed += System.nanoTime() - start;
			con.close();
		}

		pool.close();

		return (elapsed / 1000000);
	}


	private long callUnPooled(String hostname, int port, int loops) throws IOException {
		long elapsed = 0;

		for (int i = 0; i < loops; i++) {
			long start = System.nanoTime();
			IBlockingConnection con = new BlockingConnection(hostname, port);
			con.setFlushmode(FlushMode.ASYNC);

			con.write("Hello" + EchoHandler.DELIMITER);
			elapsed += System.nanoTime() - start;
			con.close();
		}


		return (elapsed / 1000000);
	}





	@Test
	public void testUnlimitedPool() throws Exception {
//		QAUtil.setLogLevel("org.xsocket.stream.NonBlockingConnectionPool", Level.FINE);
		BlockingConnectionPool pool = new BlockingConnectionPool();
		pool.setPooledIdleTimeoutMillis(5 * 1000);
		ConnectionUtils.registerMBean(pool);

		Server server = new Server(50);
		new Thread(server).start();


//		startWorkers("localhost", server.getLocalPort(), pool, 100);
		startWorkers("localhost", server.getLocalPort(), pool, 10);

		QAUtil.sleep(300);
		Assert.assertTrue(pool.getNumPooledActive() > 3);

		do {
			QAUtil.sleep(200);
		} while (running > 0);

		for (String error : errors) {
			System.out.println("error: " + error);
		}

		Assert.assertTrue(errors.size() == 0);
		Assert.assertTrue(pool.getNumPooledActive() == 0);

		pool.close();
		server.close();
	}





	@Test
	public void testLimitedPool() throws Exception {
		//QAUtil.setLogLevel("org.xsocket.ResourcePool", Level.FINE);

		int maxActive = 2;

		BlockingConnectionPool pool = new BlockingConnectionPool();
		pool.setMaxActivePooled(maxActive);

		ConnectionUtils.registerMBean(pool);

		Server server = new Server(50);
		new Thread(server).start();

		startWorkers("localhost", server.getLocalPort(), pool, 5);

		do {
			QAUtil.sleep(500);
			Assert.assertTrue(pool.getNumPooledActive() <= maxActive);
		} while (running > 0);


		for (String error : errors) {
			System.out.println("error: " + error);
		}

		Assert.assertTrue(errors.size() == 0);

		pool.close();
		server.close();
	}


	@Test
	public void testLimitedPoolWaitTimeout() throws Exception {
		int maxActive = 2;

		BlockingConnectionPool pool = new BlockingConnectionPool();
		pool.setMaxActivePooled(maxActive);
		pool.setCreationMaxWaitMillis(500);

		Server server = new Server(10L * 60L * 1000L);
		new Thread(server).start();

		startWorkers("localhost", server.getLocalPort(), pool, maxActive);  // all connections will  be taken

		QAUtil.sleep(300);

		try {
			pool.getBlockingConnection("0.0.0.0", server.getLocalPort());
			Assert.fail("WaitTimeoutEception should have been thrown");
		} catch (WaitTimeoutException expected) { }



		pool.close();
		server.close();
	}


	@Test
	public void testCloseAndDestroyConnection() throws Exception {
		//QAUtil.setLogLevel(NonBlockingConnectionPool.class.getName(), Level.FINE);

		BlockingConnectionPool pool = new BlockingConnectionPool();

		Server server = new Server(50);
		new Thread(server).start();


		IBlockingConnection connection = pool.getBlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);

		Assert.assertTrue(pool.getNumPooledActive() == 1);
		Assert.assertTrue(pool.getNumPooledIdle() == 0);

		connection.close();
		Assert.assertTrue(pool.getNumPooledActive() == 0);
		Assert.assertTrue(pool.getNumPooledIdle() == 1);

		connection = pool.getBlockingConnection("localhost", server.getLocalPort());
		Assert.assertTrue(pool.getNumPooledActive() == 1);
		Assert.assertTrue(pool.getNumPooledIdle() == 0);

		pool.destroy(connection);
		Assert.assertTrue(pool.getNumPooledActive() == 0);
		Assert.assertTrue(pool.getNumPooledIdle() == 0);


		pool.close();
		server.close();
	}


	@Test
	public void testIdleTimeout() throws Exception {
		errors.clear();
		BlockingConnectionPool pool = new BlockingConnectionPool();

		Server server = new Server(50);
		new Thread(server).start();

		IBlockingConnection con = pool.getBlockingConnection("localhost", server.getLocalPort());
		con.setIdleTimeoutMillis(1 * 1000);

		Assert.assertTrue(con.isOpen());

		QAUtil.sleep(1500);
		Assert.assertFalse(con.isOpen());
		Assert.assertTrue(pool.getNumPooledActive() == 0);


		pool.close();
		server.close();
	}


	@Test
	public void testConnectionTimeout() throws Exception {
		errors.clear();
		final BlockingConnectionPool pool = new BlockingConnectionPool();

		Server server = new Server(50);
		new Thread(server).start();

		IBlockingConnection con = pool.getBlockingConnection("localhost", server.getLocalPort());
		con.setConnectionTimeoutMillis(1 * 1000);

		Assert.assertTrue(con.isOpen());

		QAUtil.sleep(1500);
		Assert.assertFalse(con.isOpen());
		Assert.assertTrue(pool.getNumPooledActive() == 0);


		pool.close();
		server.close();
	}



	private void startWorkers(final String host, final int port, final BlockingConnectionPool pool, int count) {
		for (int i = 0; i < count; i++) {
			Thread t = new Thread() {
				@Override
				public void run() {
					running++;

					IBlockingConnection con = null;

					for (int i = 0; i < LOOPS; i++) {
						try {
							con = pool.getBlockingConnection(host, port);
							con.setAutoflush(false);

							con.write("test1" + DELIMITER);
							con.write("test2" + DELIMITER);
							con.flush();

							Assert.assertEquals("OK", con.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE));
							Assert.assertEquals("OK", con.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE));
						} catch (Exception ignore) {


						} finally {
							if (con != null) {
								try {
									con.close();

								} catch (Exception ignore) { }
							}
						}
					}

					running--;
				}
			};
			t.start();
		}
	}


	private static final class BlackHoleHandler implements IDataHandler {

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			connection.readByteBufferByLength(connection.available());
			return true;
		}
	}



	private static class Server implements Runnable {
		private ExecutorService executorService = Executors.newCachedThreadPool();
		private volatile boolean isRunning = true;

		private ServerSocket sso = null;
		private long pause = 0;

		Server(long pause) throws IOException {
			this.pause = pause;
			sso = new ServerSocket(0);
		}

		public void run()  {
			while (isRunning) {
				try {
					Socket s = sso.accept();
					executorService.execute(new Worker(s, pause));
				} catch (Exception e) {
					if (isRunning) {
						e.printStackTrace();
					}
				}
			}
		}

		public InetAddress getLocalAddress() {
			return sso.getInetAddress();
		}

		public int getLocalPort() {
			return sso.getLocalPort();
		}

		public void close() throws IOException {
			isRunning = false;
			sso.close();
		}
	}

	private static class Worker implements Runnable {
		private volatile boolean isRunning = true;

		private LineNumberReader in = null;
		private PrintWriter out = null;
		private Socket s = null;
		private long pause = 0;

	    Worker(Socket s, long pause) throws IOException {
	      this.s = s;
	      this.pause = pause;
	      in = new LineNumberReader(new InputStreamReader(s.getInputStream()));
	      out = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));
	    }

	    public void run() {
	    	while (isRunning) {
	    		try {
	    			String request = in.readLine();
	    			if (request != null) {
	    				try {
		    				Thread.sleep(pause);
		    			} catch (InterruptedException ignore) { }

		    			out.write("OK" + DELIMITER);
		    			out.flush();
		    			//System.out.print(".");

		    			//LOG.info("Server sending..");

	    			} else {
	    				isRunning = false;
	    			}
	    		} catch (Exception e ) {
	    			e.printStackTrace();
	    		}
	    	}
	    	try {
	    		in.close();
	    		out.close();
	    		s.close();
	    	} catch (Exception e) {
	    		e.printStackTrace();
	    	}
	    }
	}
}
