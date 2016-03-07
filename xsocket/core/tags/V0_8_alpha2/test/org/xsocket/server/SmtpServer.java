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

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.xsocket.server.chain.Chain;



public final class SmtpServer {



	/**
	 * @param args
	 */
	public static void main(String... args) throws Exception {
		new SmtpServer().launch(Integer.parseInt(args[0]));

		Thread.sleep(60 * 60 * 60 * 1000);
	}
	
	
	public void launch(int port) throws Exception {
		
		MultithreadedServer ss = new MultithreadedServer(port, "TestSrv");
		ss.setDispatcherSize(6);
		ss.setDispatcherWorkerSize(4);
		ss.setReceiveBufferPreallocationSize(1048576);
		ss.setReceivingTimeout(2 * 60 * 1000);
		ss.setConnectionTimeout(30 * 60 * 1000);
		
		Chain mainchain = new Chain();
		ss.setHandler(mainchain);
		
		Chain acceptorchain = new Chain();
		acceptorchain.addHandler(new BlackIpHandler("spammer.com", "hackers.com", "BadGuy.org"));
		acceptorchain.addHandler(new FirstConnectionRefuser());
		mainchain.addHandler(acceptorchain);
		
		SmtpProtocolHandler smtpHandler = new SmtpProtocolHandler("TestSrv", "SmtpMonitor");
		mainchain.addHandler(smtpHandler);
				
		startRmiAgent();
		Thread server = new Thread(ss);
		server.setPriority(Thread.MAX_PRIORITY);
		server.start();
	}

	
	private void startRmiAgent() {
		int port = 1199;
		String name = "TestSrv";
		
		try {
			Registry registry = LocateRegistry.createRegistry(port);
			registry.unbind(name);
		} catch (Exception ignore) {  }

		try {
		    JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + InetAddress.getLocalHost().getHostName() + ":" + port + "/" + name);
		    
		    MBeanServer mbeanSrv = ManagementFactory.getPlatformMBeanServer();
		    JMXConnectorServerFactory.newJMXConnectorServer(url, null, mbeanSrv).start();		
		    System.out.println("JMX RMI Agent has been bound on address '" + url + "'");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

