// $Id: AdhocSSLTest.java 1023 2007-03-16 16:27:41Z grro $
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

import org.xsocket.SSLTestContextFactory;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;
import org.xsocket.stream.NonBlockingConnection;



/**
*
* @author grro@xsocket.org
*/
public final class AdhocSSLTest {

	private static final String SSL_ON = "SSL_ON"; 
	private static final String DELIMITER = System.getProperty("line.separator");
	

	
	@Test public void testBlocking() throws Exception {
		//TestUtil.setLogLevel(Level.FINE);
		IMultithreadedServer sslTestServer = new MultithreadedServer(0, new ServerHandler(), false, new SSLTestContextFactory().getSSLContext());
		new Thread(sslTestServer).start();

		IBlockingConnection connection = new BlockingConnection(sslTestServer.getLocalAddress(), sslTestServer.getLocalPort(), new SSLTestContextFactory().getSSLContext(), false);
		connection.setAutoflush(true);

		connection.write("test" + DELIMITER);
		
		String response = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		Assert.assertEquals("test", response);

		connection.write(SSL_ON + DELIMITER);
		
		response = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		
		connection.activateSecuredMode();
		Assert.assertEquals(SSL_ON, response);

		connection.write("protected" + DELIMITER);
		
		response = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		Assert.assertEquals("protected", response);
		
		connection.close();
		sslTestServer.close();
	}
	
	

	@Test public void testNonBlockingMissingSSLFactory() throws Exception {
		IMultithreadedServer sslTestServer = new MultithreadedServer(0, new ServerHandler(), false, new SSLTestContextFactory().getSSLContext());
		new Thread(sslTestServer).start();


		INonBlockingConnection connection = new NonBlockingConnection(sslTestServer.getLocalAddress(), sslTestServer.getLocalPort());
		connection.setAutoflush(true);
		
		connection.write(SSL_ON + DELIMITER);
		
		String response = receive(connection, DELIMITER);
		Assert.assertEquals(SSL_ON, response);

		try {
			connection.activateSecuredMode();
			connection.write("testi" + DELIMITER);
			
			receive(connection, DELIMITER);
	
			Assert.fail("exception should have been thrown");
		} catch (IOException ioe) {
			// should been thrown because sslFactory is missing
		}

		
		connection.close();
		sslTestServer.close();
	}
	
	

	@Test public void testNonBlocking() throws Exception {
		IMultithreadedServer sslTestServer = new MultithreadedServer(0, new ServerHandler(), false, new SSLTestContextFactory().getSSLContext());
		new Thread(sslTestServer).start();

		INonBlockingConnection connection = new NonBlockingConnection(sslTestServer.getLocalAddress(), sslTestServer.getLocalPort(), new SSLTestContextFactory().getSSLContext(), false);
		connection.setAutoflush(true);
		
		connection.write(SSL_ON + DELIMITER);
		
		String response = receive(connection, DELIMITER);
		Assert.assertEquals(SSL_ON, response);

		connection.activateSecuredMode();
		connection.write("testi" + DELIMITER);
		
		response = receive(connection, DELIMITER);
		Assert.assertEquals("testi", response);

		connection.close();
		sslTestServer.close();		
	}

	
	private String receive(INonBlockingConnection connection, String delimiter) throws IOException {
		String response = null;
		do {
			try {
				response = connection.readStringByDelimiter(delimiter, Integer.MAX_VALUE);
			} catch (BufferUnderflowException bue) { 
				try {
					Thread.sleep(50);
				} catch (InterruptedException ignore) { }
			}
		} while (response == null);
		
		return response;
	}

	
	private static final class ServerHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			String word = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
			connection.write(word + DELIMITER);
			
			if (word.equals(SSL_ON)) {				
				connection.activateSecuredMode();
			}

			return true;
		}
	}
}
