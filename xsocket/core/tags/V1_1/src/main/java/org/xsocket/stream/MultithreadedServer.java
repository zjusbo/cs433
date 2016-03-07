// $Id: MultithreadedServer.java 1281 2007-05-29 19:48:07Z grro $
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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
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
	private boolean isOpen = false;


	// socket
	private int port = 0;
	private InetAddress localAddress = null;
	private boolean sslOn = false;
	private SSLContext sslContext = null;


	// handler
	private IHandler appHandler = null;
    private boolean isLifeCycleHandler = false;
	private boolean isConnectionScoped = false;
	private boolean isConnectHandler = false;
    private boolean isDisconnectHandler = false;
	private boolean isDataHandler = false;
	private boolean isTimeoutHandler = false;
	private boolean isSynchronnizedHandler = false;
	
	
		
	// worker pool
	private IWorkerPool workerPool = new DynamicWorkerPool(3, 250);
	private boolean isSelfCreatedWorkerPool = true;

	// dispatcher pool
	private final IoSocketDispatcherPool dispatcherPool = new IoSocketDispatcherPool(65536);

	// acceptor
	private Acceptor acceptor = null;

	
	// listeners
	private final ArrayList<IMutlithreadedServerListener> listeners = new ArrayList<IMutlithreadedServerListener>();

	
	
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
		this(InetAddress.getLocalHost(), 0, null, handler, false, null);
	}

	
	
	/**
	 * constructor <br>
	 * 
	 * The idle- and connection time out will be set with the default values 
	 *
	 * @param socketConfiguration  the socket configuration	   
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener) 
	 * @throws IOException If some other I/O error occurs
	 * @throws UnknownHostException if the locale host cannot determined
	 */
	public MultithreadedServer(StreamSocketConfiguration socketConfiguration, IHandler handler) throws UnknownHostException, IOException {
		this(InetAddress.getLocalHost(), 0, socketConfiguration, handler, false, null);
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
		this(InetAddress.getLocalHost(), port, null, handler, false, null);
	}

	
	/**
	 * constructor <br>
	 * 
	 * The idle- and connection time out will be set with the default values 
	 *
	 * @param port                 the local port
	 * @param socketConfiguration  the socket configuration	   
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
	 * @throws UnknownHostException if the locale host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(int port, StreamSocketConfiguration socketConfiguration, IHandler handler) throws UnknownHostException, IOException {
		this(InetAddress.getLocalHost(), port, socketConfiguration, handler, false, null);
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
		this(address, port, null, handler, false, null);
	}


	/**
	 * constructor <br>
	 * 
	 * The idle- and connection time out will be set with the default values
	 *  
	 * @param address              the local address
	 * @param port                 the local port
	 * @param socketConfiguration  the socket configuration	  
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
	 * @throws UnknownHostException if the locale host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(InetAddress address, int port, StreamSocketConfiguration socketConfiguration, IHandler handler) throws UnknownHostException, IOException {
		this(address, port, socketConfiguration, handler, false, null);
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
		this(InetAddress.getByName(ipAddress), port, null, handler, false, null);
	}
	
	
	/**
	 * constructor <br>
	 * 
	 * The idle- and connection time out will be set with the default values
	 *  
	 * @param ipAddress            the local ip address
	 * @param port                 the local port
	 * @param socketConfiguration  the socket configuration	    
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
	 * @throws UnknownHostException if the locale host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(String ipAddress, int port, StreamSocketConfiguration socketConfiguration, IHandler handler) throws UnknownHostException, IOException {
		this(InetAddress.getByName(ipAddress), port, socketConfiguration, handler, false, null);
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
		this(InetAddress.getLocalHost(), port, null, handler, sslOn ,sslContext);
	}
	
	
	/**
	 * constructor
	 * 
	 * @param port                 local port
	 * @param socketConfiguration  the socket configuration	    
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
	 * @param sslOn                true, is SSL should be activated
	 * @param sslContext           the ssl context to use
	 * @throws UnknownHostException if the locale host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(int port, StreamSocketConfiguration socketConfiguration, IHandler handler, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this(InetAddress.getLocalHost(), port, socketConfiguration, handler, sslOn ,sslContext);
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
		this(InetAddress.getByName(ipAddress), port, null, handler, sslOn, sslContext);
	}

	
	/**
	 * constructor
	 * 
	 * @param ipAddress            local ip address 
	 * @param port                 local port
	 * @param socketConfiguration  the socket configuration	   
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IConnectionScoped, IHandlerLifeCycleListener)
	 * @param sslOn                true, is SSL should be activated
	 * @param sslContext           the ssl context to use
	 * @throws UnknownHostException if the locale host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(String ipAddress, int port, StreamSocketConfiguration socketConfiguration, IHandler handler, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this(InetAddress.getByName(ipAddress), port, socketConfiguration, handler, sslOn, sslContext);
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
		this(address, port, null, handler, sslOn, sslContext);
	}
	
	
	/**
	 * constructor
	 * 
	 * @param address              local address 
	 * @param port                 local port
	 * @param socketConfiguration  the socket configuration	  
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IConnectionScoped, IHandlerLifeCycleListener)
	 * @param sslOn                true, is SSL should be activated
	 * @param sslContext           the ssl context to use
	 * @throws UnknownHostException if the locale host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(InetAddress address, int port, StreamSocketConfiguration socketConfiguration, IHandler handler, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this.localAddress = address;
		this.sslOn = sslOn;
		this.sslContext = sslContext;
		
		
		try {
			acceptor = new Acceptor(localAddress, port, socketConfiguration, sslContext, sslOn, Integer.toString(port), dispatcherPool, workerPool);			
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
	@SuppressWarnings("unchecked")
	public void close() {
		if (isOpen) {
			isOpen = false;

	        acceptor.shutdown();
	        
	        if (isSelfCreatedWorkerPool) {
	    		if(workerPool instanceof DynamicWorkerPool) {
	    			((DynamicWorkerPool) workerPool).close();
	    		}	
			}
			
			destroyCurrentHandler();
			
			
			for (IMutlithreadedServerListener listener : (ArrayList<IMutlithreadedServerListener>)listeners.clone()) {
				listener.onDestroy();
			}
			
			LOG.info("server " + localAddress + ":" + port + " has been shut down");
		}
	}




	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
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

		if (sslContext != null) {
			if (sslOn) {
				LOG.info("server listening on " + localAddress + ":" + port + "  - ssl mode (" + getVersionInfo() + ")");
			} else {
				LOG.info("server listening on " + localAddress + ":" + port + " - activateable ssl mode (" + getVersionInfo() + ")");
			}
		} else {
			LOG.info("server listening on " + localAddress + ":" + port + " (" + getVersionInfo() + ")");
		}
		
		LOG.fine("dispatcherPoolSize=" + getDispatcher().size());
		LOG.fine("preallocationSize=" + getReceiveBufferPreallocationSize());
		
		
		isOpen = true;
		
		for (IMutlithreadedServerListener listener : (ArrayList<IMutlithreadedServerListener>)listeners.clone()) {
			listener.onInit();
		}

		acceptor.run();
	}


	/**
	 * {@inheritDoc}
	 */
	public void addListener(IMutlithreadedServerListener listener) {
		listeners.add(listener);
		dispatcherPool.addListener(listener);
	}

	
	/**
	 * {@inheritDoc}
	 */
	public boolean removeListener(IMutlithreadedServerListener listener) {
		boolean result = listeners.remove(listener);
		dispatcherPool.removeListener(listener);
		
		return result;
	}

	
	/**
	 * {@inheritDoc}
	 */
	public IWorkerPool getWorkerPool() {
		return workerPool;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public void setWorkerPool(IWorkerPool newWorkerPool) {
		IWorkerPool oldWorkerPool = workerPool;

		if (newWorkerPool == null) {
			newWorkerPool = new NullWorkerPool();
		}
		
		this.workerPool = newWorkerPool;
 
		if (isSelfCreatedWorkerPool) {
			if(oldWorkerPool instanceof DynamicWorkerPool) {
    			((DynamicWorkerPool) oldWorkerPool).close();
    		}			
		}
		isSelfCreatedWorkerPool = false;
		
		for (IMutlithreadedServerListener listener : (ArrayList<IMutlithreadedServerListener>)listeners.clone()) {
			IWorkerPool wo = oldWorkerPool;
			if (wo instanceof NullWorkerPool) {
				wo = null;
			}
			
			IWorkerPool wn = newWorkerPool;
			if (wn instanceof NullWorkerPool) {
				wn = null;
			}
			
			listener.onWorkerPoolUpdated(null, newWorkerPool);
		}
		
		acceptor.setWorkerPool(this.workerPool);
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
		isLifeCycleHandler = (appHandler instanceof org.xsocket.ILifeCycle);			

		isSynchronnizedHandler = NonBlockingConnection.isSynchronized(appHandler);
		 
		initCurrentHandler();
	}


	private void initCurrentHandler() {
		injectContext(this.appHandler);
		
		acceptor.setHandler(appHandler, isConnectionScoped, isConnectHandler, isDisconnectHandler, isDataHandler, isTimeoutHandler, isSynchronnizedHandler);

		if (isLifeCycleHandler) {
			((org.xsocket.ILifeCycle) appHandler).onInit();
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
				((org.xsocket.ILifeCycle) this.appHandler).onDestroy();
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
		
		/**
		 * {@inheritDoc}
		 */
		public int getNumberOfOpenConnections() {
			return MultithreadedServer.this.getNumberOfOpenConnections();
		}
	}
	
	
	private static final class NullWorkerPool implements IWorkerPool {
		
		public void execute(Runnable command) {
			command.run();
		}
		
		public int getActiveCount() {
			return 0;
		}
		
		public int getPoolSize() {
			return 0;
		}
		
		public <T> List<Future<T>> invokeAll(Collection<Callable<T>> tasks) throws InterruptedException {
			throw new UnsupportedOperationException("invokeAll is not supported");
		}
	}
}
