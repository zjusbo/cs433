// $Id: CloseTest.java 412 2006-11-20 18:02:50Z grro $
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
public final class FlushOnCloseTest extends AbstractServerTest {

	private static final String DELIMITER = "x"; 

	private static final String QUIT_COMMAND = "QUIT";
	private static final String OK_RESPONSE = "OK";
	private static final String CLOSED_RESPONSE = "THIS Connection has been CLOSED";
	

	private IMultithreadedServer server = null;
		
	
	@Before public void setUp() {
		server = createServer(7353, new ServerHandler()); 
	}
	

	@After public void tearDown() {
		server.shutdown();
	}


 

	@Test public void testSimple() throws Exception {
		setUp(); // maven bug work around

		IBlockingConnection connection = new BlockingConnection("127.0.0.1", server.getPort());

		connection.write("hello" + DELIMITER);
		Assert.assertTrue(connection.receiveStringByDelimiter(DELIMITER).equals(OK_RESPONSE));

		connection.write("some command" + DELIMITER);
		Assert.assertTrue(connection.receiveStringByDelimiter(DELIMITER).equals(OK_RESPONSE));

		connection.write(QUIT_COMMAND + DELIMITER);
		Assert.assertTrue(connection.receiveStringByDelimiter(DELIMITER).equals(CLOSED_RESPONSE));
	
		connection.close();
		
		tearDown(); // maven bug work around
	}

	

	
	private static class ServerHandler implements IDataHandler, IConnectionScoped {
		
		public boolean onData(INonBlockingConnection connection) throws IOException {
			String request = connection.readStringByDelimiter(DELIMITER);
			connection.setWriteTransferRate(5);
			
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
