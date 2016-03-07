// $Id: SSLTest.java 439 2006-12-06 06:43:30Z grro $
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
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.BufferUnderflowException;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;


import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xsocket.BlockingConnection;
import org.xsocket.IBlockingConnection;
import org.xsocket.INonBlockingConnection;
import org.xsocket.SSLTestContextFactory;



/**
*
* @author grro@xsocket.org
*/
public final class SSLTest {


	private SSLContext sslContext = null;
	private static MultithreadedServer sslTestServer = null;
	private static int port = 6724;
	
	private static final String DELIMITER = System.getProperty("line.separator");
	

	
	@BeforeClass public static void setUp() throws Exception {
		do {
			try {
				sslTestServer = new MultithreadedServer(port, "SSLTestSrv", true, new SSLTestContextFactory().getSSLContext());
				sslTestServer.setHandler(new SSLHandler());
				
				Thread server = new Thread(sslTestServer);
				server.start();
		
				do {
					try {
						Thread.sleep(250);
					} catch (InterruptedException ignore) { }
				} while (!sslTestServer.isRunning());
				
			} catch (Exception be) {
				port++;
				sslTestServer.shutdown();
				sslTestServer = null;
			}
		} while (sslTestServer == null);
	}
		
	@AfterClass public static void tearDown() {
		sslTestServer.shutdown();
	}

	
	@Test public void testDirect() throws Exception {
		setUp();
		
       
		sslContext = new SSLTestContextFactory().getSSLContext();
        SocketFactory socketFactory = sslContext.getSocketFactory();
        Socket socket = socketFactory.createSocket("127.0.0.1", port);
        
        LineNumberReader lnr = new LineNumberReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
        
        for (int i = 0; i < 3; i++) {
        	String req = "hello how are how sdfsfdsf sf sdf sf s sf sdf " + i;
        	pw.write(req + DELIMITER);
        	pw.flush();
        	
        	String res = lnr.readLine();
        	
        	Assert.assertEquals(req, res);
        }
		   
        lnr.close();
        pw.close();
        socket.close();
        
		// workaround missing Junit4 support for maven 2
		tearDown();
	}
	

	@Test public void testXSocket() throws Exception {
		setUp();
		
		IBlockingConnection connection = new BlockingConnection("127.0.0.1", port, new SSLTestContextFactory().getSSLContext(), true);
		connection.write("test" + DELIMITER);
		String response = connection.receiveStringByDelimiter(DELIMITER);
		connection.close();
		
		Assert.assertEquals("test", response);

		tearDown();
	}
	
	
	

	
	private static final class SSLHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			String word = connection.readStringByDelimiter(DELIMITER);
			connection.write(word + DELIMITER);
			return true;
		}
	}
}
