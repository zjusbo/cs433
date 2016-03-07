// $Id: CloseTest.java 439 2006-12-06 06:43:30Z grro $
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
public final class CloseTest extends AbstractServerTest {

	private static final String DELIMITER = "x"; 

	private static final String QUIT_COMMAND = "QUIT";
	private static final String OK_RESPONSE = "OK";
	private static final String CLOSED_RESPONSE = "CLOSED";
	

	private IMultithreadedServer server = null;
	
	private int open = 0;
	
	
	@Before public void setUp() {
		server = createServer(7339, new ServerHandler()); 
	}
	

	@After public void tearDown() {
		server.shutdown();
	}




	@Test public void testSimple() throws Exception {
		setUp(); // maven bug work around

		call();
		
		tearDown(); // maven bug work around
	}

	
	@Test public void testBulk() throws Exception {
		setUp(); // maven bug work around

		for (int i = 0; i < 2; i++) {
			Thread t = new Thread(new Runnable() {
				public void run() {
					open++;
					for (int j = 0; j < 2; j++) {
						try {
							call();
						} catch (IOException ioe) {
							ioe.printStackTrace();
						}
					}
					open--;
				}
			});
			
			t.start();
		}
		
		do {
			try {
				Thread.sleep(500);
			} catch (InterruptedException igonre) {	}
		} while (open > 0);
		
		tearDown(); // maven bug work around
	}

	private void call() throws IOException {
		try {
			IBlockingConnection connection = new BlockingConnection("127.0.0.1", server.getPort());
				
			connection.write("hello" + DELIMITER);
			Assert.assertTrue(connection.receiveStringByDelimiter(DELIMITER).equals(OK_RESPONSE));
				
			connection.write("some command" + DELIMITER);
			Assert.assertTrue(connection.receiveStringByDelimiter(DELIMITER).equals(OK_RESPONSE));
		
		
			connection.write(QUIT_COMMAND + DELIMITER);
			Assert.assertTrue(connection.receiveStringByDelimiter(DELIMITER).equals(CLOSED_RESPONSE));
		
			connection.close();
		} catch (Exception be) {
			System.out.print("b");
		}
	}


	
	private static class ServerHandler implements IDataHandler, IConnectionScoped {
		
		public boolean onData(INonBlockingConnection connection) throws IOException {
			String request = connection.readStringByDelimiter(DELIMITER);
			
			if (request.equals(QUIT_COMMAND)) {
				connection.write(CLOSED_RESPONSE + DELIMITER);
				connection.close();
			} else {
				connection.write(OK_RESPONSE + DELIMITER);
			}
			return true;
		}

		@Override
		public Object clone() throws CloneNotSupportedException {
			return super.clone();
		}
	}
}
