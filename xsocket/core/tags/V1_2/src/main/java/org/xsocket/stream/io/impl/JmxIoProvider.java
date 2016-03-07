// $Id$
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
package org.xsocket.stream.io.impl;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.management.JMException;
import javax.management.ObjectName;

import org.xsocket.Dispatcher;
import org.xsocket.IDispatcher;
import org.xsocket.IWorkerPool;
import org.xsocket.IntrospectionBasedDynamicBean;
import org.xsocket.stream.MultithreadedServer;
import org.xsocket.stream.io.spi.IAcceptor;
import org.xsocket.stream.io.spi.IServerIoJmxProvider;



/**
 * A Mbean proxy factory, which creates and registers an appropriated mbean 
 * for a given {@link MultithreadedServer} instance.  
 * 
 * <br><br><b>This class is for test purpose only, and will be modified or discarded in future versions</b>
 * 
 * @author grro@xsocket.org
 */
public final class JmxIoProvider implements IServerIoJmxProvider {

	public Object createMBean(MultithreadedServer server, IAcceptor acceptor, String domain) throws JMException {
		
		if (acceptor instanceof Acceptor) {
			
			Acceptor acptr = (Acceptor) acceptor;
			Listener listener = new Listener(server, acptr, domain);
			acptr.getDispatcherPool().addListener(listener);
			
			for (IDispatcher dispatcher : acptr.getDispatchers()) {
				try {
					listener.onDispatcherAdded((Dispatcher) dispatcher);
				} catch(Exception ignore) { }
			}
			
			
			return new IntrospectionBasedDynamicBean(new ServerManagementBean(server, acptr));
			
		} else {
			return new IntrospectionBasedDynamicBean(server);
		}
	}
	
	
	private static final class Listener implements IIoSocketDispatcherPoolListener {
	
		
		private MultithreadedServer server = null;
		private String domain = null;
		
		Listener(MultithreadedServer server, Acceptor acceptor, String domain) {
			this.server = server;
			this.domain = domain;
		}
		
		
		public void onWorkerPoolUpdated(IWorkerPool oldWorkerPool, IWorkerPool newWorkerPool) {
			
		}
		
		
		public void onDispatcherAdded(IDispatcher dispatcher) {
			try {
				ObjectName objectName = new ObjectName(domain +":type=IoSocketDispatcher,name=" + server.getLocalAddress().getHostName() + "." + server.getLocalPort() + "." + dispatcher.hashCode());
				ManagementFactory.getPlatformMBeanServer().registerMBean(new IntrospectionBasedDynamicBean(dispatcher), objectName);
			} catch (Exception ignore) { }
		}

		
		public void onDispatcherRemoved(IDispatcher dispatcher) {
			try {
				ObjectName objectName = new ObjectName(domain +":type=IoSocketDispatcher,name=" + server.getLocalAddress().getHostName() + "." + server.getLocalPort() + "." + dispatcher.hashCode());
				ManagementFactory.getPlatformMBeanServer().unregisterMBean(objectName);
			} catch (Exception ignore) { }
		}
	}
	
	

    
    private static final class ServerManagementBean {
    	
    	private MultithreadedServer server = null;
    	private Acceptor acceptor = null;

    	/**
    	 * constructore
    	 *  
    	 * @param obj  the object to create a mbean for
    	 */
    	ServerManagementBean(MultithreadedServer server, Acceptor acceptor) {
    		this.server = server;
    		this.acceptor = acceptor;
    	}
    	
    	
    	/**
    	 * set the size of the preallocation buffer, 
    	 * for reading incomming data
    	 *
    	 * @param size preallocation buffer size
    	 */
    	public void setReceiveBufferPreallocationSize(int size) {
    		acceptor.setReceiveBufferPreallocationSize(size);
    	}

    	
    	/**
    	 * get the size of the preallocation buffer, 
    	 * for reading incomming data
    	 *   
    	 * @return preallocation buffer size
    	 */
    	public int getReceiveBufferPreallocationSize() {
    		return acceptor.getReceiveBufferPreallocationSize();
    	}
    	
    	

    	
    	/**
    	 * returns the idle timeout in sec. 
    	 * 
    	 * @return idle timeout in sec
    	 */
    	public int getIdleTimeoutSec() {
    		return server.getIdleTimeoutSec();
    	}
    	
    	
    	
    	/**
    	 * sets the idle timeout in sec 
    	 * 
    	 * @param timeoutInSec idle timeout in sec
    	 */
    	public void setIdleTimeoutSec(int timeoutInSec) {
    		server.setIdleTimeoutSec(timeoutInSec);
    	}


    	/**
    	 * gets the connection timeout
    	 * 
    	 * @return connection timeout
    	 */
    	public int getConnectionTimeoutSec() {
    		return server.getConnectionTimeoutSec();
    	}
    	
    	
    	/**
    	 * sets the max time for a connections. By 
    	 * exceeding this time the connection will be
    	 * terminated
    	 * 
    	 * @param timeoutSec the connection timeout in sec
    	 */
    	public void setConnectionTimeoutSec(int timeoutSec) {
    		server.setConnectionTimeoutSec(timeoutSec);
    	}
    	
    	
    	public long getCountHandledConnections() {
    		return acceptor.getNumberOfHandledConnections();
    	}

    	
    	public int getDispatcherPoolSize() {
    		return acceptor.getDispatcherPoolSize();
    	}
    	
    	public void setDispatcherPoolSize(int size) {
    		acceptor.setDispatcherPoolSize(size);
    	}
    	
    	
    	public List<String> getOpenConnections() {
    		return acceptor.getOpenConnections();
    	}
  
    	
      	public int getNumberOpenConnections() {
    		return acceptor.getNumberOfOpenConnections();
    	}
    	
    	
    	public long getCountConnectionTimeouts() {
    		return acceptor.getNumberOfConnectionTimeouts();
    	}

    	public long getCountIdleTimeouts() {
    		return acceptor.getNumberOfIdleTimeouts();
    	}
    	
    	/**
    	 * get the server port 
    	 * 
    	 * @return the server port
    	 */
    	public int getLocalPort() {
    		return server.getLocalPort();
    	}
    	

    	/**
    	 * get the local address
    	 * @return the local address
    	 */
    	public String getLocalAddressString() {
    		return server.getLocalAddress().toString();
    	}
    	
    	
    	
    	public Integer getWorkerpoolActiveCount() {
    		if (server.getWorkerpool() instanceof ThreadPoolExecutor) {
    			ThreadPoolExecutor tpe = (ThreadPoolExecutor) server.getWorkerpool();
    			return tpe.getActiveCount();
    			
    		} else {
    			return null;
    		}
    	}
    	
    	public Integer getWorkerpoolMaximumPoolSize() {
    		if (server.getWorkerpool() instanceof ThreadPoolExecutor) {
    			ThreadPoolExecutor tpe = (ThreadPoolExecutor) server.getWorkerpool();
    			return tpe.getMaximumPoolSize();
    			
    		} else {
    			return null;
    		}
    	}

    	
    	public Integer getWorkerpoolSize() {
    		if (server.getWorkerpool() instanceof ThreadPoolExecutor) {
    			ThreadPoolExecutor tpe = (ThreadPoolExecutor) server.getWorkerpool();
    			return tpe.getPoolSize();
    			
    		} else {
    			return null;
    		}
    	}
    	
    	public Integer getWorkerpoolLargestPoolSize() {
    		if (server.getWorkerpool() instanceof ThreadPoolExecutor) {
    			ThreadPoolExecutor tpe = (ThreadPoolExecutor) server.getWorkerpool();
    			return tpe.getLargestPoolSize();
    			
    		} else {
    			return null;
    		}
    	}
    	
    	public Integer getWorkerpoolKeepAliveTimeSec() {
    		if (server.getWorkerpool() instanceof ThreadPoolExecutor) {
    			ThreadPoolExecutor tpe = (ThreadPoolExecutor) server.getWorkerpool();
    			return (int) tpe.getKeepAliveTime(TimeUnit.SECONDS);
    			
    		} else {
    			return null;
    		}
    	}
    }

}