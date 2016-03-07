/*
 *  Copyright (c) xsocket.org, 2006 - 2008. All rights reserved.
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
package org.xsocket.connection;

import java.lang.management.ManagementFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.InstanceAlreadyExistsException;
import javax.management.JMException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;






/**
 * A Mbean proxy factory, which creates and registers an appropriated mbean
 * for a given {@link Server} instance.
 *
 * E.g.
 * <pre>
 *   ...
 *   IServer smtpServer = new Server(port, new SmtpProtcolHandler());
 *   ConnectionUtils.start(server);
 *
 *   // create a mbean for the server and register it
 *   ServerMBeanProxyFactory.createAndRegister(server);
 *
 * </pre>
 *
 *
 * @author grro@xsocket.org
 */
@SuppressWarnings("unchecked")
final class ServerMBeanProxyFactory {

	
	
	/**
	 * creates and registers a mbean for the given server on the given MBeanServer
	 * under the given domain name
	 *
	 * @param server       the server to register
	 * @param domain       the domain name to use
 	 * @param mbeanServer  the mbean server to use
	 * @return the objectName
	 * @throws JMException  if an jmx exception occurs
	 */
	public static ObjectName createAndRegister(IServer server, String domain, MBeanServer mbeanServer) throws JMException {

		String address = server.getLocalAddress().getCanonicalHostName() + ":" + server.getLocalPort();

		ObjectName serverObjectName = registerMBeans(server, domain, address);
		server.addListener(new Listener(server, domain, address));

		return serverObjectName;
	}


	private static ObjectName registerMBeans(IServer server, String domain, String address) throws MalformedObjectNameException, InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException, JMException {
		address = address.replace(":", "_");
		
		// create and register handler
		Object hdl = server.getHandler();
		ObjectName hdlObjectName = new ObjectName(domain + ".server." + address + ":type=" + hdl.getClass().getSimpleName());
		ManagementFactory.getPlatformMBeanServer().registerMBean(new IntrospectionBasedDynamicMBean(hdl), hdlObjectName);


		// register the acceptor implementation
		ObjectName serverObjectName = null;
		if (server instanceof Server) {
			serverObjectName = ConnectionUtils.getIoProvider().registerMBeans((Server) server, ((Server) server).getAcceptor(), domain, address);
		} else {
			serverObjectName = new ObjectName(domain + ".server." + address + ":type=xServer,name=" + server.hashCode());
			ManagementFactory.getPlatformMBeanServer().registerMBean(new IntrospectionBasedDynamicMBean(server), serverObjectName);
		}


		// create and register workerpool
		ObjectName workerpoolObjectName = new ObjectName(domain + ".server." + address + ":type=Workerpool");
		ManagementFactory.getPlatformMBeanServer().registerMBean(new IntrospectionBasedDynamicMBean(server.getWorkerpool()), workerpoolObjectName);

		return serverObjectName;
	}


	private static void unregisterMBeans(IServer server, String domain, String address) throws JMException {
		address = address.replace(":", "_");
		
		// unregister handler
		ObjectName hdlObjectName = new ObjectName(domain + ".server." + address + ":type=" + server.getHandler().getClass().getSimpleName());
		ManagementFactory.getPlatformMBeanServer().unregisterMBean(hdlObjectName);


		// unregister worker pool
		ObjectName workerpoolObjectName = new ObjectName(domain + ".server." + address +  ":type=Workerpool");
		ManagementFactory.getPlatformMBeanServer().unregisterMBean(workerpoolObjectName);
	}


	private static final class Listener implements IServerListener {

		private static final Logger LOG = Logger.getLogger(Listener.class.getName());

		private IServer server = null;
		private String domain = null;
		private String address = null;

		Listener(IServer server, String domain, String address) {
			this.server = server;
			this.domain = domain;
			this.address = address;

			server.addListener(this);
		}

		public void onInit() {
		}

		public void onDestroy() {
			try {
				unregisterMBeans(server, domain, address);
			} catch (Exception ex) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by deregistering the server (domain=" + domain + "). reason: " + ex.toString());
				}
			}
		}
	}
}
