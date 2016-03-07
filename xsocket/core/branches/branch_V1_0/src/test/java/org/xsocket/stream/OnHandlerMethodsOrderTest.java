// $Id: DisconnectTest.java 1060 2007-03-21 18:52:32Z grro $
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



import org.junit.Assert;
import org.junit.Test;
import org.xsocket.DynamicWorkerPool;
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
public final class OnHandlerMethodsOrderTest {
	
	private enum STATE { CONNECTED, DISCONNECTED };
	
	private static final String DELIMITER = "\r\n";
	private static final int CLIENTS = 99;
	private static final int LOOPS = 5;
	
	private List<String> clientErrors = new ArrayList<String>();
	private int running = 0;
	
	
	@Test public void testWithoutTimeout() throws Exception {
		clientErrors = new ArrayList<String>();
		running = 0;
		
		MyHandler hdl = new MyHandler();
		final IMultithreadedServer server = new MultithreadedServer(hdl);
		server.setWorkerPool(new DynamicWorkerPool(3, 250));
		Thread t = new Thread(server);
		t.start();
		
		for (int i = 0; i < CLIENTS; i++) {
			Thread client = new Thread() {
				@Override
				public void run() {
					running++;
					try {
						IBlockingConnection connection = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
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
			QAUtil.sleep(200);
		} while (running > 0);
		
		server.close();
		
		validateResult(hdl);
	}

	

	@Test public void testTimeout() throws Exception {
		clientErrors = new ArrayList<String>();
		running = 0;

		MyHandler hdl = new MyHandler();
		final IMultithreadedServer server = new MultithreadedServer(hdl);
		server.setWorkerPool(new DynamicWorkerPool(3, 250));
		server.setIdleTimeoutSec(500);
		
		IBlockingConnection connection = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
		String request = "test";
		connection.write(request);
		connection.write(DELIMITER);
		connection.flush();

		QAUtil.sleep(1000);

		
		server.setIdleTimeoutSec(5000);
		server.setConnectionTimeoutSec(500);
		
		connection = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
		request = "test";
		connection.write(request);
		connection.write(DELIMITER);
		connection.flush();

		QAUtil.sleep(1000);

		
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
	
	
	private static final class MyHandler implements IConnectHandler, IDataHandler, IDisconnectHandler, ILifeCycle, ITimeoutHandler {
		
		private final Map<String, STATE> clientMap = new HashMap<String, STATE>();
		private final List<String> errors = new ArrayList<String>();

		private boolean isInitialized = false;
		private boolean isDestroyed = false;
		
		public void onInit() {
			if (isInitialized) {
				errors.add("[onInit]  shouldn't be initialized");
			}
			
			if (isDestroyed) {
				errors.add("[onInit]  shouldn't be destroyed");				
			}
			
			isInitialized = true;
		}
		
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			

			if (!isInitialized) {
				errors.add("[onConnect]  should be initialized");
			}
			
			if (isDestroyed) {
				errors.add("[onConnect]  shouldn't be isDestroyed");				
			}
			
			
			String id = connection.getId();
			if (clientMap.containsKey(id)) {
				errors.add("[onConnect] connection " + id  + " is in state " + clientMap.get(id) + ". should be not in list");
			} else {
				clientMap.put(id, STATE.CONNECTED);
			}
			
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			if (!isInitialized) {
				errors.add("[onData]  should be initialized");
			}
			
			if (isDestroyed) {
				errors.add("[onData]  shouldn't be isDestroyed");				
			}
			
			String id = connection.getId();
			if (clientMap.containsKey(id)) {
				if (clientMap.get(id) != STATE.CONNECTED) {
					errors.add("connection " + id + " should be in state connected. current state is " + clientMap.get(id));
				}
			} else {
				errors.add("[onData] connection " + id + " is not in client map (state should be connected)");
			}
			
			connection.write(connection.readByteBufferByDelimiter(DELIMITER, Integer.MAX_VALUE));
			connection.write(DELIMITER);
			
			return true;
		}
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			
			if (!isInitialized) {
				errors.add("[onDisconnect]  should be initialized");
			}
			
			if (isDestroyed) {
				errors.add("onDisconnect]  shouldn't be isDestroyed");				
			}

			
			String id = connection.getId();
			if (clientMap.containsKey(id)) {
				if (clientMap.get(connection.getId()) != STATE.CONNECTED) {
					errors.add("[onDisconnect] connection " + id + " should be in state connected");
				} else {
					clientMap.put(id, STATE.DISCONNECTED);
				}
			} else {
				errors.add("connection " + id + " is not in client map");
			}
			return true;
		}

		
		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
			
			if (!isInitialized) {
				errors.add("[ConnectionTimeout]  should be initialized");
			}
			
			if (isDestroyed) {
				errors.add("ConnectionTimeout]  shouldn't be isDestroyed");				
			}

			
			String id = connection.getId();
			if (clientMap.containsKey(id)) {
				if (clientMap.get(connection.getId()) != STATE.CONNECTED) {
					errors.add("[ConnectionTimeout] connection " + id + " should be in state connected");
				} else {
					clientMap.put(id, STATE.DISCONNECTED);
				}
			} else {
				errors.add("connection " + id + " is not in client map");
			}
			return true;
		}
		
		
		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
			if (!isInitialized) {
				errors.add("[ConnectionTimeout]  should be initialized");
			}
			
			if (isDestroyed) {
				errors.add("ConnectionTimeout]  shouldn't be isDestroyed");				
			}

			
			String id = connection.getId();
			if (clientMap.containsKey(id)) {
				if (clientMap.get(connection.getId()) != STATE.CONNECTED) {
					errors.add("[ConnectionTimeout] connection " + id + " should be in state connected");
				} else {
					clientMap.put(id, STATE.DISCONNECTED);
				}
			} else {
				errors.add("connection " + id + " is not in client map");
			}
			return true;
		}
		
		
		public void onDestroy() {
			
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
}
