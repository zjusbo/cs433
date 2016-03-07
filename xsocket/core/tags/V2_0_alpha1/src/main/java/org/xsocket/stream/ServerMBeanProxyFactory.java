// $Id: MultithreadedServerMBeanProxyFactory.java 1386 2007-06-28 11:47:15Z grro $
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
package org.xsocket.stream;

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


import org.xsocket.IntrospectionBasedDynamicBean;
import org.xsocket.stream.io.impl.JmxIoProvider;
import org.xsocket.stream.io.spi.IAcceptorMXBean;
import org.xsocket.stream.io.spi.IServerIoJmxProvider;



/**
 * A Mbean proxy factory, which creates and registers an appropriated mbean
 * for a given {@link Server} instance.
 *
 * <br><br><b>This class is for test purpose only, and will be modified or discarded in future versions</b>
 *
 * @author grro@xsocket.org
 */
public final class ServerMBeanProxyFactory {

	private static final Logger LOG = Logger.getLogger(ServerMBeanProxyFactory.class.getName());

	private static IServerIoJmxProvider jmxProvider = null;


	static {
		String jmxIoProviderClassname = System.getProperty(IServerIoJmxProvider.PROVIDER_CLASSNAME_KEY);
		if (jmxIoProviderClassname != null) {
			try {
				Class jmxProviderClass = Class.forName(jmxIoProviderClassname);
				jmxProvider = (IServerIoJmxProvider) jmxProviderClass.newInstance();
			} catch (Exception e) {
				LOG.warning("error occured by creating jmxProivder " + jmxIoProviderClassname + ": " + e.toString());
			}
		}

		if (jmxProvider == null) {
			jmxProvider = new JmxIoProvider();
		}
	}




	/**
	 * creates and registers a mbean for the given server on the platform MBeanServer
	 *
	 * @param server  the server to register
	 * @return the objectName
	 * @throws JMException  if an jmx exception occurs
	 */
	public static ObjectName createAndRegister(Server server) throws JMException {
		return createAndRegister(server, "org.xsocket.stream");
	}


	/**
	 * creates and registers a mbean for the given server on the platform MBeanServer
	 * under the given domain name
	 *
	 * @param server   the server to register
	 * @param domain   the domain name to use
	 * @return the objectName
	 * @throws JMException  if an jmx exception occurs
	 */
	public static ObjectName createAndRegister(Server server, String domain) throws JMException {
		return createAndRegister(ManagementFactory.getPlatformMBeanServer(), server, domain);
	}


	/**
	 * creates and registers a mbean for the given server on the given MBeanServer
	 * under the given domain name
	 *
 	 * @param mbeanServer  the mbean server to use
	 * @param server       the server to register
	 * @param domain       the domain name to use
	 * @return the objectName
	 * @throws JMException  if an jmx exception occurs
	 */
	public static ObjectName createAndRegister(MBeanServer mbeanServer, Server server, String domain) throws JMException {

		String address = server.getLocalAddress().getCanonicalHostName() + "/" + server.getLocalPort();

		ObjectName serverObjectName = registerMBeans(server, domain, address);
		server.addListener(new Listener(server, domain, address));

		return serverObjectName;
	}


	private static ObjectName registerMBeans(Server server, String domain, String address) throws MalformedObjectNameException, InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException, JMException {
		// create and register handler
		ObjectName hdlObjectName = new ObjectName(domain + ":type=Handler,name=" + address);
		ManagementFactory.getPlatformMBeanServer().registerMBean(new IntrospectionBasedDynamicBean(server.getAppHandler()), hdlObjectName);


		// create and register acceptor
		ObjectName acceptorObjectName = new ObjectName(domain + ":type=Acceptor,name=" + address);
		IAcceptorMXBean acceptorMXBean = jmxProvider.createMBean(server, server.getAcceptor(), domain, address);
		ManagementFactory.getPlatformMBeanServer().registerMBean(acceptorMXBean, acceptorObjectName);


		// create and register workerpool
		ObjectName workerpoolObjectName = new ObjectName(domain + ":type=Workerpool,name=" + address);
		ManagementFactory.getPlatformMBeanServer().registerMBean(new IntrospectionBasedDynamicBean(server.getWorkerpool()), workerpoolObjectName);

		// create and register server
		ObjectName serverObjectName = new ObjectName(domain + ":type=Server,name=" + address);
		IServerMXBean serverMXBean = new ServerMXBeanAdapter(server);
		ManagementFactory.getPlatformMBeanServer().registerMBean(serverMXBean, serverObjectName);

		return serverObjectName;
	}


	private static void unregisterMBeans(IServer server, String domain, String address) throws JMException {
		// unregister handler
		ObjectName hdlObjectName = new ObjectName(domain + ":type=Handler,name=" + address);
		ManagementFactory.getPlatformMBeanServer().unregisterMBean(hdlObjectName);


		// unregister acceptor
		ObjectName acceptorObjectName = new ObjectName(domain + ":type=Acceptor,name=" + address);
		ManagementFactory.getPlatformMBeanServer().unregisterMBean(acceptorObjectName);


		// unregister workerpool
		ObjectName workerpoolObjectName = new ObjectName(domain + ":type=Workerpool,name=" + address);
		ManagementFactory.getPlatformMBeanServer().unregisterMBean(workerpoolObjectName);


		// unregister server
		ObjectName serverObjectName = new ObjectName(domain + ":type=Server,name=" + address);
		ManagementFactory.getPlatformMBeanServer().unregisterMBean(serverObjectName);
	}



	private static final class Listener implements IServerListener {

		private static final Logger LOG = Logger.getLogger(Listener.class.getName());

		private Server server = null;
		private String domain = null;
		private String address = null;

		Listener(Server server, String domain, String address) {
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

	private static final class ServerMXBeanAdapter implements IServerMXBean {

		private IServer delegee = null;

		public ServerMXBeanAdapter(IServer delegee) {
			this.delegee = delegee;
		}


		public int getConnectionTimeoutSec() {
			return delegee.getConnectionTimeoutSec();
		}


		public int getIdleTimeoutSec() {
			return delegee.getIdleTimeoutSec();
		}

		public boolean isOpen() {
			return delegee.isOpen();
		}

		public void setConnectionTimeoutSec(int timeoutSec) {
			delegee.setConnectionTimeoutSec(timeoutSec);

		}

		public void setIdleTimeoutSec(int timeoutInSec) {
			delegee.setIdleTimeoutSec(timeoutInSec);
		}
	}
}
