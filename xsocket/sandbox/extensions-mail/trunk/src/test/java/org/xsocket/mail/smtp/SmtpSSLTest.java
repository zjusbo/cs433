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


import java.security.Security;
import java.util.Properties;
import java.util.logging.Level;

import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;


import org.junit.Test;
import org.xsocket.mail.QAUtil;
import org.xsocket.mail.smtp.DummyMessageSinkService.MODE;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.MultithreadedServer;
import org.xsocket.stream.StreamUtils;



/**
*
* @author grro@xsocket.org
*/
public final class SmtpSSLTest  {

//	private int port = 465;
	private int port = 25;
	
	
	
	@Test public void testSSL() throws Exception {
		QAUtil.setLogLevel(Level.FINE);
		
		SmtpProtocolHandler hdl = new SmtpProtocolHandler(new DummyMessageSinkService(MODE.CONSOLE_OUT), new DummyAuthenticator(), null);
		IMultithreadedServer server = new MultithreadedServer(port, hdl, false, new SSLTestContextFactory().getSSLContext());
		StreamUtils.start(server);
		
		Security.setProperty("ssl.SocketFactory.provider", SSLTestSocketFactory.class.getName());
		
		
		Authenticator authenticator = new Authenticator() {
	        public PasswordAuthentication getPasswordAuthentication(){
	            return new PasswordAuthentication("userird", "password");
	        }
	    };

	    
		Properties props = System.getProperties();
		props.put("mail.debug", "false");
		props.put("mail.smtp.host", server.getLocalAddress().getHostName());
		props.put("mail.smtp.port", port);
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		Session session = Session.getInstance(props, authenticator);


		MimeMessage msg = new MimeMessage(session);
		msg.setSender(new InternetAddress("test@socket.org"));
		msg.setRecipient(MimeMessage.RecipientType.TO, new InternetAddress("test2@socket.org"));
		msg.setText("hello2");

		Transport.send(msg);
		
		server.close();
	}
}
