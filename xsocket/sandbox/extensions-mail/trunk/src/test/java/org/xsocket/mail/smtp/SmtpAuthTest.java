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



import java.util.Properties;

import javax.mail.AuthenticationFailedException;
import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import junit.framework.Assert;


import org.junit.Test;
import org.xsocket.mail.smtp.DummyMessageSinkService.MODE;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.MultithreadedServer;
import org.xsocket.stream.StreamUtils;




/**
*
* @author grro@xsocket.org
*/
public final class SmtpAuthTest {

	
	@Test public void testValidPasword() throws Exception {
		IMultithreadedServer server = new MultithreadedServer(25, new SmtpProtocolHandler(new DummyMessageSinkService(MODE.DEV0OUT), new DummyAuthenticator(), null));
		StreamUtils.start(server);

		
		Authenticator authenticator = new Authenticator() {
	        public PasswordAuthentication getPasswordAuthentication(){
	            return new PasswordAuthentication("userird", "password");
	        }
	    };

		
		Properties smtpProperties = new Properties();
		smtpProperties.put("mail.debug", "false");
		smtpProperties.put("mail.smtp.host", server.getLocalAddress().getHostName());
		smtpProperties.put("mail.smtp.port", server.getLocalPort());
		smtpProperties.put("mail.smtp.auth", "true");
		Session session = Session.getInstance(smtpProperties, authenticator);


		MimeMessage msg = new MimeMessage(session);
		msg.setSender(new InternetAddress("test@socket.org"));
		msg.setRecipient(MimeMessage.RecipientType.TO, new InternetAddress("test2@socket.org"));
		msg.setText("hello2");

		Transport.send(msg);
		
		
		server.close();
	}
	

	@Test public void testInValidPasword() throws Exception {		
		IMultithreadedServer server = new MultithreadedServer(25, new SmtpProtocolHandler(new DummyMessageSinkService(MODE.DEV0OUT), new DummyAuthenticator(), null));
		StreamUtils.start(server);
		
		Authenticator authenticator = new Authenticator() {
	        public PasswordAuthentication getPasswordAuthentication(){
	            return new PasswordAuthentication("userird", DummyAuthenticator.getInvalidPassword());
	        }
	    };

		
		Properties smtpProperties = new Properties();
		smtpProperties.put("mail.debug", "true");
		smtpProperties.put("mail.smtp.host", server.getLocalAddress().getHostName());
		smtpProperties.put("mail.smtp.port", server.getLocalPort());
		smtpProperties.put("mail.smtp.auth", "true");
		Session session = Session.getInstance(smtpProperties, authenticator);


		MimeMessage msg = new MimeMessage(session);
		msg.setSender(new InternetAddress("test@socket.org"));
		msg.setRecipient(MimeMessage.RecipientType.TO, new InternetAddress("test2@socket.org"));
		msg.setText("hello2");

		try {
			Transport.send(msg);
			Assert.fail("authentication should have failed");
		} catch (AuthenticationFailedException authEx) {
			// should occur
		}
		
		
		server.close();
	}
	
	
	@Test public void testNonAuthenticatedTrial() throws Exception {		
		IMultithreadedServer server = new MultithreadedServer(new SmtpProtocolHandler(new DummyMessageSinkService(MODE.DEV0OUT), new DummyAuthenticator(), null));
		StreamUtils.start(server);
		
		
		Properties smtpProperties = new Properties();
		smtpProperties.put("mail.debug", "true");
		smtpProperties.put("mail.smtp.host", "127.0.0.1");
	//	smtpProperties.put("mail.smtp.port", getServer().getPort());
		Session session = Session.getInstance(smtpProperties);


		MimeMessage msg = new MimeMessage(session);
		msg.setSender(new InternetAddress("test@socket.org"));
		msg.setRecipient(MimeMessage.RecipientType.TO, new InternetAddress("test2@socket.org"));
		msg.setText("hello2");

		try {
			Transport.send(msg);
			Assert.fail("authentication should have failed");
		} catch (Exception authEx) {
			// should occur
		}
		
		
		server.close();
	}

}
