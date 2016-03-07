// $Id: FlushOnCloseTest.java 1748 2007-09-17 08:19:23Z grro $
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
import java.util.logging.Level;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IConnectionScoped;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;
import org.xsocket.stream.IConnection.FlushMode;



/**
*
* @author grro@xsocket.org
*/
public final class FlushOnCloseTest {

	private static final String DELIMITER = "x";

	private static final String QUIT_COMMAND = "QUIT";
	private static final String OK_RESPONSE = "OK";
	private static final String CLOSED_RESPONSE = "THIS Connection has been CLOSED";







	@Test
	public void testSimple() throws Exception {
		//QAUtil.setLogLevel(Level.FINEST);

		IMultithreadedServer server = new MultithreadedServer(new ServerHandler());
		StreamUtils.start(server);


		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);

		connection.write("hello" + DELIMITER);
		connection.flush();

		Assert.assertTrue("OK response for hello expected", connection.readStringByDelimiter(DELIMITER).equals(OK_RESPONSE));

		connection.write("some command" + DELIMITER);
		connection.flush();

		Assert.assertTrue("OK response for some command expected", connection.readStringByDelimiter(DELIMITER).equals(OK_RESPONSE));

		connection.write(QUIT_COMMAND + DELIMITER);
		connection.flush();

		Assert.assertTrue("close response for quit expected", connection.readStringByDelimiter(DELIMITER).equals(CLOSED_RESPONSE));

		connection.close();

		server.close();
	}




	private static class ServerHandler implements IDataHandler, IConnectionScoped {

		public boolean onData(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			connection.setFlushmode(FlushMode.ASYNC);

			String request = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
			connection.setWriteTransferRate(5);

			if (request.equals(QUIT_COMMAND)) {
				connection.write(CLOSED_RESPONSE + DELIMITER);
				connection.close();
			} else {
				connection.write(OK_RESPONSE + DELIMITER);
			}

			connection.flush();
			return true;
		}

		@Override
		public Object clone() throws CloneNotSupportedException {
			return super.clone();
		}
	}
}
