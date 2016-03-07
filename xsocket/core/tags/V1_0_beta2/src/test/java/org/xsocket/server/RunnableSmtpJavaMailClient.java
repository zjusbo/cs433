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


import java.util.Properties;

import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;




import org.xsocket.TestUtil;



/**
*
* @author grro@xsocket.org
*/
public final class RunnableSmtpJavaMailClient {

	private Session session = null;
	private String data = null;

	public static void main(String... args) throws Exception {
		if (args.length != 4) {
			System.out.println("usage org.xsocket.server.RunnableSmtpJavaMailClient <host> <port> <datasize> <repeats>");
			return;
		}
		new RunnableSmtpJavaMailClient().launch(args[0], args[1], Integer.parseInt(args[2]), Integer.parseInt(args[3]));
	}
	
	
	public void launch(String host, String port, int datasize,  int repeats) throws Exception {
		
		Properties props = System.getProperties();
		props.put("mail.smtp.host", host);
		props.put("mail.smtp.port", port);
		session = Session.getInstance(props, null);
		
		data = "hello\n\n" +  new String(TestUtil.generatedByteArray(datasize));
		
		for(int i = 0; i < 5; i++) {
			send();
		}
		
		long elapsed = 0;
		for(int i = 0; i < repeats; i++) {
			try {
				elapsed += send();
			} catch (Exception e) {
				e.printStackTrace();
				try {
					Thread.sleep(500);
				} catch (InterruptedException ignore) { }
			}
		}
		
		System.out.println("elapsed average" + (elapsed/repeats));
	}
	
	private long send() throws Exception {
		MimeMessage msg = new MimeMessage(session);
		msg.setSender(new InternetAddress("test@socket.org"));
		msg.setRecipient(MimeMessage.RecipientType.TO, new InternetAddress("test2@socket.org"));
		msg.setText(data);

		long start = System.currentTimeMillis();
		Transport.send(msg);
		long elapsed = System.currentTimeMillis() - start;
		
		System.out.println(elapsed);
		return elapsed;
	}
}
