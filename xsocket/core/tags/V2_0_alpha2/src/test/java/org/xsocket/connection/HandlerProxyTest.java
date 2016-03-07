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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.Resource;
import org.xsocket.connection.HandlerProxy;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IConnectionScoped;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.IDisconnectHandler;
import org.xsocket.connection.IHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.NonBlockingConnection;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;


public class HandlerProxyTest {


	
	@Test
	public void testNullHandler() throws Exception {
		IServer server = new Server(0, new EchoHandler());
		ConnectionUtils.start(server);
		INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort());

		  
		IHandler proxy = HandlerProxy.newPrototype(null, null).newProxy(con);
		
		
		((IConnectHandler) proxy).onConnect(con);
		((IDataHandler) proxy).onData(con);
		((IIdleTimeoutHandler) proxy).onIdleTimeout(con);
		
		
		Assert.assertFalse(con.isOpen());
		server.close();
	}

	
	
	@Test
	public void testInjectServer() throws Exception {
		IServer server = new Server(0, new EchoHandler());
		ConnectionUtils.start(server);
		INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort());

		
		MultiThreadedDisconnectHandler hdl = new MultiThreadedDisconnectHandler();
		HandlerProxy prototype = HandlerProxy.newPrototype(hdl, server);
		prototype.onInit();
		
		Assert.assertEquals(server, hdl.server);
		
		
		server.close();
		con.close();
	}


	
	@Test
	public void testNonConnectionScopedInstance() throws Exception {	
		IServer server = new Server(0, new EchoHandler());
		ConnectionUtils.start(server);
		INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort());


		MultiThreadedDisconnectHandler hdl = new MultiThreadedDisconnectHandler();
		HandlerProxy prototype = HandlerProxy.newPrototype(hdl, null);
		
		IHandler proxy1 = prototype.newProxy(con);
		((IDisconnectHandler) proxy1).onDisconnect(con);
		
		QAUtil.sleep(200);
		Assert.assertEquals(1, hdl.countOnDisconnectCalled.intValue());
		
		IHandler proxy2 = prototype.newProxy(con);
		((IDisconnectHandler) proxy2).onDisconnect(con);
		
		QAUtil.sleep(200);
		Assert.assertEquals(2, hdl.countOnDisconnectCalled.intValue());
		
		con.close();
		server.close();
	}

	
	@Test
	public void testConnectionScopedInstance() throws Exception {	
		IServer server = new Server(0, new EchoHandler());
		ConnectionUtils.start(server);
		INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort());


		ConnectionScopedMultiThreadedDisconnectHandler hdl = new ConnectionScopedMultiThreadedDisconnectHandler();
		HandlerProxy prototype = HandlerProxy.newPrototype(hdl, null);
		
		IHandler proxy1 = prototype.newProxy(con);
		((IDisconnectHandler) proxy1).onDisconnect(con);
		
		QAUtil.sleep(200);
		Assert.assertEquals(0, hdl.countOnDisconnectCalled.intValue());
		
		IHandler proxy2 = prototype.newProxy(con);
		((IDisconnectHandler) proxy2).onDisconnect(con);
		
		QAUtil.sleep(200);
		Assert.assertEquals(0, hdl.countOnDisconnectCalled.intValue());
		
		con.close();
		server.close();
	}

	
		
	
	
	
	@Test
	public void testNonThreaded() throws Exception {
		NonThreadedDisconnectHandler hdl = new NonThreadedDisconnectHandler();
		IHandler proxy = HandlerProxy.newPrototype(hdl, null).newProxy(null);
		
		((IConnectHandler) proxy).onConnect(null);
		((IDataHandler) proxy).onData(null);
		((IDisconnectHandler) proxy).onDisconnect(null);
		
		Assert.assertEquals(1, hdl.countOnDisconnectCalled.get());
		Assert.assertTrue(Thread.currentThread().getName().equals(hdl.threadName));
	}
	

	@Test
	public void testMultiThreaded() throws Exception {
		IServer server = new Server(0, new EchoHandler());
		ConnectionUtils.start(server);
		INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort());

		
		MultiThreadedDisconnectHandler hdl = new MultiThreadedDisconnectHandler();
		IHandler proxy = HandlerProxy.newPrototype(hdl, null).newProxy(con);
		
		((IConnectHandler) proxy).onConnect(con);
		((IDataHandler) proxy).onData(con);
		((IDisconnectHandler) proxy).onDisconnect(con);

		QAUtil.sleep(200);
		
		Assert.assertEquals(1, hdl.countOnDisconnectCalled.get());
		Assert.assertFalse(Thread.currentThread().getName().equals(hdl.threadName));

		
		server.close();
		con.close();
	}

	
	@Test
	public void testConcurrent() throws Exception {
		//QAUtil.setLogLevel(HandlerProxy.class.getName(), Level.FINE);
		
		Handler hdl = new Handler();
		IServer server = new Server(0, hdl);
		ConnectionUtils.start(server);
		
		
		INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort());
		for (int i = 0; i < 100; i++) {
			con.write("test");
			QAUtil.sleep(i);
		}

		
		Assert.assertEquals(1, hdl.maxConnecurrent);
		
		con.close();
		server.close();
		con.close();
	}

	
	
	@Execution(Execution.Mode.NONTHREADED)
	private static final class NonThreadedDisconnectHandler implements IDisconnectHandler {
		
		@Resource
		private IServer server = null;
		
		private AtomicInteger countOnDisconnectCalled = new AtomicInteger();
		private String threadName = null;
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			threadName = Thread.currentThread().getName();
			countOnDisconnectCalled.incrementAndGet();
			return true;
		}
	}
	
	
	
	
	@Execution(Execution.Mode.MULTITHREADED)
	private static final class MultiThreadedDisconnectHandler implements IDisconnectHandler {
	
		@Resource
		private IServer server = null;
		
		private AtomicInteger countOnDisconnectCalled = new AtomicInteger();
		private String threadName = null;
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			threadName = Thread.currentThread().getName();
			countOnDisconnectCalled.incrementAndGet();
			return true;
		}
	}

	
	private static final class ConnectionScopedMultiThreadedDisconnectHandler implements IDisconnectHandler, IConnectionScoped {
		
		@Resource
		private IServer server = null;
		
		private AtomicInteger countOnDisconnectCalled = new AtomicInteger();
		private String threadName = null;
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			threadName = Thread.currentThread().getName();
			countOnDisconnectCalled.incrementAndGet();
			return true;
		}
		
		@Override
		public Object clone() throws CloneNotSupportedException {
			ConnectionScopedMultiThreadedDisconnectHandler copy = (ConnectionScopedMultiThreadedDisconnectHandler) super.clone();
			copy.countOnDisconnectCalled = new AtomicInteger();
			copy.threadName = null;
			return copy;
		}
	}

	
	
	private static final class Handler implements IDataHandler {

		private AtomicInteger countConcurrent = new AtomicInteger();
		private int maxConnecurrent = 0;
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			if (countConcurrent.incrementAndGet() > maxConnecurrent) {
				maxConnecurrent = countConcurrent.get();
			}
			
			QAUtil.sleep(5);
			
			countConcurrent.decrementAndGet();
			return true;
		}
	}

}