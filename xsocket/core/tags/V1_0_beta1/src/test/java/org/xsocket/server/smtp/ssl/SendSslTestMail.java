// $Id: SmtpTestClient.java 41 2006-06-22 06:30:23Z grro $
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

import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.net.Socket;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;




public final class SendSslTestMail {

	private SSLContext sslContext = null;

	
	public SendSslTestMail() {
	}	

	
	public boolean send(String server, int port) throws Exception {

		Socket socket = null;
		try {
			sslContext = new SSLTestContextFactory().getSSLContext();
	        SocketFactory socketFactory = sslContext.getSocketFactory();
	        socket = socketFactory.createSocket("127.0.0.1", port);
		    
	        LineNumberReader reader = new LineNumberReader(new InputStreamReader(socket.getInputStream()));
	        PrintWriter writer = new PrintWriter(socket.getOutputStream());

			String result = reader.readLine();
			if (!result.startsWith("220")) {
				System.out.println("Wrong greeting " + result);
			}
		
			writer.write("HELO you\r\n");
			writer.flush();
			result = reader.readLine();
			if (!result.startsWith("250")) {
				System.out.println("Wrong respnose for Helo " + result);
				return false;
			}
	
				
		
			writer.write("MAIL FROM:testi@example.com\r\n");
			writer.flush();
			result = reader.readLine();
			if (!result.startsWith("250 ")) {
				System.out.println("Wrong respnose for MAIL FROM " + result);
				return false;
			}

				
			writer.write("RCPT TO:you@example\r\n");
			writer.flush();
			result = reader.readLine();
			if (!result.startsWith("250 ")) {
				System.out.println("Wrong respnose for Rcpt TO " + result);
				return false;
			}

			writer.write("RCPT TO:admin@example\r\n");
			writer.flush();
			result = reader.readLine();
			if (!result.startsWith("250 ")) {
				System.out.println("Wrong respnose for Rcpt TO " + result);
				return false;
			}
				
			writer.write("DATA\r\n");
			writer.flush();
			result = reader.readLine();
			if (!result.startsWith("354")) {
				System.out.println("Wrong respnose for Data " + result);
				return false;
			}

				
			writer.write("Date: Mon, 12 Jun 2006 09:38:31 +0200\r\n");
			writer.write("From: testi@test.org\r\n");
			writer.write("To: buddy@test.org\r\n");
			writer.write("\r\n");
			writer.write("plain text mail \r\n.\r\n");
			writer.flush();
				
			result = reader.readLine();
			if (!result.startsWith("250 ")) {
				System.out.println("Wrong respnose for Data " + result);
				return false;
			}

			return true;
				
		} finally {
			if (socket != null) {
				try {
					socket.close();
				} catch (Exception ignore) { }
			}
		}
	}

}
