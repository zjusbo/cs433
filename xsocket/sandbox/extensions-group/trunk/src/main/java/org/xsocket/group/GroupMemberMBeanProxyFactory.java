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
package org.xsocket.group;

import java.lang.management.ManagementFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.xsocket.IntrospectionBasedDynamicMBean;





/**
 * A Mbean proxy factory, which creates and registers an appropriated mbean 
 * for a given {@link GroupMember} instance.  
 * 
 * <br><br><b>This class is for test purpose only, and will be modified or discarded in future versions</b>
 * 
 * @author grro@xsocket.org
 */
public final class GroupMemberMBeanProxyFactory {

	private static final Logger LOG = Logger.getLogger(GroupMemberMBeanProxyFactory.class.getName());
	
	/**
	 * creates and registers a mbean for the given endpoint on the platform MBeanServer
	 * 
	 * @param endpoint  the endpont to register 
	 * @throws JMException  if an jmx exception occurs
	 */
	public static void createAndRegister(GroupMember endpoint) throws JMException {
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
	public static void createAndRegister(GroupMember endpoint, String domain) throws JMException {
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
	public static void createAndRegister(MBeanServer mbeanServer, GroupMember endpoint, String domain) throws JMException {
		ObjectName objectName = new ObjectName(domain + ":type=GroupEndpoint,name=" + cleanName(endpoint.getId()));
		ManagementFactory.getPlatformMBeanServer().registerMBean(new IntrospectionBasedDynamicMBean(endpoint), objectName);
		endpoint.addListener(new LifeCycleListener(endpoint, domain));
		
	}
	
	
	private static void unregister(GroupMember endpoint, String domain) throws JMException {
		ObjectName objectName = new ObjectName(domain + ":type=GroupEndpoint,name=" + cleanName(endpoint.getId()));
		ManagementFactory.getPlatformMBeanServer().unregisterMBean(objectName);
	}	

	private static String cleanName(String name) {
		return name.replaceAll(":", ".");
	}
	
	
	private static final class LifeCycleListener implements IGroupMemberListener {
		
		
		private GroupMember endpoint = null;
		private String domain = null;
		
		public LifeCycleListener(GroupMember endpoint, String domain) {
			this.endpoint = endpoint;
			this.domain = domain;
		}
		
		public void onInit() {
		}
		
		public void onDestroy() {
			try {
				unregister(endpoint, domain);
			} catch (Exception e) { 
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by unregistering mbean " + e.toString());
				}
			} 
			endpoint.removeListener(this);
		}
		
		public void onEnterConsistentState() {
			// TODO Auto-generated method stub
			
		}
		
		public void onEnterInconsistentState() {
			// TODO Auto-generated method stub
			
		}
	}
}
