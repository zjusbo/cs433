// $Id: DataPackagesTest.java 448 2006-12-08 14:55:13Z grro $
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
import java.nio.BufferUnderflowException;

import java.util.Random;


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
public final class DataPackagesTest extends AbstractServerTest {

	private static final String DELIMITER = "\r\n\r\n\r";

	private byte[] request = null;
	
	
	private IMultithreadedServer server = null;
	
	
	@Before public void setUp() {
		server = createServer(7312, new TestHandler()); 
	}
	

	@After public void tearDown() {
		server.shutdown();
	}




	@Test public void testData() throws Exception {
		setUp(); // maven bug work around

		server.setReceiveBufferPreallocationSize(4);

		IBlockingConnection connection = new BlockingConnection("127.0.0.1", server.getPort());
		request = getRandomByteArray(5555);
		connection.write(request);
		connection.write(DELIMITER);

		byte[] response = connection.receiveBytesByDelimiter(DELIMITER);

		for (int i = 0; i < request.length; i++) {
			Assert.assertTrue(equals(request, response));
		}

		connection.close();
		tearDown(); // maven bug work around
	}

	private byte[] getRandomByteArray(int size) {
		Random random = new Random();
		byte[] bytes = new byte[size];
		random.nextBytes(bytes);
		return bytes;
	}


	private static boolean equals(byte[] array1, byte[] array2) {
		for (int i = 0; i < array1.length; i++) {
			if (array1[i] != array2[i]) {
				return false;
			}
		}

		return true;
	}



	private static class TestHandler implements IDataHandler {

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			byte[] buffer = connection.readBytesByDelimiter(DELIMITER);
if (buffer.length != 5555) {
	System.out.println("???");
}
			connection.write(buffer);
			connection.write(DELIMITER);
			
			return true;
		}

	}
}
