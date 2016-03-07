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
package org.xsocket.mail.pop3;


import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;


import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Store;

import junit.framework.JUnit4TestAdapter;
import junit.textui.TestRunner;



import org.junit.Assert;
import org.junit.Test;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.MultithreadedServer;
import org.xsocket.stream.StreamUtils;



/**
*
* @author grro@xsocket.org
*/
public final class POP3ServerTest  {

	
	private int executed = 0;
	

		
	@Test public void testMixed() throws Exception {
		final IMultithreadedServer server = new MultithreadedServer(new POP3ProtocolHandler(new DummyMailDropService(), new DummyAuthenticator()));
		StreamUtils.start(server);

		
		int loops = 6;
		
		ExecutorService executors = Executors.newFixedThreadPool(6);
		for (int i = 0; i < loops; i++) {
			executors.execute(new Runnable() {
				public void run() {
					try {
						getMessage(server);
						executed++;
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
			});
		}

	
		do {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException ignore) { }
		} while (executed < loops);
		
		executors.shutdownNow();
		
		server.close();
	}

	private void getMessage(IMultithreadedServer server) throws Exception {
		Properties props = System.getProperties();
		Session session = Session.getInstance(props, null);

		Store store = session.getStore("pop3");
		store.connect(server.getLocalAddress().getHostName(), server.getLocalPort(), "itsMe", "secret");

		Folder folder = store.getFolder("INBOX");
		Assert.assertTrue(folder.getName() == "INBOX");
		folder.open(folder.READ_WRITE);
		
		Message[] messages = folder.getMessages();
		for (Message message : messages) {
			String s = message.getMessageNumber() + " " + message.getSize();
		}

		folder.close(true);
	}
	
	
	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(POP3ServerTest.class);
	}


	public static void main (String... args) {
		Logger logger = Logger.getLogger("org.xsocket");
		logger.setLevel(Level.FINE);
		ConsoleHandler hdl = new ConsoleHandler();
		hdl.setLevel(Level.FINE);
		logger.addHandler(hdl);

		TestRunner.run(suite());
	}
}
