// $Id: AdhocSSLTest.java 448 2006-12-08 14:55:13Z grro $
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
import java.nio.BufferUnderflowException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xsocket.BlockingConnection;
import org.xsocket.IBlockingConnection;
import org.xsocket.INonBlockingConnection;
import org.xsocket.LogFormatter;
import org.xsocket.NonBlockingConnection;
import org.xsocket.SSLTestContextFactory;



/**
*
* @author grro@xsocket.org
*/
public final class AdhocSSLTest {

	private static final String SSL_ON = "SSL_ON"; 
	private static final String DELIMITER = System.getProperty("line.separator");
	
	private static MultithreadedServer sslTestServer = null;
	private static int port = 7737;
	

	@BeforeClass public static void setUp() throws Exception {
		//Logger logger = Logger.getLogger("org.xsocket");
		//logger.setLevel(Level.WARNING);
		//ConsoleHandler hdl = new ConsoleHandler();
		//hdl.setLevel(Level.FINEST);
		//hdl.setFormatter(new LogFormatter());
		//logger.addHandler(hdl);

		
		
		do {
			try {
				sslTestServer = new MultithreadedServer(port, "SSLTestSrv", false, new SSLTestContextFactory().getSSLContext());
				sslTestServer.setHandler(new ServerHandler());
				
				Thread server = new Thread(sslTestServer);
				server.start();
		
				do {
					try {
						Thread.sleep(250);
					} catch (InterruptedException ignore) { }
				} while (!sslTestServer.isRunning());
				
			} catch (Exception be) {
				port++;
				sslTestServer = null;
			}
		} while (sslTestServer == null);

		
	}
		
	@AfterClass public static void tearDown() {
		sslTestServer.shutdown();
	}

	
	@Test public void testBlocking() throws Exception {
		setUp(); // maven bug work around
		
		IBlockingConnection connection = new BlockingConnection("127.0.0.1", port, new SSLTestContextFactory().getSSLContext(), false);

		connection.write("test" + DELIMITER);
		String response = connection.receiveStringByDelimiter(DELIMITER);
		Assert.assertEquals("test", response);

		connection.write(SSL_ON + DELIMITER);
		response = connection.receiveStringByDelimiter(DELIMITER);

		connection.startSSL();
		Assert.assertEquals(SSL_ON, response);
		
		connection.write("protected" + DELIMITER);
		response = connection.receiveStringByDelimiter(DELIMITER);
		Assert.assertEquals("protected", response);
		
		connection.close();
		
		tearDown(); // maven bug work around
	}
	
	

	@Test public void testNonBlockingMissingSSLFactory() throws Exception {
		setUp(); // maven bug work around

		INonBlockingConnection connection = new NonBlockingConnection("127.0.0.1", port);
		connection.write(SSL_ON + DELIMITER);
		String response = receive(connection, DELIMITER);
		Assert.assertEquals(SSL_ON, response);

		try {
			connection.startSSL();
			connection.write("testi" + DELIMITER);
			receive(connection, DELIMITER);
	
			Assert.fail("exception should have been thrown");
		} catch (IOException ioe) {
			// should been thrown because sslFactory is missing
		}

		
		connection.close();
		tearDown(); // maven bug work around
	}
	
	

	@Test public void testNonBlocking() throws Exception {
		setUp(); // maven bug work around

		INonBlockingConnection connection = new NonBlockingConnection("127.0.0.1", port, new SSLTestContextFactory().getSSLContext(), false);
		connection.write(SSL_ON + DELIMITER);
		String response = receive(connection, DELIMITER);
		Assert.assertEquals(SSL_ON, response);

		connection.startSSL();
		connection.write("testi" + DELIMITER);
		response = receive(connection, DELIMITER);
		Assert.assertEquals("testi", response);

		connection.close();
		tearDown(); // maven bug work around		
	}

	
	private String receive(INonBlockingConnection connection, String delimiter) throws IOException {
		String response = null;
		do {
			try {
				response = connection.readStringByDelimiter(delimiter);
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
			String word = connection.readStringByDelimiter(DELIMITER);
			connection.write(word + DELIMITER);
			
			if (word.equals(SSL_ON)) {
				connection.startSSL();
			}

			return true;
		}
	}
}
