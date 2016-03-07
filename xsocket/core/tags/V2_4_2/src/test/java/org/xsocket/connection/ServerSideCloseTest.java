/*
 *  Copyright (c) xlightweb.org, 2008 - 2009. All rights reserved.
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
 * The latest copy of this software may be found on http://www.xlightweb.org/
 */
package org.xsocket.connection;




import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;






/**
*
* @author grro@xlightweb.org
*/
public final class ServerSideCloseTest  {
	

	private final AtomicInteger running = new AtomicInteger(0);
	private final List<String> errors  = new ArrayList<String>();
	
	
	
	@Before
	public void setup() {
		running.set(0);
		errors.clear();
	}

	
	

	
	@Test
	public void testSimple1() throws Exception {

		
		IDataHandler dh = new IDataHandler() {
			
			public boolean onData(INonBlockingConnection connection) throws IOException {
				connection.readStringByDelimiter("\r\n\r\n");
				connection.write("HTTP/1.1 200 OK\r\n" + 
						         "Server: xLightweb/2.5-SNAPSHOT\r\n" +
						         "Content-Length: 2\r\n" +
						         "Connection: close\r\n" +
						         "Content-Type: text/plain; charset=UTF-8\r\n" +
						         "\r\n" +
						         "OK");
				connection.close();
				return true;
			}
		};
		
		final IServer server = new Server(dh);
		server.start();

		
	
		for (int i =0; i < 5; i++) {
			new Thread() {
				@Override
				public void run() {

					running.incrementAndGet();
					try {
						for (int j = 0; j< 1000; j++) {
							IBlockingConnection con  = new BlockingConnection("localhost", server.getLocalPort());
							con.write("GET / HTTP/1.1\r\n" + 
									  "Host: localhost:24381\r\n" +
									  "User-Agent: xLightweb/2.5-SNAPSHOT\r\n" +
									  "\r\n");
							
							String header = con.readStringByDelimiter("\r\n\r\n");							
							Assert.assertTrue(header.indexOf("200") != -1);
							Assert.assertEquals("OK", con.readStringByLength(2));
							
							System.out.print(".");
							con.close();
						}

						
					} catch (Exception e) {
						e.printStackTrace();
						errors.add(e.toString());
						
					} finally {
						running.decrementAndGet();
					}
					
				}
			}.start();
		}

		do {
			QAUtil.sleep(200);
		} while (running.get() > 0);
		
		for (String error : errors) {
			System.out.println(error);
		}
		
		Assert.assertTrue(errors.isEmpty());
		
		server.close();
	}
}