// $Id: MultithreadedServerMBeanProxyFactory.java 1073 2007-03-22 17:21:36Z grro $
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
package org.xsocket.group.management;

import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;


import org.xsocket.group.GroupEndpoint;
import org.xsocket.group.IGroupNodeListener;



/**
 * A Mbean proxy factory, which creates and registers an appropriated mbean 
 * for a given {@link GroupEndpoint} instance.  
 * 
 * <br><br><b>This class is for test purpose only, and will be modified or discarded in future versions</b>
 * 
 * @author grro@xsocket.org
 */
public final class GroupEndpointMBeanProxyFactory {

	
	/**
	 * creates and registers a mbean for the given endpoint on the platform MBeanServer
	 * 
	 * @param endpoint  the endpont to register 
	 * @throws JMException  if an jmx exception occurs
	 */
	public static void createAndRegister(GroupEndpoint endpoint) throws JMException {
		createAndRegister(endpoint, "org.xsocket.group");
	}
	

	/**
	 * creates and registers a mbean for the given endpoint on the platform MBeanServer
	 * under the given domain name 
	 * 
	 * @param endpoint  the endpont to register 
	 * @param domain    the domain name to use
	 * @throws JMException  if an jmx exception occurs
	 */
	public static void createAndRegister(GroupEndpoint endpoint, String domain) throws JMException {
		createAndRegister(ManagementFactory.getPlatformMBeanServer(), endpoint, domain);
	}
	
	
	/**
	 * creates and registers a mbean for the given endpoint on the platform MBeanServer
	 * under the given domain name 
	 * 
 	 * @param mbeanServer  the mbean server to use
	 * @param endpoint     the endpont to register 
	 * @param domain       the domain name to use
	 * @throws JMException  if an jmx exception occurs 
	 */
	public static void createAndRegister(MBeanServer mbeanServer, GroupEndpoint endpoint, String domain) throws JMException {
		ObjectName objectName = new ObjectName(domain + ":type=GroupEndpoint,name=" + endpoint.hashCode());
		ManagementFactory.getPlatformMBeanServer().registerMBean(new IntrospectionBasedDynamicBean(endpoint), objectName);
		
		Listener listener = new Listener(endpoint, domain);
		endpoint.addListener(listener);
	}
	
	
	private static void unregister(GroupEndpoint endpoint, String domain) throws JMException {
		ObjectName objectName = new ObjectName(domain + ":type=GroupEndpoint,name=" + endpoint.hashCode());
		ManagementFactory.getPlatformMBeanServer().unregisterMBean(objectName);
	}
	
	
	
	private static final class Listener implements IGroupNodeListener {
		
		private static final Logger LOG = Logger.getLogger(Listener.class.getName());
		
		private GroupEndpoint endpoint = null;
		private String domain = null;
		
		Listener(GroupEndpoint endpoint, String domain) {
			this.endpoint = endpoint;
			this.domain = domain;
			
			endpoint.addListener(this);
		}
		
		public void onInit() {
		}
		
		public void onDestroy() {
			try {
				unregister(endpoint, domain);
			} catch (Exception ex) { 
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by deregistering the server (domain=" + domain + "). reason: " + ex.toString());
				}
			} 
		}
		
		public void onMemberJoined(InetSocketAddress address) {
			
		}
		
		 
		public void onMemberLeaved(InetSocketAddress address) {
			
		}
		
		public void onMemberTimeout(InetSocketAddress address) {
			
		}
	}	
}
