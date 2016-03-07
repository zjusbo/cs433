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



import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.xsocket.mail.JmxServer;
import org.xsocket.mail.smtp.DummyMessageSinkService.MODE;
import org.xsocket.stream.HandlerChain;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.MultithreadedServer;


public final class RunnableSMTPServer {


	private IMultithreadedServer ss = null;


	/**
	 * @param args
	 */
	public static void main(String... args) throws Exception {
		Logger logger = Logger.getLogger("org.xsocket");
		logger.setLevel(Level.FINE);
		ConsoleHandler hdl = new ConsoleHandler();
		hdl.setLevel(Level.FINEST);
		logger.addHandler(hdl);
		
		if (args.length != 1) {
			System.out.println("usage java org.xsocket.server.handler.smtp.RunnableSMTPServer <port>");
			return;
		}
		new RunnableSMTPServer().launch(Integer.parseInt(args[0]));
	}


	public void launch(int port) throws Exception {


		HandlerChain mainChain = new HandlerChain();
		HandlerChain acceptanceChain = new HandlerChain();
		
		acceptanceChain.addLast(new FirstConnectionRefuser());
		acceptanceChain.addLast(new BlackIpRefuser("Hackers.org", "Spammer.com"));
		
		mainChain.addLast(acceptanceChain);
		mainChain.addLast(new SmtpProtocolHandler(new DummyMessageSinkService(MODE.CONSOLE_OUT)));

		ss = new MultithreadedServer(port, mainChain, false, new SSLTestContextFactory().getSSLContext());
		ss.setHandler(mainChain);
		
		JmxServer rmiAgent = new JmxServer();
		rmiAgent.start("TestSrv");
		
		ss.run();
		
		do {
			try {
				Thread.sleep(3000);
			} catch (InterruptedException ignore) { }
		} while (ss.isOpen());

		
		rmiAgent.stop();
	}

	public void shutdown() throws IOException {
		ss.close();
	}

	public boolean isRunning() {
		return ss.isOpen();
	}
}

