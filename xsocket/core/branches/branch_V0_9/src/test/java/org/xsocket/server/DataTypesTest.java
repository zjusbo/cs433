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
package org.xsocket.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

import junit.framework.JUnit4TestAdapter;
import junit.textui.TestRunner;

import org.junit.Assert;
import org.junit.Test;

import org.xsocket.IBlockingConnection;
import org.xsocket.BlockingConnection;


/**
*
* @author grro@xsocket.org
*/
public final class DataTypesTest {
	
	private static final Logger LOG = Logger.getLogger(DataTypesTest.class.getName());


	private MultithreadedServer testServer = null;
	private int port = 7312;
	
	
	 // workaround -> current version of maven doesn't support junit4
//	@Before protected void setUp() throws Exception {
	private void setUp() throws Exception {
		
		testServer = new MultithreadedServer(port, "DataTypesTestSrv");
		testServer.setDispatcherSize(1);
		testServer.setDispatcherWorkerSize(1);
		testServer.setReceiveBufferPreallocationSize(3);

		testServer.setHandler(new DataTypesTestServerHandler());
				
		Thread server = new Thread(testServer);
		server.start();
		
		do {
			try {
				Thread.sleep(250);
			} catch (InterruptedException ignore) { }
		} while (!testServer.isRunning());
	}
		
	
	 // workaround -> current version of maven doesn't support junit4
//	@After protected void tearDown() {
	private void tearDown() {
		testServer.shutdown();
	}

	
	
	@Test public void testMixed() throws Exception {
		setUp();  // workaround -> current version of maven doesn't support junit4
		
		IBlockingConnection connection = new BlockingConnection(SocketChannel.open(new InetSocketAddress("127.0.0.1", port)), 3);
		

		double d = connection.receiveDouble();
		Assert.assertTrue("received value ist not excepted value ", d == 45.45);
		
		int i = connection.receiveInt();
		Assert.assertTrue("received value ist not excepted value ", i == 56);
		
		long l = connection.receiveLong();
		Assert.assertTrue("received value ist not excepted value ", l == 11);
		
		connection.writeDouble(33.33);
		connection.writeInt(11);
		connection.writeLong(33);
		connection.writeWord("this is the other end tt");
		
		double d2 = connection.receiveDouble();
		Assert.assertTrue("received value ist not excepted value ", d2 == 65.65);
		
		tearDown(); 	 // workaround -> current version of maven doesn't support junit4
	}
		
	
	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(DataTypesTest.class);
	}
		
	
	public static void main (String... args) {
		System.setProperty("java.util.logging.config.file", "src/test/java/logging.properties");
		TestRunner.run(suite());
	}
	
	
	private class DataTypesTestServerHandler implements IDataHandler, IConnectHandler, IConnectionScoped {
		
		int state = 0;
		
		public boolean onConnectionOpening(INonBlockingConnection connection) throws IOException {	
			connection.writeDouble(45.45);			
			connection.writeInt(56);
			connection.writeLong(11);
			
			return true;
		}
			
		public boolean onData(INonBlockingConnection connection) throws IOException {
	
			switch (state) {
			case 0:
				double d = connection.readDouble();
				Assert.assertTrue("received value ist not excepted value ", d == 33.33);
				LOG.fine("double received");
				state = 1;
				break;

			case 1:
				int i = connection.readInt();
				Assert.assertTrue("received value ist not excepted value ", i == 11);
				LOG.fine("int received");
				state = 2;
				break;

			case 2:
				long l = connection.readLong();
				Assert.assertTrue("received value ist not excepted value ", l == 33);
				LOG.fine("long received");				
				state = 3;
				break;

			case 3:
				String s = connection.readWord("tt");
				Assert.assertEquals(s, "this is the other end ");
				LOG.fine("word received");
				
				connection.writeDouble(65.65);			
				LOG.fine("double send");
				break;
			}
						
			return true;
		}
			
		@Override
		public Object clone() throws CloneNotSupportedException {
			return super.clone();
		}
	}
}
