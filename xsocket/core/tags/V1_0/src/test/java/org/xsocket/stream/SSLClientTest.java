// $Id: SSLClientTest.java 1017 2007-03-15 08:03:05Z grro $
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
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ServerSocketFactory;



import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xsocket.SSLTestContextFactory;
import org.xsocket.TestUtil;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.NonBlockingConnection;

/**
*
* @author grro@xsocket.org
*/
public final class SSLClientTest {

	private static final String DELIMITER = System.getProperty("line.separator");


	private static Server server = null;


	
	@BeforeClass public static void setUp() throws Exception {
		//TestUtil.setLogLevel(Level.FINE);
		
		server = new Server(0);
		server.start();
	}

	
	@AfterClass public static void tearDown() throws Exception {
		if (server != null) {
			server.shutdown();
		}
	}

	
	
	@Test public void testBlocking() throws Exception {
		setUp(); // maven bug work around

		IBlockingConnection connection = new BlockingConnection("127.0.0.1", server.getLocalPort(), new SSLTestContextFactory().getSSLContext(), true);
		
		connection.write("test"+ DELIMITER);
		connection.flush();
		
		String response = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		connection.close();
		
		Assert.assertEquals("test", response);
		tearDown(); // maven bug work around
	}
	
	
	
	@Test public void testNonBlocking() throws Exception {
		setUp(); // maven bug work around

		INonBlockingConnection connection = new NonBlockingConnection("127.0.0.1", server.getLocalPort(), new SSLTestContextFactory().getSSLContext(), true);
		connection.setAutoflush(true);
		
		connection.write("test" + DELIMITER);
		
		String response = receive(connection, DELIMITER);
		connection.close();
		
		Assert.assertEquals("test", response);
		
		tearDown(); // maven bug work around
	}
	
	
	@Test public void testDelayedNonBlocking() throws Exception {
		setUp(); // maven bug work around

		INonBlockingConnection connection = new NonBlockingConnection("127.0.0.1", server.getLocalPort(), new SSLTestContextFactory().getSSLContext(), true);
		connection.setAutoflush(true);
		connection.write("test" + DELIMITER);
		
		String response = receive(connection, DELIMITER);
		Assert.assertEquals("test", response);

		long start = System.currentTimeMillis(); 
		connection.setWriteTransferRate(2);
		connection.write("testi" + DELIMITER);
		response = receive(connection, DELIMITER);
		TestUtil.assertTimeout(System.currentTimeMillis() - start, 3000, 4500);   // 7 Bytes / 2 bytesSec -> 3,5 sec
		
		Assert.assertEquals("testi", response);

		connection.close();
		tearDown(); // maven bug work around
	}
	
	
	private String receive(INonBlockingConnection connection, String delimiter) throws IOException {
		String response = null;
		do {
			try {
				response = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
			} catch (BufferUnderflowException bue) { 
				try {
					Thread.sleep(50);
				} catch (InterruptedException ignore) { }
			}
		} while (response == null);
		
		return response;
	}


	private static final class Server {
		
		private ServerSocket ssocket = null;
		private boolean isRunning = true;
		
		private Thread server = null;
	
		private List<Worker> activeWorkers = new ArrayList<Worker>();
		
        private Server(int port) throws IOException {
    		ServerSocketFactory socketFactory = new SSLTestContextFactory().getSSLContext().getServerSocketFactory();
            ssocket = socketFactory.createServerSocket(port);
        }   
    		
        
        int getLocalPort() {
        	return ssocket.getLocalPort();
        }
		
        void start() {
        	server = new Thread("server") {
        		@Override
        		public void run() {
        			try {
	        			while (isRunning) {
	        				Socket socket = ssocket.accept();
	        				if (socket != null) {
	        					Worker worker = new Worker(socket);
	        					worker.start();
	        					activeWorkers.add(worker);
	        				}
	        			}
        			} catch (Exception ignore) { }
        		}
        	};
        	
        	server.start();
        }
        
        void shutdown() {
        	isRunning = false;
        	
        	for (Worker worker : activeWorkers) {
				worker.shutdown();
			}
        	
        	try {
        		ssocket.close();
        	} catch (Exception ignore) { }
        }
	}
	
	
	private static final class Worker extends Thread {
		
		private Socket socket = null;
		private LineNumberReader in = null;
        private PrintWriter out = null;
        
        private boolean isRunning = true;
        

		Worker(Socket socket) throws IOException {
			super("worker");
			setDaemon(true);
			
			this.socket = socket;
	        in = new LineNumberReader(new InputStreamReader(socket.getInputStream()));
	        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
		}
		
		@Override
		public void run() {
			
			while(isRunning) {
				try {
					String request = in.readLine();
					if (request != null) {
						out.println(request);
						out.flush();
					}
				} catch (IOException ioException) { }
			} while (isRunning);
			
			try {
				in.close();
				out.close();
			} catch (Exception ignore) { }
		}		


		   void shutdown()  {
	        	isRunning = false;
	        	
        		try {
            		socket.close();
    				in.close();
    				out.close();
    			} catch (Exception ignore) { }
		   }
	}
}
