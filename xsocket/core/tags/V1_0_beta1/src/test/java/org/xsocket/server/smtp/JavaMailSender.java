// $Id: JavaMailSender.java 41 2006-06-22 06:30:23Z grro $

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
package org.xsocket.server.smtp;

import java.io.IOException;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.Message.RecipientType;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;


public final class JavaMailSender {

	

	
	public boolean send(String server, int port) throws IOException {
		String mail =   "Date: Mon, 12 Jun 2006 09:38:31 +0200\r\n"
					  + "From: testi@test.org\r\n"
					  + "To: buddy@test.org\r\n"
					  + "\r\n"
					  + "plain text mail \r\n.\r\n";
		return send(server, port, mail);
	}
	
	public boolean send(String server, int port, String mail) {

		Session session = null;

		try {
			Properties props = new Properties();
			props.put("mail.smtp.host", "127.0.0.1");
			session = Session.getInstance(props);

			Message message = new MimeMessage(session);
			message.setFrom(new InternetAddress("testi@xsocket.org"));
			message.setRecipient(RecipientType.TO, new InternetAddress("testi2@xsocket.org"));
			message.setSubject("test message");
			message.setText("this is a plain test text message");
			
			Transport.send(message);
			
			return true;

		} catch (MessagingException me) {
			me.printStackTrace();
			return false;			
		}
	}

}
