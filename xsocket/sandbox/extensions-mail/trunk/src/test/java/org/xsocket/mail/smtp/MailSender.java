// $Id: AbstractGetCommand.java 335 2006-10-16 06:10:05Z grro $

/*
 *  Copyright (c) xsocket.org, 2006 - 2007. All rights reserved.
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
package org.xsocket.mail.smtp;


import java.net.BindException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;



public final class MailSender {

	private static final Logger LOG = Logger.getLogger(MailSender.class.getName());
	
	
	public void send(InetAddress address, int port) throws Exception {
		String mail =   "Date: Mon, 12 Jun 2006 09:38:31 +0200\r\n"
					  + "From: testi@test.org\r\n"
					  + "To: buddy@test.org\r\n"
					  + "\r\n"
					  + "plain text mail";
		send(address, port, ByteBuffer.wrap(mail.getBytes()));
	}
	
	
	public void send(InetAddress address, int port, ByteBuffer mail) throws Exception { 

		IBlockingConnection connection = null;
		try {
			connection = new BlockingConnection(address, port);
			connection.setAutoflush(true);
			connection.setDefaultEncoding("ASCII");
	
			String result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			LOG.fine("received " + result);
			if (!result.startsWith("220")) {
				throw new Exception("Wrong greeting " + result);
			}
		
			connection.write("HELO you\r\n");
			result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			LOG.fine("received " + result);
			if (!result.startsWith("250")) {
				throw new Exception("Wrong respnose for Helo " + result);
			}
	
				
		
			connection.write("MAIL FROM:testi@example.com\r\n");
			result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			LOG.fine("received " + result);
			if (!result.startsWith("250 ")) {
				throw new Exception("Wrong respnose for MAIL FROM " + result);
			}

				
			connection.write("RCPT TO:you@example\r\n");
			result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			LOG.fine("received " + result);
			if (!result.startsWith("250 ")) {
				throw new Exception("Wrong respnose for Rcpt TO " + result);
			}

			connection.write("RCPT TO:admin@example\r\n");
			result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			LOG.fine("received " + result);
			if (!result.startsWith("250 ")) {
				throw new Exception("Wrong respnose for Rcpt TO " + result);
			}
				
			connection.write("DATA\r\n");
			result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			LOG.fine("received " + result);
			if (!result.startsWith("354")) {
				throw new Exception("Wrong respnose for Data " + result);
			}

				
			connection.write(mail);
			connection.write("\r\n.\r\n");
			result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			LOG.fine("received " + result);
			if (!result.startsWith("250 ")) {
				throw new Exception("Message not accepted: " + result);
			}

			connection.write("QUIT\r\n");
			result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			LOG.fine("received " + result);
			if (!result.startsWith("221")) {
				throw new Exception("Wrong respnose for quit " + result);
			}
				
		} catch (BindException be) {
			System.out.print("b");
			try {
				Thread.sleep(500);
			} catch (InterruptedException igonre) { }
			
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (Exception ignore) { }
			}
		}
	}

}
