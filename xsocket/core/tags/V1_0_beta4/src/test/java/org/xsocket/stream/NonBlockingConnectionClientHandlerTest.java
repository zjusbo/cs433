// $Id: NonBlockingConnectionClientHandlerTest.java 899 2007-02-11 13:49:26Z grro $
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
import java.nio.BufferUnderflowException;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.TestUtil;
import org.xsocket.stream.IConnectHandler;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;
import org.xsocket.stream.NonBlockingConnection;



/**
*
* @author grro@xsocket.org
*/
public final class NonBlockingConnectionClientHandlerTest {

	private static final String DELIMITER = "\r\n";

		

	
	@Test public void testDataHandler() throws Exception {
		IMultithreadedServer testServer = new MultithreadedServer(new ServerHandler());
		new Thread(testServer).start();

		ClientDataHandler clientDataHandler = new ClientDataHandler();
		INonBlockingConnection connection = new NonBlockingConnection(testServer.getLocalAddress(), testServer.getLocalPort(), clientDataHandler, 1);
		
		do {
			TestUtil.sleep(100);
		} while(clientDataHandler.msg == null);
		clientDataHandler.msg = null;
		
		connection.write("helo echo" + DELIMITER);
		do {
			TestUtil.sleep(100);
		} while(clientDataHandler.msg == null);
		Assert.assertTrue(clientDataHandler.msg.equals("helo echo"));
		clientDataHandler.msg = null;		
		
		connection.close();
		testServer.shutdown();
	}


	
	
	
	private static final class ClientDataHandler implements IDataHandler {
		
		private String msg = null;
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			msg = connection.readStringByDelimiter(DELIMITER);
			return true;
		}		
	}
	
	
	private static final class ServerHandler implements IDataHandler, IConnectHandler {
		

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.write("hello" + DELIMITER);
			return true;
		}
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			String word = connection.readStringByDelimiter(DELIMITER);
			
			if (word.equals("CLOSE")) {
				connection.close();
			} else {
				connection.write(word + DELIMITER);
			}
			
			return true;
		}
	}
}
