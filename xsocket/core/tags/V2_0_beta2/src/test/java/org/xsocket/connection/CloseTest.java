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
import java.net.SocketTimeoutException;
import java.util.logging.Logger;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;



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

	private String error = null;



	@Test
	public void testSimple() throws Exception {
		IServer server = new Server(new ServerHandler());
		ConnectionUtils.start(server);

		call("localhost", server.getLocalPort());

		server.close();
	}


	@Test
	public void testBulk() throws Exception {

		final IServer server = new Server(new ServerHandler());
		ConnectionUtils.start(server);


		for (int i = 0; i < 5; i++) {
			Thread t = new Thread(new Runnable() {
				public void run() {
					open++;
					for (int j = 0; j < 3; j++) {
						try {
							call("localhost", server.getLocalPort());
							System.out.print(".");
						} catch (IOException ioe) {
							System.out.println(ioe.toString());
							error = ioe.toString();
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


		Assert.assertNull(error, error);

		server.close();
	}

	private void call(String srvAddress, int srvPort) throws IOException {
		IBlockingConnection connection = new BlockingConnection(srvAddress, srvPort);
		connection.setAutoflush(true);
		connection.setReceiveTimeoutMillis(1000);

		connection.write("hello" + DELIMITER);
		Assert.assertTrue(connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE).equals(OK_RESPONSE));

		connection.write("some command" + DELIMITER);
		Assert.assertTrue(connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE).equals(OK_RESPONSE));


		connection.write(QUIT_COMMAND + DELIMITER);
		try {
			Assert.assertTrue(connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE).equals(CLOSED_RESPONSE));
		} catch (SocketTimeoutException stoe) {
			System.out.println("socket time out reached by waiting for quit response");
			throw stoe;
		}

		try {
			connection.close();
		} catch (IOException ioe) {
			// ioe should have been thrown, because the connection is closed by the server
		}
	}



	private static class ServerHandler implements IDataHandler {

		public boolean onData(INonBlockingConnection connection) throws IOException {
			String request = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);

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
