// $Id: FlushOnCloseTest.java 764 2007-01-15 06:26:17Z grro $
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
public final class FlushOnCloseTest {

	private static final String DELIMITER = "x"; 

	private static final String QUIT_COMMAND = "QUIT";
	private static final String OK_RESPONSE = "OK";
	private static final String CLOSED_RESPONSE = "THIS Connection has been CLOSED";
	

	


 

	@Test public void testSimple() throws Exception {
		//TestUtil.setLogLevel(Level.FINE);

		IMultithreadedServer server = new MultithreadedServer(new ServerHandler());
		new Thread(server).start();


		IBlockingConnection connection = new BlockingConnection("127.0.0.1", server.getLocalPort());
		connection.setAutoflush(false);

		connection.write("hello" + DELIMITER);
		connection.flush();
		
		Assert.assertTrue(connection.receiveStringByDelimiter(DELIMITER).equals(OK_RESPONSE));

		connection.write("some command" + DELIMITER);
		connection.flush();
		
		Assert.assertTrue(connection.receiveStringByDelimiter(DELIMITER).equals(OK_RESPONSE));

		connection.write(QUIT_COMMAND + DELIMITER);
		connection.flush();
		
		Assert.assertTrue(connection.receiveStringByDelimiter(DELIMITER).equals(CLOSED_RESPONSE));
	
		connection.close();
		
		server.shutdown();
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
