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
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.rmi.server.UID;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.JMException;
import javax.management.ObjectName;
import javax.net.ssl.SSLContext;

import org.xsocket.DataConverter;
import org.xsocket.IntrospectionBasedDynamicMBean;





/**
 * IoProvider
 * 
 * 
 * @author grro@xsocket.org
 */
final class IoProvider  {

	private static final Logger LOG = Logger.getLogger(IoProvider.class.getName());

	static final String SO_SNDBUF = IConnection.SO_SNDBUF;
	static final String SO_RCVBUF = IConnection.SO_RCVBUF;
	static final String SO_REUSEADDR = IConnection.SO_REUSEADDR;
	static final String SO_TIMEOUT = "SOL_SOCKET.SO_TIMEOUT";
	static final String SO_KEEPALIVE = IConnection.SO_KEEPALIVE;
	static final String SO_LINGER = IConnection.SO_LINGER;
	static final String TCP_NODELAY = IConnection.TCP_NODELAY;

	
	static final int UNLIMITED = INonBlockingConnection.UNLIMITED;
	static final long DEFAULT_CONNECTION_TIMEOUT_MILLIS = IConnection.DEFAULT_CONNECTION_TIMEOUT_MILLIS;
	static final long DEFAULT_IDLE_TIMEOUT_MILLIS = IConnection.DEFAULT_IDLE_TIMEOUT_MILLIS;

	
	private static final Timer TIMER = new Timer("xIoTimer", true);
	private static IoSocketDispatcherPool globalClientDispatcherPool = null;

		
	
	// dispatcher props
	public static final String COUNT_DISPATCHER_KEY = "org.xsocket.connection.dispatcher.initialCount";
	private static final String MAX_HANDLES = "org.xsocket.connection.dispatcher.maxHandles";	
	private static final String DETACH_HANDLE_ON_NO_OPS = "org.xsocket.connection.dispatcher.detachHandleOnNoOps";
	private static final String DEFAULT_DETACH_HANDLE_ON_NO_OPS = "true";
	private static final String IS_BYPASSING_WRITE_ALLOWED = "org.xsocket.connection.dispatcher.bypassingWriteAllowed";
	private static final String DEFAULT_IS_BYPASSING_WRITE_ALLOWED = "true";
	
	// direct buffer?
	public static final String DEFAULT_USE_DIRECT_BUFFER = "true";
	public static final String CLIENT_READBUFFER_USE_DIRECT_KEY             = "org.xsocket.connection.client.readbuffer.usedirect";
	public static final String SERVER_READBUFFER_USE_DIRECT_KEY             = "org.xsocket.connection.server.readbuffer.usedirect";
	
	
	// preallocation params
	public static final String DEFAULT_READ_BUFFER_PREALLOCATION_ON = "true";
	public static final int DEFAULT_READ_BUFFER_PREALLOCATION_SIZE = 65536;
	public static final int DEFAULT_READ_BUFFER_MIN_SIZE = 64;
	
	
	public static final String CLIENT_READBUFFER_PREALLOCATION_ON_KEY       = "org.xsocket.connection.client.readbuffer.preallocation.on";
	public static final String CLIENT_READBUFFER_PREALLOCATION_SIZE_KEY     = "org.xsocket.connection.client.readbuffer.preallocation.size";
	public static final String CLIENT_READBUFFER_PREALLOCATION_MIN_SIZE_KEY = "org.xsocket.connection.client.readbuffer.preallocated.minSize";
	
	public static final String SERVER_READBUFFER_PREALLOCATION_ON_KEY       = "org.xsocket.connection.server.readbuffer.preallocation.on";
	public static final String SERVER_READBUFFER_PREALLOCATION_SIZE_KEY     = "org.xsocket.connection.server.readbuffer.preallocation.size";
	public static final String SERVER_READBUFFER_PREALLOCATION_MIN_SIZE_KEY = "org.xsocket.connection.server.readbuffer.preallocated.minSize";
	
	
	public static final String DEFAULT_CLIENT_MAX_READBUFFER_SIZE_KEY       = "org.xsocket.connection.client.readbuffer.defaultMaxReadBufferThreshold";
	public static final String DEFAULT_SERVER_MAX_READBUFFER_SIZE_KEY       = "org.xsocket.connection.server.readbuffer.defaultMaxReadBufferThreshold";

	public static final String DEFAULT_CLIENT_MAX_WRITEBUFFER_SIZE_KEY       = "org.xsocket.connection.client.readbuffer.defaultMaxWriteBufferThreshold";
	public static final String DEFAULT_SERVER_MAX_WRITEBUFFER_SIZE_KEY       = "org.xsocket.connection.server.readbuffer.defaultMaxWriteBufferThreshold";

	
	private static Integer defaultClientMaxReadbufferSize = null;
	private static Integer defaultServerMaxReadbufferSize = null;
	private static Integer defaultClientMaxWritebufferSize = null;
	private static Integer defaultServerMaxWritebufferSize = null;

	
	private static final String SSLENGINE_CLIENT_ENABLED_CIPHER_SUITES_KEY  = "org.xsocket.connection.client.ssl.sslengine.enabledCipherSuites";
	private static final String SSLENGINE_SERVER_ENABLED_CIPHER_SUITES_KEY  = "org.xsocket.connection.server.ssl.sslengine.enabledCipherSuites";
	private static final String SSLENGINE_CLIENT_ENABLED_PROTOCOLS_KEY  = "org.xsocket.connection.client.ssl.sslengine.enabledProtocols";
	private static final String SSLENGINE_SERVER_ENABLED_PROTOCOLS_KEY  = "org.xsocket.connection.server.ssl.sslengine.enabledProtocols";
	private static final String SSLENGINE_SERVER_WANT_CLIENT_AUTH_KEY  = "org.xsocket.connection.server.ssl.sslengine.wantClientAuth";
	private static final String SSLENGINE_SERVER_NEED_CLIENT_AUTH_KEY  = "org.xsocket.connection.server.ssl.sslengine.needClientAuth";
	
	
	private static String[] sSLEngineClientEnabledCipherSuites = null;
	private static String[] sSLEngineServerEnabledCipherSuites = null;
	private static String[] sSLEngineClientEnabledProtocols = null;
	private static String[] sSLEngineServerEnabledProtocols = null;
	private static Boolean sSLEngineWantClientAuth = null;
	private static Boolean sSLEngineNeedClientAuth = null; 
	
	
	private static Integer countDispatcher = null;
	private static Integer maxHandles = null;
	private static boolean detachHandleOnNoOps = true; 
	private static boolean bypassingWriteAllowed = false;
    
	private static Boolean clientReadBufferUseDirect = null;
	private static Boolean serverReadBufferUseDirect = null;
	
	private static Boolean clientReadBufferPreallocationOn = null;
	private static int clientReadBufferPreallocationsize = DEFAULT_READ_BUFFER_PREALLOCATION_SIZE;
	private static int clientReadBufferMinsize = DEFAULT_READ_BUFFER_MIN_SIZE;
	
	private static Boolean serverReadBufferPreallocationOn = null;
	private static int serverReadBufferPreallocationsize = DEFAULT_READ_BUFFER_PREALLOCATION_SIZE;
	private static int serverReadBufferMinsize = DEFAULT_READ_BUFFER_MIN_SIZE;

	private static String idPrefix = null;


    static {

    	
    	////////////////////////////////////////////////
    	// use direct buffer or non-direct buffer?
    	//
    	// current vm implementations (Juli/2007) seems to have
    	// problems by gc direct buffers.
    	//
    	// links
        // * [Java bugdatabase] http://bugs.sun.com/bugdatabase/view_bug.do;jsessionid=94d5403110224b692e5354bd87a92:WuuT?bug_id=6210541
    	// * [forum thread]     http://forums.java.net/jive/thread.jspa?messageID=223706&tstart=0
    	// * [mina]             https://issues.apache.org/jira/browse/DIRMINA-391
    	//
    	////////////////////////////////////////////////


    	countDispatcher = readIntProperty(COUNT_DISPATCHER_KEY);
    	maxHandles = readIntProperty(MAX_HANDLES);
    	
    	detachHandleOnNoOps = readBooleanProperty(DETACH_HANDLE_ON_NO_OPS, DEFAULT_DETACH_HANDLE_ON_NO_OPS);
    	bypassingWriteAllowed = readBooleanProperty(IS_BYPASSING_WRITE_ALLOWED, DEFAULT_IS_BYPASSING_WRITE_ALLOWED);
    	
    	
    	// direct buffer?
   		clientReadBufferUseDirect = readBooleanProperty(IoProvider.CLIENT_READBUFFER_USE_DIRECT_KEY, DEFAULT_USE_DIRECT_BUFFER);
   		serverReadBufferUseDirect = readBooleanProperty(IoProvider.SERVER_READBUFFER_USE_DIRECT_KEY, DEFAULT_USE_DIRECT_BUFFER);
   		
   		// thresholds
   		defaultClientMaxReadbufferSize = readIntProperty(IoProvider.DEFAULT_CLIENT_MAX_READBUFFER_SIZE_KEY);
   		defaultServerMaxReadbufferSize = readIntProperty(IoProvider.DEFAULT_SERVER_MAX_READBUFFER_SIZE_KEY);
   		defaultClientMaxWritebufferSize = readIntProperty(IoProvider.DEFAULT_CLIENT_MAX_WRITEBUFFER_SIZE_KEY);
   		defaultServerMaxWritebufferSize = readIntProperty(IoProvider.DEFAULT_SERVER_MAX_WRITEBUFFER_SIZE_KEY);
    	
   		
   		// ssl props 
   		sSLEngineServerEnabledProtocols = readStringArrayProperty(IoProvider.SSLENGINE_SERVER_ENABLED_PROTOCOLS_KEY, null);
   		sSLEngineClientEnabledProtocols = readStringArrayProperty(IoProvider.SSLENGINE_CLIENT_ENABLED_PROTOCOLS_KEY, null);
   		sSLEngineServerEnabledCipherSuites = readStringArrayProperty(IoProvider.SSLENGINE_SERVER_ENABLED_CIPHER_SUITES_KEY, null);
   		sSLEngineClientEnabledCipherSuites = readStringArrayProperty(IoProvider.SSLENGINE_CLIENT_ENABLED_CIPHER_SUITES_KEY, null);
   		sSLEngineWantClientAuth = readBooleanProperty(IoProvider.SSLENGINE_SERVER_WANT_CLIENT_AUTH_KEY, null);
   		sSLEngineNeedClientAuth = readBooleanProperty(IoProvider.SSLENGINE_SERVER_NEED_CLIENT_AUTH_KEY, null);
    	
   		// preallocation
   		clientReadBufferPreallocationOn = readBooleanProperty(IoProvider.CLIENT_READBUFFER_PREALLOCATION_ON_KEY, DEFAULT_READ_BUFFER_PREALLOCATION_ON);
    	if (clientReadBufferPreallocationOn) {
	    	clientReadBufferPreallocationsize = readIntProperty(IoProvider.CLIENT_READBUFFER_PREALLOCATION_SIZE_KEY, DEFAULT_READ_BUFFER_PREALLOCATION_SIZE);
	    	clientReadBufferMinsize = readIntProperty(IoProvider.CLIENT_READBUFFER_PREALLOCATION_MIN_SIZE_KEY, DEFAULT_READ_BUFFER_MIN_SIZE);
    	}

    	
    	serverReadBufferPreallocationOn = readBooleanProperty(IoProvider.SERVER_READBUFFER_PREALLOCATION_ON_KEY, DEFAULT_READ_BUFFER_PREALLOCATION_ON);
    	if (serverReadBufferPreallocationOn) {   		
    		serverReadBufferPreallocationsize = readIntProperty(IoProvider.SERVER_READBUFFER_PREALLOCATION_SIZE_KEY, DEFAULT_READ_BUFFER_PREALLOCATION_SIZE);
    		serverReadBufferMinsize = readIntProperty(IoProvider.SERVER_READBUFFER_PREALLOCATION_MIN_SIZE_KEY, DEFAULT_READ_BUFFER_MIN_SIZE);
    	}


    	// prepare id prefix
    	String base = null;
    	try {
    		base = InetAddress.getLocalHost().getCanonicalHostName();
    	} catch (Exception e) {
    		base = new UID().toString();
    	}

   		int random = 0;
   		Random rand = new Random();
   		do {
   			random = rand.nextInt();
   		} while (random < 0);
   		idPrefix = Integer.toHexString(base.hashCode()) + Long.toHexString(System.currentTimeMillis()) + Integer.toHexString(random);
   		
   		
		if (LOG.isLoggable(Level.FINE)) {
			StringBuilder sb = new StringBuilder();
			sb.append(IoProvider.class.getName() + " initialized (");
			
			// disptacher params
			sb.append("countDispatcher=" + countDispatcher + " ");
			sb.append("maxHandles=" + maxHandles + " ");
			sb.append("detachHandleOnNoOps=" + detachHandleOnNoOps + " ");
		 	
			
			// client params
			sb.append("client: directMemory=" + clientReadBufferUseDirect);
			sb.append(" preallocation=" + clientReadBufferPreallocationOn);
			if (clientReadBufferPreallocationOn) {
				sb.append(" preallocationSize=" + DataConverter.toFormatedBytesSize(clientReadBufferPreallocationsize));
				sb.append(" minBufferSize=" + DataConverter.toFormatedBytesSize(clientReadBufferMinsize));
			} 
			
			// server params 
			sb.append(" & server: directMemory=" + serverReadBufferUseDirect);
			sb.append(" preallocation=" + serverReadBufferPreallocationOn);
			if (serverReadBufferPreallocationOn) {
				sb.append(" preallocationSize=" + DataConverter.toFormatedBytesSize(serverReadBufferPreallocationsize));
				sb.append(" minBufferSize=" + DataConverter.toFormatedBytesSize(serverReadBufferMinsize));
			} 
			
			sb.append(")");
			LOG.fine(sb.toString());
		}
     }


	private AbstractMemoryManager sslMemoryManagerServer = null;
	private AbstractMemoryManager sslMemoryManagerClient = null;

	private final AtomicInteger nextId = new AtomicInteger();

	

	IoProvider() {
		if (serverReadBufferPreallocationOn) {
			sslMemoryManagerServer = IoSynchronizedMemoryManager.createPreallocatedMemoryManager(serverReadBufferPreallocationsize, serverReadBufferMinsize, serverReadBufferUseDirect);
		} else {
			sslMemoryManagerServer = IoSynchronizedMemoryManager.createNonPreallocatedMemoryManager(serverReadBufferUseDirect);
		}
		
		if (clientReadBufferPreallocationOn) {
			sslMemoryManagerClient = IoSynchronizedMemoryManager.createPreallocatedMemoryManager(clientReadBufferPreallocationsize, clientReadBufferMinsize, clientReadBufferUseDirect);
		} else {
			sslMemoryManagerClient = IoSynchronizedMemoryManager.createNonPreallocatedMemoryManager(clientReadBufferUseDirect);
		}

	}
	
	
	static String[] getSSLEngineServerEnabledCipherSuites() {
		return sSLEngineServerEnabledCipherSuites;
	}
	
	static String[] getSSLEngineClientEnabledCipherSuites() {
		return sSLEngineClientEnabledCipherSuites;
	}
	
	static String[] getSSLEngineClientEnabledProtocols() {
		return sSLEngineClientEnabledProtocols;
	}

	static String[] getSSLEngineServerEnabledProtocols() {
		return sSLEngineServerEnabledProtocols;
	}

	
	static Boolean getSSLEngineServerWantClientAuth() {
		return sSLEngineWantClientAuth;
	}
	
	
	static Boolean getSSLEngineServerNeedClientAuth() {
		return sSLEngineNeedClientAuth;
	}

	
	
	static Integer getDefaultClientMaxReadbufferSize() {
		return defaultClientMaxReadbufferSize;
	}
	
	static Integer getDefaultServerMaxReadbufferSize() {
		return defaultServerMaxReadbufferSize;
	}
	
	
	static Integer getDefaultClientMaxWritebufferSize() {
		return defaultClientMaxWritebufferSize;
	}
	
	static Integer getDefaultServerMaxWritebufferSize() {
		return defaultServerMaxWritebufferSize;
	}
	
	
    static Integer getCountDispatcher() {
    	return countDispatcher;
    }
    
    static Integer getMaxHandles() {
    	return maxHandles;
    }
    
    static boolean getDetachHandleOnNoOps() {
    	return detachHandleOnNoOps;
    }
    
    
    static boolean isBypassingWriteAllowed() {
    	return bypassingWriteAllowed;
    }

	/**
	 * Return the version of this implementation. It consists of any string assigned
	 * by the vendor of this implementation and does not have any particular syntax
	 * specified or expected by the Java runtime. It may be compared for equality
	 * with other package version strings used for this implementation
	 * by this vendor for this package.
	 *
	 * @return the version of the implementation
	 */
	public String getImplementationVersion() {
		return "";
	}


    /**
	 * {@inheritDoc}
	 */
	public IoAcceptor createAcceptor(IIoAcceptorCallback callback, InetSocketAddress address, int backlog, Map<String, Object> options) throws IOException {
	
		IoAcceptor acceptor = new IoAcceptor(callback, address, backlog);
		for (Entry<String, Object> entry : options.entrySet()) {
			acceptor.setOption(entry.getKey(), entry.getValue());
		}
	
		acceptor.setReceiveBufferIsDirect(serverReadBufferUseDirect);
		acceptor.setReceiveBufferPreallocationMode(serverReadBufferPreallocationOn);
		acceptor.setReceiveBufferPreallocatedMinSize(serverReadBufferMinsize);
		acceptor.setReceiveBufferPreallocationSize(serverReadBufferPreallocationsize);
		
		return acceptor;
	}


	/**
	 * {@inheritDoc}
	 */
	public IoAcceptor createAcceptor(IIoAcceptorCallback callback, InetSocketAddress address, int backlog, Map<String, Object> options, SSLContext sslContext, boolean sslOn) throws IOException {
		IoAcceptor acceptor = new IoAcceptor(callback, address, backlog, sslContext, sslOn);
		for (Entry<String, Object> entry : options.entrySet()) {
			acceptor.setOption(entry.getKey(), entry.getValue());
		}

		acceptor.setReceiveBufferIsDirect(serverReadBufferUseDirect);
		acceptor.setReceiveBufferPreallocationMode(serverReadBufferPreallocationOn);
		acceptor.setReceiveBufferPreallocatedMinSize(serverReadBufferMinsize);
		acceptor.setReceiveBufferPreallocationSize(serverReadBufferPreallocationsize);
		
		return acceptor;
	}


    /**
	 * {@inheritDoc}
	 */
	public IoChainableHandler createClientIoHandler(InetSocketAddress remoteAddress, int connectTimeoutMillis, Map<String ,Object> options) throws IOException {
		return createIoHandler(true, getGlobalClientDisptacherPool().nextDispatcher(0), openSocket(remoteAddress, options, connectTimeoutMillis), null, false);
	}


    /**
	 * {@inheritDoc}
	 */
    public IoChainableHandler createSSLClientIoHandler(InetSocketAddress remoteAddress, int connectTimeoutMillis, Map<String ,Object> options, SSLContext sslContext, boolean sslOn) throws IOException {
    	return createIoHandler(true, getGlobalClientDisptacherPool().nextDispatcher(0), openSocket(remoteAddress, options, connectTimeoutMillis), sslContext, sslOn);
    }



    /**
	 * {@inheritDoc}
	 */
    IoChainableHandler createIoHandler(boolean isClient, IoSocketDispatcher dispatcher, SocketChannel channel, SSLContext sslContext, boolean sslOn) throws IOException {

    	String connectionId = null;

    	if (isClient) {
    		connectionId = idPrefix + "C" + Integer.toHexString(nextId.incrementAndGet());
    	} else {
    		connectionId = idPrefix + "S" + Integer.toHexString(nextId.incrementAndGet());
    	}

		IoChainableHandler ioHandler = new IoSocketHandler(channel, dispatcher, connectionId);

		// ssl connection?
		if (sslContext != null) {

			AbstractMemoryManager mm = null;
			if (isClient) {
				mm = sslMemoryManagerClient;
			} else {
				mm = sslMemoryManagerServer;
			}

			if (sslOn) {
				ioHandler = new IoSSLHandler(ioHandler, sslContext, isClient, mm);
			} else {
				ioHandler = new IoActivateableSSLHandler(ioHandler, sslContext, isClient, mm);
			}
		}

		return ioHandler;
	}


    /**
     * {@inheritDoc}
     */
    public IoChainableHandler setWriteTransferRate(IoChainableHandler ioHandler, int bytesPerSecond) throws IOException {

    	// unlimited? remove throttling handler if exists
    	if (bytesPerSecond == UNLIMITED) {
    		IoThrottledWriteHandler delayWriter = (IoThrottledWriteHandler) getHandler((IoChainableHandler) ioHandler, IoThrottledWriteHandler.class);
    		if (delayWriter != null) {
    			
    			if (LOG.isLoggable(Level.FINE)) {
    				LOG.fine("write transfer rate is set to unlimited. flushing throttle write handler");
    			}
    			
    			delayWriter.hardFlush();
    			IoChainableHandler successor = delayWriter.getSuccessor();
    			return successor;
    		} else {
    			return ioHandler;
    		}

       	// ...no -> add throttling handler if not exists and set rate
    	} else {
			IoThrottledWriteHandler delayWriter = (IoThrottledWriteHandler) getHandler((IoChainableHandler) ioHandler, IoThrottledWriteHandler.class);
			if (delayWriter == null) {
				delayWriter = new IoThrottledWriteHandler((IoChainableHandler) ioHandler);
			}

			delayWriter.setWriteRateSec(bytesPerSecond);
			return delayWriter;
    	}
	}

    public boolean isSecuredModeActivateable(IoChainableHandler  ioHandler) {
    	IoActivateableSSLHandler activateableHandler = (IoActivateableSSLHandler) getHandler(ioHandler, IoActivateableSSLHandler.class);
		if (activateableHandler != null) {
			return true;
		} else {
			return false;
		}	
    }
  

	public boolean preStartSecuredMode(IoChainableHandler ioHandler) throws IOException {
		IoActivateableSSLHandler activateableHandler = (IoActivateableSSLHandler) getHandler(ioHandler, IoActivateableSSLHandler.class);
		if (activateableHandler != null) {
			return activateableHandler.preStartSecuredMode();
		} else {
			throw new IOException("connection is not SSL activatable (non IoActivateableHandler in chain)");
		}
	}

	public void startSecuredMode(IoChainableHandler ioHandler, ByteBuffer[] buffers) throws IOException {
		ioHandler.hardFlush();

		IoActivateableSSLHandler activateableHandler = (IoActivateableSSLHandler) getHandler((IoChainableHandler) ioHandler, IoActivateableSSLHandler.class);
		if (activateableHandler != null) {
			activateableHandler.startSecuredMode(buffers);
		} else {
			LOG.warning("connection is not SSL activatable (non IoActivateableHandler in chain");
		}
	}



	static Timer getTimer() {
		return TIMER;
	}

	
	static boolean isUseDirectReadBufferServer() {
		return serverReadBufferUseDirect;
	}


	static int getReadBufferPreallocationsizeServer() {
		return serverReadBufferPreallocationsize;
	}

	static int getReadBufferMinSizeServer() {
		return serverReadBufferMinsize;
	}


	static boolean isReadBufferPreallocationActivated() {
		return serverReadBufferPreallocationOn;
	}

	private static SocketChannel openSocket(InetSocketAddress remoteAddress, Map<String ,Object> options, int connectTimeoutMillis) throws IOException {
    	SocketChannel channel = SocketChannel.open();

		for (Entry<String, Object> entry : options.entrySet()) {
			setOption(channel.socket(), entry.getKey(), entry.getValue());
		}


		try {
			channel.socket().connect(remoteAddress, connectTimeoutMillis);
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by bindung socket to remote address " + remoteAddress + " " + ioe.toString());
			}
			throw ioe;
		}

		return channel;
    }




    /**
     * set a option
     *
     * @param socket    the socket
     * @param name      the option name
     * @param value     the option value
     * @throws IOException if an exception occurs
     */
	static void setOption(Socket socket, String name, Object value) throws IOException {

		if (name.equals(SO_SNDBUF)) {
			socket.setSendBufferSize(asInt(value));

		} else if (name.equals(SO_REUSEADDR)) {
			socket.setReuseAddress(asBoolean(value));

		} else if (name.equals(SO_TIMEOUT)) {
			socket.setSoTimeout(asInt(value));

		} else if (name.equals(SO_RCVBUF)) {
			socket.setReceiveBufferSize(asInt(value));

		} else if (name.equals(SO_KEEPALIVE)) {
			socket.setKeepAlive(asBoolean(value));

		} else if (name.equals(SO_LINGER)) {
			try {
				socket.setSoLinger(true, asInt(value));
			} catch (ClassCastException cce) {
				socket.setSoLinger(Boolean.FALSE, 0);
			}

		} else if (name.equals(TCP_NODELAY)) {
			socket.setTcpNoDelay(asBoolean(value));


		} else {
			LOG.warning("option " + name + " is not supported");
		}
	}


	/**
	 * get a option
	 *
	 * @param socket    the socket
	 * @param name      the option name
	 * @return the option value
     * @throws IOException if an exception occurs
	 */
	static Object getOption(Socket socket, String name) throws IOException {

		if (name.equals(SO_SNDBUF)) {
			return socket.getSendBufferSize();

		} else if (name.equals(SO_REUSEADDR)) {
			return socket.getReuseAddress();

		} else if (name.equals(SO_RCVBUF)) {
			return socket.getReceiveBufferSize();

		} else if (name.equals(SO_KEEPALIVE)) {
			return socket.getKeepAlive();

		} else if (name.equals(SO_TIMEOUT)) {
			return socket.getSoTimeout();

		} else if (name.equals(TCP_NODELAY)) {
			return socket.getTcpNoDelay();

		} else if (name.equals(SO_LINGER)) {
			return socket.getSoLinger();


		} else {
			LOG.warning("option " + name + " is not supported");
			return null;
		}
	}



	private static int asInt(Object obj) {
		if (obj instanceof Integer) {
			return (Integer) obj;
		}

		return Integer.parseInt(obj.toString());
	}

	private static boolean asBoolean(Object obj) {
		if (obj instanceof Boolean) {
			return (Boolean) obj;
		}

		return Boolean.parseBoolean(obj.toString());
	}



	@SuppressWarnings("unchecked")
	private IoChainableHandler getHandler(IoChainableHandler head, Class clazz) {
		IoChainableHandler handler = head;
		do {
			if (handler.getClass() == clazz) {
				return handler;
			}

			handler = handler.getSuccessor();
		} while (handler != null);

		return null;
	}


	private static synchronized IoSocketDispatcherPool getGlobalClientDisptacherPool() {
		if (globalClientDispatcherPool == null) {
			globalClientDispatcherPool = new IoSocketDispatcherPool("Glb", 1);
			
			globalClientDispatcherPool.setReceiveBufferIsDirect(clientReadBufferUseDirect);
			globalClientDispatcherPool.setReceiveBufferPreallocationMode(clientReadBufferPreallocationOn);
			globalClientDispatcherPool.setReceiveBufferPreallocatedMinSize(clientReadBufferMinsize);
			globalClientDispatcherPool.setReceiveBufferPreallocationSize(clientReadBufferPreallocationsize);
		}
		
		return globalClientDispatcherPool;
	}
	
	
	public ObjectName registerMBeans(Server server, IoAcceptor acceptor, String domain, String address) throws JMException {
		address = address.replace(":", "_");

		IntrospectionBasedDynamicMBean serverMBean = new IntrospectionBasedDynamicMBean(new MBeanAdapter(server, (IoAcceptor) acceptor));

		IoSocketDispatcherPool dispatcherPool = ((IoAcceptor) acceptor).getDispatcherPool();
		
		DispatcherPoolListener dispatcherPoolListener = new DispatcherPoolListener(domain, address);
		dispatcherPool.addListener(dispatcherPoolListener);

		for (IoSocketDispatcher dispatcher : dispatcherPool.getDispatchers()) {
			try {
				dispatcherPoolListener.onDispatcherAdded(dispatcher);
			} catch(Exception ignore) { }
		}

		server.addListener(new ServerListener());

		
		ObjectName serverObjectName = new ObjectName(domain + ".server." + address + ":type=xServer");
		ManagementFactory.getPlatformMBeanServer().registerMBean(serverMBean, serverObjectName);
		
		return serverObjectName;
	}

	
	
	private static Integer readIntProperty(String key) { 
		try {
			String property = readProperty(key);
			if (property != null) {
				return Integer.parseInt(property);
			} else {
				return null;
			}
		} catch (Exception e) {
			LOG.warning("invalid value for system property " + key + ": " + 
				    System.getProperty(key) + " " + e.toString());
			return null;
		}
	}

	private static Integer readIntProperty(String key, Integer dflt) { 
		try {
			String property = readProperty(key);
			if (property != null) {
				return Integer.parseInt(property);
			} else {
				return dflt;
			}
		} catch (Exception e) {
			LOG.warning("invalid value for system property " + key + ": "
					+ System.getProperty(key) + " (valid is int)"
					+ " using " + dflt);
			return null;
		}
	}
	
	
	private static Boolean readBooleanProperty(String key, String dflt) { 
		try {
			String property = readProperty(key);
			if (property != null) {
				return Boolean.parseBoolean(property);
			} else {
				if (dflt == null) {
					return null;
				} else {
					return Boolean.parseBoolean(dflt);
				}
			}
		} catch (Exception e) {
			LOG.warning("invalid value for system property " + key + ": "
					+ System.getProperty(key) + " (valid is true|false)"
					+ " using " + dflt);
			if (dflt != null) {
				return Boolean.parseBoolean(dflt);
			} else {
				return null;
			}
		}
	}
	
	
	private static String[] readStringArrayProperty(String key, String[] dflt) { 
		try {
			String property = readProperty(key);
			if (property != null) {
				String[] props = property.split(",");
				String[] result = new String[props.length];
				for (int i = 0; i < props.length; i++) {
					result[i] = props[i].trim(); 
				}
				return result;
			} else {
				return dflt;
			}
		} catch (Exception e) {
			LOG.warning("invalid value for system property " + key + ": "
					+ System.getProperty(key) + " "
					+ " using " + dflt);
			return dflt;
		}
	}
	
	private static String readProperty(String key) { 
		try {
			return System.getProperty(key);
		} catch (Exception e) {
			LOG.warning("invalid value for system property " + key + " " + e.toString());
			return null;
		}
	}
		
	
	
	

 
	private static final class MBeanAdapter {

		private Server server = null;
		private IoAcceptor acceptor = null;

		public MBeanAdapter(Server server, IoAcceptor acceptor) {
			this.server = server;
			this.acceptor = acceptor;
		}

		public long getNumberOfConnectionTimeouts() {
			return server.getNumberOfConnectionTimeouts();
		}

		public long getNumberOfIdleTimeouts() {
			return server.getNumberOfIdleTimeouts();
		}
		
		public String getImplementationVersion() {
			return server.getImplementationVersion();
		}

		public String getImplementationDate() {
			return server.getImplementationDate();
		}

		
		public String getLocalHost() {
			return acceptor.getLocalAddress().getCanonicalHostName();
		}

		public int getLocalPort() {
			return acceptor.getLocalPort();
		}
		
		public int getDispatcherPoolSize() {
			return acceptor.getDispatcherSize();
		}
		
		public void setDispatcherPoolSize(int size) {
			acceptor.setDispatcherSize(size);
		}
		
		public List<String> getActiveConnectionInfos() {
			return server.getOpenConnectionInfos();
		}
		
		public boolean getReceiveBufferIsDirect() {
			return acceptor.getReceiveBufferIsDirect();
		}
		
		public void setReceiveBufferIsDirect(boolean isDirect) {
			acceptor.setReceiveBufferIsDirect(isDirect);
		}
		
		public Integer getReceiveBufferPreallocatedMinSize() {
		   	if (acceptor.isReceiveBufferPreallocationMode()) {
		   		return acceptor.getReceiveBufferPreallocatedMinSize();
	    	} else {
	    		return null;
	    	} 
		}
		
		public void setReceiveBufferPreallocatedMinSize(Integer minSize) {
			acceptor.setReceiveBufferPreallocatedMinSize(minSize);
		}
		
		public boolean getReceiveBufferPreallocationMode() {
			return acceptor.isReceiveBufferPreallocationMode();
		}
		
		public void setReceiveBufferPreallocationMode(boolean mode) {
			acceptor.setReceiveBufferPreallocationMode(mode);
		}
		
		public Integer getReceiveBufferPreallocationSize() {
		   	if (acceptor.isReceiveBufferPreallocationMode()) {
		   		return acceptor.getReceiveBufferPreallocationSize();
		   	} else {
		   		return null;
		   	}
		}
		
		public void setReceiveBufferPreallocationSize(Integer size) {
			acceptor.setReceiveBufferPreallocationSize(size);
		}
		
		public long getConnectionTimeoutMillis() {
			return server.getConnectionTimeoutMillis();
		}
		
		public void setConnectionTimeoutMillis(int timeoutMillis) {
			server.setConnectionTimeoutMillis(timeoutMillis);
		}
		
		public long getIdleTimeoutMillis() {
			return server.getIdleTimeoutMillis();
		}
		
		public void setIdleTimeoutMillis(int timeoutMillis) {
			server.setIdleTimeoutMillis(timeoutMillis);
		}
		
		public long getReceiveRateBytesPerSec() {
			return acceptor.getReceiveRateBytesPerSec();
		}
		
		public long getSendRateBytesPerSec() {
			return acceptor.getSendRateBytesPerSec();
		}
		
		public double getAcceptedRateCountPerSec() {
			return acceptor.getAcceptedRateCountPerSec();
		}
	}

	
	
	
	private static final class ServerListener implements IServerListener {

		public void onInit() {

		}

		public void onDestroy() {

		}
	}



	private static final class DispatcherPoolListener implements IIoDispatcherPoolListener {

		private String domain = null;
		private String address = null;

		DispatcherPoolListener(String domain, String address) {
			this.domain = domain;
			this.address = address;
		}



		public void onDispatcherAdded(IoSocketDispatcher dispatcher) {

			try {
				ObjectName objectName = new ObjectName(domain + ".server." + address + ":type=xDispatcher,name=" + dispatcher.getId());
				ManagementFactory.getPlatformMBeanServer().registerMBean(new IntrospectionBasedDynamicMBean(dispatcher), objectName);
			} catch (Exception e) { 
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by adding mbean for new dispatcher: " + e.toString());
				}
			}
		}


		public void onDispatcherRemoved(IoSocketDispatcher dispatcher) {
			try {
				ObjectName objectName = new ObjectName(domain + ".server." + address + ":type=xDispatcher,name=" + dispatcher.getId());
				ManagementFactory.getPlatformMBeanServer().unregisterMBean(objectName);
			} catch (Exception e) { 
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by removing mbean of dispatcher: " + e.toString());
				}
			}
		}
	}
}