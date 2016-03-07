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


import java.io.IOException;

import org.xsocket.mail.JmxServer;
import org.xsocket.stream.MultithreadedServer;
import org.xsocket.stream.MultithreadedServerMBeanProxyFactory;






public final class RunnablePOP3Server {


	private MultithreadedServer ss = null;


	/**
	 * @param args
	 */
	public static void main(String... args) throws Exception {
		new RunnablePOP3Server().launch(Integer.parseInt(args[0]));
	}


	public void launch(int port) throws Exception {
		
		ss = new MultithreadedServer(port, new POP3ProtocolHandler(new DummyMailDropService(), new DummyAuthenticator()));
		ss.setIdleTimeoutSec(5 * 60);
		ss.setConnectionTimeoutSec(30 * 60);

		MultithreadedServerMBeanProxyFactory.createAndRegister(ss);
		
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

