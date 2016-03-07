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
package org.xsocket.server.smtp.ssl;




import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import junit.framework.JUnit4TestAdapter;
import junit.textui.TestRunner;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xsocket.server.IMultithreadedServer;
import org.xsocket.server.MultithreadedSSLServer;
import org.xsocket.server.smtp.SmtpProtocolHandler;



/**
*
* @author grro@xsocket.org
*/
public final class SSLServerTest {

	private IMultithreadedServer sslTestServer = null;
	private  int port = 7724;
	
	//  workaround missing Junit4 support for maven 2 (see http://jira.codehaus.org/browse/SUREFIRE-31)  
	public void setUp() throws Exception {
//	@Before public void setUp() throws Exception {
		do {
			try {
				sslTestServer = new MultithreadedSSLServer(port, "SSLTestSrv", new SSLTestContextFactory());
	
				sslTestServer.setDispatcherPoolSize(1);
				sslTestServer.setWorkerPoolSize(1);
				sslTestServer.setReceiveBufferPreallocationSize(51200);

				sslTestServer.setHandler(new SmtpProtocolHandler(port));
				
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
		
	//  workaround missing Junit4 support for maven 2
	public void tearDown() {
//	@After public void tearDown() {
		sslTestServer.shutdown();
	}

	
	
	@Test public void testMixed() throws Exception {
		// workaround missing Junit4 support for maven 2
		setUp();
		
        for (int i = 0; i < 3; i++) {
        	boolean result = new SendSslTestMail().send("127.0.0.1", port);
        	if (result) {
        		System.out.print(".");
        	}
		}
        
		// workaround missng Junit4 support for maven 2
		tearDown();
	}
		
	
	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(SSLServerTest.class);
	}
		
	
	public static void main (String... args) {
		Logger logger = Logger.getLogger("org.xsocket");
		logger.setLevel(Level.INFO);
		ConsoleHandler hdl = new ConsoleHandler();
		hdl.setLevel(Level.FINE);
		logger.addHandler(hdl);

		TestRunner.run(suite());
	}
		
}
