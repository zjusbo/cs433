// $Id: MultithreadedServer.java 1781 2007-09-28 06:12:20Z grro $
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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;


import org.xsocket.Resource;
import org.xsocket.stream.io.impl.IoProvider;
import org.xsocket.stream.io.spi.IAcceptor;
import org.xsocket.stream.io.spi.IServerIoProvider;
import org.xsocket.stream.io.spi.IAcceptorCallback;
import org.xsocket.stream.io.spi.IIoHandler;




/**
 * Implementation of a multithreaded server. For more information see
 * {@link IMultithreadedServer}
 *  
 * @author grro@xsocket.org
 */
@SuppressWarnings("unchecked")
public final class MultithreadedServer implements IMultithreadedServer {

	/*
	 * known tcp issues
	 * to many max files ->  http://support.bea.com/application_content/product_portlets/support_patterns/wls/TooManyOpenFilesPattern.html
	 * windows tcp -> http://publib.boulder.ibm.com/infocenter/wasinfo/v4r0/index.jsp?topic=/com.ibm.websphere.v4.doc/wasa_content/0901.html 
	 */
	
	private static final Logger LOG = Logger.getLogger(MultithreadedServer.class.getName());
	
	
	private static final String DEFAULT_HOST_ADDRESS = "0.0.0.0"; 
	
	
	

	// is open flag
	private boolean isOpen= false;

	
	// acceptor 
	private static IServerIoProvider serverIoProvider = null;
	private IAcceptor acceptor = null;
	private InetSocketAddress address = null;

	
	// config
	private Map<String, Object> options = null;
	private boolean sslOn = false;
	private SSLContext sslContext = null;
	
	
	// worker pool
	private Executor workerpool = null;
	private boolean isSelfCreatedWorkerPool = false;

	
	
	// app handler
	private IHandler appHandlerPrototype = null;
	
	// io handler
	private final IoHandlerContext ioHandlerContext = new IoHandlerContext(null, null);

	
	// server listeners
	private final ArrayList<IMutlithreadedServerListener> listeners = new ArrayList<IMutlithreadedServerListener>();

	
	//timeouts
	private int idleTimeoutSec = Integer.MAX_VALUE;
	private int connectionTimeoutSec = Integer.MAX_VALUE;
	
	
	
	static {
		// ServerIoProvider
		String serverIoProviderClassname = System.getProperty(IServerIoProvider.PROVIDER_CLASSNAME_KEY);
		if (serverIoProviderClassname != null) {
			try {
				Class serverIoProviderClass = Class.forName(serverIoProviderClassname);
				serverIoProvider = (IServerIoProvider) serverIoProviderClass.newInstance();
			} catch (Exception e) {
				LOG.warning("error occured by creating ServerIoProivder " + serverIoProviderClassname + ": " + e.toString());
				LOG.info("using default ServerIoProvider");
				
			}
		}
		 
		if (serverIoProvider == null) {
			serverIoProvider = new IoProvider();
		}
	}
	

	

	/**
	 * constructor <br><br>
	 * 
	 * The idle- and connection time out will be set with the default values. To perform the worker threads a 
	 * FixedThreadPool {@link Executors#newFixedThreadPool(int)} with 250 max threads will be created.   
	 *
	 * @param handler the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener) 
	 * @throws IOException If some other I/O error occurs
	 * @throws UnknownHostException if the locale host cannot determined
	 */
	public MultithreadedServer(IHandler handler) throws UnknownHostException, IOException {
		this(InetAddress.getByName(DEFAULT_HOST_ADDRESS), 0, new HashMap<String, Object>(), handler, false, null);
	}

	
	/**
	 * constructor <br><br>
	 * 
	 * For idle-, connection time out and the used workerpool see {@link MultithreadedServer#MultithreadedServer(IHandler)} 
	 *
	 * @param handler      the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
  	 * @param workerpool   the workerpool to use. By setting the WorkerPool with null the worker pool will be deactivated. The callback handler will be
  	 *                     executed by the main dispatching thread. If the handler performs blocking operations, the disptaching will also be blocked!					    
	 * @throws IOException If some other I/O error occurs
	 * @throws UnknownHostException if the locale host cannot determined
	 */
	public MultithreadedServer(IHandler handler, Executor workerpool) throws UnknownHostException, IOException {
		this(InetAddress.getByName(DEFAULT_HOST_ADDRESS), 0, new HashMap<String, Object>(), handler, workerpool, false, null);
	}


	
	/**
	 * @deprecated
	 */
	public MultithreadedServer(StreamSocketConfiguration socketConfiguration, IHandler handler) throws UnknownHostException, IOException {
		this(InetAddress.getByName(DEFAULT_HOST_ADDRESS), 0, socketConfiguration, handler, false, null);
	}
	
	
	
	/**
	 * constructor <br><br>
	 * 
	 * For idle-, connection time out and the used workerpool see {@link MultithreadedServer#MultithreadedServer(IHandler)} 
	 *
	 * @param options              the socket options	   
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener) 
	 * @throws IOException If some other I/O error occurs
	 * @throws UnknownHostException if the locale host cannot determined
	 */
	public MultithreadedServer(Map<String, Object> options, IHandler handler) throws UnknownHostException, IOException {
		this(InetAddress.getByName(DEFAULT_HOST_ADDRESS), 0, options, handler, false, null);
	}
	
	
	/**
	 * constructor  <br><br>
	 * 
	 * For idle-, connection time out and the used workerpool see {@link MultithreadedServer#MultithreadedServer(IHandler)} 
	 *
	 * @param port        the local port
	 * @param handler     the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
	 * @throws UnknownHostException if the locale host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(int port, IHandler handler) throws UnknownHostException, IOException {
		this(InetAddress.getByName(DEFAULT_HOST_ADDRESS), port, new HashMap<String, Object>(), handler, false, null);
	}

	
	/**
	 * constructor  <br><br>
	 * 
	 * For idle-, connection time out see {@link MultithreadedServer#MultithreadedServer(IHandler)} 
	 *
	 * @param port        the local port
	 * @param handler     the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
  	 * @param workerpool   the workerpool to use. By setting the WorkerPool with null the worker pool will be deactivated. The callback handler will be
  	 *                     executed by the main dispatching thread. If the handler performs blocking operations, the disptaching will also be blocked!					    
	 * @throws UnknownHostException if the locale host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(int port, IHandler handler, Executor workerpool) throws UnknownHostException, IOException {
		this(InetAddress.getByName(DEFAULT_HOST_ADDRESS), port, new HashMap<String, Object>(), handler, workerpool, false, null);
	}
	
	
	/**
	 * @deprecated 
	 */
	public MultithreadedServer(int port, StreamSocketConfiguration socketConfiguration, IHandler handler) throws UnknownHostException, IOException {
		this(InetAddress.getByName(DEFAULT_HOST_ADDRESS), port, socketConfiguration, handler, false, null);
	}
	
	/**
	 * constructor <br><br>
	 * 
	 * For idle-, connection time out and the used workerpool see {@link MultithreadedServer#MultithreadedServer(IHandler)} 
	 *
	 * @param port                 the local port
	 * @param options              the acceptor socket options	   
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
	 * @throws UnknownHostException if the locale host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(int port, Map<String , Object> options, IHandler handler) throws UnknownHostException, IOException {
		this(InetAddress.getByName(DEFAULT_HOST_ADDRESS), port, options, handler, false, null);
	}

	
	
	/**
	 * constructor <br><br>
	 * 
	 * For idle-, connection time out and the used workerpool see {@link MultithreadedServer#MultithreadedServer(IHandler)}
	 *  
	 * @param address     the local address
	 * @param port        the local port
	 * @param handler     the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
	 * @throws UnknownHostException if the locale host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(InetAddress address, int port, IHandler handler) throws UnknownHostException, IOException {
		this(address, port, new HashMap<String, Object>(), handler, false, null);
	}

	
	
	
	/**
	 * constructor <br><br>
	 * 
	 * For idle-, connection time out and the used workerpool see {@link MultithreadedServer#MultithreadedServer(IHandler)}
	 *  
	 * @param address     the local address
	 * @param port        the local port
	 * @param handler     the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
	 * @param workerPool  the workerpool
	 * @throws UnknownHostException if the locale host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(InetAddress address, int port, IHandler handler, Executor workerPool) throws UnknownHostException, IOException {
		this(address, port, new HashMap<String, Object>(), handler, workerPool, false, null);
	}

	

	/**
	 * @deprecated
	 */
	public MultithreadedServer(InetAddress address, int port, StreamSocketConfiguration socketConfiguration, IHandler handler) throws UnknownHostException, IOException {
		this(address, port, socketConfiguration, handler, false, null);
	}
	
	
	
	/**
	 * constructor  <br><br>
	 * 
	 * For idle-, connection time out and the used workerpool see {@link MultithreadedServer#MultithreadedServer(IHandler)}
	 *  
	 * @param ipAddress   the local ip address
	 * @param port        the local port
	 * @param handler     the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
	 * @throws UnknownHostException if the locale host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(String ipAddress, int port, IHandler handler) throws UnknownHostException, IOException {
		this(InetAddress.getByName(ipAddress), port, new HashMap<String, Object>(), handler, false, null);
	}
	
	
	/**
	 * @deprecated
	 */
	public MultithreadedServer(String ipAddress, int port, StreamSocketConfiguration socketConfiguration, IHandler handler) throws UnknownHostException, IOException {
		this(InetAddress.getByName(ipAddress), port, socketConfiguration, handler, false, null);
	}
	
	
	/**
	 * constructor <br><br>
	 * 
	 * For idle-, connection time out and the used workerpool see {@link MultithreadedServer#MultithreadedServer(IHandler)}
	 *  
	 * @param ipAddress            the local ip address
	 * @param port                 the local port
	 * @param options              the socket options	    
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
	 * @throws UnknownHostException if the locale host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(String ipAddress, int port, Map<String, Object> options, IHandler handler) throws UnknownHostException, IOException {
		this(InetAddress.getByName(ipAddress), port, options, handler, false, null);
	}
	
	
	
	
	/**
	 * constructor <br><br>
	 * 
	 * For idle-, connection time out and the used workerpool see {@link MultithreadedServer#MultithreadedServer(IHandler)}
	 * 
	 * @param port               local port
	 * @param handler            the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
	 * @param sslOn              true, is SSL should be activated
	 * @param sslContext         the ssl context to use
	 * @throws UnknownHostException if the locale host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(int port, IHandler handler, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this(InetAddress.getByName(DEFAULT_HOST_ADDRESS), port, new HashMap<String, Object>(), handler, sslOn ,sslContext);
	}
	
	
	/**
	 * @deprecated 
	 */
	public MultithreadedServer(int port, StreamSocketConfiguration socketConfiguration, IHandler handler, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this(InetAddress.getByName(DEFAULT_HOST_ADDRESS), port, socketConfiguration, handler, sslOn ,sslContext);
	}
	
	
	
	/**
	 * constructor <br><br>
	 * 
	 * For idle-, connection time out and the used workerpool see {@link MultithreadedServer#MultithreadedServer(IHandler)}
	 * 
	 * @param port                 local port
	 * @param options              the acceptor socket options	    
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
	 * @param sslOn                true, is SSL should be activated
	 * @param sslContext           the ssl context to use
	 * @throws UnknownHostException if the locale host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(int port,Map<String, Object> options, IHandler handler, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this(InetAddress.getByName(DEFAULT_HOST_ADDRESS), port, options, handler, sslOn ,sslContext);
	}
	
	
	/**
	 * constructor <br><br>
	 * 
	 * For idle-, connection time out and the used workerpool see {@link MultithreadedServer#MultithreadedServer(IHandler)}
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
		this(InetAddress.getByName(ipAddress), port, new HashMap<String, Object>(), handler, sslOn, sslContext);
	}

	
	/**
	 * @deprecated
	 */
	public MultithreadedServer(String ipAddress, int port, StreamSocketConfiguration socketConfiguration, IHandler handler, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this(InetAddress.getByName(ipAddress), port, socketConfiguration, handler, sslOn, sslContext);
	}

	/**
	 * constructor <br><br>
	 * 
	 * For idle-, connection time out and the used workerpool see {@link MultithreadedServer#MultithreadedServer(IHandler)}
	 * 
	 * @param ipAddress            local ip address 
	 * @param port                 local port
	 * @param options              the acceptor socket options	   
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IConnectionScoped, IHandlerLifeCycleListener)
	 * @param sslOn                true, is SSL should be activated
	 * @param sslContext           the ssl context to use
	 * @throws UnknownHostException if the locale host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(String ipAddress, int port, Map<String, Object> options, IHandler handler, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this(InetAddress.getByName(ipAddress), port, options, handler, sslOn, sslContext);
	}
	
	
	/**
	 * constructor <br><br>
	 * 
	 * For idle-, connection time out and the used workerpool see {@link MultithreadedServer#MultithreadedServer(IHandler)}
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
		this(address, port, new HashMap<String, Object>(), handler, sslOn, sslContext);
	}
	
	
	/**
	 * @deprecated
	 */
	public MultithreadedServer(InetAddress address, int port, StreamSocketConfiguration socketConfiguration, IHandler handler, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this(address, port, socketConfiguration, handler, newServerWorkerPool(), sslOn, sslContext);
		isSelfCreatedWorkerPool = true;
	}
	

	/**
	 * constructor <br><br>
	 * 
	 * For idle-, connection time out and the used workerpool see {@link MultithreadedServer#MultithreadedServer(IHandler)}
	 * 
	 * @param address              local address 
	 * @param port                 local port
	 * @param options              the socket options	  
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IConnectionScoped, IHandlerLifeCycleListener)
	 * @param sslOn                true, is SSL should be activated
	 * @param sslContext           the ssl context to use
	 * @throws UnknownHostException if the locale host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(InetAddress address, int port, Map<String, Object> options, IHandler handler, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this(address, port, options, handler, newServerWorkerPool(), sslOn, sslContext);
		isSelfCreatedWorkerPool = true;
	}
		
	/**
	 * @deprecated
	 */
	public MultithreadedServer(InetAddress address, int port, StreamSocketConfiguration socketConfiguration, IHandler appHandler, Executor workerpool, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this(new InetSocketAddress(address, port), socketConfiguration.toOptions(), appHandler, workerpool, sslOn, sslContext);
	}


	/**
	 * constructor <br><br>
	 * 
	 * For idle-, connection time out see {@link MultithreadedServer#MultithreadedServer(IHandler)}
	 * 
	 * @param address              local address 
	 * @param port                 local port
	 * @param options              the acceptor socket options	  
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IConnectionScoped, IHandlerLifeCycleListener)
	 * @param workerpool		   the workerpool to use					    
	 * @param sslOn                true, is SSL should be activated
	 * @param sslContext           the ssl context to use
	 * @throws UnknownHostException if the locale host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public MultithreadedServer(InetAddress address, int port, Map<String, Object> options, IHandler appHandler, Executor workerpool, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this(new InetSocketAddress(address, port), options, appHandler, workerpool, sslOn, sslContext);
	}
	
	
	private MultithreadedServer(InetSocketAddress address, Map<String, Object> options, IHandler appHandler, Executor workerpool, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this.address = address;
		this.options = options;
		this.appHandlerPrototype = appHandler;
		this.workerpool = workerpool;
		this.sslOn = sslOn;
		this.sslContext = sslContext;
		
		ioHandlerContext.updateAppHandler(appHandler);
		ioHandlerContext.updateWorkerpool(workerpool);
	}	
	
	

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public final void run() {
		
		try {
			if (appHandlerPrototype == null) {
				LOG.warning("no handler has been set. Call setHandler-method to set an assigned handler");
			}
			
			
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					close();
				}
			});
	

			
			// start listening
			if (sslContext != null) {
				acceptor = new IoProvider().create(new AcceptorCallback(), ioHandlerContext, address, 0, options, sslContext, sslOn);
			} else {
				acceptor = serverIoProvider.createAcceptor(new AcceptorCallback(), ioHandlerContext, address, 0, options);
			}
			acceptor.listen();

			
		} catch (Exception e) {
			throw new RuntimeException(e.toString());
		}
	}

	
	/**
	 * {@inheritDoc}
	 */
	public Object getOption(String name) throws IOException {
		return acceptor.getOption(name);
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Class> getOptions() {
		return acceptor.getOptions();
	}
	


	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public void close() {
		if (isOpen) {
			isOpen = false;

			try {
				acceptor.close();  // closing of dispatcher will be initiated by acceptor
			} catch (IOException ignore) { }
	        
	        if (isSelfCreatedWorkerPool) {
	    		if(workerpool instanceof ExecutorService) {
	    			((ExecutorService) workerpool).shutdown();
	    		}	
			}		
		}
	}


	
	private static final ExecutorService newServerWorkerPool() {
		return Executors.newFixedThreadPool(40);
	}



	IAcceptor getAcceptor() {
		return acceptor;
	}
	

	/**
	 * {@inheritDoc}
	 */
	public void addListener(IMutlithreadedServerListener listener) {
		listeners.add(listener);
//		dispatcherPool.addListener(listener);
	}

	
	/**
	 * {@inheritDoc}
	 */
	public boolean removeListener(IMutlithreadedServerListener listener) {
		boolean result = listeners.remove(listener);
//		dispatcherPool.removeListener(listener);
		
		return result;
	}

	
	/**
	 * {@inheritDoc}
	 * 
     * @deprecated use {@link IMultithreadedServer#getWorkerpool()} instead 
	 */
	public org.xsocket.IWorkerPool getWorkerPool() {
		return (org.xsocket.IWorkerPool) workerpool;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public Executor getWorkerpool() {
		return workerpool;
	}
	
	
	/**
	 * {@inheritDoc}
	 * @deprecated
	 */
	@SuppressWarnings("unchecked")
	public void setWorkerPool(org.xsocket.IWorkerPool newWorkerPool) {
		setWorkerPool((Executor) newWorkerPool);
	}
	
	
	@SuppressWarnings({ "unchecked", "deprecation" })
	private void setWorkerPool(Executor newWorkerPool) {
		Executor oldWorkerPool = workerpool;

		this.workerpool = newWorkerPool;
 
		if (isSelfCreatedWorkerPool) {
			if(oldWorkerPool instanceof ExecutorService) {
    			((ExecutorService) oldWorkerPool).shutdown();
    		}			
		}
		isSelfCreatedWorkerPool = false;
		
		ioHandlerContext.updateWorkerpool(workerpool);
		
		if (newWorkerPool instanceof org.xsocket.IWorkerPool)  {
			for (IMutlithreadedServerListener listener : (ArrayList<IMutlithreadedServerListener>)listeners.clone()) {
				listener.onWorkerPoolUpdated(null, (org.xsocket.IWorkerPool) newWorkerPool);
			}
		}
	}


	
	
	/**
	 * {@inheritDoc}
	 * 
	 * @deprecated
	 */
	public final void setDispatcherPoolSize(int size) {
		LOG.warning("not implemented. Method will be removed");
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @deprecated 
	 */
	public int getDispatcherPoolSize() {
		LOG.warning("not implemented. Method will be removed");
		return -1;
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
			throw new NullPointerException("handler is null");
		}

		if (isOpen) {	
			destroyCurrentHandler();
			this.appHandlerPrototype = appHandler;
			
			ioHandlerContext.updateAppHandler(appHandlerPrototype);
			initCurrentHandler();
			
		} else {
			this.appHandlerPrototype = appHandler;
			ioHandlerContext.updateAppHandler(appHandlerPrototype);
		}
	}

	
	
	private void destroyCurrentHandler() {
		if (appHandlerPrototype != null) {
			if (ioHandlerContext.isLifeCycleHandler()) {
				((org.xsocket.ILifeCycle) appHandlerPrototype).onDestroy();
			}	
			appHandlerPrototype = null;
		}
	}
	
	private void initCurrentHandler() {
		injectContext(appHandlerPrototype);
		
		if (ioHandlerContext.isLifeCycleHandler()) {
			((org.xsocket.ILifeCycle) appHandlerPrototype).onInit();
		}
	}
	


	
	/**
	 * {@inheritDoc}
	 */
	public final int getLocalPort() {
		return address.getPort();
	}
	

	/**
	 * {@inheritDoc}
	 */
	public final InetAddress getLocalAddress() {
		return address.getAddress();
	}




	/**
	 * {@inheritDoc}
	 * 
 	 * @deprecated no replacement
	 */
	public final int getReceiveBufferPreallocationSize() {
		LOG.warning("not implemented. Method will be removed");
		return -1;
	}

	

	/**
	 * {@inheritDoc}
	 * 
	 * @deprecated use System.property instead. see {@link org.xsocket.stream.io.impl.IoProvider}
	 */
	public void setReceiveBufferPreallocationSize(int size) {
		LOG.warning("not implemented. Method will be removed");
	}
	

	
	
	/**
	 * {@inheritDoc}
	 */
	public void setConnectionTimeoutSec(int timeoutSec) {
		this.connectionTimeoutSec = timeoutSec;
	}

	
	/**
	 * {@inheritDoc}
	 */
	public void setIdleTimeoutSec(int timeoutSec) {
		this.idleTimeoutSec = timeoutSec;
	}



	/**
	 * {@inheritDoc}
	 */
	public int getConnectionTimeoutSec() {
		return connectionTimeoutSec;
	}



	/**
	 * {@inheritDoc}
	 */
	public int getIdleTimeoutSec() {
		return idleTimeoutSec;
	}
	
	
	
	int getNumberOfOpenConnections() {
		return acceptor.getNumberOfOpenConnections();
	}

	/**
	 * @deprecated
	 */
	long getNumberOfHandledConnections() {
		return -1;
	}
	
	
	


	

	private String getVersionInfo(String packName) {
		Package p = Package.getPackage(packName);
		if (p != null) {
			String implVersion = p.getImplementationVersion();
			if (implVersion == null) {
				implVersion = "";
			}
			return implVersion;
		} else {
			return "";
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
	
	
	private final class AcceptorCallback implements IAcceptorCallback {
		
		@SuppressWarnings("unchecked")
		public void onConnected() {

			address = acceptor.getLocalAddress();
			isOpen = true;
			
			
			// init app handler
			initCurrentHandler();
			
			// notify listenes
			for (IMutlithreadedServerListener listener : (ArrayList<IMutlithreadedServerListener>)listeners.clone()) {
				listener.onInit();
			}

			String versionInfo = getVersionInfo("org.xsocket").trim();
			if (versionInfo.length() > 0) {
				versionInfo = "xSocket " + versionInfo;
			}

			
			if (!(serverIoProvider instanceof IoProvider)) {
				String verInfo = getVersionInfo(serverIoProvider.getClass().getPackage().getName());
				if (verInfo.length() > 0) {
					versionInfo = versionInfo + "; " + serverIoProvider.getClass().getName() + " " + verInfo;
				}
			}
			
			LOG.info("server listening on " + acceptor.getLocalAddress().toString() + " (" + versionInfo + ")");
		}
		
		
		@SuppressWarnings("unchecked")
		public void onDisconnected() {
			
	        destroyCurrentHandler();
			
			for (IMutlithreadedServerListener listener : (ArrayList<IMutlithreadedServerListener>)listeners.clone()) {
				listener.onDestroy();
			}
			
			LOG.info("server has been shutdown");
		}
		
		
		public void onConnectionAccepted(IIoHandler ioHandler) throws IOException {
			
			IHandler hdl = appHandlerPrototype;
			if (ioHandlerContext.isAppHandlerConnectionScoped()) {
				try {
					hdl = (IHandler) ((IConnectionScoped) appHandlerPrototype).clone();
				} catch (CloneNotSupportedException cne) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("error occured by cloning appHandler " + appHandlerPrototype.getClass().getName() + ". " + cne.toString());
					}
				}
			}
			
			
			// create a new connection based on the socketHandler and set timeouts
			INonBlockingConnection connection = new NonBlockingConnection(ioHandlerContext, ioHandler, hdl, serverIoProvider);
			connection.setIdleTimeoutSec(idleTimeoutSec);
			connection.setConnectionTimeoutSec(connectionTimeoutSec);				
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
			return MultithreadedServer.this.getLocalAddress();
		}
		
		/**
		 * {@inheritDoc}
		 */
		public int getNumberOfOpenConnections() {
			return MultithreadedServer.this.getNumberOfOpenConnections();
		}
		
		/**
		 * {@inheritDoc}
		 */
		@SuppressWarnings("deprecation")
		public org.xsocket.IWorkerPool getWorkerPool() {
			return MultithreadedServer.this.getWorkerPool();
		}
		
		/**
		 * {@inheritDoc}
		 */
		public Executor getWorkerpool() {
			return MultithreadedServer.this.getWorkerpool();
		}
		
		/**
		 * {@inheritDoc}
		 */
		public int getReceiveBufferPreallocationSize() {
			return MultithreadedServer.this.getReceiveBufferPreallocationSize();
		}
	}
}
