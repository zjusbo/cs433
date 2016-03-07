// $Id: DataTypesTest.java 439 2006-12-06 06:43:30Z grro $
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
import java.util.logging.Logger;



import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.xsocket.IBlockingConnection;
import org.xsocket.BlockingConnection;
import org.xsocket.INonBlockingConnection;




/**
*
* @author grro@xsocket.org
*/
public final class DataTypesTest extends AbstractServerTest {

	private static final Logger LOG = Logger.getLogger(DataTypesTest.class.getName());

	
	private IMultithreadedServer server = null;
	

	
	@Before public void setUp() {
		server = createServer(7376, new DataTypesTestServerHandler()); 
	}
	

	@After public void tearDown() {
		server.shutdown();
	}

	
	@Test public void testMixed() throws Exception {
		setUp(); // maven bug work around

		server.setReceiveBufferPreallocationSize(3);

		
		
		IBlockingConnection connection = new BlockingConnection("127.0.0.1", server.getPort());


		double d = connection.receiveDouble();
		Assert.assertTrue("received value ist not excepted value ", d == 45.45);

		int i = connection.receiveInt();
		Assert.assertTrue("received value ist not excepted value ", i == 56);

		long l = connection.receiveLong();
		Assert.assertTrue("received value ist not excepted value ", l == 11);

		byte[] bytes = connection.receiveBytesByDelimiter("r");
		byte[] expected = new byte[] {2, 78, 45, 78, 23, 11, 45, 78, 12, 56};
		for(int j = 0; j < bytes.length; j++) {
			if (bytes[j] != expected[j]) {
				Assert.fail("received value ist not excepted value ");
			}
		}
		
		String w = connection.receiveStringByLength(5);
		Assert.assertEquals(w, "hello");

		
		connection.write(33.33);
		connection.write((int) 11);
		connection.write((long) 33);
		connection.write("\r\n");
		connection.write("this is the other end tt");
		connection.write((byte) 34);
		connection.write(bytes);
		connection.write("r");
		connection.write("you");

		double d2 = connection.receiveDouble();
		Assert.assertTrue("received value ist not excepted value ", d2 == 65.65);
		
		tearDown(); // maven bug work around
	}

	
	private static final class DataTypesTestServerHandler implements IDataHandler, IConnectHandler, IConnectionScoped {

		int state = 0;

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.write(45.45);
			connection.write((int) 56);
			connection.write((long) 11);
			connection.write(new byte[] {2, 78, 45, 78, 23, 11, 45, 78, 12, 56});
			connection.write("r");
			connection.write("hello");

			return true;
		}

		public boolean onData(INonBlockingConnection connection) throws IOException {

			do {
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
					String s1 = connection.readStringByDelimiter("\r\n");
					Assert.assertEquals(s1, "");
					state = 4;
					break;
	
				case 4:
					String s2 = connection.readStringByDelimiter("tt");
					Assert.assertEquals(s2, "this is the other end ");
					LOG.fine("word received");
	
					state = 5;
					break;
	
				case 5:
					byte b = connection.readByte();
					Assert.assertEquals(b, (byte) 34);
					LOG.fine("byte received");
	
					state = 6;
					break;
	
				case 6:
					byte[] bytes = connection.readBytesByDelimiter("r");
					byte[] expected = new byte[] {2, 78, 45, 78, 23, 11, 45, 78, 12, 56};
					for(int j = 0; j < bytes.length; j++) {
						if (bytes[j] != expected[j]) {
							Assert.fail("received value ist not excepted value ");
						}
					}
	
					state = 7;
					break;
	
				case 7:
					String w = connection.readStringByLength(3);
					Assert.assertEquals(w, "you");
	
					state = 99;
					connection.write(65.65);
					LOG.fine("double send");
					
					break;
				}
			} while (state != 99);


			return true;
		}

		@Override
		public Object clone() throws CloneNotSupportedException {
			return super.clone();
		}
	}
}
