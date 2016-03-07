/*
 *  Copyright (c) xsocket.org, 2006 - 2009. All rights reserved.
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


import java.nio.BufferUnderflowException;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.SSLTestContextFactory;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.Server;



/**
*
* @author grro@xsocket.org
*/
public final class SSLUserActivateAndDeactivateTest {

	
	@Test
	public void testSimpleBulk() throws Exception {
		for (int i = 0; i < 100; i++) {
			System.out.print(i +  " run");
			testSimple();
		}
	}
	
	

	
	@Test
	public void testSimple() throws Exception {
		Server sslTestServer = new Server(0, new ServerHandler(), SSLTestContextFactory.getSSLContext(), false);
		sslTestServer.start();

		IBlockingConnection connection = new BlockingConnection("localhost", sslTestServer.getLocalPort(), SSLTestContextFactory.getSSLContext(), false);

		connection.deactivateSecuredMode(); // should be ignored

		connection.write("cmd_plain\r\n");
		Assert.assertEquals("cmd_plain", connection.readStringByDelimiter("\r\n")); 
		
		connection.write("cmd_activateSSL\r\n");
		Assert.assertEquals("cmd_activateSSL", connection.readStringByDelimiter("\r\n"));
		connection.activateSecuredMode();
		
		connection.activateSecuredMode(); // should be ignored
		
		connection.write("cmd_securedOne\r\n");
		Assert.assertEquals("cmd_securedOne", connection.readStringByDelimiter("\r\n")); 
		
		connection.write("cmd_deactivateSSL\r\n");
		Assert.assertEquals("cmd_deactivateSSL", connection.readStringByDelimiter("\r\n"));
		
		connection.deactivateSecuredMode();

		connection.write("cmd_plain\r\n");
		Assert.assertEquals("cmd_plain", connection.readStringByDelimiter("\r\n")); 
		
		connection.write("cmd_activateSSL\r\n");
		Assert.assertEquals("cmd_activateSSL", connection.readStringByDelimiter("\r\n"));
		connection.activateSecuredMode();
		
		connection.write("cmd_securedOne\r\n");
		Assert.assertEquals("cmd_securedOne", connection.readStringByDelimiter("\r\n")); 
		
		connection.write("cmd_deactivateSSL\r\n");
		Assert.assertEquals("cmd_deactivateSSL", connection.readStringByDelimiter("\r\n"));
		
		connection.deactivateSecuredMode();

		connection.write("cmd_plain\r\n");
		Assert.assertEquals("cmd_plain", connection.readStringByDelimiter("\r\n")); 
	
		connection.close();
		sslTestServer.close();
	}




	private static final class ServerHandler implements IDataHandler {

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			String cmd = connection.readStringByDelimiter("\r\n");
			
			if (cmd.startsWith("cmd_activateSSL")) {
				connection.write("cmd_activateSSL\r\n");
				connection.activateSecuredMode();
				
			} else if (cmd.startsWith("cmd_deactivateSSL")) {
				connection.write("cmd_deactivateSSL\r\n");
				connection.deactivateSecuredMode();
				
			} else {
				connection.write(cmd + "\r\n");
			}
			return true;
		}
	}


}
