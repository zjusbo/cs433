// $Id: MultithreadedServer.java 457 2006-12-09 14:13:33Z grro $
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

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.net.BindException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.ObjectName;
import javax.management.StandardMBean;
import javax.net.ssl.SSLContext;

import org.xsocket.server.management.MultithreadedServerMBean;
import org.xsocket.util.TextUtils;




/**
 * Implementation of a multithreaded server. For more information see
 * <code>IMultithreadedServer</code>
 * 
 * @see IMultithreadedServer
 *  
 * @author grro@xsocket.org
 */
public final class MultithreadedServer implements IMultithreadedServer, MultithreadedServerMBean {

	private static final Logger LOG = Logger.getLogger(MultithreadedServer.class.getName());
	
	
	
	// running flag
	private boolean isRunning = true;


	// socket
	private int port = 0;
	private InetAddress localAddress = null;


	// handler
	private IHandler appHandler = null;
	private IHandlerTypeInfo appHandlerTypeInfo = null;

	
	// workers 
	private WorkerPool workerPool = new WorkerPool();
	

	// acceptor & dispather pool
	private Acceptor acceptor = null;
	private DispatcherPool dispatcherPool = null;
	
	
	// statistics & management
	private String hostname = null;
	private String appDomain = null;
	private ObjectName mbeanName = null;
	

	
	/**
	 * constructor <br>
	 * 
	 * The idle- and connection time out will be set with the default values 
	 *
	 * @param port the server port
	 * @throws UnknownHostException if the locale host cannot determined
	 */
	public MultithreadedServer(int port) throws UnknownHostException, IOException {
		this(port, "xsocket", false, null);
	}

		
	/**
	 * constructor<br>
	 * 
	 * The idle- and connection time out will be set with the default values 
	 *
	 * @param port the server port
	 * @param appDomain the application domain 
	 * @throws UnknownHostException if the locale host cannot determined
	 */
	public MultithreadedServer(int port, String appDomain, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this.port = port;
		this.appDomain = appDomain + "." + port;

		this.localAddress = InetAddress.getLocalHost();
		this.hostname = localAddress.getCanonicalHostName() + "." + port;

		try {
			setWorkerPoolSize(Runtime.getRuntime().availableProcessors() * 10);
			dispatcherPool = new DispatcherPool(Runtime.getRuntime().availableProcessors() + 1, this.appDomain, workerPool, Long.MAX_VALUE, Long.MAX_VALUE);
			acceptor = new Acceptor(port, dispatcherPool, sslContext, sslOn, Integer.toString(port));
			
			setConnectionTimeoutSec(DEFAULT_CONNECTION_TIMEOUT_SEC);
			setIdleTimeoutSec(DEFAULT_IDLE_TIMEOUT_SEC);
			
		} catch (BindException be) {	
			throw be;
		}
	}	



	/**
	 * {@inheritDoc}
	 */
	WorkerPool getWorkerPool() {
		return workerPool;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void shutdown() {
		if (isRunning) {
			isRunning = false;

	        try {
   				ManagementFactory.getPlatformMBeanServer().unregisterMBean(mbeanName);
	        } catch (Exception mbe) {
	        	LOG.warning("error " + mbe.toString() + " occured while unregistering mbean");
	        }

	        	        
	        if (LOG.isLoggable(Level.FINER)) {
				LOG.fine("close acceptor");
			}

	        acceptor.shutdown();
	        dispatcherPool.shutdown();
	        workerPool.shutdownNow();
			
			destroyCurrentHandler();
		}
	}



	/**
	 * {@inheritDoc}
	 */
	public final void run() {
		if (appHandler == null) {
			LOG.warning("no handler has been set. Call setHandler-method to set an assigned handler");
		}

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				shutdown();
			}
		});

        try {
        	StandardMBean mbean = new StandardMBean(this, MultithreadedServerMBean.class);
        	mbeanName = new ObjectName(appDomain + ":type=MultithreadedServer,name=" + hostname);
  			ManagementFactory.getPlatformMBeanServer().registerMBean(mbean, mbeanName);
        } catch (Exception mbe) {
        	LOG.warning("error " + mbe.toString() + " occured while registering mbean");
        }

		dispatcherPool.run();
		
		LOG.info("server " + appDomain + " listening on port " + port + " (" + getVersionInfo() + ")");
		LOG.fine("dispatcherPoolSize=" + getDispatcherPoolSize() + " workerPoolsize=" + getWorkerPoolSize());
		LOG.fine("preallocationSize=" + getReceiveBufferPreallocationSize());
		LOG.fine("connectionTimeout=" + TextUtils.printFormatedDuration(dispatcherPool.getConnectionTimeout()) 
				  + "; idleTimeout=" + TextUtils.printFormatedDuration(dispatcherPool.getIdleTimeout()));
		
		acceptor.run();
	}


	
	/**
	 * {@inheritDoc}
	 */
	public final void setDispatcherPoolSize(int size) {
		dispatcherPool.setSize(size);
	}

			

	/**
	 * {@inheritDoc}
	 */
	public boolean isRunning() {
		return isRunning;
	}



	/**
	 * {@inheritDoc}
	 */
	public void setHandler(IHandler appHandler) {
		if (appHandler == null) {
			throw new NullPointerException("handler have to be not null");
		}
		
		
		destroyCurrentHandler();
		
		this.appHandler = appHandler;
		appHandlerTypeInfo = new HandlerMetaData(appHandler);

		initCurrentHandler();
	}


	private void initCurrentHandler() {
		injectContext(this.appHandler);
		
		acceptor.setHandler(appHandler, appHandlerTypeInfo);

		if (appHandlerTypeInfo.isLifeCycleHandler()) {
			((ILifeCycle) appHandler).onInit();
		}
	}

	

	private void injectContext(IHandler hdl) {
		IHandlerContext ctx = null;
		Field[] fields = hdl.getClass().getDeclaredFields();
		for (Field field : fields) {
			if ((field.getType() == IHandlerContext.class) && (field.getAnnotation(Resource.class) != null)) {
				field.setAccessible(true);
				try {
					if (ctx == null) {
						ctx = new HandlerContext();
					}
					field.set(hdl, ctx);
				} catch (IllegalAccessException iae) {
					LOG.warning("couldn't set HandlerContext for attribute " + field.getName() + ". Reason " + iae.toString());
				}				
			}
		}
	}

	
	
	private void destroyCurrentHandler() {
		if (appHandler != null) {
			if (appHandlerTypeInfo.isLifeCycleHandler()) {
				((ILifeCycle) this.appHandler).onDestroy();
			}	
			appHandler = null;
		}
	}

	

	
	/**
	 * {@inheritDoc}
	 */
	public final int getPort() {
		return port;
	}


	
	/**
	 * {@inheritDoc}
	 */
	public void setWorkerPoolSize(int workerSize) {
		workerPool.setSize(workerSize);
	}


	/**
	 * {@inheritDoc}
	 */
	public int getWorkerPoolSize() {
		return workerPool.getSize();
	}


	/**
	 * {@inheritDoc}
	 */
	public final int getReceiveBufferPreallocationSize() {
		return dispatcherPool.getReceiveBufferPreallocationSize();
	}

	

	/**
	 * {@inheritDoc}
	 */
	public void setReceiveBufferPreallocationSize(int size) {
		dispatcherPool.setReceiveBufferPreallocationSize(size);
	}

	
	/**
	 * {@inheritDoc}
	 */
	public void setConnectionTimeoutSec(int timeoutSec) {
		dispatcherPool.setConnectionTimeout(((long) timeoutSec) *  1000);
	}

	
	
	/**
	 * {@inheritDoc}
	 */
	public void setIdleTimeoutSec(int timeoutInSec) {
		dispatcherPool.setIdleTimeout(((long) timeoutInSec) * 1000);
	}

	


	/**
	 * {@inheritDoc}
	 */
	public final int getDispatcherPoolSize() {
		return dispatcherPool.getSize();
	}


	/**
	 * {@inheritDoc}
	 */
	public final int getConnectionTimeoutSec() {
		return (int) (getConnectionTimeout() / 1000);
	}


	private long getConnectionTimeout() {
		return dispatcherPool.getConnectionTimeout();
	}


	/**
	 * {@inheritDoc}
	 */
	public final int getIdleTimeoutSec() {
		return (int) (getIdleTimeout() / 1000);
	}


	private long getIdleTimeout() {
		return dispatcherPool.getIdleTimeout();
	}



	/**
	 * {@inheritDoc}
	 */
	public int getNumberOfConnectionTimeout() {
		return dispatcherPool.getNumberOfConnectionTimeout();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int getNumberOfIdleTimeout() {
		return dispatcherPool.getNumberOfIdleTimeout();
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final List<String> getOpenConnections() {
		return dispatcherPool.getOpenConnections();
	}


	/**
	 * {@inheritDoc}
	 */
	public int getNumberOfOpenConnections() {
		return dispatcherPool.getNumberOfOpenConnections();
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final long getNumberOfHandledConnections() {
		return dispatcherPool.getNumberOfHandledConnections();
	}

	
	
	private String getVersionInfo() {
		Package p = Package.getPackage("org.xsocket");
		if (p != null) {
			return p.getSpecificationTitle() + " " + p.getImplementationVersion();
		} else {
			return "";
		}
	}

	
	private static final class HandlerMetaData implements IHandlerTypeInfo {

	    private boolean isLifeCycleHandler = false;
		private boolean isConnectionScoped = false;
		private boolean isConnectHandler = false;
	    private boolean isDisconnectHandler = false;
		private boolean isDataHandler = false;
		private boolean isTimeoutHandler = false;

		
		public HandlerMetaData(IHandler handler) {
			isConnectHandler = (handler instanceof IConnectHandler);
			isDisconnectHandler = (handler instanceof IDisconnectHandler);
			isDataHandler = (handler instanceof IDataHandler);
			isTimeoutHandler = (handler instanceof ITimeoutHandler);
			isConnectionScoped = (handler instanceof IConnectionScoped); 
			isLifeCycleHandler = (handler instanceof ILifeCycle);
		}
		
		public boolean isLifeCycleHandler() {
			return isLifeCycleHandler;
		}
		
		public boolean isConnectHandler() {
			return isConnectHandler;
		}
		
		public boolean isConnectionScoped() {
			return isConnectionScoped;
		}
		
		public boolean isDataHandler() {
			return isDataHandler;
		}
		
		public boolean isDisconnectHandler() {
			return isDisconnectHandler;
		}
		
		public boolean isTimeoutHandler() {
			return isTimeoutHandler;
		}
	}
	

	private final class HandlerContext implements IHandlerContext {
		
		/**
		 * {@inheritDoc}
		 */
		public int getLocalePort() {
			return MultithreadedServer.this.getPort();
		}

		/**
		 * {@inheritDoc}
		 */
		public InetAddress getLocaleAddress() {
			return MultithreadedServer.this.localAddress;
		}

		/**
		 * {@inheritDoc}
		 */
		public String getDomainname() {
			return MultithreadedServer.this.appDomain;
		}
	}
}
