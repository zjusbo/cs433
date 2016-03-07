// $Id: ChainTest.java 444 2006-12-07 06:28:54Z grro $
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
package org.xsocket.server.handler.chain;


import java.io.IOException;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;


import org.junit.Assert;
import org.junit.Test;

import org.xsocket.ClosedConnectionException;
import org.xsocket.INonBlockingConnection;
import org.xsocket.server.IConnectHandler;
import org.xsocket.server.IConnectionScoped;
import org.xsocket.server.IDataHandler;
import org.xsocket.server.IHandler;
import org.xsocket.server.IHandlerContext;
import org.xsocket.server.ILifeCycle;
import org.xsocket.server.ITimeoutHandler;
import org.xsocket.server.MultithreadedServer;
import org.xsocket.server.Resource;


/**
*
* @author grro@xsocket.org
*/
public final class ChainTest {
	
	
	@Test public void testCloneChain() throws Exception {
		Chain chain  = new Chain();

		DataHandler dh1 = new DataHandler(false);
		ConnectionScopedDataHandler dh2 = new ConnectionScopedDataHandler();
		chain.addLast(dh1);
		chain.addLast(dh2);

		Chain innerChain  = new Chain();
		DataHandler dhi1 = new DataHandler(false);
		ConnectionScopedDataHandler dhi2 = new ConnectionScopedDataHandler();
		innerChain.addLast(dhi1);
		innerChain.addLast(dhi2);
		chain.addLast(innerChain);
		
		
		Chain copy = (Chain) chain.clone();
		Assert.assertTrue(dh1 == copy.getHandler(0));
		Assert.assertFalse(dh2 == copy.getHandler(1));
		Assert.assertFalse(innerChain == copy.getHandler(2));
		
		Chain innerCopy = (Chain) ((Chain) copy.getHandler(2)).clone();
		Assert.assertTrue(dhi1 == innerCopy.getHandler(0));
		Assert.assertFalse(dhi2 == innerCopy.getHandler(1));

		
		
		
		Chain chain2  = new Chain();
		DataHandler dh21 = new DataHandler(false);
		DataHandler dh22 = new DataHandler(false);
		chain2.addLast(dh21);
		chain2.addLast(dh22);

		Chain chain3 = new Chain();
		chain3.addLast(chain2);
		
		ConnectionScopedDataHandler dh24 = new ConnectionScopedDataHandler();
		chain3.addLast(dh24);
		
		Chain copy2 = (Chain) chain3.clone();
		Assert.assertTrue(chain2 == copy2.getHandler(0));
		Assert.assertFalse(dh24 == copy2.getHandler(1));
	}
	
		
	@Test public void testDataHandlerChain() throws Exception {
		Chain chain  = new Chain();
		DataHandler dh1 = new DataHandler(false);
		DataHandler dh2 = new DataHandler(false);
		DataHandler dh3 = new DataHandler(true);
		DataHandler dh4 = new DataHandler(false);
		
		chain.addLast(dh1);
		chain.addLast(dh2);
		chain.addLast(dh3);
		
		chain.onData(new DummyNonBlockingConnection());
		
		Assert.assertTrue(dh1.hasBeenVisited());
		Assert.assertTrue(dh2.hasBeenVisited());
		Assert.assertTrue(dh3.hasBeenVisited());
		Assert.assertFalse(dh4.hasBeenVisited());
		
	}
	

	@Test public void testMixedChain() throws Exception {
		Chain chain  = new Chain();
		ConnectDataHandler ch1 = new ConnectDataHandler(false);
		DataHandler ch2 = new DataHandler(false);
		ConnectHandler ch3 = new ConnectHandler(true);
		DataHandler ch4 = new DataHandler(false);
		
		chain.addLast(ch1);
		chain.addLast(ch2);
		chain.addLast(ch3);
		chain.addLast(ch4);
		
		chain.onConnect(new DummyNonBlockingConnection());
		
		Assert.assertTrue(ch1.hasBeenVisited());
		Assert.assertFalse(ch2.hasBeenVisited());
		Assert.assertTrue(ch3.hasBeenVisited());
		Assert.assertFalse(ch4.hasBeenVisited());
	}

	
	
	@Test public void testTimeoutChain() throws Exception {
		Chain chain  = new Chain();
		TimeoutHandler th1 = new TimeoutHandler(false);
		TimeoutHandler th2 = new TimeoutHandler(false);
		chain.addLast(th1);
		chain.addLast(th2);

		
		Chain innerChain = new Chain();
		TimeoutHandler thi1 = new TimeoutHandler(false);
		TimeoutHandler thi2 = new TimeoutHandler(true);
		TimeoutHandler thi3 = new TimeoutHandler(true);
		innerChain.addLast(thi1);
		innerChain.addLast(thi2);
		innerChain.addLast(thi3);
		
		chain.addLast(innerChain);

		
		chain.onConnectionTimeout(new DummyNonBlockingConnection());
		
		Assert.assertTrue(th1.hasConnectBeenVisited());
		Assert.assertTrue(th2.hasConnectBeenVisited());
		Assert.assertTrue(thi1.hasConnectBeenVisited());
		Assert.assertTrue(thi2.hasConnectBeenVisited());
		Assert.assertFalse(thi3.hasConnectBeenVisited());
	}
	
	@Test public void testLifeCycleChain() throws Exception {
		int port = 7348;
		MultithreadedServer server = null;
		
		ConnectHandler ch = null;
		do {
			Chain chain  = new Chain();
			TimeoutHandler th1 = new TimeoutHandler(false);
			TimeoutHandler th2 = new TimeoutHandler(false);
			chain.addLast(th1);
			chain.addLast(th2);
	
			
			Chain innerChain = new Chain();
			ch = new ConnectHandler(true);
			innerChain.addLast(ch);
			chain.addLast(innerChain);
			
			Assert.assertFalse(ch.hasInited());
			Assert.assertFalse(ch.hasDestroyed());
			
			server = startServer(5673, chain);
			port++;
		} while (server == null); 
			
		Assert.assertTrue(ch.hasInited());
		Assert.assertFalse(ch.hasDestroyed());
		
		server.shutdown();
		Assert.assertTrue(ch.hasInited());
		Assert.assertTrue(ch.hasDestroyed());
	}
	
	
	@Test public void testHandlerContext() throws Exception {
		int port = 7248;
		MultithreadedServer server = null;
		
		TimeoutHandler th1 = null;
		do {
			Chain chain  = new Chain();
			th1 = new TimeoutHandler(false);
			TimeoutHandler th2 = new TimeoutHandler(false);
			chain.addLast(th1);
			chain.addLast(th2);
	
			
			Chain innerChain = new Chain();
			ConnectHandler ch = new ConnectHandler(true);
			innerChain.addLast(ch);
			chain.addLast(innerChain);
			
			
			server = startServer(7553, chain);
			port++;
		} while (server == null); 
		
		Assert.assertNotNull(th1.getHandlerContext());
		
		server.shutdown();
	}
	
	
	private MultithreadedServer startServer(int port, IHandler handler) throws Exception {
		MultithreadedServer server = null;
		try {
			server = new MultithreadedServer(port);
					
			server.setDispatcherPoolSize(3);
			server.setWorkerPoolSize(12);
			server.setReceiveBufferPreallocationSize(4096);
			server.setConnectionTimeoutSec(60 * 60);
			server.setIdleTimeoutSec(10 * 60);
		
			server.setHandler(handler);
				
			Thread t = new Thread(server);
			t.start();
				
			do {
				try {
					Thread.sleep(10);
				} catch (InterruptedException ignore) { }
			} while (!server.isRunning());
					
			return server;
		} catch (Exception be) {
			server.shutdown();
			return null;
		} 
	}
		 



	
	private static class DataHandler implements IDataHandler {
		
		private boolean returnHandled = false;
		private boolean hasBeenVisited = false;
		
		public DataHandler(boolean returnHandled) {
			this.returnHandled = returnHandled;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			hasBeenVisited = true;
			return returnHandled;
		}
		
		public boolean hasBeenVisited() {
			return hasBeenVisited;
		}
	}

	
	
	private static class ConnectHandler implements IConnectHandler, ILifeCycle {
		
		private boolean returnHandled = false;
		private boolean hasBeenVisited = false;
		private boolean hasInited = false;
		private boolean hasDestroyed = false;
		
		public ConnectHandler(boolean returnHandled) {
			this.returnHandled = returnHandled;
		}
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			hasBeenVisited = true;
			return returnHandled;
		}
		
		public void onInit() {
			hasInited = true;
		}
		
		public void onDestroy() {
			hasDestroyed = true;
		}
		
		
		public boolean hasBeenVisited() {
			return hasBeenVisited;
		}
		
		public boolean hasInited() {
			return hasInited;
		}
		
		public boolean hasDestroyed() {
			return hasDestroyed;
		}
	}
	
	private static class ConnectDataHandler implements IConnectHandler, IDataHandler {
		
		private boolean returnHandled = false;
		private boolean hasBeenVisited = false;
		
		public ConnectDataHandler(boolean returnHandled) {
			this.returnHandled = returnHandled;
		}
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			hasBeenVisited = true;
			return returnHandled;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			hasBeenVisited = true;
			return returnHandled; 
		}
		
		public boolean hasBeenVisited() {
			return hasBeenVisited;
		}
	}

	
	private static class TimeoutHandler implements ITimeoutHandler {
		
		@Resource
		private IHandlerContext ctx;
		
		private boolean returnHandled = false;
		private boolean hasConnectBeenVisited = false;
		private boolean hasIdleBeenVisited = false;
		
		public TimeoutHandler(boolean returnHandled) {
			this.returnHandled = returnHandled;
		}
		
		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
			hasConnectBeenVisited = true;
			return returnHandled;
		}

		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
			hasIdleBeenVisited = true;
			return returnHandled;
		}

		
		public boolean hasConnectBeenVisited() {
			return hasConnectBeenVisited;
		}
		
		public boolean hasIdleBeenVisited() {
			return hasIdleBeenVisited;
		}
		
		public IHandlerContext getHandlerContext() {
			return ctx;
		}
	}

	
	
	private static class ConnectionScopedDataHandler implements IDataHandler, IConnectionScoped {

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			return false;
		}
		
		@Override
		public Object clone() throws CloneNotSupportedException {
			return super.clone();
		}
	}
	
	
	private static class DummyNonBlockingConnection implements INonBlockingConnection {
		public void close() {
		}
		
		public long getConnectionOpenedTime() {
			return 0;
		}
		
		public String getDefaultEncoding() {
			return null;
		}
		
		public void flush() throws ClosedConnectionException, IOException {
		}
		
		public String getId() {
			return null;
		}
		
		public ByteBuffer[] readByteBufferByLength(int length) throws IOException, ClosedConnectionException, BufferUnderflowException {
			return null;
		}
		
		public byte[] readBytesByLength(int length) throws IOException, ClosedConnectionException, BufferUnderflowException {
			return null;
		}

		public String readStringByLength(int length) throws IOException, ClosedConnectionException, BufferUnderflowException {
			return null;
		}

		public String readStringByLength(int length, String encoding) throws IOException, ClosedConnectionException, BufferUnderflowException {
			return null;
		}
		
		public long getLastReceivingTime() {
			return 0;
		}
		
		public InetAddress getLocalAddress() {
			return null;
		}
		
		public int getLocalePort() {
			return 0;
		}
		
		public byte[] readBytesByDelimiter(String delimiter) throws IOException {
			return null;
		}
		
		public int getNumberOfAvailableBytes() {
			return 0;
		}
		
		public int write(byte... b) throws ClosedConnectionException, IOException {
			return 0;
		}
		
		public int write(byte[] bytes, int offset, int length) throws ClosedConnectionException, IOException {
			return 0;
		}
		
		public InetAddress getRemoteAddress() {
			return null;
		}
		
		public int getRemotePort() {
			return 0;
		}
		
		public boolean isOpen() {
			return true;
		}
		
		public ByteBuffer[] readAvailable() throws IOException {
			return null;
		}
		
		public byte readByte() throws IOException, BufferUnderflowException {
			return 0;
		}
		
		public double readDouble() throws IOException, BufferUnderflowException {
			return 0;
		}
		
		public int readInt() throws IOException, BufferUnderflowException {
			return 0;
		}
		
		public void startSSL() {
			// TODO Auto-generated method stub
			
		}
		
		public void stopSSL() {
			// TODO Auto-generated method stub
			
		}
		
		public void setWriteTransferRate(int delaySec) throws ClosedConnectionException, IOException {
		}
		
		public long readLong() throws IOException, BufferUnderflowException {
			return 0;
		}
		
		public ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException, BufferUnderflowException {
			return null;
		}
		
		public String readStringByDelimiter(String delimiter) throws IOException, BufferUnderflowException {
			return null;
		}
		
		public String readStringByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException {
			return null;
		}
		
		public void setDefaultEncoding(String encoding) {
		}
		
		public void setId(String id) {
		}
				
		public void stopReceiving() {
		}
		
		public int write(ByteBuffer buffer) throws ClosedConnectionException, IOException {
			return 0;
		}
		
		public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
			return 0;
		}
		
		public long write(ByteBuffer[] buffers) throws ClosedConnectionException, IOException {
			return 0;
		}
		
		public int write(byte b) throws ClosedConnectionException, IOException {
			return 0;
		}
		
		public int write(double d) throws ClosedConnectionException, IOException {
			return 0;
		}
		
		public int write(int i) throws ClosedConnectionException, IOException {
			return 0;
		}
		
		public int write(long l) throws ClosedConnectionException, IOException {
			return 0;
		}
		
		public int write(String s) throws ClosedConnectionException, IOException {
			return 0;
		}
		
		public int write(String s, String encoding) throws ClosedConnectionException, IOException {
			return 0;
		}
		
		public String toCompactString() {
			return null;
		}
		
		public long getConnectionTimeout() {
			return 0;
		}
		

		public ByteBuffer[] readAvailable(String delimiter, Boolean delimiterFoundResult) throws IOException {
			return null;
		}
		

		public boolean readAvailableByDelimiter(String delimiter, WritableByteChannel outputChannel) throws IOException {
			return false;
		}
		
		
		public long getIdleTimeout() {
			return 0;
		}
	}
}
