// $Id$
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


import java.io.File;

import javax.net.ssl.SSLContext;

import junit.framework.JUnit4TestAdapter;
import junit.textui.TestRunner;

import org.junit.Test;
import org.xsocket.server.IMultithreadedServer;
import org.xsocket.server.MultithreadedSSLServer;


/**
*
* @author grro@xsocket.org
*/
public final class SSLServerTest {

	private IMultithreadedServer sslTestServer = null;
	private int port = 7332;
	
	private SSLContext sslContext = null;
	
	
	 // workaround -> current version of maven doesn't support junit4
//	@Before protected void setUp() throws Exception {
	private void setUp() throws Exception {
	
		
		sslTestServer = new MultithreadedSSLServer(port, "SSLTestSrv", new SSLTestContextFactory());
		sslTestServer.setDispatcherSize(1);
		sslTestServer.setDispatcherWorkerSize(1);
		sslTestServer.setReceiveBufferPreallocationSize(51200);

		sslTestServer.setHandler(new SmtpProtocolHandler("SSLTestSrv", "SmtpMonitor"));
				
		Thread server = new Thread(sslTestServer);
		server.start();
		
		do {
			try {
				Thread.sleep(250);
			} catch (InterruptedException ignore) { }
		} while (!sslTestServer.isRunning());
	}
		
	
	 // workaround -> current version of maven doesn't support junit4
//	@After protected void tearDown() {
	private void tearDown() {
		sslTestServer.shutdown();
	}

	
	
	@Test public void testMixed() throws Exception {
		setUp();  // workaround -> current version of maven doesn't support junit4
		
        for (int i = 0; i < 3; i++) {
        	boolean result = new SendSslTestMail().send("127.0.0.1", port);
        	if (result) {
        		System.out.print(".");
        	}
		}
		
				
		tearDown(); 	 // workaround -> current version of maven doesn't support junit4
	}
		
	
	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(SSLServerTest.class);
	}
		
	
	public static void main (String... args) {
		System.setProperty("java.util.logging.config.file", "src/test/java/logging.properties");
		TestRunner.run(suite());
	}
		
}
