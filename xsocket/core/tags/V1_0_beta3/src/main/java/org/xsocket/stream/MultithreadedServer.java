// $Id: MultithreadedServer.java 778 2007-01-16 07:13:20Z grro $
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

import org.xsocket.Resource;
import org.xsocket.DataConverter;
import org.xsocket.WorkerPool;




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
    private boolean isLifeCycleHandler = false;
	private boolean isConnectionScoped = false;
	private boolean isConnectHandler = false;
    private boolean isDisconnectHandler = false;
	private boolean isDataHandler = false;
	private boolean isTimeoutHandler = false;
	
	
	// workers 
	private WorkerPool workerPool = new WorkerPool();
	

	// acceptor
	private Acceptor acceptor = null;
	
	
	// statistics & management
	private String hostname = null;
	private String appDomain = null;
	private ObjectName mbeanName = null;
	


	
	
	/**
	 * constructor <br>
	 * 
	 * The idle- and connection time out will be set with the default values 
	 *
	 * @param handler the handler to use
	 * @throws IOException If some other I/O error occurs
	 * @throws UnknownHostException if the locale host cannot determined
	 */
	public MultithreadedServer(IHandler handler) throws UnknownHostException, IOException {
		this(0, handler);
	}

	
	
	
	/**
	 * constructor <br>
	 * 
	 * The idle- and connection time out will be set with the default values 
	 *
	 * @param port the server port
	 * @param handler the handler to use
	 * @throws UnknownHostException if the locale host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(int port, IHandler handler) throws UnknownHostException, IOException {
		this(port, handler, "xsocket.tcp", false, null);
	}

	
	/**
	 * constructor<br>
	 * 
	 * The idle- and connection time out will be set with the default values 
	 *
	 * @param handler the handler to use 
	 * @param  applicationDomain the application domain 
	 * @throws UnknownHostException if the locale host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(IHandler handler, String applicationDomain) throws UnknownHostException, IOException {
		this(0, handler, applicationDomain, false, null);
	}

	
	/**
	 * constructor<br>
	 * 
	 * The idle- and connection time out will be set with the default values 
	 *
	 * @param port the server port
	 * @param handler the handler to use 
	 * @param  applicationDomain the application domain 
	 * @throws UnknownHostException if the locale host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(int port, IHandler handler, String applicationDomain) throws UnknownHostException, IOException {
		this(port, handler, applicationDomain, false, null);
	}

		
	/**
	 * constructor
	 * 
	 * @param port               port the server port
	 * @param handler            the handler to use 
	 * @param applicationDomain  the application domain 
	 * @param sslOn              true, is SSL should be activated
	 * @param sslContext         the ssl context to use
	 * @throws UnknownHostException if the locale host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(int port, IHandler handler, String applicationDomain, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this.localAddress = InetAddress.getLocalHost();
		
		
		try {
			setWorkerPoolSize(Runtime.getRuntime().availableProcessors() * 10);
			acceptor = new Acceptor(port, workerPool, sslContext, sslOn, Integer.toString(port));			
		} catch (BindException be) {	
			throw be;
		}

		this.port = acceptor.getLocalePort();
		this.hostname = localAddress.getCanonicalHostName() + "." + this.port;
		this.appDomain =  applicationDomain + "." + this.port;

		acceptor.setAppDomain(this.appDomain);

		
		setDispatcherPoolSize(Runtime.getRuntime().availableProcessors() + 1);		
		setConnectionTimeoutSec(DEFAULT_CONNECTION_TIMEOUT_SEC);
		setIdleTimeoutSec(DEFAULT_IDLE_TIMEOUT_SEC);

		
		
		if (handler != null) {
			setHandler(handler);
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
	        workerPool.stopPooling();
			
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

		LOG.info("server " + appDomain + " listening on port " + port + " (" + getVersionInfo() + ")");
		LOG.fine("dispatcherPoolSize=" + getDispatcherPoolSize() + " workerPoolsize=" + getWorkerPoolSize());
		LOG.fine("preallocationSize=" + getReceiveBufferPreallocationSize());
		LOG.fine("connectionTimeout=" + DataConverter.toFormatedDuration(acceptor.getConnectionTimeout()) 
				  + "; idleTimeout=" + DataConverter.toFormatedDuration(acceptor.getIdleTimeout()));
		
		acceptor.run();
	}


	
	/**
	 * {@inheritDoc}
	 */
	public final void setDispatcherPoolSize(int size) {
		acceptor.setDispatcherPoolSize(size);
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
		isConnectHandler = (appHandler instanceof IConnectHandler);
		isDisconnectHandler = (appHandler instanceof IDisconnectHandler);
		isDataHandler = (appHandler instanceof IDataHandler);
		isTimeoutHandler = (appHandler instanceof ITimeoutHandler);
		isConnectionScoped = (appHandler instanceof IConnectionScoped); 
		isLifeCycleHandler = (appHandler instanceof ILifeCycle);			

		initCurrentHandler();
	}


	private void initCurrentHandler() {
		injectContext(this.appHandler);
		
		acceptor.setHandler(appHandler, isConnectionScoped, isConnectHandler, isDisconnectHandler, isDataHandler, isTimeoutHandler);

		if (isLifeCycleHandler) {
			((ILifeCycle) appHandler).onInit();
		}
	}

	

	private void injectContext(IHandler hdl) {
		IHandlerServerContext ctx = null;
		Field[] fields = hdl.getClass().getDeclaredFields();
		for (Field field : fields) {
			if ((field.getType() == IHandlerServerContext.class) && (field.getAnnotation(Resource.class) != null)) {
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
			if (isLifeCycleHandler) {
				((ILifeCycle) this.appHandler).onDestroy();
			}	
			appHandler = null;
		}
	}

	

	
	/**
	 * {@inheritDoc}
	 */
	public final int getLocalPort() {
		return port;
	}
	

	/**
	 * {@inheritDoc}
	 */
	public final InetAddress getLocalAddress() {
		return localAddress;
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
		return acceptor.getReceiveBufferPreallocationSize();
	}

	

	/**
	 * {@inheritDoc}
	 */
	public void setReceiveBufferPreallocationSize(int size) {
		acceptor.setReceiveBufferPreallocationSize(size);
	}

	
	/**
	 * {@inheritDoc}
	 */
	public void setConnectionTimeoutSec(int timeoutSec) {
		acceptor.setConnectionTimeout(((long) timeoutSec) *  1000);
	}

	
	
	/**
	 * {@inheritDoc}
	 */
	public void setIdleTimeoutSec(int timeoutInSec) {
		acceptor.setIdleTimeout(((long) timeoutInSec) * 1000);
	}

	


	/**
	 * {@inheritDoc}
	 */
	public final int getDispatcherPoolSize() {
		return acceptor.getDispatcherPoolSize();
	}


	/**
	 * {@inheritDoc}
	 */
	public final int getConnectionTimeoutSec() {
		return (int) (getConnectionTimeout() / 1000);
	}


	private long getConnectionTimeout() {
		return acceptor.getConnectionTimeout();
	}


	/**
	 * {@inheritDoc}
	 */
	public final int getIdleTimeoutSec() {
		return (int) (getIdleTimeout() / 1000);
	}


	private long getIdleTimeout() {
		return acceptor.getIdleTimeout();
	}



	/**
	 * {@inheritDoc}
	 */
	public int getNumberOfConnectionTimeout() {
		return acceptor.getNumberOfConnectionTimeout();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int getNumberOfIdleTimeout() {
		return acceptor.getNumberOfIdleTimeout();
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final List<String> getOpenConnections() {
		return acceptor.getOpenConnections();
	}


	/**
	 * {@inheritDoc}
	 */
	public int getNumberOfOpenConnections() {
		return acceptor.getNumberOfOpenConnections();
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final long getNumberOfHandledConnections() {
		return acceptor.getNumberOfHandledConnections();
	}

	
	
	private String getVersionInfo() {
		Package p = Package.getPackage("org.xsocket");
		if (p != null) {
			return p.getSpecificationTitle() + " " + p.getImplementationVersion();
		} else {
			return "";
		}
	}



	private final class HandlerContext implements IHandlerServerContext {
		
		/**
		 * {@inheritDoc}
		 */
		public int getLocalePort() {
			return MultithreadedServer.this.getLocalPort();
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
		public String getApplicationDomain() {
			return MultithreadedServer.this.appDomain;
		}
	}
}
