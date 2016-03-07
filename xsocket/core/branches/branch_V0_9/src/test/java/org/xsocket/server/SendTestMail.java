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
package org.xsocket.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

import org.xsocket.IBlockingConnection;
import org.xsocket.BlockingConnection;


public final class SendTestMail {

	private static final Logger LOG = Logger.getLogger(SendTestMail.class.getName());
	
	private IBlockingConnection connection = null;

	
	public SendTestMail() {
	}	

	
	public boolean send(String server, int port) throws IOException {

		try {
			connection = new BlockingConnection(SocketChannel.open(new InetSocketAddress(server, port)));
			connection.setDefaultEncoding("ASCII");
	
			String result = connection.receiveWord("\r\n");
			LOG.fine("received " + result);
			if (!result.startsWith("220")) {
				System.out.println("Wrong greeting " + result);
			}
		
			connection.writeWord("HELO you\r\n");
			result = connection.receiveWord("\r\n");
			LOG.fine("received " + result);
			if (!result.startsWith("250")) {
				System.out.println("Wrong respnose for Helo " + result);
				return false;
			}
	
				
		
			connection.writeWord("MAIL FROM:testi@example.com\r\n");
			result = connection.receiveWord("\r\n");
			LOG.fine("received " + result);
			if (!result.startsWith("250 ")) {
				System.out.println("Wrong respnose for MAIL FROM " + result);
				return false;
			}

				
			connection.writeWord("RCPT TO:you@example\r\n");
			result = connection.receiveWord("\r\n");
			LOG.fine("received " + result);
			if (!result.startsWith("250 ")) {
				System.out.println("Wrong respnose for Rcpt TO " + result);
				return false;
			}

			connection.writeWord("RCPT TO:admin@example\r\n");
			result = connection.receiveWord("\r\n");
			LOG.fine("received " + result);
			if (!result.startsWith("250 ")) {
				System.out.println("Wrong respnose for Rcpt TO " + result);
				return false;
			}
				
			connection.writeWord("DATA\r\n");
			result = connection.receiveWord("\r\n");
			LOG.fine("received " + result);
			if (!result.startsWith("354")) {
				System.out.println("Wrong respnose for Data " + result);
				return false;
			}

				
			connection.writeWord("Date: Mon, 12 Jun 2006 09:38:31 +0200\r\n");
			connection.writeWord("From: testi@test.org\r\n");
			connection.writeWord("To: buddy@test.org\r\n");
			connection.writeWord("\r\n");
			connection.writeWord("plain text mail \r\n.\r\n");				
				
			result = connection.receiveWord("\r\n");
			LOG.fine("received " + result);
			if (!result.startsWith("250 ")) {
				System.out.println("Wrong respnose for Data " + result);
				return false;
			}

			return true;
				
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (Exception ignore) { }
			}
		}
	}

}
