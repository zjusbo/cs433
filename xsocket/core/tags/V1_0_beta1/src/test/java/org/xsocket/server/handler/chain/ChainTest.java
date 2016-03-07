// $Id$
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
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import junit.framework.JUnit4TestAdapter;
import junit.textui.TestRunner;


import org.junit.Assert;
import org.junit.Test;

import org.xsocket.ClosedConnectionException;
import org.xsocket.LogFormatter;
import org.xsocket.server.IConnectHandler;
import org.xsocket.server.IConnectionScoped;
import org.xsocket.server.IDataHandler;
import org.xsocket.server.INonBlockingConnection;
import org.xsocket.server.ITimeoutHandler;


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

	
	
	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(ChainTest.class);
	}
		
	
	public static void main (String... args) {
		Logger logger = Logger.getLogger("org.xsocket");
		logger.setLevel(Level.INFO);
		ConsoleHandler hdl = new ConsoleHandler();
		hdl.setLevel(Level.FINE);
		hdl.setFormatter(new LogFormatter());
		logger.addHandler(hdl);
		
		TestRunner.run(suite());
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

	
	
	private static class ConnectHandler implements IConnectHandler {
		
		private boolean returnHandled = false;
		private boolean hasBeenVisited = false;
		
		public ConnectHandler(boolean returnHandled) {
			this.returnHandled = returnHandled;
		}
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			hasBeenVisited = true;
			return returnHandled;
		}
		
		public boolean hasBeenVisited() {
			return hasBeenVisited;
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
		
		public String getId() {
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
		
		public int getNumberOfAvailableBytes() {
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
		
		public long readLong() throws IOException, BufferUnderflowException {
			return 0;
		}
		
		public ByteBuffer[] readRecord(String delimiter) throws IOException, BufferUnderflowException {
			return null;
		}
		
		public String readWord(String delimiter) throws IOException, BufferUnderflowException {
			return null;
		}
		
		public String readWord(String delimiter, String encoding) throws IOException, BufferUnderflowException {
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
		
		public int writeByte(byte b) throws ClosedConnectionException, IOException {
			return 0;
		}
		
		public int writeDouble(double d) throws ClosedConnectionException, IOException {
			return 0;
		}
		
		public int writeInt(int i) throws ClosedConnectionException, IOException {
			return 0;
		}
		
		public int writeLong(long l) throws ClosedConnectionException, IOException {
			return 0;
		}
		
		public int writeWord(String s) throws ClosedConnectionException, IOException {
			return 0;
		}
		
		public int writeWord(String s, String encoding) throws ClosedConnectionException, IOException {
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
		

		public boolean readAvailable(String delimiter, WritableByteChannel outputChannel) throws IOException {
			return false;
		}
		
		
		public long getIdleTimeout() {
			return 0;
		}
	}
}
