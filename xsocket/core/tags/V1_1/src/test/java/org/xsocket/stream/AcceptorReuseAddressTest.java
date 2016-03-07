// $Id: FlushOnCloseTest.java 1017 2007-03-15 08:03:05Z grro $
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
import java.net.InetAddress;
import java.nio.BufferUnderflowException;

import org.junit.Assert;
import org.junit.Test;




/**
*
* @author grro@xsocket.org
*/
public final class AcceptorReuseAddressTest {
	
	private static final String DELIMITER = "\r"; 
	
	
	@Test public void testTakenPort() throws Exception {
		Handler hdl = new Handler();
		MultithreadedServer server = new MultithreadedServer(hdl);
		StreamUtils.start(server);
		
		int port = server.getLocalPort();
		
		Handler hdl2 = new Handler();
		
		server.close();

		IMultithreadedServer server2 = new MultithreadedServer(port, hdl2);
		Thread t2 = new Thread(server2);
		t2.start();
		server.close();
		
		sendData(server.getLocalAddress(), server.getLocalPort());
		
		server2.close();
		
		Assert.assertFalse(hdl.hasReceived);
		Assert.assertTrue(hdl2.hasReceived);
	}


	
	private void sendData(InetAddress inetAddress, int port) throws IOException {
		IBlockingConnection connection = new BlockingConnection(inetAddress, port);
		connection.setAutoflush(false);
		
		String request = "test";
		connection.write(request + DELIMITER);
		connection.flush();
		String response = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		Assert.assertEquals(request, response);
		connection.close();
	}
	
	
	
	private static final class Handler implements IConnectHandler, IDataHandler {
		
		private boolean hasReceived = false; 
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			String data = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
			connection.write(data);
			connection.write(DELIMITER);
			hasReceived = true;
			
			connection.flush();
			return true;
		}
	}
}
