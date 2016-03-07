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


import org.junit.Test;
import org.xsocket.mail.DummyMail;
import org.xsocket.mail.smtp.DummyMessageSinkService.MODE;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.MultithreadedServer;
import org.xsocket.stream.StreamUtils;



/**
*
* @author grro@xsocket.org
*/
public final class SmtpParameterTest {

	
	@Test public void testCommandOrder() throws Exception {
		
		InetAddress localAddress = InetAddress.getLocalHost();
		
		IMultithreadedServer server = new MultithreadedServer(localAddress, 0, new SmtpProtocolHandler(new DummyMessageSinkService(MODE.DEV0OUT)));
		StreamUtils.start(server);

	
		IBlockingConnection connection = null;
		try {
			connection = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
			connection.setDefaultEncoding("ASCII");
			connection.setAutoflush(true);
	
			String result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			if (!result.startsWith("220")) {
				throw new Exception("Wrong greeting " + result);
			}
		
			connection.write("HELO you\r\n");
			result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			if (!result.startsWith("250")) {
				throw new Exception("Wrong respnose for Helo " + result);
			}

			connection.write("DATA\r\n");
			result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			if (result.startsWith("354")) {
				throw new Exception("Error exceptedWrong respnose for MAIL FROM " + result);
			}

			connection.write("MAIL FROM: ertetertetet\r\n");
			result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			if (result.startsWith("250 ")) {
				throw new Exception("Error excepted for wrong mail address " + result);
			}
	
			
			connection.write("MAIL FROM: testi@example.com\r\n");
			result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			if (!result.startsWith("250 ")) {
				throw new Exception("Wrong respnose for MAIL FROM " + result);
			}
	
			connection.write("MAIL FROM: testi@example.com\r\n");
			result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			if (result.startsWith("250 ")) {
				throw new Exception("Error excepted: duplicated MAIL FROM " + result);
			}

			
			connection.write("RCPT TO: you@example\r\n");
			result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			if (!result.startsWith("250 ")) {
				throw new Exception("Wrong respnose for Rcpt TO " + result);
			}
	
			connection.write("RCPT TO: admin@example\r\n");
			result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			if (!result.startsWith("250 ")) {
				throw new Exception("Wrong respnose for Rcpt TO " + result);
			}
			
			connection.write("RCPT TO: uzuzuzuzuz\r\n");
			result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			if (result.startsWith("250 ")) {
				throw new Exception("Error excepted for wrong mail address " + result);
			}

			
			connection.write("DATA\r\n");
			result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			if (!result.startsWith("354")) {
				throw new Exception("Wrong respnose for Data " + result);
			}
	
				
			connection.write(new DummyMail().getAsByteBuffer());
			connection.write("\r\n.\r\n");
			result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			if (!result.startsWith("250 ")) {
				throw new Exception("Message not accepted: " + result);
			}

			
			connection.write("DATA\r\n");
			result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			if (result.startsWith("354")) {
				throw new Exception("Error excepted receiver and senders should be reset automatically" + result);
			}

			
			connection.write("MAIL FROM: testi@example.com\r\n");
			result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			if (!result.startsWith("250 ")) {
				throw new Exception("Wrong respnose for MAIL FROM " + result);
			}

			connection.write("RSET\r\n");
			result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			if (!result.startsWith("250 ")) {
				throw new Exception("Wrong respnose for RSET " + result);
			}


			connection.write("MAIL FROM: testi@example.com\r\n");
			result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			if (!result.startsWith("250 ")) {
				throw new Exception("Wrong respnose for MAIL FROM " + result);
			}

			
			
			connection.write("RCPT TO: you@example\r\n");
			result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			if (!result.startsWith("250 ")) {
				throw new Exception("Wrong respnose for Rcpt TO " + result);
			}

			connection.write("DATA\r\n");
			result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			if (!result.startsWith("354")) {
				throw new Exception("Wrong respnose for Data " + result);
			}
	
				
			connection.write(new DummyMail().getAsByteBuffer());
			connection.write("\r\n.\r\n");
			result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
			if (!result.startsWith("250 ")) {
				throw new Exception("Message not accepted: " + result);
			}

			
			
			
			connection.write("QUIT\r\n");
			result = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
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
		
		server.close();
	}
}
