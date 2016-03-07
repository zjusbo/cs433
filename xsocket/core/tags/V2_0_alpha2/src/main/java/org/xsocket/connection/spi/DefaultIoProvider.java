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
package org.xsocket.connection.spi;

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
import org.xsocket.Dispatcher;
import org.xsocket.IDispatcher;
import org.xsocket.IntrospectionBasedDynamicMBean;
import org.xsocket.connection.IServerListener;
import org.xsocket.connection.Server;




/**
 * Server and Client IoProvider<br><br>
 *
 * This class is a default implementation of the {@link org.xsocket.connection.spi} and shouldn`t be used
 * outside this context. <br>
 * The readbuffer preallocation size and direct/non-direct mode should be set by System.properties. Please
 * note that current vm implementations (Juli/2007) could have problems by managing direct buffers. In this
 * case non-direct buffer should be used.
 * <pre>
 * 
 * ...
 * // example configuration to use non-direct memory 
 * System.setProperty("org.xsocket.connection.server.readbuffer.usedirect", "false");
 * 
 * 
 * // example configuration to switch off preallocating (params like preallocation.size or preallocation.minsize will be ignored) 
 * System.setProperty("org.xsocket.connection.server.readbuffer.preallocation.on", "false");
 * 
 * 
 * // example configuration to determine the preallocation buffer
 * System.setProperty("org.xsocket.connection.server.readbuffer.preallocation.on", "true");
 * System.setProperty("org.xsocket.connection.server.readbuffer.preallocation.size", "1024");
 * System.setProperty("org.xsocket.connection.server.readbuffer.preallocation.minsize", "8");
 * 
 * </pre>
 *
 * @author grro@xsocket.org
 */
public final class DefaultIoProvider implements IClientIoProvider, IServerIoProvider {

	private static final Logger LOG = Logger.getLogger(DefaultIoProvider.class.getName());


	private static final Timer TIMER = new Timer("xIoTimer", true);
	private static IoSocketDispatcher globalDispatcher = null;

	
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
	public static final String CLIENT_READBUFFER_PREALLOCATION_MIN_SIZE_KEY = "org.xsocket.connection.client.readbuffer.preallocation.minSize";
	
	public static final String SERVER_READBUFFER_PREALLOCATION_ON_KEY       = "org.xsocket.connection.server.readbuffer.preallocation.on";
	public static final String SERVER_READBUFFER_PREALLOCATION_SIZE_KEY     = "org.xsocket.connection.server.readbuffer.preallocation.size";
	public static final String SERVER_READBUFFER_PREALLOCATION_MIN_SIZE_KEY = "org.xsocket.connection.server.readbuffer.preallocation.minSize";
	
	
    
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
    	// problems by gc direct buffers. For this reason the NIO framework
    	// mina decided to use non-direct allocated buffer by default with V2
    	//
    	// links
        // * [Java bugdatabase] http://bugs.sun.com/bugdatabase/view_bug.do;jsessionid=94d5403110224b692e5354bd87a92:WuuT?bug_id=6210541
    	// * [forum thread]     http://forums.java.net/jive/thread.jspa?messageID=223706&tstart=0
    	// * [mina]             https://issues.apache.org/jira/browse/DIRMINA-391
    	//
    	////////////////////////////////////////////////


    	
    	// direct buffer?
    	try {
    		clientReadBufferUseDirect = Boolean.valueOf(System.getProperty(DefaultIoProvider.CLIENT_READBUFFER_USE_DIRECT_KEY, DEFAULT_USE_DIRECT_BUFFER));
    	} catch (Exception e) {
    		LOG.warning("invalid value for system property " + DefaultIoProvider.CLIENT_READBUFFER_USE_DIRECT_KEY + ": "
    				+ System.getProperty(DefaultIoProvider.CLIENT_READBUFFER_USE_DIRECT_KEY) + " (valid is true|false)"
    				+ " using direct buffer");
    		clientReadBufferUseDirect = Boolean.TRUE;
    	}
    	
   		try {
   			serverReadBufferUseDirect = Boolean.valueOf(System.getProperty(DefaultIoProvider.SERVER_READBUFFER_USE_DIRECT_KEY, DEFAULT_USE_DIRECT_BUFFER));
   		} catch (Exception e) {
   			LOG.warning("invalid value for system property " + DefaultIoProvider.SERVER_READBUFFER_USE_DIRECT_KEY + ": "
   					+ System.getProperty(DefaultIoProvider.SERVER_READBUFFER_USE_DIRECT_KEY) + " (valid is true|false)"
   					+ " using direct buffer");
   			serverReadBufferUseDirect = Boolean.TRUE;
    	}

   		
   		
   		// preallocation
   		try {
   			clientReadBufferPreallocationOn = Boolean.valueOf(System.getProperty(DefaultIoProvider.CLIENT_READBUFFER_PREALLOCATION_ON_KEY, DEFAULT_READ_BUFFER_PREALLOCATION_ON));
    	} catch (Exception e) {
    		LOG.warning("invalid value for system property " + DefaultIoProvider.CLIENT_READBUFFER_PREALLOCATION_ON_KEY + ": "
    				+ System.getProperty(DefaultIoProvider.CLIENT_READBUFFER_PREALLOCATION_ON_KEY)
    				+ " using preallocation mode");
    		clientReadBufferPreallocationOn = Boolean.TRUE;
    	}
   		
    	// is activated
    	if (clientReadBufferPreallocationOn) {   		
	    	try {
	    		clientReadBufferPreallocationsize = Integer.parseInt(System.getProperty(DefaultIoProvider.CLIENT_READBUFFER_PREALLOCATION_SIZE_KEY, Integer.toString(DEFAULT_READ_BUFFER_PREALLOCATION_SIZE)));
	    	} catch (Exception e) {
	    		LOG.warning("invalid value for system property " + DefaultIoProvider.CLIENT_READBUFFER_PREALLOCATION_SIZE_KEY + ": "
	    				+ System.getProperty(DefaultIoProvider.CLIENT_READBUFFER_PREALLOCATION_SIZE_KEY)
	    				+ " using default preallocation size " + DEFAULT_READ_BUFFER_PREALLOCATION_SIZE);
	    		clientReadBufferPreallocationsize = DEFAULT_READ_BUFFER_PREALLOCATION_SIZE;
	    	}
	
	    	try {
	    		clientReadBufferMinsize = Integer.parseInt(System.getProperty(DefaultIoProvider.CLIENT_READBUFFER_PREALLOCATION_MIN_SIZE_KEY, Integer.toString(DEFAULT_READ_BUFFER_MIN_SIZE)));
	    	} catch (Exception e) {
	    		LOG.warning("invalid value for system property " + DefaultIoProvider.CLIENT_READBUFFER_PREALLOCATION_MIN_SIZE_KEY + ": "
	    				+ System.getProperty(DefaultIoProvider.CLIENT_READBUFFER_PREALLOCATION_MIN_SIZE_KEY)
	    				+ " using default min size " + DEFAULT_READ_BUFFER_MIN_SIZE);
	    		clientReadBufferMinsize = DEFAULT_READ_BUFFER_MIN_SIZE;
	    	}
    	}

    	
    	try {
   			serverReadBufferPreallocationOn = Boolean.valueOf(System.getProperty(DefaultIoProvider.SERVER_READBUFFER_PREALLOCATION_ON_KEY, DEFAULT_READ_BUFFER_PREALLOCATION_ON));
    	} catch (Exception e) {
    		LOG.warning("invalid value for system property " + DefaultIoProvider.SERVER_READBUFFER_PREALLOCATION_ON_KEY + ": "
    				+ System.getProperty(DefaultIoProvider.SERVER_READBUFFER_PREALLOCATION_ON_KEY)
    				+ " using preallocation mode");
    		serverReadBufferPreallocationOn = Boolean.TRUE;
    	}
   		
    	// is activated
    	if (serverReadBufferPreallocationOn) {   		
	    	try {
	    		serverReadBufferPreallocationsize = Integer.parseInt(System.getProperty(DefaultIoProvider.SERVER_READBUFFER_PREALLOCATION_SIZE_KEY, Integer.toString(DEFAULT_READ_BUFFER_PREALLOCATION_SIZE)));
	    	} catch (Exception e) {
	    		LOG.warning("invalid value for system property " + DefaultIoProvider.SERVER_READBUFFER_PREALLOCATION_SIZE_KEY + ": "
	    				+ System.getProperty(DefaultIoProvider.SERVER_READBUFFER_PREALLOCATION_SIZE_KEY)
	    				+ " using default preallocation size " + DEFAULT_READ_BUFFER_PREALLOCATION_SIZE);
	    		serverReadBufferPreallocationsize = DEFAULT_READ_BUFFER_PREALLOCATION_SIZE;
	    	}
	
	    	try {
	    		serverReadBufferMinsize = Integer.parseInt(System.getProperty(DefaultIoProvider.SERVER_READBUFFER_PREALLOCATION_MIN_SIZE_KEY, Integer.toString(DEFAULT_READ_BUFFER_MIN_SIZE)));
	    	} catch (Exception e) {
	    		LOG.warning("invalid value for system property " + DefaultIoProvider.SERVER_READBUFFER_PREALLOCATION_MIN_SIZE_KEY + ": "
	    				+ System.getProperty(DefaultIoProvider.SERVER_READBUFFER_PREALLOCATION_MIN_SIZE_KEY)
	    				+ " using default min size " + DEFAULT_READ_BUFFER_MIN_SIZE);
	    		serverReadBufferMinsize = DEFAULT_READ_BUFFER_MIN_SIZE;
	    	}
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
   		idPrefix = Integer.toHexString(base.hashCode()) + "." + Long.toHexString(System.currentTimeMillis()) + "." + Integer.toHexString(random);
   		
   		
		if (LOG.isLoggable(Level.FINE)) {
			StringBuilder sb = new StringBuilder();
			sb.append(DefaultIoProvider.class.getName() + " initialized (");
			
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


	private IMemoryManager sslMemoryManagerServer = null;
	private IMemoryManager sslMemoryManagerClient = null;

	private final AtomicInteger nextId = new AtomicInteger();


	public DefaultIoProvider() {
		if (serverReadBufferPreallocationOn) {
			sslMemoryManagerServer = SynchronizedMemoryManager.createPreallocatedMemoryManager(serverReadBufferPreallocationsize, serverReadBufferMinsize, serverReadBufferUseDirect);
		} else {
			sslMemoryManagerServer = SynchronizedMemoryManager.createNonPreallocatedMemoryManager(serverReadBufferUseDirect);
		}
		
		if (clientReadBufferPreallocationOn) {
			sslMemoryManagerClient = SynchronizedMemoryManager.createPreallocatedMemoryManager(clientReadBufferPreallocationsize, clientReadBufferMinsize, clientReadBufferUseDirect);
		} else {
			sslMemoryManagerClient = SynchronizedMemoryManager.createNonPreallocatedMemoryManager(clientReadBufferUseDirect);
		}

	}
	

	/**
     * returns if current thread is  dispatcher thread
     * @return true, if current thread is a dispatcher thread
     */
    public static boolean isDispatcherThread() {
        return IoSocketDispatcher.isDispatcherThread();
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
	public IAcceptor createAcceptor(IAcceptorCallback callback, InetSocketAddress address, int backlog, Map<String, Object> options) throws IOException {
		Acceptor acceptor = new Acceptor(callback, address, backlog);
		for (Entry<String, Object> entry : options.entrySet()) {
			acceptor.setOption(entry.getKey(), entry.getValue());
		}

		return acceptor;
	}


	/**
	 * {@inheritDoc}
	 */
	public IAcceptor create(IAcceptorCallback callback, InetSocketAddress address, int backlog, Map<String, Object> options, SSLContext sslContext, boolean sslOn) throws IOException {
		Acceptor acceptor = new Acceptor(callback, address, backlog, sslContext, sslOn);
		for (Entry<String, Object> entry : options.entrySet()) {
			acceptor.setOption(entry.getKey(), entry.getValue());
		}

		return acceptor;
	}


    /**
	 * {@inheritDoc}
	 */
	public IIoHandler createClientIoHandler(InetSocketAddress remoteAddress, int connectTimeoutMillis, Map<String ,Object> options) throws IOException {
		return createIoHandler(true, getClientDispatcher(), openSocket(remoteAddress, options, connectTimeoutMillis), null, false);
	}


    /**
	 * {@inheritDoc}
	 */
    public IIoHandler createSSLClientIoHandler(InetSocketAddress remoteAddress, int connectTimeoutMillis, Map<String ,Object> options, SSLContext sslContext, boolean sslOn) throws IOException {
    	return createIoHandler(true, getClientDispatcher(), openSocket(remoteAddress, options, connectTimeoutMillis), sslContext, sslOn);
    }



    /**
	 * {@inheritDoc}
	 */
    IIoHandler createIoHandler(boolean isClient, IoSocketDispatcher dispatcher, SocketChannel channel, SSLContext sslContext, boolean sslOn) throws IOException {

    	String connectionId = null;

    	if (isClient) {
    		connectionId = idPrefix + ".c." + nextId.incrementAndGet();
    	} else {
    		connectionId = idPrefix + ".s." + nextId.incrementAndGet();
    	}

		ChainableIoHandler ioHandler = new IoSocketHandler(channel, dispatcher, connectionId);

		// ssl connection?
		if (sslContext != null) {

			IMemoryManager mm = null;
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
    public IIoHandler setWriteTransferRate(IIoHandler ioHandler, int bytesPerSecond) throws IOException {

    	// unlimited? remove throttling handler if exists
    	if (bytesPerSecond == UNLIMITED) {
    		IoThrottledWriteHandler delayWriter = (IoThrottledWriteHandler) getHandler((ChainableIoHandler) ioHandler, IoThrottledWriteHandler.class);
    		if (delayWriter != null) {
    			delayWriter.flushOutgoing();
    			ChainableIoHandler successor = delayWriter.getSuccessor();
    			return successor;
    		} else {
    			return ioHandler;
    		}

       	// ...no -> add throttling handler if not exists and set rate
    	} else {
			IoThrottledWriteHandler delayWriter = (IoThrottledWriteHandler) getHandler((ChainableIoHandler) ioHandler, IoThrottledWriteHandler.class);
			if (delayWriter == null) {
				delayWriter = new IoThrottledWriteHandler((ChainableIoHandler) ioHandler);
			}

			delayWriter.setWriteRateSec(bytesPerSecond);
			return delayWriter;
    	}
	}



	public boolean preStartSecuredMode(IIoHandler ioHandler) throws IOException {
		try {
			IoActivateableSSLHandler activateableHandler = (IoActivateableSSLHandler) getHandler((ChainableIoHandler) ioHandler, IoActivateableSSLHandler.class);
			if (activateableHandler != null) {
				return activateableHandler.preStartSecuredMode();
			} else {
				throw new IOException("connection is not SSL activatable (non IoActivateableHandler in chain)");
			}
		} catch (ClassCastException cce) {
			throw new IOException("only ioHandler of tpye " + ChainableIoHandler.class.getName() + " are supported");
		}
	}

	public void startSecuredMode(IIoHandler ioHandler, ByteBuffer[] buffers) throws IOException {
		try {
			((ChainableIoHandler) ioHandler).flushOutgoing();
		} catch (ClassCastException cce) {
			throw new IOException("only ioHandler of tpye " + ChainableIoHandler.class.getName() + " are supported");
		}

		IoActivateableSSLHandler activateableHandler = (IoActivateableSSLHandler) getHandler((ChainableIoHandler) ioHandler, IoActivateableSSLHandler.class);
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

		if (name.equals(IClientIoProvider.SO_SNDBUF)) {
			socket.setSendBufferSize(asInt(value));

		} else if (name.equals(IClientIoProvider.SO_REUSEADDR)) {
			socket.setReuseAddress(asBoolean(value));

		} else if (name.equals(IClientIoProvider.SO_TIMEOUT)) {
			socket.setSoTimeout(asInt(value));

		} else if (name.equals(IClientIoProvider.SO_RCVBUF)) {
			socket.setReceiveBufferSize(asInt(value));

		} else if (name.equals(IClientIoProvider.SO_KEEPALIVE)) {
			socket.setKeepAlive(asBoolean(value));

		} else if (name.equals(IClientIoProvider.SO_LINGER)) {
			try {
				socket.setSoLinger(true, asInt(value));
			} catch (ClassCastException cce) {
				socket.setSoLinger(Boolean.FALSE, 0);
			}

		} else if (name.equals(IClientIoProvider.TCP_NODELAY)) {
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

		if (name.equals(IClientIoProvider.SO_SNDBUF)) {
			return socket.getSendBufferSize();

		} else if (name.equals(IClientIoProvider.SO_REUSEADDR)) {
			return socket.getReuseAddress();

		} else if (name.equals(IClientIoProvider.SO_RCVBUF)) {
			return socket.getReceiveBufferSize();

		} else if (name.equals(IClientIoProvider.SO_KEEPALIVE)) {
			return socket.getKeepAlive();

		} else if (name.equals(IClientIoProvider.SO_TIMEOUT)) {
			return socket.getSoTimeout();

		} else if (name.equals(IClientIoProvider.TCP_NODELAY)) {
			return socket.getTcpNoDelay();

		} else if (name.equals(IClientIoProvider.SO_LINGER)) {
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
	private ChainableIoHandler getHandler(ChainableIoHandler head, Class clazz) {
		ChainableIoHandler handler = head;
		do {
			if (handler.getClass() == clazz) {
				return handler;
			}

			handler = handler.getSuccessor();
		} while (handler != null);

		return null;
	}


	private static synchronized IoSocketDispatcher getClientDispatcher() {
		if (globalDispatcher == null) {
			
			UnsynchronizedMemoryManager memoryManager = null;
			if (clientReadBufferPreallocationOn) {
				memoryManager = UnsynchronizedMemoryManager.createPreallocatedMemoryManager(clientReadBufferPreallocationsize, clientReadBufferMinsize, clientReadBufferUseDirect);
			} else {
				memoryManager = UnsynchronizedMemoryManager.createNonPreallocatedMemoryManager(clientReadBufferUseDirect);
			}
			
			globalDispatcher = new IoSocketDispatcher(memoryManager);
			Thread t = new Thread(globalDispatcher);
			t.setName(IoSocketDispatcher.DISPATCHER_PREFIX + "#" + "CLIENT");
			t.setDaemon(true);
			t.start();

			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("client dispatcher created");
			}
		}
		return globalDispatcher;
	}
	
	
	@SuppressWarnings("unchecked")
	public ObjectName registerMBeans(Server server, IAcceptor acceptor, String domain, String address) throws JMException {
		address = address.replace(":", "_");
		
		if (acceptor instanceof Acceptor) {

			IntrospectionBasedDynamicMBean serverMBean = new IntrospectionBasedDynamicMBean(new MBeanAdapter(server, (Acceptor) acceptor));

			DispatcherPoolListener dispatcherPoolListener = new DispatcherPoolListener(domain, address);
			((Acceptor) acceptor).addListener(dispatcherPoolListener);

			for (IDispatcher dispatcher : ((Acceptor) acceptor).getDispatchers()) {
				try {
					dispatcherPoolListener.onDispatcherAdded((Dispatcher) dispatcher);
				} catch(Exception ignore) { }
			}

			server.addListener(new ServerListener());

			
			ObjectName serverObjectName = new ObjectName(domain + ":type=xServer,name=" + address);
			ManagementFactory.getPlatformMBeanServer().registerMBean(serverMBean, serverObjectName);
			
			return serverObjectName;

		} else {
			throw new JMException("only accpetor of instance " + Acceptor.class.getName() + " is supported, not " + acceptor.getClass().getName());
		}
	}
	
	
	
	
	

 
	private static final class MBeanAdapter {

		private Server server = null;
		private Acceptor acceptor = null;

		public MBeanAdapter(Server server, Acceptor acceptor) {
			this.server = server;
			this.acceptor = acceptor;
		}

		public long getNumberOfConnectionTimeouts() {
			return acceptor.getNumberOfConnectionTimeouts();
		}

		public long getNumberOfIdleTimeouts() {
			return acceptor.getNumberOfIdleTimeouts();
		}
		
		public String getVersion() {
			return server.getVersion();
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
			return acceptor.getOpenConntionInfos();
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
		
		public int getConnectionTimeoutSec() {
			return server.getConnectionTimeoutSec();
		}
		
		public void setConnectionTimeoutSec(int timeoutSec) {
			server.setConnectionTimeoutSec(timeoutSec);
		}
		
		public int getIdleTimeoutSec() {
			return server.getIdleTimeoutSec();
		}
		
		public void setIdleTimeoutSec(int timeoutSec) {
			server.setIdleTimeoutSec(timeoutSec);
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



	private static final class DispatcherPoolListener implements IAcceptorListener {

		private String domain = null;
		private String address = null;

		DispatcherPoolListener(String domain, String address) {
			this.domain = domain;
			this.address = address;
		}



		@SuppressWarnings("unchecked")
		public void onDispatcherAdded(IDispatcher dispatcher) {

			try {
				ObjectName objectName = new ObjectName(domain +":type=xDispatcher,name=" + address + "." + dispatcher.hashCode());
				ManagementFactory.getPlatformMBeanServer().registerMBean(new IntrospectionBasedDynamicMBean(dispatcher), objectName);
			} catch (Exception e) { 
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by adding mbean for new dispatcher: " + e.toString());
				}
			}
		}


		@SuppressWarnings("unchecked")
		public void onDispatcherRemoved(IDispatcher dispatcher) {
			try {
				ObjectName objectName = new ObjectName(domain +":type=xDispatcher,name=" + address + "." + dispatcher.hashCode());
				ManagementFactory.getPlatformMBeanServer().unregisterMBean(objectName);
			} catch (Exception e) { 
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by removing mbean of dispatcher: " + e.toString());
				}
			}
		}
	}
}