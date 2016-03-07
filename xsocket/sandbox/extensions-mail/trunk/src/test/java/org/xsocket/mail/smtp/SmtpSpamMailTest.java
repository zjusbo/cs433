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


import junit.framework.Assert;

import org.junit.Test;
import org.xsocket.mail.DummySpamMail;
import org.xsocket.mail.smtp.DummyMessageSinkService.MODE;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.MultithreadedServer;
import org.xsocket.stream.StreamUtils;




/**
*
* @author grro@xsocket.org
*/
public final class SmtpSpamMailTest {

	@Test public void testSpam() throws Exception {		
		final IMultithreadedServer server = new MultithreadedServer(new SmtpProtocolHandler(new DummyMessageSinkService(MODE.DEV0OUT)));
		StreamUtils.start(server);
		
		try {
			new MailSender().send(server.getLocalAddress(), server.getLocalPort(), new DummySpamMail().getAsByteBuffer());
			Assert.fail("server should have detect spam mail");

		} catch (Exception e)  {
			if (e.getMessage().startsWith("Message not accepted: ")) {
				// expected exception 
			} else {
				throw e;
			}
		}
		
		
		server.close();
	}
}
