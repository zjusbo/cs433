// $Id: MultithreadedServerMBeanProxyFactory.java 1043 2007-03-20 18:59:28Z grro $
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
package org.xsocket.stream.management;

import java.lang.management.ManagementFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.xsocket.Dispatcher;
import org.xsocket.DynamicWorkerPool;
import org.xsocket.IDispatcher;
import org.xsocket.IWorkerPool;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.IMutlithreadedServerListener;
import org.xsocket.stream.MultithreadedServer;



/**
 * A Mbean proxy factory, which creates and registers an appropriated mbean 
 * for a given {@link MultithreadedServer} instance.  
 * 
 * <br><br><b>This class is for test purpose only, and will be modified or discarded in future versions</b>
 * 
 * @author grro@xsocket.org
 */
public final class MultithreadedServerMBeanProxyFactory {

	
	/**
	 * creates and registers a mbean for the given server on the platform MBeanServer
	 * 
	 * @param server  the server to register 
	 * @throws JMException  if an jmx exception occurs
	 */
	public static void createAndRegister(MultithreadedServer server) throws JMException {
		createAndRegister(server, "org.xsocket.stream");
	}
	

	/**
	 * creates and registers a mbean for the given server on the platform MBeanServer 
	 * under the given domain name 
	 * 
	 * @param server   the server to register 
	 * @param domain   the domain name to use
	 * @throws JMException  if an jmx exception occurs
	 */
	public static void createAndRegister(MultithreadedServer server, String domain) throws JMException {
		createAndRegister(ManagementFactory.getPlatformMBeanServer(), server, domain);
	}
	
	
	/**
	 * creates and registers a mbean for the given server on the given MBeanServer
	 * under the given domain name 
	 *
 	 * @param mbeanServer  the mbean server to use
	 * @param server       the server to register 
	 * @param domain       the domain name to use
	 * @throws JMException  if an jmx exception occurs 
	 */
	public static void createAndRegister(MBeanServer mbeanServer, MultithreadedServer server, String domain) throws JMException {
		ObjectName objectName = new ObjectName(domain + ":type=MultithreadedServer,name=" + server.getLocalAddress().getHostName() + "." + server.getLocalPort());
		ManagementFactory.getPlatformMBeanServer().registerMBean(new IntrospectionBasedDynamicBean(server), objectName);
		
		Listener listener = new Listener(server, domain);
		listener.onWorkerPoolUpdated(null, server.getWorkerPool());
		
		for (IDispatcher dispatcher : server.getDispatcher()) {
			listener.onDispatcherAdded(dispatcher);
		}
	}
	
	
	private static void unregister(IMultithreadedServer server, String domain) throws JMException {
		ObjectName objectName = new ObjectName(domain + ":type=MultithreadedServer,name=" + server.getLocalAddress().getHostName() + "." + server.getLocalPort());
		ManagementFactory.getPlatformMBeanServer().unregisterMBean(objectName);
	}
	
	
	private static final class Listener implements IMutlithreadedServerListener {
		
		private static final Logger LOG = Logger.getLogger(Listener.class.getName());
		
		private MultithreadedServer server = null;
		private String domain = null;
		
		Listener(MultithreadedServer server, String domain) {
			this.server = server;
			this.domain = domain;
			
			server.addListener(this);
		}
		
		public void onInit() {
		}
		
		public void onDestroy() {
			try {
				unregister(server, domain);
			} catch (Exception ex) { 
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by deregistering the server (domain=" + domain + "). reason: " + ex.toString());
				}
			} 
		}
		

		
		public void onWorkerPoolUpdated(IWorkerPool oldWorkerPool, IWorkerPool newWorkerPool) {
			try {
				if (oldWorkerPool instanceof DynamicWorkerPool) {
					unregisterDynamicWorkerPool((DynamicWorkerPool) oldWorkerPool, domain, server.getLocalAddress().getHostName(), server.getLocalPort());
				}
			} catch (Exception ignore) { }
			
			try {
				if (newWorkerPool instanceof DynamicWorkerPool) {
					registerDynamicWorkerPool((DynamicWorkerPool) newWorkerPool, domain, server.getLocalAddress().getHostName(), server.getLocalPort());
				}
			} catch (Exception ignore) { }
		}
		
		
		public void onDispatcherAdded(IDispatcher dispatcher) {
			try {
				if (dispatcher instanceof Dispatcher) {
					if (dispatcher.getClass().getSimpleName().equals("IoSocketDispatcher")) {
						registerIoSocketDispatcher((Dispatcher) dispatcher, domain, server.getLocalAddress().getHostName(), server.getLocalPort());
					} 
				}
			} catch (Exception ignore) { }
		}

		
		public void onDispatcherRemoved(IDispatcher dispatcher) {
			try {
				if (dispatcher instanceof Dispatcher) {
					if (dispatcher.getClass().getSimpleName().equals("IoSocketDispatcher")) {
						unregisterIoSocketDispatcher((Dispatcher) dispatcher, domain, server.getLocalAddress().getHostName(), server.getLocalPort());
					} 
				}
			} catch (Exception ignore) { }
		}
	}
	
	
	public static void registerIoSocketDispatcher(Dispatcher dispatcher, String domain, String hostname, int port) throws JMException {
		ObjectName objectName = new ObjectName(domain +":type=IoSocketDispatcher,name=" + hostname + "." + port + "." + dispatcher.hashCode());
		ManagementFactory.getPlatformMBeanServer().registerMBean(new IntrospectionBasedDynamicBean(dispatcher), objectName);
	}

	public static void unregisterIoSocketDispatcher(Dispatcher dispatcher, String domain, String hostname, int port) throws JMException {
		ObjectName objectName = new ObjectName(domain +":type=IoSocketDispatcher,name=" + hostname + "." + port + "." + dispatcher.hashCode());
		ManagementFactory.getPlatformMBeanServer().unregisterMBean(objectName);
	}	
	
	public static void registerDynamicWorkerPool(DynamicWorkerPool workerPool, String domain, String hostname, int port) throws JMException {
		ObjectName objectName = new ObjectName(domain + ":type=DynamicWorkerPool,name=" + hostname + "." + port);
		ManagementFactory.getPlatformMBeanServer().registerMBean(new IntrospectionBasedDynamicBean(workerPool), objectName);
	}

	public static void unregisterDynamicWorkerPool(DynamicWorkerPool workerPool, String domain, String hostname, int port) throws JMException {
		ObjectName objectName = new ObjectName(domain + ":type=DynamicWorkerPool,name=" + hostname + "." + port);
		ManagementFactory.getPlatformMBeanServer().unregisterMBean(objectName);
	}	
}
