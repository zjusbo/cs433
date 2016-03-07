// $Id: SmtpServer.java 41 2006-06-22 06:30:23Z grro $
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
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.xsocket.server.IConnectHandler;
import org.xsocket.server.INonBlockingConnection;
import org.xsocket.server.MultithreadedServer;
import org.xsocket.server.handler.chain.Chain;



public final class RunnableSmtpServer {

	private static final Logger LOG = Logger.getLogger(RunnableSmtpServer.class.getName());

	private MultithreadedServer ss = null;


	/**
	 * @param args
	 */
	public static void main(String... args) throws Exception {
		Logger logger = Logger.getLogger("org.xsocket");
		logger.setLevel(Level.INFO);
		ConsoleHandler hdl = new ConsoleHandler();
		hdl.setLevel(Level.FINE);
		logger.addHandler(hdl);
		
		new RunnableSmtpServer().launch(Integer.parseInt(args[0]));

		Thread.sleep(60 * 60 * 60 * 1000);
	}
	
	
	public void launch(int port) throws Exception {
		
		ss = new MultithreadedServer(port);
		ss.setDispatcherPoolSize(6);
		ss.setWorkerPoolSize(4);
		ss.setReceiveBufferPreallocationSize(3);
		ss.setIdleTimeout(2 * 60 * 1000);
		ss.setConnectionTimeout(30 * 60 * 1000);
		
		Chain mainchain = new Chain();
		ss.setHandler(mainchain);
		
		Chain acceptorchain = new Chain();
		acceptorchain.addLast(new BlackIpHandler("spammer.com", "hackers.com", "BadGuy.org"));
	//	acceptorchain.addHandler(new FirstConnectionRefuser());
		mainchain.addLast(acceptorchain);
		
		SmtpProtocolHandler smtpHandler = new SmtpProtocolHandler(port);
		mainchain.addLast(smtpHandler);
				
		startRmiAgent();
		Thread server = new Thread(ss);
		server.setPriority(Thread.MAX_PRIORITY);
		server.start();
	}
	
	public void shutdown() {
		ss.shutdown();
	}
	
	public boolean isRunning() {
		return ss.isRunning();
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
	
	
	private static class BlackIpHandler implements IConnectHandler {
		
		
		private List<String> hostnames = null;
			
		public BlackIpHandler(String... hostnames) {
			this.hostnames = Arrays.asList(hostnames);;
		}
			
			
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			for (String hostname : hostnames) {
				if (connection.getRemoteAddress().getCanonicalHostName().equals(hostname)) {
					LOG.fine("hostname " + hostname + " is black listed");
					connection.close();
					return true;
				}
			}
				
			return false;
		}
	}

	
	private static class FirstConnectionRefuser implements IConnectHandler {		

		private final List<String> knownHostnames = new ArrayList<String>();
		
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			String remoteHostname = connection.getRemoteAddress().getCanonicalHostName();
			if (knownHostnames.contains(remoteHostname)) {
				return false;
			} else {
				knownHostnames.add(remoteHostname);	
				LOG.fine("hostname " + remoteHostname + " is unknown.");
				connection.close();

				return true;
			}
		}
	}

	
	

}

