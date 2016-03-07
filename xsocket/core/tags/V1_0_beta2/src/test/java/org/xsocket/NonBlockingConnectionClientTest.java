// $Id: NonBlockingConnectionClientTest.java 439 2006-12-06 06:43:30Z grro $
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
package org.xsocket;


import java.io.IOException;
import java.nio.BufferUnderflowException;


import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;


import org.xsocket.server.IConnectHandler;
import org.xsocket.server.IDataHandler;
import org.xsocket.server.MultithreadedServer;


/**
*
* @author grro@xsocket.org
*/
public final class NonBlockingConnectionClientTest {

	private static final String DELIMITER = "\r\n";


	private static MultithreadedServer testServer = null;
	private static int port = 7768;


	
	@BeforeClass public static void setUp() throws Exception {
		
		do {
			try {
				testServer = new MultithreadedServer(port);
		
				testServer.setDispatcherPoolSize(3);
				testServer.setWorkerPoolSize(6);
				testServer.setReceiveBufferPreallocationSize(3);

				testServer.setHandler(new ServerHandler());
				
				Thread server = new Thread(testServer);
				server.start();
		
				do {
					try {
						Thread.sleep(250);
					} catch (InterruptedException ignore) { }
				} while (!testServer.isRunning());
			
			} catch (Exception be) {
				port++;
				testServer = null;
			}
		} while (testServer == null);
	}

	
	@AfterClass public static void tearDown() throws Exception {
		testServer.shutdown();
	}

	
	
	@Test public void testSimple() throws Exception {
		setUp(); // maven bug work around

		SendTask sendTask = new SendTask("127.0.0.1", port, "testi@semta.de");
		do {
			sendTask.perform();
			
			try {
				Thread.sleep(10);
			} catch (InterruptedException ignore) { };
			
		} while (!sendTask.isDone());
		tearDown(); // maven bug work around
	}
	
	
	
	@Test public void testDelayedWrite() throws Exception {
		setUp(); // maven bug work around

		INonBlockingConnection connection = new NonBlockingConnection("127.0.0.1", port);
		
		receive(connection, DELIMITER);
		
		
		byte[] tenByteRequest = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 0 };
		
		// call without delay
		long start = System.currentTimeMillis();
		connection.write(tenByteRequest);
		connection.write(DELIMITER);
		byte[] response = receive(connection, DELIMITER);
		long elapsed = System.currentTimeMillis() - start;
		
		Assert.assertTrue(isEquals(tenByteRequest, response));
		Assert.assertTrue(inTimeRange(elapsed, 0, 500)); 

		

		// call with 2 sec delay
		connection.setWriteTransferRate(5);

		start = System.currentTimeMillis();
		connection.write(tenByteRequest);
		connection.write(DELIMITER);
		response = receive(connection, DELIMITER);
		elapsed = System.currentTimeMillis() - start;
		
		Assert.assertTrue(isEquals(tenByteRequest, response));
		Assert.assertTrue(inTimeRange(elapsed, 2000, 3000));  // 10 byte / 5 bytesPerSec -> 2,5 sec

		
		

		// call with 4 sec delay
		connection.setWriteTransferRate(3);

		start = System.currentTimeMillis();
		connection.write(tenByteRequest);
		connection.write(DELIMITER);
		response = receive(connection, DELIMITER);
		elapsed = System.currentTimeMillis() - start;
		
		Assert.assertTrue(isEquals(tenByteRequest, response));
		Assert.assertTrue(inTimeRange(elapsed, 2800, 4500)); // 10 byte / 3 bytesPerSec -> 3,3333  sec

		
		// send with no delay
		connection.setWriteTransferRate(3);

		start = System.currentTimeMillis();
		connection.write(tenByteRequest);
		connection.setWriteTransferRate(INonBlockingConnection.UNLIMITED);
		connection.write(DELIMITER);
		response = receive(connection, DELIMITER);
		elapsed = System.currentTimeMillis() - start;
		
		Assert.assertTrue(isEquals(tenByteRequest, response));
		Assert.assertTrue(inTimeRange(elapsed, 0, 500));

		
		
		// send with no delay
		start = System.currentTimeMillis();
		connection.write(tenByteRequest);
		connection.write(DELIMITER);
		response = receive(connection, DELIMITER);
		elapsed = System.currentTimeMillis() - start;
		
		Assert.assertTrue(isEquals(tenByteRequest, response));
		Assert.assertTrue(inTimeRange(elapsed, 0, 500));
				
		connection.close();

		tearDown(); // maven bug work around
	}
	
	
	private boolean isEquals(byte[] array1, byte[] array2) {
		if (array1.length != array2.length) {
			return false;
		}
		
		for (int i = 0; i < array1.length; i++) {
			if (array1[i] !=array2[i]) {
				return false;
			}
		}
		
		return true;
	}
	
	private byte[] receive(INonBlockingConnection connection, String delimiter) throws ClosedConnectionException, IOException {
		byte[] response = null;
		do {
			try {
				response = connection.readBytesByDelimiter(delimiter);
			} catch (BufferUnderflowException bue) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException ignore) { }
			}
		} while (response == null);
		
		return response;
	}
	
	
	private boolean inTimeRange(long time, long min, long max) {
		System.out.println("elapsed=" + time + " (min=" + min + ", max=" + max + ")");
		return ((time >= min) && (time <= max));
	}
	

	
	private static final class SendTask {
		private static final int PRE_CONNECT = 0; 
		private static final int WAIT_FOR_GREETING = 1;
		private static final int WAIT_FOR_SENDER = 2;
		private static final int WAIT_FOR_RECEIVER = 3;
		private static final int WAIT_FOR_DATA = 4;
		private static final int WAIT_FOR_MAIL = 5;
		private static final int WAIT_FOR_QUIT = 6;
		private static final int DONE = 9;
		
		private int state = PRE_CONNECT;
		
		private String hostname = null;
		private int port = 0; 
		private String sender = null; 
		
		private INonBlockingConnection connection = null;
		
		public SendTask(String hostname, int port, String sender) {
			this.hostname = hostname;
			this.port = port;
			this.sender = sender;
		}

		
		public void perform() {
		
			try {
				switch (state) {
					case PRE_CONNECT:
						connection = new NonBlockingConnection(hostname, port);
						state = WAIT_FOR_GREETING;
					break;
					
					case WAIT_FOR_GREETING:
						String greeting = connection.readStringByDelimiter(DELIMITER);
						if (!greeting.equals("hello")) {
							throw new IOException("Wrong greeting " + greeting);
						}
						
						connection.write("HELO response" + DELIMITER);
						state = WAIT_FOR_SENDER;
					break;

					case WAIT_FOR_SENDER:
						String response = connection.readStringByDelimiter(DELIMITER);
						if (!response.equals("HELO response")) {
							throw new IOException("Wrong response " + response);
						}
						
						connection.write("QUIT" + DELIMITER);
						connection.close();
						state = DONE;
					break;

					case DONE:
						
					break;
					
					
				default:
					break;
				}
				
				
			} catch (BufferUnderflowException ignore) {
				// not enough data available -> wait for data
				
			} catch (Exception e) {
				if (connection != null) {
					try {
						connection.close();
					} catch (Exception ignore) { }
				}
			}
		}
		
		public boolean isDone() {
			return (state == DONE);
		}
	}
	
	
	private static final class ServerHandler implements IDataHandler, IConnectHandler {
		

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.write("hello" + DELIMITER);
			return true;
		}
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			String word = connection.readStringByDelimiter(DELIMITER);
			connection.write(word + DELIMITER);
			return true;
		}
	}
}
