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
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.JmxServer;
import org.xsocket.LogFormatter;
import org.xsocket.QAUtil;




/**
*
* @author grro@xsocket.org
*/
public final class BlockingConnectionPoolTest {
	
	private static final String DELIMITER = System.getProperty("line.separator");
	private static final int LOOPS = 10;
	
	private int running = 0;
	
	private final List<String> errors = new ArrayList<String>();

	@Test 
	public void testUnlimitedPool() throws Exception {	
		BlockingConnectionPool pool = new BlockingConnectionPool(500);
		ConnectionPoolMBeanProxyFactory.createAndRegister(pool);

		Server server = new Server(50);
		new Thread(server).start();
		
		
		startWorkers("localhost", server.getLocalPort(), pool, 10);

		QAUtil.sleep(200);
		Assert.assertTrue(pool.getNumActive() > 3);
		
		do {
			QAUtil.sleep(200);
		} while (running > 0);

		for (String error : errors) {
			System.out.println("error: " + error);
		}

		Assert.assertTrue(errors.size() == 0);
		Assert.assertTrue(pool.getNumActive() == 0);

		pool.close();
		server.close();		
	}

	

	@Test 
	public void testLimitedPool() throws Exception {
		
		int maxActive = 2;  
		
		BlockingConnectionPool pool = new BlockingConnectionPool(500, maxActive, 2L * 60L * 1000L);

		Server server = new Server(50);
		new Thread(server).start();
		
		startWorkers("localhost", server.getLocalPort(), pool, 10);
		
		do {
			QAUtil.sleep(200);
			Assert.assertTrue(pool.getNumActive() <= maxActive);
		} while (running > 0);

		
		for (String error : errors) {
			System.out.println("error: " + error);
		}

		Assert.assertTrue(errors.size() == 0);

		
		QAUtil.sleep(1000);
		Assert.assertTrue("idle size should be 0 not " + pool.getNumIdle(), pool.getNumIdle() == 0);
		
		pool.close();
		server.close();		
	}

	
	@Test 
	public void testLimitedPoolWaitTimeout() throws Exception {
		int maxActive = 2;  
	
		BlockingConnectionPool pool = new BlockingConnectionPool(60L * 1000L, maxActive, 2L * 60L * 1000L);
		pool.setMaxWaitMillis(500);

		Server server = new Server(10L * 60L * 1000L);
		new Thread(server).start();

		startWorkers("localhost", server.getLocalPort(), pool, maxActive);  // all connections will  be taken

		QAUtil.sleep(300);

		try {
			pool.getBlockingConnection("0.0.0.0", server.getLocalPort());
			Assert.fail("WaitTimeoutEception should have been thrown");
		} catch (WaitTimeoutException expected) {
			// expected
		}
		
		

		pool.close();
		server.close();		
	}

	
	@Test 
	public void testDestroyConnection() throws Exception {		
		BlockingConnectionPool pool = new BlockingConnectionPool(60L * 60L * 1000L);

		Server server = new Server(50);
		new Thread(server).start();

		
		IBlockingConnection connection = pool.getBlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);
		
		Assert.assertTrue(pool.getNumActive() == 1);
		Assert.assertTrue(pool.getNumIdle() == 0);
		
		connection.close();
		Assert.assertTrue(pool.getNumActive() == 0);
		Assert.assertTrue(pool.getNumIdle() == 1);

		connection = pool.getBlockingConnection("localhost", server.getLocalPort());
		Assert.assertTrue(pool.getNumActive() == 1);
		Assert.assertTrue(pool.getNumIdle() == 0);
		
		pool.destroyConnection(connection);
		Assert.assertTrue(pool.getNumActive() == 0);
		Assert.assertTrue(pool.getNumIdle() == 0);
		

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

							con.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);

							con.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
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
	
	

	private static class Server implements Runnable {
		private ExecutorService executorService = Executors.newCachedThreadPool();
		private boolean isRunning = true;

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
		private boolean isRunning = true;
		
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
