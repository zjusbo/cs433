// $Id: DataTypesTest.java 899 2007-02-11 13:49:26Z grro $
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
import java.util.logging.Logger;



import org.junit.Assert;
import org.junit.Test;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IConnectHandler;
import org.xsocket.stream.IConnectionScoped;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;





/**
*
* @author grro@xsocket.org
*/
public final class DataTypesTest {

	private static final Logger LOG = Logger.getLogger(DataTypesTest.class.getName());
		
	

	
	@Test public void testMixed() throws Exception {
		//TestUtil.setLogLevel(Level.FINE);
		
		IMultithreadedServer server = new MultithreadedServer(new DataTypesTestServerHandler());
		new Thread(server).start();

		server.setReceiveBufferPreallocationSize(3);

		
		
		IBlockingConnection connection = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
		connection.setAutoflush(false);


		double d = connection.readDouble();
		Assert.assertTrue("received value ist not excepted value ", d == 45.45);

		int i = connection.readInt();
		Assert.assertTrue("received value ist not excepted value ", i == 56);

		long l = connection.readLong();
		Assert.assertTrue("received value ist not excepted value ", l == 11);

		byte[] bytes = connection.readBytesByDelimiter("r");
		byte[] expected = new byte[] {2, 78, 45, 78, 23, 11, 45, 78, 12, 56};
		for(int j = 0; j < bytes.length; j++) {
			if (bytes[j] != expected[j]) {
				Assert.fail("received value ist not excepted value ");
			}
		}
		
		String w = connection.readStringByLength(5);
		Assert.assertEquals(w, "hello");

		
		connection.write(33.33);
		connection.flush();
		
		connection.write((int) 11);
		connection.flush();
		
		connection.write((long) 33);
		connection.flush();
		
		connection.write("\r\n");
		connection.flush();
		
		connection.write("this is the other end tt");
		connection.flush();
		
		connection.write((byte) 34);
		connection.flush();
		
		connection.write(bytes);
		connection.flush();
		
		connection.write("r");
		connection.flush();
				
		connection.write("you");
		connection.flush();

		double d2 = connection.readDouble();
		Assert.assertTrue("received value ist not excepted value ", d2 == 65.65);
		
		server.shutdown();
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
