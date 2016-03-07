// $Id: MarkAndResetTest.java 1630 2007-08-02 11:37:20Z grro $
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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;



/**
*
* @author grro@xsocket.org
*/
public final class ProxyTest  {


	private int running = 0;
	private final List<String> errors = new ArrayList<String>();


	@Test
	public void testSimple() throws IOException {

		//QAUtil.setLogLevel(Level.FINEST);
		Proxy proxy = new Proxy(9966, "www.web.de", 80);


		for (int i = 0; i < 5; i++) {
			Thread t = new Thread() {
				@Override
				public void run() {
					running++;

					for (int j = 0; j < 10; j++) {
						try {
							IBlockingConnection con = new BlockingConnection("127.0.0.1", 9966);
							con.write("GET / HTTP \r\n\r\n");
							String responseCode =  con.readStringByDelimiter("\r\n");

							if (!responseCode.contains("OK")) {
								errors.add("got " + responseCode);
							} else {
								System.out.print(".");
							}

							con.close();
						} catch (IOException e) {
							errors.add(e.toString());
						}
					}

					running--;
				}

			};

			t.start();

		}


		do {
			QAUtil.sleep(100);
		} while (running > 0);


		proxy.close();

		Assert.assertTrue(errors.isEmpty());
	}
}
