/*
 * Copyright (c) xlightweb.org, 2006 - 2010. All rights reserved.
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
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;


import org.xsocket.DataConverter;
import org.xsocket.WorkerPool;
import org.xsocket.connection.IConnection.FlushMode;



/**
 * Implementation of a server. For more information see
 * {@link IServer}
 *
 * @author grro@xsocket.org
 */
public class Server implements IServer {

	private static final Logger LOG = Logger.getLogger(Server.class.getName());


	private static String implementationVersion = ConnectionUtils.getImplementationVersion();
	private static String implementationDate;

	protected static final int SIZE_WORKER_POOL = Integer.parseInt(System.getProperty("org.xsocket.connection.server.workerpoolSize", "100"));
    protected static final int MIN_SIZE_WORKER_POOL = Integer.parseInt(System.getProperty("org.xsocket.connection.server.workerpoolMinSize", "2"));
    protected static final int TASK_QUEUE_SIZE = Integer.parseInt(System.getProperty("org.xsocket.connection.server.taskqueuesize", Integer.toString(SIZE_WORKER_POOL)));

    private static final int WAITTIME_MAXCONNECTION_EXCEEDED = Integer.parseInt(System.getProperty("org.xsocket.connection.server.waittimeMaxConnectionExceeded", Integer.toString(500)));



    
	private FlushMode flushMode = IConnection.DEFAULT_FLUSH_MODE;
	private boolean autoflush = IConnection.DEFAULT_AUTOFLUSH;
	private Integer writeRate;
	

	// is open flag
	private final AtomicBoolean isOpen = new AtomicBoolean(false);

	// name
	private String name = "server";
	
	// acceptor
	private IoAcceptor acceptor;
	
	// workerpool
	private ExecutorService defaultWorkerPool;
	private Executor workerpool;

	
	//connection manager
	private ConnectionManager connectionManager = new ConnectionManager();
	private int maxConcurrentConnections = Integer.MAX_VALUE;
	private boolean isMaxConnectionCheckAvtive = false;

	
	// handler replace Listener
    private final AtomicReference<IHandlerChangeListener> handlerReplaceListenerRef = new AtomicReference<IHandlerChangeListener>();

	
	// app handler
	private HandlerAdapter handlerAdapter = HandlerAdapter.newInstance(null);
	
	
	// thresholds
	private Integer maxReadBufferThreshold = null;
	
	// timeouts
	private long idleTimeoutMillis = IConnection.MAX_TIMEOUT_MILLIS;
	private long connectionTimeoutMillis = IConnection.MAX_TIMEOUT_MILLIS;


	// server listeners
	private final ArrayList<IServerListener> listeners = new ArrayList<IServerListener>();

	
	// local address
	private String localHostname = "";
	private int localPort = -1;
	
	// start up message
	private String startUpLogMessage = "xSocket " + implementationVersion;
	
	
	// startup date
	private long startUpTime = 0;


	/**
	 * constructor <br><br>
	 *
	 * @param handler the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @throws IOException If some other I/O error occurs
	 * @throws UnknownHostException if the local host cannot determined
	 */
	public Server(IHandler handler) throws UnknownHostException, IOException {
		this(new InetSocketAddress(0), new HashMap<String, Object>(), handler, null, false, 0, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
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
		this(new InetSocketAddress(0), options, handler, null, false, 0, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
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
		this(new InetSocketAddress(port), new HashMap<String, Object>(), handler, null, false, 0, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
	}

	
	/**
     * constructor  <br><br>
     *
     *
     * @param port         the local port
     * @param handler      the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
     * @param minPoolsize  the min workerpool size
     * @param maxPoolsize  the max workerpool size
     * @throws UnknownHostException if the local host cannot determined
     * @throws IOException If some other I/O error occurs
     */
    public Server(int port, IHandler handler, int minPoolsize, int maxPoolsize) throws UnknownHostException, IOException {
        this(new InetSocketAddress(port), new HashMap<String, Object>(), handler, null, false, 0, minPoolsize, maxPoolsize, maxPoolsize);
    }
	


	/**
	 * constructor  <br><br>
	 *
	 *
	 * @param port        the local port
	 * @param handler     the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @param backlog     The maximum number number of pending connections. If has the value 0, or a negative value, then an implementation specific default is used.
	 * @throws UnknownHostException if the local host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public Server(int port, IHandler handler, int backlog) throws UnknownHostException, IOException {
		this(new InetSocketAddress(port), new HashMap<String, Object>(), handler, null, false, backlog, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
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
		this(new InetSocketAddress(port), options, handler, null, false, 0, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
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
		this(address, port, new HashMap<String, Object>(), handler, null, false,0);
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
		this(new InetSocketAddress(ipAddress, port), new HashMap<String, Object>(), handler, null, false, 0, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
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
		this(new InetSocketAddress(ipAddress, port), options, handler, null, false, 0, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
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
		this(new InetSocketAddress(port), new HashMap<String, Object>(), handler, sslContext, sslOn, 0, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
	}



    /**
     * constructor <br><br>
     *
     * @param port               local port
     * @param handler            the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
     * @param sslOn              true, is SSL should be activated
     * @param sslContext         the ssl context to use
     * @param minPoolsize  the min workerpool size
     * @param maxPoolsize  the max workerpool size
     * @throws UnknownHostException if the local host cannot determined
     * @throws IOException If some other I/O error occurs
     */
    public Server(int port, IHandler handler, SSLContext sslContext, boolean sslOn, int minPoolsize, int maxPoolsize) throws UnknownHostException, IOException {
        this(new InetSocketAddress(port), new HashMap<String, Object>(), handler, sslContext, sslOn, 0, minPoolsize, maxPoolsize, maxPoolsize);
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
		this(new InetSocketAddress(port), options, handler, sslContext, sslOn, 0, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
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
		this(new InetSocketAddress(ipAddress, port), new HashMap<String, Object>(), handler,  sslContext, sslOn, 0, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
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
		this(new InetSocketAddress(ipAddress, port), options, handler, sslContext, sslOn, 0, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
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
		this(new InetSocketAddress(address, port), new HashMap<String, Object>(), handler, sslContext, sslOn, 0, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
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
		this(new InetSocketAddress(address, port), options, handler, sslContext, sslOn, 0, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
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
	 * @param backlog              The maximum number number of pending connections. If has the value 0, or a negative value, then an implementation specific default is used. 
	 * @throws UnknownHostException if the local host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public Server(InetAddress address, int port, Map<String, Object> options, IHandler handler, SSLContext sslContext, boolean sslOn, int backlog) throws UnknownHostException, IOException {
		this(new InetSocketAddress(address, port), options, handler, sslContext, sslOn, backlog, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
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
     * @param backlog              The maximum number number of pending connections. If has the value 0, or a negative value, then an implementation specific default is used.
     * @param minPoolsize          The min workerpool size
     * @param maxPoolsize          The max workerpool size
     * @throws UnknownHostException if the local host cannot determined
     * @throws IOException If some other I/O error occurs
     */
    public Server(InetAddress address, int port, Map<String, Object> options, IHandler handler, SSLContext sslContext, boolean sslOn, int backlog, int minPoolsize, int maxPoolsize) throws UnknownHostException, IOException {
        this(new InetSocketAddress(address, port), options, handler, sslContext, sslOn, backlog, minPoolsize, maxPoolsize, maxPoolsize);
    }
	
	
    
    
    /**
     * constructor 
     * 
     * @param address              local address
     * @param options              the socket options
     * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
     * @param sslOn                true, is SSL should be activated
     * @param sslContext           the ssl context to use
     * @param backlog              The maximum number number of pending connections. If has the value 0, or a negative value, then an implementation specific default is used.
     * @throws UnknownHostException if the local host cannot determined
     * @throws IOException If some other I/O error occurs
     */
    protected Server(InetSocketAddress address, Map<String, Object> options, IHandler handler, SSLContext sslContext, boolean sslOn, int backlog) throws UnknownHostException, IOException {
        this(address, options, handler, sslContext, sslOn, backlog, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
    }
      
    
    /**
     * constructor 
     * 
     * @param address              local address
     * @param options              the socket options
     * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
     * @param sslOn                true, is SSL should be activated
     * @param sslContext           the ssl context to use
     * @param backlog              The maximum number number of pending connections. If has the value 0, or a negative value, then an implementation specific default is used.
     * @param minPoolsize          The min workerpool size
     * @param maxPoolsize          The max workerpool size
     * @throws UnknownHostException if the local host cannot determined
     * @throws IOException If some other I/O error occurs
     */
    protected Server(InetSocketAddress address, Map<String, Object> options, IHandler handler, SSLContext sslContext, boolean sslOn, int backlog, int minPoolsize, int maxPoolsize) throws UnknownHostException, IOException {
        this(address, options, handler, sslContext, sslOn, backlog, minPoolsize, maxPoolsize, maxPoolsize);
    }
                
        

	/**
	 * constructor 
	 * 
	 * @param address              local address
	 * @param options              the socket options
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @param sslOn                true, is SSL should be activated
	 * @param sslContext           the ssl context to use
	 * @param backlog              The maximum number number of pending connections. If has the value 0, or a negative value, then an implementation specific default is used.
     * @param minPoolsize          The min workerpool size
     * @param maxPoolsize          The max workerpool size
     * @param taskqueueSize        The taskqueue size
	 * @throws UnknownHostException if the local host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	protected Server(InetSocketAddress address, Map<String, Object> options, IHandler handler, SSLContext sslContext, boolean sslOn, int backlog, int minPoolsize, int maxPoolsize, int taskqueueSize) throws UnknownHostException, IOException {
				
	    defaultWorkerPool = new WorkerPool(minPoolsize, maxPoolsize, taskqueueSize);
	    workerpool = defaultWorkerPool;
	    
		if (sslContext != null) {
			acceptor = ConnectionUtils.getIoProvider().createAcceptor(new LifeCycleHandler(), address, backlog, options, sslContext, sslOn);
			
		} else {
			acceptor = ConnectionUtils.getIoProvider().createAcceptor(new LifeCycleHandler(), address, backlog, options);
		}
			
		localHostname = acceptor.getLocalAddress().getHostName();
		localPort = acceptor.getLocalPort();
		
		setHandler(handler);
	}


	/**
	 * set the handler 
	 * @param handler the handler
	 */
    public void setHandler(IHandler handler) {
        
		if (isOpen.get()) {
			callCurrentHandlerOnDestroy();
		}
		
		IHandlerChangeListener changeListener = handlerReplaceListenerRef.get();
		if (changeListener != null) {
		    IHandler oldHandler = handlerAdapter.getHandler();
		    changeListener.onHanderReplaced(oldHandler, handler);
		}
		
		handlerAdapter = HandlerAdapter.newInstance(handler);
		

        // init app handler
        initCurrentHandler();
	}
    
	
	private void initCurrentHandler() {
		ConnectionUtils.injectServerField(this, handlerAdapter.getHandler());
		handlerAdapter.onInit();
	}
	
	
	private void callCurrentHandlerOnDestroy() {
		handlerAdapter.onDestroy();
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

	
	/**
	 * {@inheritDoc}
	 */
	public void run() {

		try {
			if (getHandler() == null) {
				LOG.warning("no handler has been set. Call setHandler-method to assign a handler");
			}
			
			startUpTime = System.currentTimeMillis();

			ShutdownHookHandler shutdownHookHandler = new ShutdownHookHandler(this);
			
			try {
				// register shutdown hook handler
				shutdownHookHandler.register();

				
				// listening in a blocking way  
				acceptor.listen();
				
			} finally {
				// deregister shutdown hook handler
				shutdownHookHandler.deregister();
			}


		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	
	private static final class ShutdownHookHandler extends Thread {
	
		private Runtime runtime;
		private Server server;
		
		public ShutdownHookHandler(Server server) {
			this.server = server;
		}
		
		void register() {
			runtime = Runtime.getRuntime();
			runtime.addShutdownHook(this);
		}

		
		public void run() {
		    if (server != null) {
		        server.close();
		    }
		}
		
		
		void deregister() {
			if (runtime != null) {
				try {
					runtime.removeShutdownHook(this);
				} catch (Exception e) {
					// eat and log exception
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("error occured by derigistering shutdwon hook " + e.toString());
					}
					
				}
				runtime = null;
				server = null;
			}
		}
	}
	
	
	
	/**
	 * starts the given server within a dedicated thread. This method blocks 
	 * until the server is open. This method is equals to {@link ConnectionUtils#start(IServer)} 
	 * 
	 * @throws SocketTimeoutException is the timeout has been reached 
	 */
	public void start() throws IOException {
		ConnectionUtils.start(this);
	}


	/**
	 * {@inheritDoc}
	 */
	public final Object getOption(String name) throws IOException {
		return acceptor.getOption(name);
	}


	public IHandler getHandler() {
		return handlerAdapter.getHandler();
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
	public final void close() {

		// is open?
		if (isOpen.getAndSet(false)) {
			// close connection manager
			try {
			    connectionManager.close();
			} catch (Throwable e) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("error occured by closing acceptor " + DataConverter.toString(e));
                }
            }
			connectionManager = null;

			
			// closing acceptor
			try {
				acceptor.close();  // closing of dispatcher will be initiated by acceptor
			} catch (Throwable e) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by closing acceptor " + DataConverter.toString(e));
				}
			}
			acceptor = null;
			
			
			// notify listeners 
			try {
				onClosed();
			} catch (Throwable e) { 
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by performing onClosed method " + DataConverter.toString(e));
				}
			}
			

			// unset references 
			handlerAdapter = null;
		}
	}

	
	protected void onClosed() throws IOException {
		
	}

	protected void onPreRejectConnection(NonBlockingConnection connection) throws IOException {
	    
	}



	final IoAcceptor getAcceptor() {
		return acceptor;
	}
	

	
	final long getConnectionManagerWatchDogPeriodMillis() {
		return connectionManager.getWatchDogPeriodMillis();
	}
	
	final int getConnectionManagerWatchDogRuns() {
		return connectionManager.getWatchDogRuns();
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
		
		if (isOpen.get()) {
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
		return isOpen.get();
	}



	/**
	 * sets the max number of concurrent connections
	 * 
	 * @param maxConcurrentConnections the max number of concurrent connections
	 */
	public final void setMaxConcurrentConnections(int maxConcurrentConnections) {
		this.maxConcurrentConnections = maxConcurrentConnections;
		
		if (maxConcurrentConnections == Integer.MAX_VALUE) {
		    isMaxConnectionCheckAvtive = false;
		} else {
		    isMaxConnectionCheckAvtive = true;
		}
	}
	
	
	/**
     * set the max app read buffer threshold
     * 
     * @param maxSize the max read buffer threshold
     */
    public void setMaxReadBufferThreshold(int maxSize) {
        this.maxReadBufferThreshold = maxSize;
    }
    
	
	/**
	 * returns the number of max concurrent connections 
	 * @return the number of max concurrent connections 
	 */
	int getMaxConcurrentConnections() {
		return maxConcurrentConnections;
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

	final int getNumberOfOpenConnections() {
		return connectionManager.getSize();
	}
	
    final int getDispatcherPoolSize() {
        return acceptor.getDispatcherSize();
    }
    
    final void setDispatcherPoolSize(int size) {
        acceptor.setDispatcherSize(size);
    }
    
    
    final boolean getReceiveBufferIsDirect() {
        return acceptor.getReceiveBufferIsDirect();
    }
    
    final void setReceiveBufferIsDirect(boolean isDirect) {
        acceptor.setReceiveBufferIsDirect(isDirect);
    }
    
    
    final Integer getReceiveBufferPreallocatedMinSize() {
        if (acceptor.isReceiveBufferPreallocationMode()) {
            return acceptor.getReceiveBufferPreallocatedMinSize();
        } else {
            return null;
        } 
    }
	
	
	public Set<INonBlockingConnection> getOpenConnections() {
		HashSet<INonBlockingConnection> cons = new HashSet<INonBlockingConnection>();

		if (connectionManager != null) {
    		for (INonBlockingConnection con: connectionManager.getConnections()) {
    			if (con.isOpen()) {
    				cons.add(con);
    			}
    		}
		}
		
		return cons;
	}
	
	final List<String> getOpenConnectionInfos() {
		List<String> infos = new ArrayList<String>();
		for (NonBlockingConnection con : connectionManager.getConnections()) {
			infos.add(con.toDetailedString());
		}
		
		return infos;
	}
	
	
	
	final int getNumberOfIdleTimeouts() {
		return connectionManager.getNumberOfIdleTimeouts();
	}
	
	final int getNumberOfConnectionTimeouts() {
		return connectionManager.getNumberOfConnectionTimeouts();
	}
	

	final Date getStartUPDate() {
	    return new Date(startUpTime); 
	}
	
	
	

	/**
	 * {@inheritDoc}
	 */
	public final FlushMode getFlushmode() {
		return flushMode;
	}
	
	

	/**
	 * {@inheritDoc}
	 */
	public final void setFlushmode(FlushMode flusmode) {
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
		
		if ((bytesPerSecond != INonBlockingConnection.UNLIMITED) && (flushMode != FlushMode.ASYNC)) {
			LOG.warning("setWriteTransferRate is only supported for FlushMode ASYNC. Ignore update of the transfer rate");
			return;
		}
		
		this.writeRate = bytesPerSecond;
	}
	
	

//	public void setReadTransferRate(int bytesPerSecond) throws IOException {
//		this.readRate = bytesPerSecond;
//	}

	
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

	

	
	/**
	 * returns the implementation version
	 *  
	 * @return the implementation version
	 */
	public String getImplementationVersion() {
		return implementationVersion;
	}
	
	
	
	/**
	 * returns the implementation date
	 * 
	 * @return  the implementation date 
	 */
	public String getImplementationDate() {
		if (implementationDate == null) {
			implementationDate = ConnectionUtils.getImplementationDate();
		}

		return implementationDate;
	}
	
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getServerName() + " on " + getLocalAddress().toString() + " Port " + getLocalPort());
		sb.append("\r\nopen connections:");
		for (NonBlockingConnection con : connectionManager.getConnections()) {
			sb.append("\r\n  " + con.toString());
		}
		
		return sb.toString();
	}
	

	private final class LifeCycleHandler implements IIoAcceptorCallback {

        @SuppressWarnings("unchecked")
		public void onConnected() {
			isOpen.set(true);
			
	        // notify listeners
	        for (IServerListener listener : (ArrayList<IServerListener>) listeners.clone()) {
	            listener.onInit();
	        }
	        
	        
			// print out the startUp log message
			if (acceptor.isSSLSupported()) {
				if (acceptor.isSSLOn()) {
					LOG.info(name + " listening on " + localHostname + ":" + localPort + " - SSL (" + startUpLogMessage + ")");
				} else {
					LOG.info(name + " listening on " + localHostname + ":" + localPort + " - activatable SSL (" + startUpLogMessage + ")");
				}
			} else {
				LOG.info(name + " listening on " + localHostname + ":" + localPort + " (" + startUpLogMessage + ")");
			}
		}


		@SuppressWarnings("unchecked")
		public void onDisconnected() {
			
			// perform handler callback 
			callCurrentHandlerOnDestroy();

		
			// calling server listener 
			for (IServerListener listener : (ArrayList<IServerListener>)listeners.clone()) {
				try {
					listener.onDestroy();
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("exception occured by destroying " + listener + " " + ioe.toString());
					}
				}
			}
			listeners.clear();

			
			// close default worker pool if exists
			if (defaultWorkerPool != null) {
				WorkerpoolCloser workerpoolCloser = new WorkerpoolCloser(Server.this);
				workerpoolCloser.start();
			}

			
			// unset workerpool reference 
			workerpool = null;			
			
			// print log message
			LOG.info("server (" + localHostname + ":" + localPort + ") has been shutdown");
		}


		
		public void onConnectionAccepted(IoChainableHandler ioHandler) throws IOException {
		    
	        // if max connection size reached  -> reject connection & sleep
		    if (isMacConnectionSizeExceeded()) {
		        
		        // create connection
	            NonBlockingConnection connection = new NonBlockingConnection(connectionManager, HandlerAdapter.newInstance(null));
	            init(connection, ioHandler);
	            
	            // first call pre reject 
	            try {
	                onPreRejectConnection(connection);
	            } catch (IOException e) {
	                if (LOG.isLoggable(Level.FINE)) {
	                    LOG.fine("[" + connection.getId() + "] error occured by calling onPreRejectConnection " + e.toString());
	                }
	            }
	            
	            // ... and than reject it
	            if (LOG.isLoggable(Level.FINE)) {
	                LOG.fine("[" + connection.getId() + "] rejecting connection. Max concurrent connection size (" + maxConcurrentConnections + ") exceeded");
	            }
	            connection.closeQuietly();
	            

                // wait while max connection size is exceeded
	            do {
    	            try {
    	                Thread.sleep(WAITTIME_MAXCONNECTION_EXCEEDED);
    	            } catch (InterruptedException ie) {
    	                // Restore the interrupted status
    	                Thread.currentThread().interrupt();
    	            }
	            } while (isMacConnectionSizeExceeded());
	            

	        // .. not exceeded
	        } else {
	            // create a new connection 
	            NonBlockingConnection connection = new NonBlockingConnection(connectionManager, handlerAdapter.getConnectionInstance());
	            init(connection, ioHandler);
	        }
		}
		
		
		private boolean isMacConnectionSizeExceeded() {
		    return (isMaxConnectionCheckAvtive && 
                    isOpen.get() && 
                    (acceptor.getDispatcherPool().getRoughNumRegisteredHandles() >= maxConcurrentConnections) &&
                    (acceptor.getDispatcherPool().getNumRegisteredHandles() >= maxConcurrentConnections));
		}
		
		
		private void init(NonBlockingConnection connection, IoChainableHandler ioHandler) throws SocketTimeoutException, IOException {
		    // set default flush properties
            connection.setAutoflush(autoflush);
            connection.setFlushmode(flushMode);
            connection.setWorkerpool(workerpool);
                
            // initialize the connection
            connection.init(ioHandler);
                
            // set timeouts  (requires that connection is already initialized)
            connection.setIdleTimeoutMillis(idleTimeoutMillis);
            connection.setConnectionTimeoutMillis(connectionTimeoutMillis);
            
            // set transfer rates
            if (writeRate != null) {
                connection.setWriteTransferRate(writeRate);
            }
            

            // and threshold
            if (maxReadBufferThreshold != null) {
                connection.setMaxReadBufferThreshold(maxReadBufferThreshold);
            }
		}
	}
	
	

	private static final class WorkerpoolCloser extends Thread {

		private Server server;
		
		public WorkerpoolCloser(Server server) {
			super("workerpoolCloser");
			setDaemon(true);
			
			this.server = server;
		}
		
		public void run() {
			try {
				Thread.sleep(3000);
			} catch (InterruptedException ie) { 
				// Restore the interrupted status
				Thread.currentThread().interrupt();
			}
				
			try {
				server.defaultWorkerPool.shutdownNow();
			} finally { 
				server.defaultWorkerPool = null;
				server = null;
			}
		}
	}
}
