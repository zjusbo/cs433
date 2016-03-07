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
package org.xsocket.mail.smtp.example;



import java.net.InetAddress;
import java.util.Properties;


import javax.mail.BodyPart;
import javax.mail.Multipart;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;



import org.junit.Assert;
import org.junit.Test;
import org.xsocket.mail.smtp.SmtpProtocolHandler;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.MultithreadedServer;
import org.xsocket.stream.StreamUtils;



/**
 * 
 * 
 * @author grro@xsocket.org
 */
public final class ExampleSMTPTest {
 
	
	
	@Test public void testGoodGuy() throws Exception {
			
		// prepare the smtp data sink and start the smtp server 
		ExampleDataSinkService smptDataSinkService = new ExampleDataSinkService();
		smptDataSinkService.setForwardPathPattern(".*@example.de.");
		smptDataSinkService.setReversePathPattern(".*@example.de.");
		
		IMultithreadedServer server = new MultithreadedServer(0, new SmtpProtocolHandler(smptDataSinkService));
		StreamUtils.start(server);
		
		
		// run the client
		Properties props = System.getProperties();
		props.put("mail.smtp.host", server.getLocalAddress().getHostName());
		props.put("mail.smtp.port", Integer.toString(server.getLocalPort()));
		Session session = Session.getInstance(props, null);
		
		MimeMessage msg = new MimeMessage(session);
		msg.setSender(new InternetAddress("trustMe@example.de"));
		msg.setRecipient(MimeMessage.RecipientType.TO, new InternetAddress("anyone@example.de"));


		Multipart mp = new MimeMultipart("signed");
		
		String text = "Hello";
		BodyPart textPart = new MimeBodyPart();
		textPart.setText(text);
		
		BodyPart signaturePart = new MimeBodyPart();
		//signaturePart.setContent(signature, "application/pkcs7-signature");
		signaturePart.setText(Integer.toString(text.length()));

		mp.addBodyPart(textPart);
		mp.addBodyPart(signaturePart);

		msg.setContent(mp);

		
		Transport.send(msg);				
		
		server.close();
	}

	
	
	@Test public void testBadGuy() throws Exception {

	
		// prepare the smtp data sink and start the smtp server 
		ExampleDataSinkService smptDataSinkService = new ExampleDataSinkService();
		smptDataSinkService.setForwardPathPattern(".*@example.de.");
		smptDataSinkService.setReversePathPattern(".*@example.de.");
		
		
		IMultithreadedServer server = new MultithreadedServer(0, new SmtpProtocolHandler(smptDataSinkService));
		StreamUtils.start(server);
		
		
		// run the client
		Properties props = System.getProperties();
		props.put("mail.smtp.host", server.getLocalAddress().getHostName());
		props.put("mail.smtp.port", Integer.toString(server.getLocalPort()));
		Session session = Session.getInstance(props, null);
		
		MimeMessage msg = new MimeMessage(session);
		msg.setSender(new InternetAddress("trustMe@example.de"));
		msg.setRecipient(MimeMessage.RecipientType.TO, new InternetAddress("anyone@example.de"));


		Multipart mp = new MimeMultipart("signed");
		
		String text = "Hello";
		BodyPart textPart = new MimeBodyPart();
		textPart.setText(text);
		
		BodyPart signaturePart = new MimeBodyPart();
		//signaturePart.setContent(signature, "application/pkcs7-signature");
		signaturePart.setText(Integer.toString(text.length() + 1));

		mp.addBodyPart(textPart);
		mp.addBodyPart(signaturePart);

		msg.setContent(mp);

		try {
			Transport.send(msg);
			Assert.fail("sending should have been failed");
		} catch (SendFailedException expected) { 
			System.out.println(expected.getMessage());
		}
		
		server.close();
	}

	
	
	@Test public void testBadReceiver() throws Exception {
		
		// prepare the smtp data sink and start the smtp server 
		ExampleDataSinkService smptDataSinkService = new ExampleDataSinkService();
		smptDataSinkService.setForwardPathPattern(".*@example.de.");
		smptDataSinkService.setReversePathPattern(".*@example.de.");
		
		
		IMultithreadedServer server = new MultithreadedServer(0, new SmtpProtocolHandler(smptDataSinkService));
		StreamUtils.start(server);

		
		
		// run the client
		Properties props = System.getProperties();
		props.put("mail.smtp.host", server.getLocalAddress().getHostName());
		props.put("mail.smtp.port", Integer.toString(server.getLocalPort()));
		Session session = Session.getInstance(props, null);
		
		MimeMessage msg = new MimeMessage(session);
		msg.setSender(new InternetAddress("trustMe@example.de"));
		msg.setRecipient(MimeMessage.RecipientType.TO, new InternetAddress("badboy@hackers.tv"));
		msg.setText("hello2");
		
		try {
			Transport.send(msg);
			Assert.fail("sending should have been failed");
		} catch (SendFailedException expected) { 
			System.out.println(expected.getMessage());
		}
		server.close();
	}
	
}

