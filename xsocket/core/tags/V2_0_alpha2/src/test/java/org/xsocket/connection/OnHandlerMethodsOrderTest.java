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
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;



import org.junit.Assert;
import org.junit.Test;
import org.xsocket.ILifeCycle;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.IDisconnectHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;



/**
*
* @author grro@xsocket.org
*/
public final class OnHandlerMethodsOrderTest {
	
	private enum State { CONNECTED, DISCONNECTED };
	
	private static final String DELIMITER = "\r\n";
	private static final int CLIENTS = 20;
	private static final int LOOPS = 50;
	
	private List<String> clientErrors = new ArrayList<String>();
	private int running = 0;
	
	
	@Test 
	public void testWithoutTimeout() throws Exception {		
		clientErrors = new ArrayList<String>();
		running = 0;
		
		MyHandler hdl = new MyHandler();
		final IServer server = new Server(hdl);
		server.setWorkerpool(Executors.newCachedThreadPool(new MyThreadFactory()));
		ConnectionUtils.start(server);

		
		running = CLIENTS;
		for (int i = 0; i < CLIENTS; i++) {
			Thread client = new Thread() {
				@Override
				public void run() {
					try {
						IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
						connection.setAutoflush(false);
						
						for (int j = 0; j < LOOPS; j++) {
							String request = "test";
							connection.write(request);
							connection.write(DELIMITER);
							connection.flush();
							String result = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
							
							if (!result.equals(request)) {
								clientErrors.add("Error send " + request + " but got " + result);
							}
						}
						
						
						connection.close();
						
					} catch (Exception e) {
						e.printStackTrace();
					}
					running--;
				}
			};
			client.start();
		}
	
		
		do {
			QAUtil.sleep(250);
		} while (running > 0);
		
		server.close();
		
		validateResult(hdl);
	}

	

	@Test 
	public void testIdleTimeout() throws Exception {
		clientErrors = new ArrayList<String>();
		running = 0;

		MyHandler hdl = new MyHandler();
		IServer server = new Server(hdl);
		server.setWorkerpool(Executors.newCachedThreadPool(new MyThreadFactory()));
		server.setIdleTimeoutSec(1);
		ConnectionUtils.start(server);
		
		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);
		
		String request = "test";
		connection.write(request);
		connection.write(DELIMITER);
		connection.flush();

		QAUtil.sleep(1500);
		Assert.assertTrue(hdl.onIdleTimeoutCalled);
		Assert.assertFalse(hdl.onConnectionTimeoutCalled);
		Assert.assertTrue(hdl.onDisconnectCalled);
		validateResult(hdl);
		
		server.close();
	}


	@Test 
	public void testConnectionTimeout() throws Exception {
		clientErrors = new ArrayList<String>();
		running = 0;

		MyHandler hdl = new MyHandler();
		IServer server = new Server(hdl);
		server.setWorkerpool(Executors.newCachedThreadPool(new MyThreadFactory()));
		server.setIdleTimeoutSec(5);
		server.setConnectionTimeoutSec(1);
		ConnectionUtils.start(server);

		
		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);
		
		String request = "test";
		connection.write(request);
		connection.write(DELIMITER);
		connection.flush();

		QAUtil.sleep(1500);
		Assert.assertFalse(hdl.onIdleTimeoutCalled);
		Assert.assertTrue(hdl.onConnectionTimeoutCalled);
		Assert.assertTrue(hdl.onDisconnectCalled);
		validateResult(hdl);
		
		server.close();
		
		validateResult(hdl);
	}


	
	
	private void validateResult(MyHandler hdl) {
		
		if (clientErrors.size() > 0) {
			System.out.println("client error occured");
			for (String error : clientErrors) {
				System.out.println(error);
			}
		}

		if (hdl.errors.size() > 0) {
			System.out.println("server error occured");
			for (String error : hdl.errors) {
				System.out.println(error);
			}
		}

		
		Assert.assertTrue("client error occured", clientErrors.size() == 0);
		Assert.assertTrue("server error occured", hdl.errors.size() == 0);
	}
	
	
	private static final class MyHandler implements IConnectHandler, IDataHandler, IDisconnectHandler, ILifeCycle, IIdleTimeoutHandler, IConnectionTimeoutHandler {
		
		private final List<String> errors = new ArrayList<String>();

		private boolean isInitialized = false;
		private boolean isDestroyed = false;
		
		
		private boolean onConnectCalled = false;
		private boolean onDisconnectCalled = false;
		private boolean onIdleTimeoutCalled = false;
		private boolean onConnectionTimeoutCalled = false;

		
		public void onInit() {
			if (Thread.currentThread().getName().startsWith(MyThreadFactory.PREFIX)) {
				errors.add("[onInit] shouldn't be executed by a worker thread not by " + Thread.currentThread().getName());
			}
			
			if (Thread.currentThread().getName().startsWith("xDispatcher")) {
				errors.add("[onInit] shouldn't be executed by a disptacher thread not by " + Thread.currentThread().getName());
			}

			
			if (isInitialized) {
				errors.add("[onInit]  shouldn't be initialized");
			}
			
			if (isDestroyed) {
				errors.add("[onInit]  shouldn't be destroyed");				
			}
			
			isInitialized = true;
		}
		
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			onConnectCalled = true;
			
			if (!Thread.currentThread().getName().startsWith(MyThreadFactory.PREFIX)) {
				errors.add("[onConnect] should be executed by a worker thread not by " + Thread.currentThread().getName());
			}
			

			if (!isInitialized) {
				errors.add("[onConnect]  should be initialized");
			}
			
			if (isDestroyed) {
				errors.add("[onConnect]  shouldn't be isDestroyed");				
			}
			
			
			State state = (State) connection.getAttachment();
			if (state != null) {
				errors.add("[onConnect] connection is in state " + state + ". should be not in a state");
			} else {
				connection.setAttachment(State.CONNECTED);
			}

			connection.setAutoflush(false);
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			if (!Thread.currentThread().getName().startsWith(MyThreadFactory.PREFIX)) {
				errors.add("[onData] should be executed by a worker thread not by " + Thread.currentThread().getName());
			}

			if (!isInitialized) {
				errors.add("[onData]  should be initialized");
			}
			
			if (isDestroyed) {
				errors.add("[onData]  shouldn't be isDestroyed");				
			}
			
			State state = (State) connection.getAttachment();
			if (state == null) {
				errors.add("[onData] connection doesn't contains state attachment (state should be connected)");
			} else {
				if (state != State.CONNECTED) {
					errors.add("[on data] connection should be in state connected. current state is " + state);
				}
			}
			
			connection.write(connection.readByteBufferByDelimiter(DELIMITER, Integer.MAX_VALUE));
			connection.write(DELIMITER);
			
			connection.flush();
			return true;
		}
		
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			onDisconnectCalled = true;
			
			if (!Thread.currentThread().getName().startsWith(MyThreadFactory.PREFIX)) {
				errors.add("[onDisconnect] should be executed by a worker thread not by " + Thread.currentThread().getName());
			}
			
			if (!isInitialized) {
				errors.add("[onDisconnect]  should be initialized");
			}
			
			if (isDestroyed) {
				errors.add("onDisconnect]  shouldn't be isDestroyed");				
			}
			
			
			State state = (State) connection.getAttachment();
			if (state == null) {
				errors.add("[onDisconnect] connection doesn't contains state attachment (state should be connected)");
			} else {
				if (state != State.CONNECTED) {
					errors.add("[onDisconnect] connection  should be in state connected. not in " + state);
				} else {
					connection.setAttachment(State.DISCONNECTED);
				}
			}
			return true;
		}

		
		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
			onConnectionTimeoutCalled = true;
			
			if (!Thread.currentThread().getName().startsWith(MyThreadFactory.PREFIX)) {
				errors.add("[ConnectionTimeout] should be executed by a worker thread not by " + Thread.currentThread().getName());
			}
			
			if (!isInitialized) {
				errors.add("[ConnectionTimeout]  should be initialized");
			}
			
			if (isDestroyed) {
				errors.add("ConnectionTimeout]  shouldn't be isDestroyed");				
			}

			
			State state = (State) connection.getAttachment();
			if (state == null) {
				errors.add("[onConnectionTimeout] connection doesn't contains state attachment (state should be connected)");
			} else {
				if (state != State.CONNECTED) {
					errors.add("[ConnectionTimeout] connection  should be in state connected. not in " + state);
				} 
			}
			return false;
		}
		
		
		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
			onIdleTimeoutCalled = true;
			
			if (!Thread.currentThread().getName().startsWith(MyThreadFactory.PREFIX)) {
				errors.add("[ConnectionTimeout] should be executed by a worker thread");
			}
			
			if (!isInitialized) {
				errors.add("[ConnectionTimeout]  should be initialized");
			}
			
			if (isDestroyed) {
				errors.add("ConnectionTimeout]  shouldn't be isDestroyed");				
			}

			
			State state = (State) connection.getAttachment();
			if (state == null) {
				errors.add("[onIdleTimeout] connection doesn't contains state attachment (state should be connected)");
			} else {
				if (state != State.CONNECTED) {
					errors.add("[IdleTimeout] connection should be in state connected. not in " + state);
				}
			}
			
			return false;
		}
		
		
		public void onDestroy() {
			if (Thread.currentThread().getName().startsWith(MyThreadFactory.PREFIX)) {
				errors.add("[onInit] shouldn't be executed by a worker thread not by " + Thread.currentThread().getName());
			}
			if (Thread.currentThread().getName().startsWith("xDispatcher")) {
				errors.add("[onInit] shouldn't be executed by a disptacher thread");
			}
			
			
			if (!isInitialized) {
				errors.add("[onDestroy]  should be initialized");
			}
			
			if (isDestroyed) {
				errors.add("onDestroy]  shouldn't be isDestroyed");				
			}
			
			isInitialized = true;

			
		}
		
		List<String> getErrors() {
			return errors;
		}
	}
	
	
	private static class MyThreadFactory implements ThreadFactory {
		
		static final String PREFIX = "WORKER";
		private int num = 0;
		
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r);
			t.setName(PREFIX + (++num));
			t.setDaemon(true);
			t.setPriority(Thread.NORM_PRIORITY);
			return t;
		}
	}
}
