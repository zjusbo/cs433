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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

import org.xsocket.IBlockingConnection;
import org.xsocket.BlockingConnection;


public final class SmtpTestClient {


	/**
	 * @param args
	 */
	public static void main(final String[] args) throws IOException {
		System.out.println("start test client for server " +  args[0] + " with " + args[1] + " threads");
		int size = Integer.parseInt(args[1]); 
		for (int i = 0; i < size; i++) {
			Thread t = new Thread() {
				public void run() {
					try {
						new SmtpTestClient().launch(args[0]);
					} catch (Exception e) {
						e.printStackTrace();
					}
				};
			};
			t.start();
		}
	}
	
	
	public void launch(String server) throws IOException {

		IBlockingConnection connection = null;
		while (true) {
			try {
				connection = new BlockingConnection(SocketChannel.open(new InetSocketAddress(server, 25)));
				connection.setDefaultEncoding("ASCII");

				String result = connection.receiveWord("\r\n");
				if (!result.startsWith("220")) {
					System.out.println("Wrong greeting " + result);
					continue;
				}
	
				connection.write("HELO you\r\n");
				result = connection.receiveWord("\r\n");
				if (!result.startsWith("250")) {
					System.out.println("Wrong respnose for Helo " + result);
					continue;
				}
	
				
		
				connection.write("MAIL FROM:testi@example.com\r\n");
				result = connection.receiveWord("\r\n");
				if (!result.startsWith("250 ")) {
					System.out.println("Wrong respnose for MAIL FROM " + result);
					continue;
				}

				
				connection.write("RCPT TO:you@example\r\n");
				result = connection.receiveWord("\r\n");
				if (!result.startsWith("250 ")) {
					System.out.println("Wrong respnose for Rcpt TO " + result);
					continue;
				}

				
				connection.write("DATA\r\n");
				result = connection.receiveWord("\r\n");
				if (!result.startsWith("354")) {
					System.out.println("Wrong respnose for Data " + result);
					continue;
				}

				
				connection.write("Date: Mon, 12 Jun 2006 09:38:31 +0200\r\n");
				connection.write("From: testi@test.org\r\n");
				connection.write("To: buddy@test.org\r\n");
				connection.write("\r\n");
				connection.write("plain text mail \r\n.\r\n");				
				
				result = connection.receiveWord("\r\n");
				if (!result.startsWith("250 ")) {
					System.out.println("Wrong respnose for Data " + result);
					continue;
				}

				System.out.print(".");
				
			} catch (Throwable e) {
				e.printStackTrace();
			} finally {
				if (connection != null) {
					try {
						connection.close();
					} catch (Exception ignore) { }
				}
			}
							
		}
	}
}
