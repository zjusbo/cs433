/*
 *  Copyright (c) xsocket.org, 2006-2008. All rights reserved.
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
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.SSLTestContextFactory;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.ConnectionUtils;



/**
*
* @author grro@xsocket.org
*/
public final class NonThreadedSSLProxyTest  {


	private int running = 0;
	private final List<String> errors = new ArrayList<String>();


	@Test
	public void testPrestarted() throws Exception {

		IServer server = new Server(new BusinessService());
		ConnectionUtils.start(server);
		
		final IServer proxy = new NonThreadedSSLProxy(0, "localhost", server.getLocalPort(), true);
		ConnectionUtils.start(proxy);
		ConnectionUtils.registerMBean(proxy);
		
		
		

		for (int i = 0; i < 3; i++) {
			
			final int num = i;
			
			Thread t = new Thread() {
				@Override
				public void run() {
					running++;
					System.out.println("starting " + num);

					for (int j = 0; j < 10; j++) {
						try {
							IBlockingConnection con = new BlockingConnection("localhost", proxy.getLocalPort(), SSLTestContextFactory.getSSLContext(), true);
							String text = "test234567";
							con.write(text + "\r\n");
							String response =  con.readStringByDelimiter("\r\n");

							if (!response.equals(text)) {
								errors.add(text + " != " + response);
							} else {
								System.out.print(".");
							}

							con.close();
						} catch (IOException e) {
							e.printStackTrace();
							errors.add(e.toString());
						}
					}

					System.out.println("closing " + num);
					running--;
				}

			};

			t.start();

		}


		do {
			QAUtil.sleep(100);
		} while (running > 0);

		proxy.close();

		for (String error : errors) {
			System.out.println(error);
		}
		
		Assert.assertTrue(errors.isEmpty());
	}
	
/*
	@Test
	public void testNonPrestarted() throws Exception {

		IServer server = new Server(new BusinessService());
		ConnectionUtils.start(server);
		
		final IServer proxy = new NonThreadedSSLProxy(0, "localhost", server.getLocalPort(), false);
		ConnectionUtils.start(proxy);
		ConnectionUtils.registerMBean(proxy);
		
		
		

		for (int i = 0; i < 3; i++) {
			
			final int num = i;
			
			Thread t = new Thread() {
				@Override
				public void run() {
					running++;
					System.out.println("starting " + num);

					for (int j = 0; j < 10; j++) {
						try {
							IBlockingConnection con = new BlockingConnection("localhost", proxy.getLocalPort(), SSLTestContextFactory.getSSLContext(), true);
							String text = "test234567";
							con.write(text + "\r\n");
							String response =  con.readStringByDelimiter("\r\n");

							if (!response.equals(text)) {
								errors.add(text + " != " + response);
							} else {
								System.out.print(".");
							}

							con.close();
						} catch (IOException e) {
							e.printStackTrace();
							errors.add(e.toString());
						}
					}

					System.out.println("closing " + num);
					running--;
				}

			};

			t.start();

		}


		do {
			QAUtil.sleep(100);
		} while (running > 0);

		proxy.close();

		for (String error : errors) {
			System.out.println(error);
		}
		
		Assert.assertTrue(errors.isEmpty());
	}
*/
	
	private static final class BusinessService implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			String cmd = connection.readStringByDelimiter("\r\n");
			
			if (cmd.equals("startSSL")) {
				connection.activateSecuredMode();
			}
			
			connection.write(cmd + "\r\n");
			
			return true;
		}
		
	}
}
