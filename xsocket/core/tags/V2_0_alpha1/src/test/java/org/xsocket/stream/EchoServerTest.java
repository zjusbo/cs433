// $Id: FlushOnCloseTest.java 1379 2007-06-25 08:43:44Z grro $
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

import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;



/**
*
* @author grro@xsocket.org
*/
public final class EchoServerTest {

	private int running = 0;
	private final List<String> errors = new ArrayList<String>();

	@Test
	public void testXSocketServerSide() throws Exception {
		EchoServer server = new EchoServer(9322);

		Socket client = new Socket("localhost", 9322);
		client.getOutputStream().write("test\r\n".getBytes());
		LineNumberReader lnr = new LineNumberReader(new InputStreamReader(client.getInputStream()));
		String response = lnr.readLine();

		Assert.assertEquals("test", response);

		lnr.close();
		client.close();

		server.close();
	}


	@Test
	public void testXSocketBothSide() throws Exception {

		EchoServer server = new EchoServer(9312);

		for (int i = 0; i < 10; i++) {
			Thread t = new Thread() {
				@Override
				public void run() {
					running++;

					try {
						for (int j = 0; j < 5; j++) {
							IBlockingConnection connection = new BlockingConnection("127.0.0.1", 9312);

							connection.write("hello\r\n");
							String response = connection.readStringByDelimiter("\r\n");
							if (!response.equals("hello")) {
								errors.add("got " + response + " instead of hello");
							}

							connection.write("you\r\n");
							response = connection.readStringByDelimiter("\r\n");
							if (!response.equals("you")) {
								errors.add("got " + response + " instead of you");
							}


							connection.close();
						}

					} catch (Exception e) {
						errors.add(e.toString());
					}

					running--;
				}
			};
			t.start();
		}

		do {
			QAUtil.sleep(100);
		} while (running > 0);

		server.close();

		Assert.assertTrue(errors.isEmpty());
	}
}
