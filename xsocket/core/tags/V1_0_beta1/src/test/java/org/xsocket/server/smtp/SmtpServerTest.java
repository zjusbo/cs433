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
package org.xsocket.server.smtp;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import junit.framework.Assert;
import junit.framework.JUnit4TestAdapter;
import junit.textui.TestRunner;


import org.junit.Test;


import org.xsocket.LogFormatter;
import org.xsocket.server.MultithreadedServer;
import org.xsocket.util.TextUtils;


/**
*
* @author grro@xsocket.org
*/
public final class SmtpServerTest {
	
	private MultithreadedServer testServer = null;
	private MailTestHandler handler = null;
	private int port = 7768;

	private List<String> errors = new ArrayList<String>();
	private int open = 0; 

	
	// workaround missing Junit4 support for maven 2 (see http://jira.codehaus.org/browse/SUREFIRE-31)
	public void setUp() throws Exception {
	//@Before public void setUp() throws Exception {
		
		do {
			try {
				testServer = new MultithreadedServer(port);
		
				testServer.setDispatcherPoolSize(3);
				testServer.setWorkerPoolSize(6);
				testServer.setReceiveBufferPreallocationSize(3);

				handler = new MailTestHandler(port);
				testServer.setHandler(handler);
				
				Thread server = new Thread(testServer);
				server.start();
		
				do {
					try {
						Thread.sleep(250);
					} catch (InterruptedException ignore) { }
				} while (!testServer.isRunning());
			
			} catch (Exception be) {
				port++;
				testServer = null;
			}
		} while (testServer == null);
	}

	
	// workaround missing Junit4 support for maven 2
	public void tearDown() {
//	@AfterClass public void tearDown() {
		testServer.shutdown();
	}

	
	
	@Test public void testMixed() throws Exception {		
		// workaround missing Junit4 support for maven 2
		setUp();
		
		final MailSender mailSender = new MailSender();
		
		int loops = 199;
		open = loops; 
		
		Executor executor = Executors.newFixedThreadPool(9);
		
		for (int i = 0; i < loops; i++) {
			final String mail = "Date: Mon, 12 Jun 2006 09:38:31 +0200\r\n"
		  		  + "From: testi@test.org\r\n"
		  		  + "To: buddy@test.org\r\n"
		  		  + "\r\n"
		  		  + generateContent(i + 1) + "\r\n.\r\n";
			
			Runnable task = new Runnable() {
				public void run() {
					String errorMessage = mailSender.send("127.0.0.1", port, mail);
					if(errorMessage == null) {
						System.out.print(".");
					} else {
 						errors.add(errorMessage);
					}
					
					open--;
				}
			};
			
			executor.execute(task);
		}
		
		
		
		do {
			try {
				Thread.sleep(250);
			} catch (InterruptedException ignore) { }
		} while (open > 0);
		
		// workaround missng Junit4 support for maven 2
		tearDown();

		
		if (!errors.isEmpty()) {
			Assert.fail("errors occured");
		}		
	}
	
	private String generateContent(int size) {
		StringBuilder sb = new StringBuilder();
		
		long l = 0;
		int value = 0;
		for (int i = 0; i < size; i++) {
			value++;
			if (value > 9) {
				value = 0;
			}
			
			l += value;
			sb.append(value);
		}
		
		return l + "\n" + sb.toString() + "\nend";
	}
		
	
	private static boolean check(long sumValue, String values) {
		int l = 0;
		for (int i = 0; i < values.length(); i++) {
			int value = Integer.parseInt(Character.toString(values.charAt(i)));
			l += value;
		}
		return sumValue == l;
	}
	
	
	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(SmtpServerTest.class);
	}
		
	
	public static void main (String... args) {
		Logger logger = Logger.getLogger("org.xsocket");
		logger.setLevel(Level.INFO);
		ConsoleHandler hdl = new ConsoleHandler();
		hdl.setLevel(Level.FINE);
		hdl.setFormatter(new LogFormatter());
		logger.addHandler(hdl);
		
		TestRunner.run(suite());
	}
	
	
	private class MailTestHandler extends SmtpProtocolHandler {
	
		public MailTestHandler(int port) {
			super(port);
		}

		
		@Override
		protected void handleMailBuffer(List<ByteBuffer> bufs) {
			
			String s = TextUtils.toString(bufs, "UTF-8");
			StringTokenizer st = new StringTokenizer(s, "\r\n");
			st.nextToken();
			st.nextToken();
			st.nextToken();
			long sumValue = Long.parseLong(st.nextToken());
			String values = st.nextToken();
			String end = st.nextToken();
			if (!end.endsWith("end")) {
				errors.add("end token error");
			}
			
			if (!check(sumValue, values)) {
				errors.add("sum error");
			}
		}
		
		@Override
		public Object clone() throws CloneNotSupportedException {
			return super.clone();
		}
	}
}
