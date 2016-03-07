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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.ConnectionUtils;



/**
*
* @author grro@xsocket.org
*/
public final class ProxyTest  {


	private AtomicInteger running = new AtomicInteger(0);
	private final List<String> errors = new ArrayList<String>();


	@Test
	public void testLiveSimple() throws Exception {
		String host = "www.web.de";

		System.out.println("run proxy test by calling " + host);

		Proxy proxy = new Proxy(9966, host, 80);
		ConnectionUtils.start(proxy);
		ConnectionUtils.registerMBean(proxy);
		
		
		

		for (int i = 0; i < 5; i++) {
			
			final int num = i;
			
			Thread t = new Thread() {
				@Override
				public void run() {
					running.incrementAndGet();
					System.out.println("starting " + num);

					for (int j = 0; j < 20; j++) {
						try {
							IBlockingConnection con = new BlockingConnection("localhost", 9966);
							con.write("GET / HTTP \r\n\r\n");
							String responseCode =  con.readStringByDelimiter("\r\n");

							if (!responseCode.contains("OK")) {
								errors.add("got " + responseCode);
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
					running.decrementAndGet();
				}

			};

			t.start();

		}


		do {
			QAUtil.sleep(100);
		} while (running.get() > 0);

		proxy.close();

		for (String error : errors) {
			System.out.println(error);
		}
		
		Assert.assertTrue(errors.isEmpty());
	}
}
