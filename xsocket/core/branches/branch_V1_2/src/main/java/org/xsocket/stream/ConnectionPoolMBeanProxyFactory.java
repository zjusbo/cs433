// $Id: MultithreadedServerMBeanProxyFactory.java 1134 2007-04-05 17:44:43Z grro $
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

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.xsocket.ILifeCycle;
import org.xsocket.IntrospectionBasedDynamicBean;



/**
 * A Mbean proxy factory, which creates and registers an appropriated mbean 
 * for a given connction pool instance.  
 * 
 * <br><br><b>This class is for test purpose only, and will be modified or discarded in future versions</b>
 * 
 * @author grro@xsocket.org
 */
final class ConnectionPoolMBeanProxyFactory {

	
	/**
	 * creates and registers a mbean for the given connection pool on the platform MBeanServer
	 * 
	 * @param pool  the pool to register 
	 * @throws JMException  if an jmx exception occurs
	 */
	public static void createAndRegister(BlockingConnectionPool pool) throws JMException {
		createAndRegister(pool, "org.xsocket.stream");
	}

	/**
	 * creates and registers a mbean for the given connection pool on the platform MBeanServer
	 * 
	 * @param pool  the pool to register 
	 * @throws JMException  if an jmx exception occurs
	 */
	public static void createAndRegister(NonBlockingConnectionPool pool) throws JMException {
		createAndRegister(pool, "org.xsocket.stream");
	}
	
	/**
	 * creates and registers a mbean for the given connection pool on the platform MBeanServer 
	 * under the given domain name 
	 * 
	 * @param pool     the pool to register 
	 * @param domain   the domain name to use
	 * @throws JMException  if an jmx exception occurs
	 */
	public static void createAndRegister(BlockingConnectionPool pool, String domain) throws JMException {
		createAndRegister(ManagementFactory.getPlatformMBeanServer(), pool, domain);
	}
	

	/**
	 * creates and registers a mbean for the given connection pool on the platform MBeanServer 
	 * under the given domain name 
	 * 
	 * @param pool     the pool to register 
	 * @param domain   the domain name to use
	 * @throws JMException  if an jmx exception occurs
	 */
	public static void createAndRegister(NonBlockingConnectionPool pool, String domain) throws JMException {
		createAndRegister(ManagementFactory.getPlatformMBeanServer(), pool, domain);
	}
	
	
	/**
	 * creates and registers a mbean for the given pool on the given MBeanServer
	 * under the given domain name 
	 *
 	 * @param mbeanServer  the mbean server to use
	 * @param pool         the pool to register 
	 * @param domain       the domain name to use
	 * @throws JMException  if an jmx exception occurs 
	 */
	public static void createAndRegister(MBeanServer mbeanServer, BlockingConnectionPool pool, String domain) throws JMException {
		ObjectName objectName = new ObjectName(domain + ":type=BlockingConnectionPool,name=" + pool.hashCode());
		ManagementFactory.getPlatformMBeanServer().registerMBean(new IntrospectionBasedDynamicBean(pool), objectName);
		
		new BlockingConnectionPoolListener(pool, domain);
	}
	
	
	/**
	 * creates and registers a mbean for the given pool on the given MBeanServer
	 * under the given domain name 
	 *
 	 * @param mbeanServer  the mbean server to use
	 * @param pool         the pool to register 
	 * @param domain       the domain name to use
	 * @throws JMException  if an jmx exception occurs 
	 */
	public static void createAndRegister(MBeanServer mbeanServer, NonBlockingConnectionPool pool, String domain) throws JMException {
		ObjectName objectName = new ObjectName(domain + ":type=NonBlockingConnectionPool,name=" + pool.hashCode());
		ManagementFactory.getPlatformMBeanServer().registerMBean(new IntrospectionBasedDynamicBean(pool), objectName);
		
		new NonBlockingConnectionPoolListener(pool, domain);
	}

	private static void unregister(BlockingConnectionPool pool, String domain) throws JMException {
		ObjectName objectName = new ObjectName(domain + ":type=BlockingConnectionPool,name=" + pool.hashCode());
		ManagementFactory.getPlatformMBeanServer().unregisterMBean(objectName);
	}
	

	private static void unregister(NonBlockingConnectionPool pool, String domain) throws JMException {
		ObjectName objectName = new ObjectName(domain + ":type=NonBlockingConnectionPool,name=" + pool.hashCode());
		ManagementFactory.getPlatformMBeanServer().unregisterMBean(objectName);
	}

	
	private static final class BlockingConnectionPoolListener implements ILifeCycle {
		
		private static final Logger LOG = Logger.getLogger(BlockingConnectionPoolListener.class.getName());
		
		private BlockingConnectionPool pool = null;
		private String domain = null;
		
		BlockingConnectionPoolListener(BlockingConnectionPool pool, String domain) {
			this.pool = pool;
			this.domain = domain;
			
			pool.addListener(this);
		}
		
		public void onInit() {
		}
		
		public void onDestroy() {
			try {
				unregister(pool, domain);
			} catch (Exception ex) { 
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by deregistering the pool (domain=" + domain + "). reason: " + ex.toString());
				}
			} 
		}
	}
	
	private static final class NonBlockingConnectionPoolListener implements ILifeCycle {
		
		private static final Logger LOG = Logger.getLogger(BlockingConnectionPoolListener.class.getName());
		
		private NonBlockingConnectionPool pool = null;
		private String domain = null;
		
		NonBlockingConnectionPoolListener(NonBlockingConnectionPool pool, String domain) {
			this.pool = pool;
			this.domain = domain;
			
			pool.addListener(this);
		}
		
		public void onInit() {
		}
		
		public void onDestroy() {
			try {
				unregister(pool, domain);
			} catch (Exception ex) { 
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by deregistering the pool (domain=" + domain + "). reason: " + ex.toString());
				}
			} 
		}
	}

}
