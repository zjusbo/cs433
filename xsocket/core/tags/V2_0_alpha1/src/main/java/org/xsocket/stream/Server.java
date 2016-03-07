// $Id: MultithreadedServer.java 1629 2007-08-01 06:14:37Z grro $
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
import java.io.InputStreamReader;
import java.io.LineNumberReader;
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

import javax.annotation.Resource;
import javax.net.ssl.SSLContext;


import org.xsocket.stream.io.impl.IoProvider;
import org.xsocket.stream.io.spi.IAcceptor;
import org.xsocket.stream.io.spi.IServerIoProvider;
import org.xsocket.stream.io.spi.IAcceptorCallback;
import org.xsocket.stream.io.spi.IIoHandler;




/**
 * Implementation of a server. For more information see
 * {@link IServer}
 *
 * @author grro@xsocket.org
 */
public final class Server implements IServer {

	/*
	 * performance
	 *  * http://www-128.ibm.com/developerworks/linux/library/l-hisock.html?ca=dgr-lnxw01BoostSocket
	 *
	 * known tcp issues
	 * to many max files ->  http://support.bea.com/application_content/product_portlets/support_patterns/wls/TooManyOpenFilesPattern.html
	 * windows tcp -> http://publib.boulder.ibm.com/infocenter/wasinfo/v4r0/index.jsp?topic=/com.ibm.websphere.v4.doc/wasa_content/0901.html
	 */

	private static final Logger LOG = Logger.getLogger(Server.class.getName());

	private static final int DEFAULT_WORKER_POOL_MAX_SIZE = 250;
	private static String implementationVersion = null;



	// is open flag
	private boolean isOpen= false;

	// name
	private String name = "server";

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

	// connection factory
	private INonBlockingConnectionFactory connectionFactory = new DefaultConnectionFactory();


	// app handler
	private Object orgAppHandler = null;
	private IHandler appHandlerPrototype = null;

	// io handler
	private final IoHandlerContext ioHandlerContext = new IoHandlerContext(null, null);


	// server listeners
	private final ArrayList<IServerListener> listeners = new ArrayList<IServerListener>();


	//timeouts
	private int idleTimeoutSec = Integer.MAX_VALUE;
	private int connectionTimeoutSec = Integer.MAX_VALUE;



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
	public Server(IHandler handler) throws UnknownHostException, IOException {
		this(new InetSocketAddress(InetAddress.getByName(DEFAULT_HOST_ADDRESS), 0), new HashMap<String, Object>(), handler, newServerWorkerPool(DEFAULT_WORKER_POOL_MAX_SIZE), false, null);
	}


	public Server(Object handler) throws UnknownHostException, IOException {
		this(new InetSocketAddress(InetAddress.getByName(DEFAULT_HOST_ADDRESS), 0), new HashMap<String, Object>(), handler, newServerWorkerPool(DEFAULT_WORKER_POOL_MAX_SIZE), false, null);
	}


	/**
	 * constructor <br><br>
	 *
	 * For idle-, connection time out and the used workerpool see {@link Server#Server(IHandler)}
	 *
	 * @param handler      the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
  	 * @param workerpool   the workerpool to use. By setting the WorkerPool with null the worker pool will be deactivated. The callback handler will be
  	 *                     executed by the main dispatching thread. If the handler performs blocking operations, the disptaching will also be blocked!
	 * @throws IOException If some other I/O error occurs
	 * @throws UnknownHostException if the locale host cannot determined
	 */
	public Server(IHandler handler, Executor workerpool) throws UnknownHostException, IOException {
		this(new InetSocketAddress(InetAddress.getByName(DEFAULT_HOST_ADDRESS), 0), new HashMap<String, Object>(), handler, workerpool, false, null);
	}


	public Server(Object handler, int maxWorkers) throws UnknownHostException, IOException {
		this(new InetSocketAddress(InetAddress.getByName(DEFAULT_HOST_ADDRESS), 0), new HashMap<String, Object>(), handler, newServerWorkerPool(maxWorkers), false, null);
	}




	/**
	 * constructor <br><br>
	 *
	 * For idle-, connection time out and the used workerpool see {@link Server#Server(IHandler)}
	 *
	 * @param options              the socket options
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
	 * @throws IOException If some other I/O error occurs
	 * @throws UnknownHostException if the locale host cannot determined
	 */
	public Server(Map<String, Object> options, IHandler handler) throws UnknownHostException, IOException {
		this(InetAddress.getByName(DEFAULT_HOST_ADDRESS), 0, options, handler, false, null);
	}


	/**
	 * constructor  <br><br>
	 *
	 * For idle-, connection time out and the used workerpool see {@link Server#MultithreadedServer(IHandler)}
	 *
	 * @param port        the local port
	 * @param handler     the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
	 * @throws UnknownHostException if the locale host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public Server(int port, IHandler handler) throws UnknownHostException, IOException {
		this(new InetSocketAddress(InetAddress.getByName(DEFAULT_HOST_ADDRESS), port), new HashMap<String, Object>(), handler, newServerWorkerPool(DEFAULT_WORKER_POOL_MAX_SIZE), false, null);
	}


	public Server(int port, Object handler) throws UnknownHostException, IOException {
		this(new InetSocketAddress(InetAddress.getByName(DEFAULT_HOST_ADDRESS), port), new HashMap<String, Object>(), handler, newServerWorkerPool(DEFAULT_WORKER_POOL_MAX_SIZE), false, null);
	}

	/**
	 * constructor  <br><br>
	 *
	 * For idle-, connection time out see {@link Server#MultithreadedServer(IHandler)}
	 *
	 * @param port        the local port
	 * @param handler     the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
  	 * @param workerpool   the workerpool to use. By setting the WorkerPool with null the worker pool will be deactivated. The callback handler will be
  	 *                     executed by the main dispatching thread. If the handler performs blocking operations, the disptaching will also be blocked!
	 * @throws UnknownHostException if the locale host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public Server(int port, IHandler handler, Executor workerpool) throws UnknownHostException, IOException {
		this(InetAddress.getByName(DEFAULT_HOST_ADDRESS), port, new HashMap<String, Object>(), handler, workerpool, false, null);
	}


	public Server(int port, Object handler, int maxWorkers) throws UnknownHostException, IOException {
		this(new InetSocketAddress(InetAddress.getByName(DEFAULT_HOST_ADDRESS), port), new HashMap<String, Object>(), handler, newServerWorkerPool(maxWorkers), false, null);
	}




	/**
	 * constructor <br><br>
	 *
	 * For idle-, connection time out and the used workerpool see {@link Server#MultithreadedServer(IHandler)}
	 *
	 * @param port                 the local port
	 * @param options              the acceptor socket options
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
	 * @throws UnknownHostException if the locale host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public Server(int port, Map<String , Object> options, IHandler handler) throws UnknownHostException, IOException {
		this(InetAddress.getByName(DEFAULT_HOST_ADDRESS), port, options, handler, false, null);
	}



	/**
	 * constructor <br><br>
	 *
	 * For idle-, connection time out and the used workerpool see {@link Server#MultithreadedServer(IHandler)}
	 *
	 * @param address     the local address
	 * @param port        the local port
	 * @param handler     the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
	 * @throws UnknownHostException if the locale host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public Server(InetAddress address, int port, IHandler handler) throws UnknownHostException, IOException {
		this(address, port, new HashMap<String, Object>(), handler, false, null);
	}




	/**
	 * constructor <br><br>
	 *
	 * For idle-, connection time out and the used workerpool see {@link Server#MultithreadedServer(IHandler)}
	 *
	 * @param address     the local address
	 * @param port        the local port
	 * @param handler     the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
	 * @param workerPool  the workerpool
	 * @throws UnknownHostException if the locale host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public Server(InetAddress address, int port, IHandler handler, Executor workerPool) throws UnknownHostException, IOException {
		this(address, port, new HashMap<String, Object>(), handler, workerPool, false, null);
	}




	/**
	 * constructor  <br><br>
	 *
	 * For idle-, connection time out and the used workerpool see {@link Server#MultithreadedServer(IHandler)}
	 *
	 * @param ipAddress   the local ip address
	 * @param port        the local port
	 * @param handler     the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
	 * @throws UnknownHostException if the locale host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public Server(String ipAddress, int port, IHandler handler) throws UnknownHostException, IOException {
		this(InetAddress.getByName(ipAddress), port, new HashMap<String, Object>(), handler, false, null);
	}



	/**
	 * constructor <br><br>
	 *
	 * For idle-, connection time out and the used workerpool see {@link Server#MultithreadedServer(IHandler)}
	 *
	 * @param ipAddress            the local ip address
	 * @param port                 the local port
	 * @param options              the socket options
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
	 * @throws UnknownHostException if the locale host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public Server(String ipAddress, int port, Map<String, Object> options, IHandler handler) throws UnknownHostException, IOException {
		this(InetAddress.getByName(ipAddress), port, options, handler, false, null);
	}




	/**
	 * constructor <br><br>
	 *
	 * For idle-, connection time out and the used workerpool see {@link Server#MultithreadedServer(IHandler)}
	 *
	 * @param port               local port
	 * @param handler            the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
	 * @param sslOn              true, is SSL should be activated
	 * @param sslContext         the ssl context to use
	 * @throws UnknownHostException if the locale host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public Server(int port, IHandler handler, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this(InetAddress.getByName(DEFAULT_HOST_ADDRESS), port, new HashMap<String, Object>(), handler, sslOn ,sslContext);
	}



	/**
	 * constructor <br><br>
	 *
	 * For idle-, connection time out and the used workerpool see {@link Server#MultithreadedServer(IHandler)}
	 *
	 * @param port                 local port
	 * @param options              the acceptor socket options
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IHandlerLifeCycleListener)
	 * @param sslOn                true, is SSL should be activated
	 * @param sslContext           the ssl context to use
	 * @throws UnknownHostException if the locale host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public Server(int port,Map<String, Object> options, IHandler handler, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this(InetAddress.getByName(DEFAULT_HOST_ADDRESS), port, options, handler, sslOn ,sslContext);
	}


	/**
	 * constructor <br><br>
	 *
	 * For idle-, connection time out and the used workerpool see {@link Server#MultithreadedServer(IHandler)}
	 *
	 * @param ipAddress          local ip address
	 * @param port               local port
	 * @param handler            the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IConnectionScoped, IHandlerLifeCycleListener)
	 * @param sslOn              true, is SSL should be activated
	 * @param sslContext         the ssl context to use
	 * @throws UnknownHostException if the locale host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public Server(String ipAddress, int port, IHandler handler, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this(InetAddress.getByName(ipAddress), port, new HashMap<String, Object>(), handler, sslOn, sslContext);
	}



	/**
	 * constructor <br><br>
	 *
	 * For idle-, connection time out and the used workerpool see {@link Server#MultithreadedServer(IHandler)}
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
	public Server(String ipAddress, int port, Map<String, Object> options, IHandler handler, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this(InetAddress.getByName(ipAddress), port, options, handler, sslOn, sslContext);
	}


	/**
	 * constructor <br><br>
	 *
	 * For idle-, connection time out and the used workerpool see {@link Server#MultithreadedServer(IHandler)}
	 *
	 * @param address            local address
	 * @param port               local port
	 * @param handler            the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IConnectionScoped, IHandlerLifeCycleListener)
	 * @param sslOn              true, is SSL should be activated
	 * @param sslContext         the ssl context to use
	 * @throws UnknownHostException if the locale host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public Server(InetAddress address, int port, IHandler handler, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this(address, port, new HashMap<String, Object>(), handler, sslOn, sslContext);
	}




	/**
	 * constructor <br><br>
	 *
	 * For idle-, connection time out and the used workerpool see {@link Server#MultithreadedServer(IHandler)}
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
	public Server(InetAddress address, int port, Map<String, Object> options, IHandler handler, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this(address, port, options, handler, newServerWorkerPool(DEFAULT_WORKER_POOL_MAX_SIZE), sslOn, sslContext);
		isSelfCreatedWorkerPool = true;
	}


	public Server(int port, Object appHandler, int maxWorkers, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this(new InetSocketAddress(InetAddress.getByName(DEFAULT_HOST_ADDRESS), port), new HashMap<String, Object>(), appHandler, newServerWorkerPool(maxWorkers), sslOn, sslContext);
	}


	/**
	 * constructor <br><br>
	 *
	 * For idle-, connection time out see {@link Server#MultithreadedServer(IHandler)}
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
	public Server(InetAddress address, int port, Map<String, Object> options, IHandler appHandler, Executor workerpool, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this(new InetSocketAddress(address, port), options, appHandler, workerpool, sslOn, sslContext);
	}


	private Server(InetSocketAddress address, Map<String, Object> options, Object appHandler, Executor workerpool, boolean sslOn, SSLContext sslContext) throws UnknownHostException, IOException {
		this.address = address;
		this.options = options;
		this.workerpool = workerpool;
		this.sslOn = sslOn;
		this.sslContext = sslContext;

		ioHandlerContext.updateWorkerpool(workerpool);
		setAppHandler(appHandler);
	}


	private void setAppHandler(Object appHandler) {
		this.orgAppHandler = appHandler;

		ioHandlerContext.updateAppHandler(appHandler);
		injectServerContext(appHandler);

		if (ioHandlerContext.isDynamicHandler()) {
			appHandlerPrototype = DynamicHandlerAdapterFactory.getInstance().createHandlerAdapter(ioHandlerContext, appHandler);
		} else {
			appHandlerPrototype = (IHandler) appHandler;
		}
	}


	public Object getAppHandler() {
		return orgAppHandler;
	}


	/**
	 * the the server name. The server name will be used to print out the start log message.<br>
	 *
	 * E.g.
	 * <pre>
	 *   IServer cacheServer = new Server(port, new CacheHandler());
	 *   StreamUtils.start(server);
	 *   server.setServerName("CacheServer");
	 *
	 *
	 *   // prints out
	 *   // 01::52::42,756 10 INFO [Server$AcceptorCallback#onConnected] CacheServer listening on 172.25.34.33/172.25.34.33:9921 (xSocket 2.0)
     * </pre>
	 *
	 * @param name the server name
	 */
	public void setServerName(String name) {
		this.name = name;
	}


	/**
	 * return the server name
	 *
	 * @return the server name
	 */
	public String getServerName() {
		return name;
	}


	private static IServerIoProvider getServerIoProvider() {

		if (serverIoProvider == null) {
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

		return serverIoProvider;
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
				acceptor = getServerIoProvider().createAcceptor(new AcceptorCallback(), ioHandlerContext, address, 0, options);
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


	public IHandler getHandler() {
		return appHandlerPrototype;
	}


	/**
	 * {@inheritDoc}
	 */
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



	private static final ExecutorService newServerWorkerPool(int size) {
		return Executors.newFixedThreadPool(size);
	}



	IAcceptor getAcceptor() {
		return acceptor;
	}


	/**
	 * {@inheritDoc}
	 */
	public void addListener(IServerListener listener) {
		listeners.add(listener);
	}


	/**
	 * {@inheritDoc}
	 */
	public void setConnectionFactory(INonBlockingConnectionFactory connectionFactory) {
		this.connectionFactory = connectionFactory;
	}


	/**
	 * {@inheritDoc}
	 */
	public boolean removeListener(IServerListener listener) {
		boolean result = listeners.remove(listener);
		return result;
	}




	/**
	 * {@inheritDoc}
	 */
	public Executor getWorkerpool() {
		return workerpool;
	}






	/**
	 * {@inheritDoc}
	 */
	public boolean isOpen() {
		return isOpen;
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
		if (ioHandlerContext.isLifeCycleHandler()) {
			((org.xsocket.ILifeCycle) appHandlerPrototype).onInit();
		}
	}




	/**
	 * {@inheritDoc}
	 */
	public final int getLocalPort() {
		return acceptor.getLocalPort();
	}


	/**
	 * {@inheritDoc}
	 */
	public final InetAddress getLocalAddress() {
		return acceptor.getLocalAddress();
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


	private int numberOfOpenConnections() {
		return acceptor.getNumberOfOpenConnections();
	}


	public String getImplementationVersion() {
		if (implementationVersion == null) {
			try {
				LineNumberReader lnr = new LineNumberReader(new InputStreamReader(this.getClass().getResourceAsStream("/org/xsocket/version.txt")));
				String line = null;
				do {
					line = lnr.readLine();
					if (line != null) {
						if (line.startsWith("Implementation-Version=")) {
							implementationVersion = line.substring("Implementation-Version=".length(), line.length()).trim();
							break;
						}
					}
				} while (line != null);

				lnr.close();
			} catch (Exception ignore) { }
		}

		return implementationVersion;
	}


	private void injectServerContext(Object hdl) {

		IServerContext ctx = null;
		Field[] fields = hdl.getClass().getDeclaredFields();
		for (Field field : fields) {

			if (field.isAnnotationPresent(Resource.class)) {
				Resource annotation = field.getAnnotation(Resource.class);
				if ((annotation.type() == IServerContext.class) || (field.getType() == IServerContext.class)) {
					field.setAccessible(true);
					try {
						if (ctx == null) {
							ctx = new HandlerContext();
						}
						field.set(hdl, ctx);
					} catch (IllegalAccessException iae) {
						LOG.warning("could not set HandlerContext for attribute " + field.getName() + ". Reason " + iae.toString());
					}
				}
			}
		}
	}


	private final class AcceptorCallback implements IAcceptorCallback {

		@SuppressWarnings("unchecked")
		public void onConnected() {

			isOpen = true;


			// init app handler
			initCurrentHandler();

			// notify listenes
			for (IServerListener listener : (ArrayList<IServerListener>)listeners.clone()) {
				listener.onInit();
			}

			String versionInfo = getImplementationVersion();
			if (versionInfo.length() > 0) {
				versionInfo = "xSocket " + versionInfo;
			}


			if (!(serverIoProvider instanceof IoProvider)) {
				String verInfo = getServerIoProvider().getImplementationVersion();
				if (verInfo.length() > 0) {
					versionInfo = versionInfo + "; " + getServerIoProvider().getClass().getSimpleName() + " " + verInfo;
				}
			}

			LOG.info(name + " listening on " + acceptor.getLocalAddress().getHostName() + "/" + acceptor.getLocalPort() + " (" + versionInfo + ")");
		}


		@SuppressWarnings("unchecked")
		public void onDisconnected() {

	        destroyCurrentHandler();

			for (IServerListener listener : (ArrayList<IServerListener>)listeners.clone()) {
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
			INonBlockingConnection connection = connectionFactory.createNonBlockingConnection(ioHandlerContext, ioHandler, hdl, getServerIoProvider());
			connection.setIdleTimeoutSec(idleTimeoutSec);
			connection.setConnectionTimeoutSec(connectionTimeoutSec);
		}
	}


	private final class HandlerContext implements IServerContext {

		/**
		 * {@inheritDoc}
		 */
		public int getLocalPort() {
			return Server.this.getLocalPort();
		}

		/**
		 * {@inheritDoc}
		 */
		public InetAddress getLocalAddress() {
			return Server.this.getLocalAddress();
		}


		/**
		 * {@inheritDoc}
		 */
		public int getNumberOfOpenConnections() {
			return Server.this.numberOfOpenConnections();
		}

		/**
		 * {@inheritDoc}
		 */
		public Executor getWorkerpool() {
			return Server.this.getWorkerpool();
		}
	}
}
