// $Id: MultithreadedServer.java 1047 2007-03-20 19:45:31Z grro $
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
import java.lang.reflect.Field;
import java.net.BindException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;


import org.xsocket.DynamicWorkerPool;
import org.xsocket.IDispatcher;
import org.xsocket.IWorkerPool;
import org.xsocket.Resource;


/**
 * Implementation of a multithreaded server. For more information see
 * {@link IMultithreadedServer}
 *  
 * @author grro@xsocket.org
 */
public final class MultithreadedServer implements IMultithreadedServer {

	private static final Logger LOG = Logger.getLogger(MultithreadedServer.class.getName());
	
	
	// is open flag
	private boolean isOpen = true;


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
	
	
		
	// worker pool
	private IWorkerPool workerPool = new DynamicWorkerPool(3, 250);
	private boolean isSelfCreatedWorkerPool = true;

	// dispatcher pool
	private final IoSocketDispatcherPool dispatcherPool = new IoSocketDispatcherPool(65536, workerPool);

	// acceptor
	private Acceptor acceptor = null;

	
	// listeners
	private final List<IMutlithreadedServerListener> listeners = new ArrayList<IMutlithreadedServerListener>();

	
	
	/**
	 * constructor <br>
	 * 
	 * The idle- and connection time out will be set with the default values 
	 *
	 * @param handler the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener) 
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
	 * @param port        the local port
	 * @param handler     the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
	 * @throws UnknownHostException if the locale host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(int port, IHandler handler) throws UnknownHostException, IOException {
		this(InetAddress.getLocalHost(), port, handler, false, null);
	}

	/**
	 * constructor <br>
	 * 
	 * The idle- and connection time out will be set with the default values
	 *  
	 * @param address     the local address
	 * @param port        the local port
	 * @param handler     the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
	 * @throws UnknownHostException if the locale host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(InetAddress address, int port, IHandler handler) throws UnknownHostException, IOException {
		this(address, port, handler, false, null);
	}
	
	/**
	 * constructor <br>
	 * 
	 * The idle- and connection time out will be set with the default values
	 *  
	 * @param ipAddress   the local ip address
	 * @param port        the local port
	 * @param handler     the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
	 * @throws UnknownHostException if the locale host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(String ipAddress, int port, IHandler handler) throws UnknownHostException, IOException {
		this(InetAddress.getByName(ipAddress), port, handler, false, null);
	}
	
	
	/**
	 * constructor
	 * 
	 * @param port               local port
	 * @param handler            the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
	 * @param sslOn              true, is SSL should be activated
	 * @param sslContext         the ssl context to use
	 * @throws UnknownHostException if the locale host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(int port, IHandler handler, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this(InetAddress.getLocalHost(), port, handler, sslOn ,sslContext);
	}
	
	
	/**
	 * constructor
	 * 
	 * @param ipAddress          local ip address 
	 * @param port               local port
	 * @param handler            the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IConnectionScoped, IHandlerLifeCycleListener)
	 * @param sslOn              true, is SSL should be activated
	 * @param sslContext         the ssl context to use
	 * @throws UnknownHostException if the locale host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(String ipAddress, int port, IHandler handler, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this(InetAddress.getByName(ipAddress), port, handler, sslOn, sslContext);
	}
	
	
	/**
	 * constructor
	 * 
	 * @param address            local address 
	 * @param port               local port
	 * @param handler            the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IConnectionScoped, IHandlerLifeCycleListener)
	 * @param sslOn              true, is SSL should be activated
	 * @param sslContext         the ssl context to use
	 * @throws UnknownHostException if the locale host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(InetAddress address, int port, IHandler handler, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this.localAddress = address;
		
		
		try {
			acceptor = new Acceptor(localAddress, port, sslContext, sslOn, Integer.toString(port), dispatcherPool);			
		} catch (BindException be) {	
			throw be;
		}

		this.port = acceptor.getLocalePort();
		
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
	public final void close() {
		if (isOpen) {
			isOpen = false;


	        if (LOG.isLoggable(Level.FINER)) {
				LOG.fine("close acceptor");
			}

	        acceptor.shutdown();
	        
	        if (isSelfCreatedWorkerPool) {
	    		if(workerPool instanceof DynamicWorkerPool) {
	    			((DynamicWorkerPool) workerPool).close();
	    		}	
			}
			
			destroyCurrentHandler();
			
			
			for (IMutlithreadedServerListener listener : listeners) {
				listener.onDestroy();
			}
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
				close();
			}
		});

		LOG.info("server listening on " + localAddress + ":" + port + " (" + getVersionInfo() + ")");
		LOG.fine("dispatcherPoolSize=" + getDispatcher().size());
		LOG.fine("preallocationSize=" + getReceiveBufferPreallocationSize());
		
		
		for (IMutlithreadedServerListener listener : listeners) {
			listener.onInit();
		}

		acceptor.run();
	}

	
	public void addListener(IMutlithreadedServerListener listener) {
		listeners.add(listener);
		dispatcherPool.addListener(listener);
	}
	
	public boolean removeListener(IMutlithreadedServerListener listener) {
		boolean result = listeners.remove(listener);
		dispatcherPool.removeListener(listener);
		
		return result;
	}
	
	
	public IWorkerPool getWorkerPool() {
		return workerPool;
	}
	
	public void setWorkerPool(IWorkerPool workerPool) {
		IWorkerPool oldWorkerPool = workerPool;
		this.workerPool = workerPool;

		if (isSelfCreatedWorkerPool) {
			if(workerPool instanceof DynamicWorkerPool) {
    			((DynamicWorkerPool) oldWorkerPool).close();
    		}			
		}
		
		isSelfCreatedWorkerPool = false;
		
		dispatcherPool.setWorkerPool(workerPool);
		
		for (IMutlithreadedServerListener listener : listeners) {
			listener.onWorkerPoolUpdated(oldWorkerPool, workerPool);
		}
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
	public int getDispatcherPoolSize() {
		return dispatcherPool.getDispatchers().size();
	}
		
	
	int getMultiMemoryManagerFreeBufferSize() {
		return dispatcherPool.getMultithreadedMemoryManager().getFreeBufferSize();
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isOpen() {
		return isOpen;
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
		IServerContext ctx = null;
		Field[] fields = hdl.getClass().getDeclaredFields();
		for (Field field : fields) {
			if ((field.getType() == IServerContext.class) && (field.getAnnotation(Resource.class) != null)) {
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
		acceptor.setConnectionTimeoutSec(timeoutSec);
	}

	
	
	/**
	 * {@inheritDoc}
	 */
	public void setIdleTimeoutSec(int timeoutInSec) {
		acceptor.setIdleTimeoutSec(timeoutInSec);
	}

	


	/**
	 * {@inheritDoc}
	 */
	public final List<IDispatcher> getDispatcher() {
		List<IDispatcher> result = new ArrayList<IDispatcher>();
		for (IDispatcher<IoSocketHandler> dispatcher : dispatcherPool.getDispatchers()) {
			result.add(dispatcher);
		}
		
		return result;
	}


	/**
	 * {@inheritDoc}
	 */
	public final int getConnectionTimeoutSec() {
		return acceptor.getConnectionTimeoutSec();
	}



	/**
	 * {@inheritDoc}
	 */
	public final int getIdleTimeoutSec() {
		return acceptor.getIdleTimeoutSec();
	}



	int getNumberOfConnectionTimeout() {
		return acceptor.getNumberOfConnectionTimeout();
	}
	
	
	int getNumberOfIdleTimeout() {
		return acceptor.getNumberOfIdleTimeout();
	}

	
	final List<String> getOpenConnections() {
		List<String> result = new ArrayList<String>();
		for (IDispatcher<IoSocketHandler> dispatcher : dispatcherPool.getDispatchers()) {
			for (IoSocketHandler handler : dispatcher.getRegistered()) {
				result.add(handler.toString());
			}
		}
		
		return result;
	}


	int getNumberOfOpenConnections() {
		return getOpenConnections().size();
	}

	
	long getNumberOfHandledConnections() {
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



	private final class HandlerContext implements IServerContext {
		
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
	}
}
