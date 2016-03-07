// $Id: CloseTest.java 764 2007-01-15 06:26:17Z grro $
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
import org.xsocket.stream.IConnectionScoped;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;



/**
*
* @author grro@xsocket.org
*/
public final class CloseTest {

	private static final Logger LOG = Logger.getLogger(CloseTest.class.getName());
	
	
	private static final String DELIMITER = "x"; 

	private static final String QUIT_COMMAND = "QUIT";
	private static final String OK_RESPONSE = "OK";
	private static final String CLOSED_RESPONSE = "CLOSED";
	

	private int open = 0;
	
	



	@Test public void testSimple() throws Exception {
		IMultithreadedServer server = new MultithreadedServer(new ServerHandler());
		new Thread(server).start();

		call(server.getLocalPort());
		
		server.shutdown();
	}

	
	@Test public void testBulk() throws Exception {

		final IMultithreadedServer server = new MultithreadedServer(new ServerHandler());
		new Thread(server).start();


		for (int i = 0; i < 5; i++) {
			Thread t = new Thread(new Runnable() {
				public void run() {
					open++;
					for (int j = 0; j < 3; j++) {
						try {
							call(server.getLocalPort());
						} catch (IOException ioe) {
							Assert.fail(ioe.toString());
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
		
		server.shutdown();
	}

	private void call(int srvPort) throws IOException {
		IBlockingConnection connection = new BlockingConnection("127.0.0.1", srvPort);
		connection.setReceiveTimeout(1000);
				
		connection.write("hello" + DELIMITER);
		Assert.assertTrue(connection.receiveStringByDelimiter(DELIMITER).equals(OK_RESPONSE));
				
		connection.write("some command" + DELIMITER);
		Assert.assertTrue(connection.receiveStringByDelimiter(DELIMITER).equals(OK_RESPONSE));
		
		
		connection.write(QUIT_COMMAND + DELIMITER);
		Assert.assertTrue(connection.receiveStringByDelimiter(DELIMITER).equals(CLOSED_RESPONSE));
		
		connection.close();
	}


	
	private static class ServerHandler implements IDataHandler, IConnectionScoped {
		
		public boolean onData(INonBlockingConnection connection) throws IOException {
			String request = connection.readStringByDelimiter(DELIMITER);
			
			if (request.equals(QUIT_COMMAND)) {
				connection.write(CLOSED_RESPONSE + DELIMITER);
				LOG.fine("[" + connection.getId() + "] server-side closing of connection");
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
