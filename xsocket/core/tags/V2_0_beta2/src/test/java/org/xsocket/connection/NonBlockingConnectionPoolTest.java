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
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.DataConverter;
import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.NonBlockingConnection;
import org.xsocket.connection.NonBlockingConnectionPool;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.IConnection.FlushMode;




/**
*
* @author grro@xsocket.org
*/
public final class NonBlockingConnectionPoolTest {

	private static final String DELIMITER = System.getProperty("line.separator");
	private static final int WORKER_COUNT = 10;
	private static final int LOOPS = 20;

	private static final String QUIT = "QUIT";

	private int running = 0;

	private final List<String> errors = new ArrayList<String>();


	public static void main(String[] args) throws Exception {
		IServer server = new org.xsocket.connection.Server(new BlackHoleHandler());
		ConnectionUtils.start(server);

		new NonBlockingConnectionPoolTest().callPooled("localhost", server.getLocalPort(), 4000000);
	}


	@Test
	public void testReuse() throws Exception {

		IServer server = new org.xsocket.connection.Server(new BlackHoleHandler());
		ConnectionUtils.start(server);
		ConnectionUtils.registerMBean(server);

		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();
		ConnectionUtils.registerMBean(pool);

		int usage = 0;
		for (int i = 0; i < 10; i++) {
			INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server.getLocalPort());

			con.write("test");
			QAUtil.sleep(100);

			String s = con.toString();
			int pos = s.indexOf("countUsage=");

			int usageNew = Integer.parseInt(s.substring(pos + "countUsage=".length(), s.length() - 1));
			Assert.assertTrue(usageNew > usage);
			usage = usageNew;

			con.close();
			Assert.assertFalse(NonBlockingConnectionPool.isDestroyed(con));
		}

		pool.close();
		server.close();
	}





	@Test
	public void testLiveSimple() throws Exception {
		//QAUtil.setLogLevel(NonBlockingConnectionPool.class.getName(), Level.FINE);

		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();
		INonBlockingConnection con = pool.getNonBlockingConnection("www.web.de", 80);

		Assert.assertTrue(pool.getNumPooledActive() == 1);

		con.write("GET / HTTP1.1 \r\n\r\n");
		QAUtil.sleep(500);

		String responseCode = con.readStringByDelimiter("\r\n");
		Assert.assertTrue(responseCode.contains("OK"));

		con.close();

		Assert.assertTrue(pool.getNumPooledActive() == 0);
	}








	@Test
	public void testIdleTimeout() throws Exception {
	//	QAUtil.setLogLevel(NonBlockingConnectionPool.class.getName(), Level.FINE);

		errors.clear();
		final NonBlockingConnectionPool pool = new NonBlockingConnectionPool();

		final Server server = new Server();
		new Thread(server).start();

		for (int i = 0; i < 2; i++) {
			INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server.getLocalPort());
			con.setIdleTimeoutMillis(1 * 1000);

			Assert.assertTrue(con.isOpen());

			QAUtil.sleep(1500);
			Assert.assertFalse(con.isOpen());
			Assert.assertTrue(pool.getNumPooledActive() == 0);
		}


		pool.close();
		server.close();
	}


	@Test
	public void testConnectionTimeout() throws Exception {
		// QAUtil.setLogLevel(NonBlockingConnectionPool.class.getName(), Level.FINE);

		errors.clear();
		final NonBlockingConnectionPool pool = new NonBlockingConnectionPool();

		final Server server = new Server();
		new Thread(server).start();

		INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server.getLocalPort());
		con.setConnectionTimeoutMillis(1 * 1000);

		Assert.assertTrue(con.isOpen());

		QAUtil.sleep(1500);
		Assert.assertFalse(con.isOpen());
		Assert.assertTrue(pool.getNumPooledActive() == 0);


		pool.close();
		server.close();
	}



	@Test
	public void testSimplePerformanceCompare() throws Exception {

		IServer server = new org.xsocket.connection.Server(new BlackHoleHandler());
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



		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();

		for (int i = 0; i < loops; i++) {
			long start = System.nanoTime();
			INonBlockingConnection con = pool.getNonBlockingConnection(hostname, port);
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
			INonBlockingConnection con = new NonBlockingConnection(hostname, port);
			con.setFlushmode(FlushMode.ASYNC);
			con.write("Hello" + EchoHandler.DELIMITER);
			elapsed += System.nanoTime() - start;

			con.close();
		}


		return (elapsed / 1000000);
	}







	@Test
	public void testNonHandler() throws Exception {

		IServer server = new org.xsocket.connection.Server(0, new EchoHandler());
		ConnectionUtils.start(server);

		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();

		INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server.getLocalPort());
		con.write("test" + EchoHandler.DELIMITER);

		QAUtil.sleep(400);

		con.close();
		pool.close();
		server.close();
	}





	@Test
	public void testNonThreadedHandler() throws Exception {

		IServer server = new org.xsocket.connection.Server(0, new EchoHandler());
		ConnectionUtils.start(server);

		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();

		NonThreadedClientHandler hdl = new NonThreadedClientHandler();
		INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server.getLocalPort(), hdl);
		con.write("test" + EchoHandler.DELIMITER);

		QAUtil.sleep(400);

		Assert.assertEquals("xDispatcher#CLIENT", hdl.threadName);

		con.close();
		pool.close();
		server.close();
	}



	@Test
	public void testThreadedHandler() throws Exception {

		IServer server = new org.xsocket.connection.Server(0, new EchoHandler());
		ConnectionUtils.start(server);

		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();

		ClientHandler hdl = new ClientHandler();
		INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server.getLocalPort(), hdl);
		con.write("test" + EchoHandler.DELIMITER);

		QAUtil.sleep(400);

		Assert.assertTrue(hdl.threadName.startsWith("xNbcPo"));

		con.close();
		pool.close();
		server.close();
	}


	@Execution(Execution.MULTITHREADED)
	private static final class ClientHandler implements IDataHandler {

		private String threadName = null;

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			threadName = Thread.currentThread().getName();

			connection.readByteBufferByLength(connection.available());
			return true;
		}
	}


	@Execution(Execution.NONTHREADED)
	private static final class NonThreadedClientHandler implements IDataHandler {

		private String threadName = null;

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			threadName = Thread.currentThread().getName();

			connection.readByteBufferByLength(connection.available());
			return true;
		}
	}












	@Execution(Execution.NONTHREADED)
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

		Server() throws IOException {
			sso = new ServerSocket(0);
		}

		public void run()  {
			while (isRunning) {
				try {
					Socket s = sso.accept();
					executorService.execute(new Worker(s));
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

	    Worker(Socket s) throws IOException {
	      this.s = s;
	      in = new LineNumberReader(new InputStreamReader(s.getInputStream()));
	      out = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));
	    }

	    public void run() {
	    	while (isRunning) {
	    		try {
	    			String request = in.readLine();
	    			if (request != null) {
	    				if (request.equals(QUIT)) {
	    					isRunning = false;
	    				} else {
			    			out.write("OK" + DELIMITER);
			    			out.flush();
			    			//LOG.info("Server sending..");
	    				}
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
