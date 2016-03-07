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
package org.xsocket.connection;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;


import org.xsocket.connection.IConnection.FlushMode;
import org.xsocket.connection.spi.DefaultIoProvider;
import org.xsocket.connection.spi.IAcceptor;
import org.xsocket.connection.spi.IAcceptorCallback;
import org.xsocket.connection.spi.IIoHandler;
import org.xsocket.connection.spi.IServerIoProvider;




/**
 * Implementation of a server. For more information see
 * {@link IServer}
 *
 * @author grro@xsocket.org
 */
public class Server implements IServer {

	private static final Logger LOG = Logger.getLogger(Server.class.getName());

	
	public static final int SIZE_DEFAULT_WORKER_POOL = 40;
	private static String implementationVersion = null;


	private FlushMode flushMode = IConnection.DEFAULT_FLUSH_MODE;
	private boolean autoflush = IConnection.DEFAULT_AUTOFLUSH;
	private Integer writeRate = null;
	private Integer readRate = null;
	

	// is open flag
	private boolean isOpen = false;

	// name
	private String name = "server";

	// acceptor
	private boolean isSsslOn = false;
	private static IServerIoProvider serverIoProvider = null;
	private IAcceptor acceptor = null;


	// workerpool
	private ExecutorService defaultWorkerPool = Executors.newFixedThreadPool(SIZE_DEFAULT_WORKER_POOL, new DefaultThreadFactory());
	private Executor workerpool = defaultWorkerPool;

	
	// app handler
	private HandlerProxy handlerProxyPrototype = HandlerProxy.newPrototype(null, null);
	
	
	 // timeouts
	private long idleTimeoutMillis = IConnection.MAX_TIMEOUT_MILLIS;
	private long connectionTimeoutMillis = IConnection.MAX_TIMEOUT_MILLIS;


	// server listeners
	private final ArrayList<IServerListener> listeners = new ArrayList<IServerListener>();

	
	// modules
	private String startUpLogMessage = null;
	


	/**
	 * constructor <br><br>
	 *
	 * @param handler the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @throws IOException If some other I/O error occurs
	 * @throws UnknownHostException if the local host cannot determined
	 */
	public Server(IHandler handler) throws UnknownHostException, IOException {
		this(new InetSocketAddress(InetAddress.getByName(DEFAULT_HOST_ADDRESS), 0), new HashMap<String, Object>(), handler, null, false);
	}



	/**
	 * constructor <br><br>
	 *
	 * @param options              the socket options
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @throws IOException If some other I/O error occurs
	 * @throws UnknownHostException if the local host cannot determined
	 */
	public Server(Map<String, Object> options, IHandler handler) throws UnknownHostException, IOException {
		this(InetAddress.getByName(DEFAULT_HOST_ADDRESS), 0, options, handler, null, false);
	}


	/**
	 * constructor  <br><br>
	 *
	 *
	 * @param port        the local port
	 * @param handler     the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @throws UnknownHostException if the local host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public Server(int port, IHandler handler) throws UnknownHostException, IOException {
		this(new InetSocketAddress(InetAddress.getByName(DEFAULT_HOST_ADDRESS), port), new HashMap<String, Object>(), handler, null, false);
	}





	/**
	 * constructor <br><br>
	 *
	 * @param port                 the local port
	 * @param options              the acceptor socket options
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @throws UnknownHostException if the local host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public Server(int port, Map<String , Object> options, IHandler handler) throws UnknownHostException, IOException {
		this(InetAddress.getByName(DEFAULT_HOST_ADDRESS), port, options, handler, null, false);
	}



	/**
	 * constructor <br><br>
	 *
	 * @param address     the local address
	 * @param port        the local port
	 * @param handler     the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @throws UnknownHostException if the local host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public Server(InetAddress address, int port, IHandler handler) throws UnknownHostException, IOException {
		this(address, port, new HashMap<String, Object>(), handler, null, false);
	}




	



	/**
	 * constructor  <br><br>
	 *
	 * @param ipAddress   the local ip address
	 * @param port        the local port
	 * @param handler     the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @throws UnknownHostException if the local host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public Server(String ipAddress, int port, IHandler handler) throws UnknownHostException, IOException {
		this(InetAddress.getByName(ipAddress), port, new HashMap<String, Object>(), handler, null, false);
	}



	/**
	 * constructor <br><br>
	 *
	 *
	 * @param ipAddress            the local ip address
	 * @param port                 the local port
	 * @param options              the socket options
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @throws UnknownHostException if the local host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public Server(String ipAddress, int port, Map<String, Object> options, IHandler handler) throws UnknownHostException, IOException {
		this(InetAddress.getByName(ipAddress), port, options, handler, null, false);
	}




	/**
	 * constructor <br><br>
	 *
	 * @param port               local port
	 * @param handler            the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @param sslOn              true, is SSL should be activated
	 * @param sslContext         the ssl context to use
	 * @throws UnknownHostException if the local host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public Server(int port, IHandler handler, SSLContext sslContext, boolean sslOn) throws UnknownHostException, IOException {
		this(InetAddress.getByName(DEFAULT_HOST_ADDRESS), port, new HashMap<String, Object>(), handler, sslContext, sslOn);
	}



	/**
	 * constructor <br><br>
	 *
	 * @param port                 local port
	 * @param options              the acceptor socket options
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @param sslOn                true, is SSL should be activated
	 * @param sslContext           the ssl context to use
	 * @throws UnknownHostException if the local host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public Server(int port,Map<String, Object> options, IHandler handler, SSLContext sslContext, boolean sslOn) throws UnknownHostException, IOException {
		this(InetAddress.getByName(DEFAULT_HOST_ADDRESS), port, options, handler, sslContext, sslOn);
	}


	/**
	 * constructor <br><br>
	 *
	 * @param ipAddress          local ip address
	 * @param port               local port
	 * @param handler            the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @param sslOn              true, is SSL should be activated
	 * @param sslContext         the ssl context to use
	 * @throws UnknownHostException if the local host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public Server(String ipAddress, int port, IHandler handler, SSLContext sslContext, boolean sslOn) throws UnknownHostException, IOException {
		this(InetAddress.getByName(ipAddress), port, new HashMap<String, Object>(), handler,  sslContext, sslOn);
	}



	/**
	 * constructor <br><br>
	 *
	 * @param ipAddress            local ip address
	 * @param port                 local port
	 * @param options              the acceptor socket options
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @param sslOn                true, is SSL should be activated
	 * @param sslContext           the ssl context to use
	 * @throws UnknownHostException if the local host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public Server(String ipAddress, int port, Map<String, Object> options, IHandler handler, SSLContext sslContext, boolean sslOn) throws UnknownHostException, IOException {
		this(InetAddress.getByName(ipAddress), port, options, handler, sslContext, sslOn);
	}


	/**
	 * constructor <br><br>
	 *
	 * @param address            local address
	 * @param port               local port
	 * @param handler            the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @param sslOn              true, is SSL should be activated
	 * @param sslContext         the ssl context to use
	 * @throws UnknownHostException if the local host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public Server(InetAddress address, int port, IHandler handler, SSLContext sslContext, boolean sslOn) throws UnknownHostException, IOException {
		this(address, port, new HashMap<String, Object>(), handler, sslContext, sslOn);
	}




	/**
	 * constructor <br><br>
	 *
	 * @param address              local address
	 * @param port                 local port
	 * @param options              the socket options
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @param sslOn                true, is SSL should be activated
	 * @param sslContext           the ssl context to use
	 * @throws UnknownHostException if the local host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public Server(InetAddress address, int port, Map<String, Object> options, IHandler handler, SSLContext sslContext, boolean sslOn) throws UnknownHostException, IOException {
		this(new InetSocketAddress(address, port), options, handler, sslContext, sslOn);
	}




	protected Server(InetSocketAddress address, Map<String, Object> options, IHandler appHandler, SSLContext sslContext, boolean sslOn) throws UnknownHostException, IOException {
				
		if (sslContext != null) {
			isSsslOn = true;
			acceptor = new DefaultIoProvider().create(new AcceptorCallback(), address, 0, options, sslContext, sslOn);
			
		} else {
			isSsslOn = false;
			acceptor = getServerIoProvider().createAcceptor(new AcceptorCallback(), address, 0, options);
		}
		
		
		setHandler(appHandler);
	}



	private void setHandler(IHandler handler) {
		if (isOpen) {
			destroyCurrentHandler();
		}
		
		handlerProxyPrototype = HandlerProxy.newPrototype(handler, this);
		
		if (isOpen) {
			initCurrentHandler();
		}
	}
	
	private void initCurrentHandler() {
		handlerProxyPrototype.onInit();
	}
	
	
	private void destroyCurrentHandler() {
		handlerProxyPrototype.onDestroy();
	}
	

	IServerIoProvider getIoProvider() {
		return serverIoProvider;
	}
	

	/**
	 * the the server name. The server name will be used to print out the start log message.<br>
	 *
	 * E.g.
	 * <pre>
	 *   IServer cacheServer = new Server(port, new CacheHandler());
	 *   ConnectionUtils.start(server);
	 *   server.setServerName("CacheServer");
	 *
	 *
	 *   // prints out
	 *   // 01::52::42,756 10 INFO [Server$AcceptorCallback#onConnected] CacheServer listening on 172.25.34.33/172.25.34.33:9921 (xSocket 2.0)
     * </pre>
	 *
	 * @param name the server name
	 */
	public final void setServerName(String name) {
		this.name = name;
	}


	/**
	 * return the server name
	 *
	 * @return the server name
	 */
	public final String getServerName() {
		return name;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public String getStartUpLogMessage() {
		return startUpLogMessage;
	}
	
	/**
	 * {@inheritDoc}
	 */	
	public void setStartUpLogMessage(String message) {
		this.startUpLogMessage = message;
	}


	@SuppressWarnings("unchecked")
	private static IServerIoProvider getServerIoProvider() {

		if (serverIoProvider == null) {
			String serverIoProviderClassname = System.getProperty(IServerIoProvider.PROVIDER_CLASSNAME_KEY);
			if (serverIoProviderClassname != null) {
				try {
					Class serverIoProviderClass = Class.forName(serverIoProviderClassname, true, Thread.currentThread().getContextClassLoader());
					serverIoProvider = (IServerIoProvider) serverIoProviderClass.newInstance();
				} catch (Exception e) {
					LOG.warning("error occured by creating ServerIoProivder " + serverIoProviderClassname + ": " + e.toString());
					LOG.info("using default ServerIoProvider");

				}
			}

			if (serverIoProvider == null) {
				serverIoProvider = new DefaultIoProvider();
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
			if (getHandler() == null) {
				LOG.warning("no handler has been set. Call setHandler-method to set an assigned handler");
			}


			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					close();
				}
			});



			// start listening
			acceptor.listen();


		} catch (Exception e) {
			throw new RuntimeException(e.toString());
		}
	}



	/**
	 * {@inheritDoc}
	 */
	public final Object getOption(String name) throws IOException {
		return acceptor.getOption(name);
	}


	public IHandler getHandler() {
		return handlerProxyPrototype.getHandler();
	}


	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public final Map<String, Class> getOptions() {
		return acceptor.getOptions();
	}



	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public final void close() {
		if (isOpen) {
			isOpen = false;

			try {
				acceptor.close();  // closing of dispatcher will be initiated by acceptor
			} catch (IOException ignore) { }

			if (defaultWorkerPool != null) {
				defaultWorkerPool.shutdownNow();
			}
		}
	}





	IAcceptor getAcceptor() {
		return acceptor;
	}


	/**
	 * {@inheritDoc}
	 */
	public final void addListener(IServerListener listener) {
		listeners.add(listener);
	}



	/**
	 * {@inheritDoc}
	 */
	public final boolean removeListener(IServerListener listener) {
		boolean result = listeners.remove(listener);
		return result;
	}




	/**
	 * {@inheritDoc}
	 */
	public final Executor getWorkerpool() {
		return workerpool;
	}


	/**
	 * {@inheritDoc}
	 */
	public final void setWorkerpool(Executor executor) {
		if (executor == null) {
			throw new NullPointerException("executor has to be set");
		}
		
		if (isOpen) {
			LOG.warning("server is already running");
		}
		
		workerpool = executor;
		
		if (defaultWorkerPool != null) {
			defaultWorkerPool.shutdown();
			defaultWorkerPool = null;
		}
	}




	/**
	 * {@inheritDoc}
	 */
	public final boolean isOpen() {
		return isOpen;
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
	public final int getNumberOfOpenConnections() {
		return acceptor.getNumberOfOpenConnections();
	}
	

	/**
	 * {@inheritDoc}
	 */
	public final FlushMode getFlushMode() {
		return flushMode;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void setFlushMode(FlushMode flusmode) {
		this.flushMode = flusmode;
	}
	

	/**
	 * {@inheritDoc}
	 */
	public final void setAutoflush(boolean autoflush) {
		this.autoflush = autoflush;
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final boolean getAutoflush() {
		return autoflush;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void setConnectionTimeoutMillis(long timeoutMillis) {
		this.connectionTimeoutMillis = timeoutMillis;
	}

	
	/**
	 * {@inheritDoc}
	 */	
	public void setWriteTransferRate(int bytesPerSecond) throws IOException {
		this.writeRate = bytesPerSecond;
	}
	
	
	/**
	 * {@inheritDoc}
	 */	
	public void setReadTransferRate(int bytesPerSecond) throws IOException {
		this.readRate = bytesPerSecond;
	}

	
	/**
	 * {@inheritDoc}
	 */
	public void setIdleTimeoutMillis(long timeoutMillis) {
		this.idleTimeoutMillis = timeoutMillis;
	}



	/**
	 * {@inheritDoc}
	 */
	public final long getConnectionTimeoutMillis() {
		return connectionTimeoutMillis;
	}


	/**
	 * {@inheritDoc}
	 */
	public final long getIdleTimeoutMillis() {
		return idleTimeoutMillis;
	}


	public final String getVersion() {
		if (implementationVersion == null) {
			implementationVersion = ConnectionUtils.getVersionInfo();
		}

		return implementationVersion;
	}
	
	
	

	private final class AcceptorCallback implements IAcceptorCallback {

		@SuppressWarnings("unchecked")
		public void onConnected() {

			isOpen = true;
			
			
			startUpLogMessage = getVersion();
			if (startUpLogMessage.length() > 0) {
				startUpLogMessage = "xSocket " + startUpLogMessage;
			}


			if (!(serverIoProvider instanceof DefaultIoProvider)) {
				String verInfo = getServerIoProvider().getImplementationVersion();
				if (verInfo.length() > 0) {
					startUpLogMessage = startUpLogMessage + "/" + getServerIoProvider().getClass().getSimpleName() + " " + verInfo;
				}
			}

				

			// init app handler
			initCurrentHandler();

			
			// notify listeners
			for (IServerListener listener : (ArrayList<IServerListener>)listeners.clone()) {
				listener.onInit();
			}

			
			// print out the startUp log message
			if (isSsslOn) {
				LOG.info(name + " listening on " + acceptor.getLocalAddress().getHostName() + ":" + acceptor.getLocalPort() + " - SSL mode (" + startUpLogMessage + ")");
			} else {
				LOG.info(name + " listening on " + acceptor.getLocalAddress().getHostName() + ":" + acceptor.getLocalPort() + " (" + startUpLogMessage + ")");
			}
		}


		@SuppressWarnings("unchecked")
		public void onDisconnected() {
			
			destroyCurrentHandler();

			for (IServerListener listener : (ArrayList<IServerListener>)listeners.clone()) {
				try {
					listener.onDestroy();
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("exception occured by destroying " + listener + " " + ioe.toString());
					}
				}
			}
			
			LOG.info("server has been shutdown");
		}


		public void onConnectionAccepted(IIoHandler ioHandler) throws IOException {

			// create a new connection 
			NonBlockingConnection connection = new NonBlockingConnection();
				
			// set default flush properties
			connection.setAutoflush(autoflush);
			connection.setFlushmode(flushMode);
	
				
			// initialize the connection
			connection.init(ioHandler, getServerIoProvider(), handlerProxyPrototype.newProxy(connection), workerpool);
				
			// set timeouts  (requires that connection is already initialized)
			connection.setIdleTimeoutMillis(idleTimeoutMillis);
			connection.setConnectionTimeoutMillis(connectionTimeoutMillis);
			
			// set transfer rates
			if (writeRate != null) {
				connection.setWriteTransferRate(writeRate);
			}
	/*		if (readRate != null) {
				connection.setReadTransferRate(readRate);
			}*/
		}
	}
	
	
	private static class DefaultThreadFactory implements ThreadFactory {
		private static final AtomicInteger poolNumber = new AtomicInteger(1);
		private final ThreadGroup group;
		private final AtomicInteger threadNumber = new AtomicInteger(1);
		private final String namePrefix;

		DefaultThreadFactory() {
			SecurityManager s = System.getSecurityManager();
			group = (s != null)? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
			namePrefix = "xServerPool-" + poolNumber.getAndIncrement() + "-thread-";
        }

		public Thread newThread(Runnable r) {
			Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }
}
