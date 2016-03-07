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
package org.xcache;

import java.lang.management.ManagementFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.xsocket.IntrospectionBasedDynamicBean;
import org.xsocket.stream.IServerListener;
import org.xsocket.stream.ServerMBeanProxyFactory;



/**
 * A Mbean proxy factory, which creates and registers an appropriated mbean 
 * for a given {@link Server} instance.  
 * 
 * <br><br><b>This class is for test purpose only, and will be modified or discarded in future versions</b>
 * 
 * @author grro@xsocket.org
 */
public final class CacheServerMBeanProxyFactory {
	
	private static final Logger LOG = Logger.getLogger(CacheServerMBeanProxyFactory.class.getName());
	
	
	/**
	 * creates and registers a mbean for the given server on the platform MBeanServer
	 * 
	 * @param server  the server to register 
	 * @throws JMException  if an jmx exception occurs
	 */
	public static void createAndRegister(CacheServer server) throws JMException {
		createAndRegister(server, "org.xcache");
	}
	

	/**
	 * creates and registers a mbean for the given server on the platform MBeanServer 
	 * under the given domain name 
	 * 
	 * @param server   the server to register 
	 * @param domain   the domain name to use
	 * @throws JMException  if an jmx exception occurs
	 */
	public static void createAndRegister(CacheServer server, String domain) throws JMException {
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
	public static void createAndRegister(MBeanServer mbeanServer, final CacheServer server, final String domain) throws JMException {
		
		ServerMBeanProxyFactory.createAndRegister(server.getUnderlyingServer(), domain);
		
		register(server, domain);
		
		server.addListener(new IServerListener() {
			public void onInit() {
			}
			
			public void onDestroy() {
				try {
					unregister(server, domain);
				} catch (Exception e) { 
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("couldn't unregister mbean. reason: " + e.toString());
					}
				}				
			}
		});
	}
	
	
	private static void register(CacheServer server, String domain) throws JMException {
		
		ObjectName objectName = new ObjectName(domain + ":type=StoreServer,name=" + server.getLocalAddress().getHostName() + "." + server.getLocalPort());

		IntrospectionBasedDynamicBean mbean = new IntrospectionBasedDynamicBean(server);
		ManagementFactory.getPlatformMBeanServer().registerMBean(mbean, objectName);
		
		server.addListener(new Listener(server, domain));
	}
	
	
	private static void unregister(CacheServer server, String domain) throws JMException {
		ObjectName objectName = new ObjectName(domain + ":type=StoreServer,name=" + server.getLocalAddress().getHostName() + "." + server.getLocalPort());
		ManagementFactory.getPlatformMBeanServer().unregisterMBean(objectName);
	}
	
	
	private static final class Listener implements IServerListener {
		
		private static final Logger LOG = Logger.getLogger(Listener.class.getName());
		
		private CacheServer server = null;
		private String domain = null;
		
		Listener(CacheServer server, String domain) {
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
	}	
}
