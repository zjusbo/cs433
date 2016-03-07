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
import java.net.SocketTimeoutException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.xsocket.DataConverter;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.TimeoutManager.TimeoutMgmHandle;



/**
 * Implementation of the <code>INonBlockingConnection</code> interface. <br><br>
 *
 * Depending on the constructor parameter waitForConnect a newly created connection is in the open state. 
 * Write or read methods can be called immediately. 
 * Absence of the waitForConnect parameter is equals to waitForConnect==true.  
 *
 * <br><br><b>The methods of this class are not thread-safe.</b>
 *
 * @author grro@xsocket.org
 */
public final class NonBlockingConnection extends AbstractNonBlockingStream implements INonBlockingConnection {

	private static final Logger LOG = Logger.getLogger(NonBlockingConnection.class.getName());

	private static final SimpleDateFormat DF = new SimpleDateFormat("HH:mm:ss,S");

	public static final String SEND_TIMEOUT_KEY  = "org.xsocket.stream.send_timeout_millis";
	public static final long DEFAULT_SEND_TIMEOUT_MILLIS = 60L * 1000L;

	private static Executor defaultWorkerPool = null;



	private static long sendTimeoutMillis = DEFAULT_SEND_TIMEOUT_MILLIS;
	static {
		try {
			sendTimeoutMillis = Long.valueOf(System.getProperty(SEND_TIMEOUT_KEY, Long.toString(DEFAULT_SEND_TIMEOUT_MILLIS)));
    	} catch (Exception e) {
    		LOG.warning("invalid value for system property " + SEND_TIMEOUT_KEY + ": "
    				+ System.getProperty(SEND_TIMEOUT_KEY) + " (valid is a int value)"
    				+ " using default");
    		sendTimeoutMillis = DEFAULT_SEND_TIMEOUT_MILLIS;
    	}

    	if (LOG.isLoggable(Level.FINE)) {
    		LOG.fine("non blocking connection send time out set with " + DataConverter.toFormatedDuration(sendTimeoutMillis));
    	}
	}


	// execution
	private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
	

	// flags
	private AtomicBoolean isOpen = new AtomicBoolean(true);
	private AtomicBoolean isConnected = new AtomicBoolean(false);
	private AtomicBoolean isSuspended = new AtomicBoolean(false);
	

	
	// connection manager
	private static final TimeoutManager DEFAULT_CONNECTION_MANAGER = new TimeoutManager();
	private TimeoutMgmHandle timeoutMgmHandle = null;
	

	// io handler
	private final IoHandlerCallback ioHandlerCallback = new IoHandlerCallback();
	private IoChainableHandler ioHandler = null;


	// app handler
	private final AtomicReference<IHandler> appHandler = new AtomicReference<IHandler>(null);


	// execution
	private Executor workerpool = null;
	

	// write thread handling
	private final Object writeSynchronizer = new Object();

	// write transfer rate
	private int bytesPerSecond = UNLIMITED;

	// sync flush support
	private IOException writeException = null;
	private final List<ByteBuffer> pendingWriteConfirmations = new ArrayList<ByteBuffer>();


	// timeout support
	private long idleTimeoutMillis = IConnection.MAX_TIMEOUT_MILLIS;
	private long idleTimeoutDateMillis = Long.MAX_VALUE;
	private long connectionTimeoutMillis = IConnection.MAX_TIMEOUT_MILLIS;
	private long connectionTimeoutDateMillis = Long.MAX_VALUE;
	private boolean idleTimeoutOccured = false;
	private boolean connectionTimeoutOccured = false;
	private boolean disconnectOccured = false;


	// cached values
	private Integer cachedSoSndBuf = null;

	
	// max app buffer size
	private Integer maxReadBufferSize = null;
	
	

	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
     * @param hostname  the remote host
	 * @param port		the port of the remote host to connect
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port) throws IOException {
		this(InetAddress.getByName(hostname), port);
	}
	
	
	


	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 * @param address   the remote host
	 * @param port		the port of the remote host to connect
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port) throws IOException {
		this(new InetSocketAddress(address, port), true, Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, null, getDefaultWorkerpool());
	}


	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 * @param address   the remote host address
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetSocketAddress address) throws IOException {
		this(address, true, Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, null, getDefaultWorkerpool());
	}


	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
     *
	 * @param address                the remote host
	 * @param port		             the port of the remote host to connect
	 * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, int connectTimeoutMillis) throws IOException {
		this(new InetSocketAddress(address, port), true, connectTimeoutMillis, new HashMap<String, Object>(), null, false, null, getDefaultWorkerpool());
	}

	

	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 * @param address              the remote host
	 * @param port	          	   the port of the remote host to connect
	 * @param options              the socket options
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, Map<String, Object> options) throws IOException {
		this(new InetSocketAddress(address, port), true, Integer.MAX_VALUE, options, null, false, null, getDefaultWorkerpool());
	}


	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 * @param address                the remote host
	 * @param port	          	     the port of the remote host to connect
	 * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @param options                the socket options
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, int connectTimeoutMillis, Map<String, Object> options) throws IOException {
		this(new InetSocketAddress(address, port), true, connectTimeoutMillis, options, null, false, null, getDefaultWorkerpool());
	}





	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 *
	 * @param address          the remote address
	 * @param port             the remote port
	 * @param appHandler       the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler) throws IOException {
		this(new InetSocketAddress(address, port), true, Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, appHandler, getDefaultWorkerpool());
	}



	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 *
	 * @param address                the remote address
	 * @param port                   the remote port
	 * @param appHandler             the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
 	 * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, int connectTimeoutMillis) throws IOException {
		this(new InetSocketAddress(address, port), true, connectTimeoutMillis, new HashMap<String, Object>(), null, false, appHandler, getDefaultWorkerpool());
	}

	
	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 *
	 * @param address                the remote address
	 * @param port                   the remote port
	 * @param appHandler             the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param waitForConnect         true, if the constructor should block until the connection is established. If the connect fails, the handler's onData and onDisconnect method will be called (if present) 
 	 * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, boolean waitForConnect, int connectTimeoutMillis) throws IOException {
		this(new InetSocketAddress(address, port), waitForConnect, connectTimeoutMillis, new HashMap<String, Object>(), null, false, appHandler, getDefaultWorkerpool());
	}
	
	
	



	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 *
	 * @param address                the remote address
	 * @param port                   the remote port
	 * @param appHandler             the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param sslContext             the ssl context
	 * @param sslOn                  true, if ssl should be activated
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(address, port), true, Integer.MAX_VALUE, new HashMap<String, Object>(), sslContext, sslOn, appHandler, getDefaultWorkerpool());
	}

	
	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 *
	 * @param address                the remote address
	 * @param port                   the remote port
	 * @param appHandler             the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param sslContext             the ssl context
	 * @param sslOn                  true, if ssl should be activated
 	 * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, int connectTimeoutMillis, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(address, port), true, connectTimeoutMillis, new HashMap<String, Object>(), sslContext, sslOn, appHandler, getDefaultWorkerpool());
	}

	
	
	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 *
	 * @param address                the remote address
	 * @param port                   the remote port
	 * @param appHandler             the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param sslContext             the ssl context
	 * @param sslOn                  true, if ssl should be activated
     * @param waitForConnect         true, if the constructor should block until the connection is established. If the connect fails, the handler's onData and onDisconnect method will be called (if present) 
 	 * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, boolean waitForConnect, int connectTimeoutMillis, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(address, port), waitForConnect, connectTimeoutMillis, new HashMap<String, Object>(), sslContext, sslOn, appHandler, getDefaultWorkerpool());
	}

	
	


	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
	 * @param address              the remote address
	 * @param port                 the remote port
	 * @param appHandler           the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param options              the socket options
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, Map<String, Object> options) throws IOException {
		this(new InetSocketAddress(address, port), true, Integer.MAX_VALUE, options, null, false, appHandler, getDefaultWorkerpool());
	}


	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
	 *
	 * @param address                the remote address
	 * @param port                   the remote port
	 * @param appHandler             the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
 	 * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @param options                the socket options
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, int connectTimeoutMillis, Map<String, Object> options) throws IOException {
		this(new InetSocketAddress(address, port), true, connectTimeoutMillis, options, null, false, appHandler, getDefaultWorkerpool());
	}


	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
	 *
	 * @param address                the remote address
	 * @param port                   the remote port
	 * @param appHandler             the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
     * @param waitForConnect         true, if the constructor should block until the connection is established. If the connect fails, the handler's onData and onDisconnect method will be called (if present) 
 	 * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @param options                the socket options
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, boolean waitForConnect, int connectTimeoutMillis, Map<String, Object> options) throws IOException {
		this(new InetSocketAddress(address, port), waitForConnect, connectTimeoutMillis, options, null, false, appHandler, getDefaultWorkerpool());
	}

	


	/**
	 * constructor <br><br>
	 *
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
	 * @param hostname         the remote host
	 * @param port             the remote port
	 * @param appHandler       the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port, IHandler appHandler) throws IOException {
		this(new InetSocketAddress(hostname, port), true, Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, appHandler, getDefaultWorkerpool());
	}


	/**
	 * constructor <br><br>
	 *
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
	 * @param hostname         the remote host
	 * @param port             the remote port
	 * @param appHandler       the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param workerPool       the worker pool to use 
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port, IHandler appHandler, Executor workerPool) throws IOException {
		this(new InetSocketAddress(hostname, port), true, Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, appHandler, workerPool);
	}





	/**
	 * constructor <br><br>
	 *
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
	 *
	 * @param hostname             the remote host
	 * @param port                 the remote port
	 * @param appHandler           the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param options              the socket options
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port, IHandler appHandler, Map<String, Object> options) throws IOException {
		this(new InetSocketAddress(hostname, port), true, Integer.MAX_VALUE, options, null, false, appHandler, getDefaultWorkerpool());
	}



	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 *
	 * @param address              the remote address
	 * @param port                 the remote port
	 * @param sslContext           the ssl context to use
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IReadWriteableConnection#activateSecuredMode()})
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(address, port), true, Integer.MAX_VALUE, new HashMap<String, Object>(), sslContext, sslOn, null, getDefaultWorkerpool());
	}



	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 *
	 * @param address              the remote address
	 * @param port                 the remote port
	 * @param options              the socket options
	 * @param sslContext           the ssl context to use
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IReadWriteableConnection#activateSecuredMode()})
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, Map<String, Object> options, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(address, port), true, Integer.MAX_VALUE, options, sslContext, sslOn, null, getDefaultWorkerpool());
	}



	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 * @param hostname             the remote host
	 * @param port                 the remote port
	 * @param sslContext           the ssl context to use
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IReadWriteableConnection#activateSecuredMode()})
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(hostname, port), true, Integer.MAX_VALUE, new HashMap<String, Object>(), sslContext, sslOn, null, getDefaultWorkerpool());
	}



	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 *
	 *
	 * @param hostname             the remote host
	 * @param port                 the remote port
	 * @param options              the socket options
	 * @param sslContext           the ssl context to use
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IReadWriteableConnection#activateSecuredMode()})
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port, Map<String, Object> options, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(hostname, port), true, Integer.MAX_VALUE, options, sslContext, sslOn, null, getDefaultWorkerpool());
	}



	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
	 *
	 * @param address          the remote address
	 * @param port             the remote port
	 * @param appHandler       the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param workerPool       the worker pool to use
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, Executor workerPool) throws IOException {
		this(new InetSocketAddress(address, port), true, Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, appHandler, workerPool);
	}


	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 * 
	 * @param address               the remote address
	 * @param port                  the remote port
	 * @param appHandler            the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param connectTimeoutMillis  the timeout of the connect procedure  
	 * @param workerPool            the worker pool to use
	 * @throws IOException if a I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, int connectTimeoutMillis, Executor workerPool) throws IOException {
		this(new InetSocketAddress(address, port), true, connectTimeoutMillis, new HashMap<String, Object>(), null, false, appHandler, workerPool);
	}
	
	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 * 
	 * @param address               the remote address
	 * @param port                  the remote port
	 * @param appHandler            the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
     * @param waitForConnect        true, if the constructor should block until the connection is established. If the connect fails, the handler's onData and onDisconnect method will be called (if present) 
	 * @param connectTimeoutMillis  the timeout of the connect procedure  
	 * @param workerPool            the worker pool to use
	 * @throws IOException if a I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, boolean waitForConnect, int connectTimeoutMillis, Executor workerPool) throws IOException {
		this(new InetSocketAddress(address, port), waitForConnect, connectTimeoutMillis, new HashMap<String, Object>(), null, false, appHandler, workerPool);
	}


	
	
	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
	 * @param address               the remote address
	 * @param port                  the remote port
	 * @param appHandler            the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)r
	 * @param connectTimeoutMillis  the timeout of the connect procedure 
	 * @param sslContext            the ssl context to use
	 * @param sslOn                 true, activate SSL mode. false, ssl can be activated by user (see {@link IReadWriteableConnection#activateSecuredMode()})
	 * @param workerPool            the worker pool to use
	 * @throws IOException if a I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, int connectTimeoutMillis, SSLContext sslContext, boolean sslOn, Executor workerPool) throws IOException {
		this(new InetSocketAddress(address, port), true, connectTimeoutMillis, new HashMap<String, Object>(), sslContext, sslOn, appHandler, workerPool);
	}
	
	
	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
	 * @param address               the remote address
	 * @param port                  the remote port
	 * @param appHandler            the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)r
*    * @param waitForConnect        true, if the constructor should block until the connection is established. If the connect fails, the handler's onData and onDisconnect method will be called (if present) 
	 * @param connectTimeoutMillis  the timeout of the connect procedure 
	 * @param sslContext            the ssl context to use
	 * @param sslOn                 true, activate SSL mode. false, ssl can be activated by user (see {@link IReadWriteableConnection#activateSecuredMode()})
	 * @param workerPool            the worker pool to use
	 * @throws IOException if a I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, boolean waitForConnect, int connectTimeoutMillis, SSLContext sslContext, boolean sslOn, Executor workerPool) throws IOException {
		this(new InetSocketAddress(address, port), waitForConnect, connectTimeoutMillis, new HashMap<String, Object>(), sslContext, sslOn, appHandler, workerPool);
	}
	
	
	

	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 * 
	 * @param address               the remote address
	 * @param port                  the remote port
	 * @param appHandler            the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param autoflush             the auto flush mode
	 * @param flushmode             the flush mode
	 * @throws IOException if a I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, boolean autoflush, FlushMode flushmode) throws IOException {
		this(new InetSocketAddress(address, port), true, Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, appHandler, getDefaultWorkerpool(), autoflush, flushmode);
	}



	/**
	 *  intermediate client constructor, which uses a specific dispatcher
	 */
	NonBlockingConnection(InetSocketAddress remoteAddress, boolean waitForConnect, int connectTimeoutMillis, Map<String, Object> options, SSLContext sslContext, boolean sslOn, IHandler appHandler, Executor workerpool) throws IOException {
		this(remoteAddress, waitForConnect, connectTimeoutMillis, options, sslContext, sslOn, appHandler, workerpool, DEFAULT_AUTOFLUSH, DEFAULT_FLUSH_MODE);
	}


	/**
	 *  client constructor, which uses a specific dispatcher
	 */
	private NonBlockingConnection(final InetSocketAddress remoteAddress, boolean waitForConnect, final int connectTimeoutMillis, final Map<String, Object> options, final SSLContext sslContext, final boolean isSecured, final IHandler appHdl, final Executor workerpool, boolean autoflush, FlushMode flushmode) throws IOException {
		setFlushmode(flushmode);
		setAutoflush(autoflush);
		setWorkerpool(workerpool);
		
		appHandler.set(HandlerAdapter.newInstance(appHdl));
		
		if (waitForConnect) {
			connect(remoteAddress, connectTimeoutMillis, options, sslContext, isSecured);
			
		} else {
			Runnable connectTask = new Runnable() {
				
				public void run() {
					try {
						connect(remoteAddress, connectTimeoutMillis, options, sslContext, isSecured);
					} catch (IOException ioe) {
						onDisconnect();
					}
				}
			};
			EXECUTOR.execute(connectTask);
		}
	}




	/**
	 *  server-side constructor
	 */
	protected NonBlockingConnection(TimeoutManager connectionManager, IHandler handlerAdapter) throws IOException {
		appHandler.set(handlerAdapter);
		
		isConnected.set(true);
		timeoutMgmHandle = connectionManager.register(this);
	}

	

	/**
	 * will be used for client-side connections only 
	 */
	private void connect(InetSocketAddress remoteAddress, int connectTimeoutMillis, Map<String, Object> options, SSLContext sslContext, boolean isSecured) throws IOException, SocketTimeoutException {
		IoChainableHandler ioHdl = createClientIoHandler(remoteAddress, connectTimeoutMillis, options, sslContext, isSecured);
		
		timeoutMgmHandle = DEFAULT_CONNECTION_MANAGER.register(this);
		
		init(ioHdl);

		// "true" set of the timeouts
		setIdleTimeoutMillis(idleTimeoutMillis);
		setConnectionTimeoutMillis(connectionTimeoutMillis);
	}


	long getLastTimeReceivedMillis() {
		return ioHandler.getLastTimeReceivedMillis();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getMaxReadBufferThreshold() {
		if (maxReadBufferSize == null) {
			return Integer.MAX_VALUE;
		} else {
			return maxReadBufferSize;
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void setMaxReadBufferThreshold(int size) {
		
		if (size == Integer.MAX_VALUE) {
			maxReadBufferSize = null;
			ioHandler.setRetryRead(true);
		} else {
			maxReadBufferSize = size;
			ioHandler.setRetryRead(false);
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	/*void setMaxWriteBufferThreshold(int size) {
		
		if (size == Integer.MAX_VALUE) {
			maxWriteBufferSize = null;
		} else {
			maxWriteBufferSize = size;
		}
	}*/
	
	

	/**
	 * returns the default workerpool 
	 *  
	 * @return  the default worker pool
	 */
	synchronized static Executor getDefaultWorkerpool() {
		if (defaultWorkerPool == null) {
			defaultWorkerPool = Executors.newCachedThreadPool(new DefaultThreadFactory());
		}

		return defaultWorkerPool;
	}

	
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean isMoreInputDataExpected() {
		if (ioHandler != null) {
			return ioHandler.isOpen();
		} else {
			return false;
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean isDataWriteable() {
		return ioHandler.isOpen();
	}

	
	final void init(IoChainableHandler ioHandler) throws IOException, SocketTimeoutException {
		this.ioHandler = ioHandler;
		
		ioHandler.init(ioHandlerCallback);
		
		isConnected.set(true);

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("connection " + getId() + " created. IoHandler: " + ioHandler.toString());
		}
	}
	


	/**
	 * {@inheritDoc}
	 */
	public final void setHandler(IHandler hdl) throws IOException {	
		appHandler.set(HandlerAdapter.newInstance(hdl));
		
		if (getReadQueueSize() > 0) {
			ioHandlerCallback.onPostData();
		}
	}

	
	/**
	 * {@inheritDoc}
	 */
	public IHandler getHandler() {	
		IHandler hdl = appHandler.get();
		if (hdl == null) {
			return null;
		} else {
			return ((HandlerAdapter) hdl).getHandler();
		}
	}
	
	/**
	 * sets the worker pool 
	 * @param workerpool  the worker pool
	 */
	public void setWorkerpool(Executor workerpool) {
		this.workerpool = workerpool;
	}


	
	/**
	 * gets the workerpool
	 * 
	 * @return the workerpool
	 */
	public Executor getWorkerpool() {
		return workerpool;
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final void setFlushmode(FlushMode flushMode) {

		if (flushMode == FlushMode.ASYNC) {
			super.setFlushmode(flushMode);
			
		} else {
			super.setFlushmode(flushMode);
		}
	}

	


	/**
	 * pool support (the connection- and idle timeout and handler will not be reset)
	 *
	 * @return true, if connection has been reset
	 */
	protected boolean reset() {
		try {
			if (!pendingWriteConfirmations.isEmpty() && (getFlushmode() == FlushMode.SYNC)) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + getId() + "] is not reuseable, because write confirmations are open");
				}
				return false;
			}
	
			writeException = null;
	
			
			boolean isReset = ioHandler.reset();
			if (!isReset) {
				return false;
			}
	
			return super.reset();
		} catch (Exception e) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by reseting connection " + getId() + " " + e.toString());
			}
			return false;
		}
	}






	/**
	 * {@inheritDoc}
	 */
	public final boolean isOpen() {
		return isOpen.get();
	}

	boolean isConnected() {
		return ioHandler.isOpen();
	}
	
	
	
	@Override
	protected void onPostAppend() {
		if ((maxReadBufferSize != null) && (getReadQueueSize() >= maxReadBufferSize)) {
			try {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("suspending read, because max read buffers size " + maxReadBufferSize + " is execced (" + getReadQueueSize() + ")");
				}
					
				ioHandler.suspendRead();
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by suspending read (cause by max read queue size " + maxReadBufferSize + " " + ioe.toString());
				}
			}
		}	
	}

	
	@Override
	protected void onPostRead() throws IOException {
		if ((maxReadBufferSize != null) && ioHandler.isReadSuspended() && ((getReadQueueSize() < maxReadBufferSize))) {
			try {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("resuming read, because read buffer size is lower than max read buffers size " + maxReadBufferSize);
				}

				if (!isSuspended.get()) {
					ioHandler.resumeRead();
				}
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by suspending read (cause by max read queue size " + maxReadBufferSize + " " + ioe.toString());
				}
			}
		}	
	}
	
	
	private void onData(ByteBuffer[] data, int size) {

		if (data != null) {
			appendDataToReadBuffer(data, size);
		}
	}
	
	
	private void onPostData() {
		try {
			((IDataHandler) appHandler.get()).onData(NonBlockingConnection.this);
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by performing onData callback on " + appHandler.get() + " " + ioe.toString());
			}
		}
	}
	
	
	private void onWritten(ByteBuffer data) {
		if (getFlushmode() == FlushMode.SYNC) {
			synchronized (writeSynchronizer) {
				if (data != null) {
					pendingWriteConfirmations.remove(data);
				}

				if (pendingWriteConfirmations.isEmpty()) {
					writeSynchronizer.notifyAll();
				}
			}
		}
	}

	
	private void onWriteException(IOException ioException, ByteBuffer data) {
		
		isOpen.set(false);
		
		if (getFlushmode() == FlushMode.SYNC) {
			synchronized (writeSynchronizer) {
				writeException = ioException;
				if (data != null) {
					pendingWriteConfirmations.remove(data);
				}
				writeSynchronizer.notifyAll();
			}
		}
	}
	
	
	private void onConnect() {
		try {
			((IConnectHandler) appHandler.get()).onConnect(NonBlockingConnection.this);
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by performing onConnect callback on " + appHandler.get() + " " + ioe.toString());
			}
		}
	}
	
	
	private void onConnectionAbnormalTerminated() {
		forceClose();
	}
	
	
	private void onDisconnect() {
		if (timeoutMgmHandle != null) {
			timeoutMgmHandle.destroy();
		}
		
		if (!disconnectOccured) {
			disconnectOccured = true;
			
			
			// call first onData
			try {
				((IDataHandler) appHandler.get()).onData(NonBlockingConnection.this);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by performing onData callback on " + appHandler.get() + " " + ioe.toString());
				}
			}

			// then onDisconnect
			try {
				((IDisconnectHandler) appHandler.get()).onDisconnect(NonBlockingConnection.this);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by performing onDisconnect callback on " + appHandler.get() + " " + ioe.toString());
				}
			}
		}
	}

	boolean checkIdleTimeout(Long currentMillis) {
		if (getRemainingMillisToIdleTimeout(currentMillis) <= 0) {
			onIdleTimeout();
			return true;
		}
		return false;
	}

	
	void onIdleTimeout() {
		if (!idleTimeoutOccured) {
			idleTimeoutOccured = true;
			try {
				((IIdleTimeoutHandler) appHandler.get()).onIdleTimeout(NonBlockingConnection.this);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by performing onIdleTimeout callback on " + appHandler.get() + " " + ioe.toString());
				}
			}
		} else {
			setIdleTimeoutMillis(IConnection.MAX_TIMEOUT_MILLIS);
		}
	}


		

	boolean checkConnectionTimeout(Long currentMillis) {
		if (getRemainingMillisToConnectionTimeout(currentMillis) <= 0) {
			onConnectionTimeout();
			return true;
		}
		return false;
	}
	
	private void onConnectionTimeout() {
		if (!connectionTimeoutOccured) {
			connectionTimeoutOccured = true;
			try {
				((IConnectionTimeoutHandler) appHandler.get()).onConnectionTimeout(NonBlockingConnection.this);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by performing onConnectionTimeout callback on " + appHandler.get() + " " + ioe.toString());
				}
			}
		} else {
			setConnectionTimeoutMillis(IConnection.MAX_TIMEOUT_MILLIS);
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	public long getRemainingMillisToConnectionTimeout() {
		return getRemainingMillisToConnectionTimeout(System.currentTimeMillis());
	}


	/**
	 * {@inheritDoc}
	 */
	public long getRemainingMillisToIdleTimeout() {
		return getRemainingMillisToIdleTimeout(System.currentTimeMillis());
	}


	private long getRemainingMillisToConnectionTimeout(long currentMillis) {
		return connectionTimeoutDateMillis - currentMillis;
	}


	public long getNumberOfReceivedBytes() {
		return ioHandler.getNumberOfReceivedBytes();
	}
	
	public long getNumberOfSendBytes() {
		return ioHandler.getNumberOfSendBytes();
	}
	

	private long getRemainingMillisToIdleTimeout(long currentMillis) {
		
		long remaining = idleTimeoutDateMillis - currentMillis;

		// time out not received
		if (remaining > 0) {
			return remaining;

		// time out received
		} else {

			// ... but check if meantime data has been received!
			return (getLastTimeReceivedMillis() + idleTimeoutMillis) - currentMillis;
		}
	}
	
	String getRegisteredOpsInfo() {
		return ioHandler.getRegisteredOpsInfo();
	}
	
	


	/**
	 * {@inheritDoc}
	 */
	public final void setWriteTransferRate(int bytesPerSecond) throws ClosedChannelException, IOException {

		if ((bytesPerSecond != UNLIMITED) && ((getFlushmode() != FlushMode.ASYNC))) {
			LOG.warning("setWriteTransferRate is only supported for FlushMode ASYNC. Ignore update of the transfer rate");
			return;
		}

		if (this.bytesPerSecond == bytesPerSecond) {
			return;
		}
		
		this.bytesPerSecond = bytesPerSecond;
		ioHandler = ConnectionUtils.getIoProvider().setWriteTransferRate(ioHandler, bytesPerSecond);
	}


	/**
	 * {@inheritDoc}
	 */
	public int getWriteTransferRate() throws ClosedChannelException, IOException {
		return bytesPerSecond;
	}

	/**
	 * {@inheritDoc}
	 */
/*	public final void setReadTransferRate(int bytesPerSecond) throws ClosedChannelException, IOException {
		ioHandler = ioProvider.setReadTransferRate(ioHandler, bytesPerSecond);
	}*/

	
	
	/**
	 * {@inheritDoc}
	 */
	public final boolean isSecuredModeActivateable() {
		return ConnectionUtils.getIoProvider().isSecuredModeActivateable(ioHandler);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void activateSecuredMode() throws IOException {

		boolean isPrestarted = ConnectionUtils.getIoProvider().preStartSecuredMode(ioHandler);

		if (isPrestarted) {
			FlushMode currentFlushMode = getFlushmode();
			setFlushmode(FlushMode.ASYNC);

			internalFlush();

			setFlushmode(currentFlushMode);

			ByteBuffer[] buffer = readByteBufferByLength(available());
			ConnectionUtils.getIoProvider().startSecuredMode(ioHandler, buffer);
		}
	}


	/**
	 * {@inheritDoc}
	 */
	public boolean isSecure() {
		return ioHandler.isSecure();
	}


	/**
	 * {@inheritDoc}
	 */
	public final ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		try {
			return super.readByteBufferByDelimiter(delimiter, encoding, maxLength);

		} catch (MaxReadSizeExceededException mre) {
			if (isOpen()) {
				throw mre;

			} else {
				throw new ClosedChannelException();
			}
			
		} catch (BufferUnderflowException bue) {
			if (isOpen()) {
				throw bue;

			} else {
				throw new ClosedChannelException();
			}
		}
	}



	/**
	 * {@inheritDoc}
	 */
	public ByteBuffer[] readByteBufferByLength(int length) throws IOException, BufferUnderflowException {
		try {
			return super.readByteBufferByLength(length);

		} catch (BufferUnderflowException bue) {
			if (isOpen()) {
				throw bue;

			} else {
				throw new ClosedChannelException();
			}
		}
	}


	/**
	 * {@inheritDoc}
	 */
	protected ByteBuffer readSingleByteBuffer(int length) throws IOException, ClosedChannelException, BufferUnderflowException {
		try {
			return super.readSingleByteBuffer(length);

		} catch (BufferUnderflowException bue) {

			if (isOpen()) {
				throw bue;

			} else {
				throw new ClosedChannelException();
			}
		}
	}




	/**
	 * {@inheritDoc}
	 *
	 */
	public final void setIdleTimeoutMillis(long timeoutMillis) {
		idleTimeoutOccured = false;
		
		if (timeoutMillis <= 0) {
			LOG.warning("idle timeout " + timeoutMillis + " millis is invalid");
			return;
		}

		this.idleTimeoutMillis = timeoutMillis;
		this.idleTimeoutDateMillis = System.currentTimeMillis() + idleTimeoutMillis;
		
		if (idleTimeoutDateMillis < 0) {
			idleTimeoutDateMillis = Long.MAX_VALUE;
		}

		if (isConnected.get()) {
			long period = idleTimeoutMillis;
			if (idleTimeoutMillis > 500) {
				period = idleTimeoutMillis / 5;
			}
			
			timeoutMgmHandle.updateCheckPeriod(period);
		}
	}
	


	/**
	 * {@inheritDoc}
	 */
	public final void setConnectionTimeoutMillis(long timeoutMillis) {
		connectionTimeoutOccured = false;
	

		if (timeoutMillis <= 0) {
			LOG.warning("connection timeout " + timeoutMillis + " millis is invalid");
			return;
		}

		this.connectionTimeoutMillis = timeoutMillis;
		this.connectionTimeoutDateMillis = System.currentTimeMillis() + connectionTimeoutMillis;


		if (isConnected.get()) {
			long period = connectionTimeoutMillis;
			if (connectionTimeoutMillis > 500) {
				period = connectionTimeoutMillis / 5;
			}
			
			timeoutMgmHandle.updateCheckPeriod(period);
		}
	}

	

	/**
	 * {@inheritDoc}
	 */
	public final long getConnectionTimeoutMillis() {
		return connectionTimeoutMillis;
	}



	/**
	 * {@inheritDoc}
	 *
	 */
	public final long getIdleTimeoutMillis() {
		return idleTimeoutMillis;
	}



	/**
	 * {@inheritDoc}
	 */
	public final InetAddress getLocalAddress() {
		return ioHandler.getLocalAddress();
	}



	/**
	 * {@inheritDoc}
	 */
	public final String getId() {
		return ioHandler.getId();
	}




	/**
	 * {@inheritDoc}
	 */
	public final int getLocalPort() {
		return ioHandler.getLocalPort();
	}



	/**
	 * {@inheritDoc}
	 */
	public final InetAddress getRemoteAddress() {
		return ioHandler.getRemoteAddress();
	}



	/**
	 * {@inheritDoc}
	 */
	public final int getRemotePort() {
		return ioHandler.getRemotePort();
	}


	/**
	 * {@inheritDoc}
	 */
	public final int getPendingWriteDataSize() {
		return getWriteBufferSize() + ioHandler.getPendingWriteDataSize();
	}




	/**
	 * {@inheritDoc}
	 */
	public final void suspendRead() throws IOException {
		ioHandler.suspendRead();
		isSuspended.set(true);
	}

	
	
	/**
	 * {@inheritDoc}
	 */
	public boolean isReadSuspended() {
		return isSuspended.get();
	}


	/**
	 * {@inheritDoc}
	 */
	public final void resumeRead() throws IOException {
		ioHandler.resumeRead();
		isSuspended.set(false);
		
		if (getReadQueueSize() > 0) {
			ioHandlerCallback.onPostData();
		}
	}



	/**
	 * {@inheritDoc}
	 */
	public long transferFrom(ReadableByteChannel sourceChannel) throws ClosedChannelException, IOException {
		int chunkSize = getSoSndBufSize();
		return transferFrom(sourceChannel, chunkSize);
	}


	private int getSoSndBufSize() throws IOException {
		if (cachedSoSndBuf == null) {
			cachedSoSndBuf = (Integer) getOption(SO_SNDBUF);
		}

		return cachedSoSndBuf;
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onWriteDataInserted() throws IOException, ClosedChannelException {
		if (isAutoflush()) {
			internalFlush();
		}
	}




	/**
	 * {@inheritDoc}
	 */
	public final Object getOption(String name) throws IOException {
		return ioHandler.getOption(name);
	}


	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public final Map<String, Class> getOptions() {
		return ioHandler.getOptions();
	}


	/**
	 * {@inheritDoc}
	 */
	public void setOption(String name, Object value) throws IOException {
		if (name.equalsIgnoreCase(SO_SNDBUF)) {
			cachedSoSndBuf = (Integer) value;
		}

		ioHandler.setOption(name, value);
	}
	
	
	@Override
	protected int getWriteTransferChunkeSize() {
		try {
			return getSoSndBufSize();
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by retrieving SoSndBufSize " + ioe.toString());
			}
			return super.getWriteTransferChunkeSize();
		}
	} 



	private void forceClose() {
		try {
			isOpen.set(false);
			if (ioHandler != null) {
				ioHandler.close(true);
			}
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("Error occured by closing " + ioe.toString());
			}
		}
	}


	/**
	 * {@inheritDoc}
	 */
	public void close() throws IOException {
		super.close(); 
		
		if (isOpen.get() == true) {
			isOpen.set(false);
						
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("closing connection -> flush all remaining data");
			}

			if (!isWriteBufferEmpty()) {
				ByteBuffer[] buffers = drainWriteQueue();
				ioHandler.write(buffers);
				ioHandler.flush();
			}
			ioHandler.close(false);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	void closeSilence() {
		try {
			close();
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by closing connection " + getId() + " " + DataConverter.toString(ioe));
			}
		}
	}





	/**
	 * {@inheritDoc}
	 */
	public final void flush() throws ClosedChannelException, IOException {
		internalFlush();
	}


	private void internalFlush() throws ClosedChannelException, IOException {
		
		if (!isOpen.get()) {
			throw new ClosedChannelException();
		}

		removeWriteMark();
		if (getFlushmode() == FlushMode.SYNC) {

			// flush write queue by using a write guard to wait until the onWrittenEvent has been occurred
			syncFlush();


		} else {

			// just flush the queue and return
			if (!isWriteBufferEmpty()) {
				ByteBuffer[] buffers = drainWriteQueue();
				ioHandler.write(buffers);
				ioHandler.flush();
			}
		}

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + getId() + "] flushed");
		}
	}




	/**
	 * flush, and wait until data has been written to channel
	 */
	private void syncFlush() throws ClosedChannelException, IOException {

		long start = System.currentTimeMillis();
		long remainingTime = sendTimeoutMillis;

		if (ConnectionUtils.isDispatcherThread()) {
			String msg = "synchronized flushing in NonThreaded mode could cause dead locks (hint: set flush mode to ASYNC)";
			LOG.warning("[" + getId() + "] " + msg);
		}
		
		synchronized (writeSynchronizer) {
			if (!isWriteBufferEmpty()) {
				try {
					ByteBuffer[] buffers = drainWriteQueue();					
					pendingWriteConfirmations.addAll(Arrays.asList(buffers));

					ioHandler.write(buffers);
					ioHandler.flush();

					do {
						// all data written?
						if (pendingWriteConfirmations.isEmpty()) {
							return;

						// write exception occurred?
						} else if(writeException != null) {
							IOException ioe = writeException;
							writeException = null;
							throw ioe;

						// ... no -> wait
						} else {
							try {
								writeSynchronizer.wait(remainingTime);
							} catch (InterruptedException ignore) { }
						}

						remainingTime = (start + sendTimeoutMillis) - System.currentTimeMillis();
					} while (remainingTime > 0);

					throw new SocketTimeoutException("send timeout " + DataConverter.toFormatedDuration(sendTimeoutMillis) + " reached. returning from sync flushing");

				} finally {
					pendingWriteConfirmations.clear();
				}
			}
		}
	}




	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		try {
			if (isOpen()) {
				return "id=" + getId() + ", remote=" + getRemoteAddress() + "(" + getRemoteAddress() + ":" + getRemotePort() + ")";
				
			} else {
				return "id=" + getId() + " (closed)";
			}
		} catch (Exception e) {
			return super.toString();
		}
	}

	String toDetailedString() {
		try {
			if (isOpen()) {
				return "id=" + getId() + ", remote=" + getRemoteAddress().getCanonicalHostName() + "(" + getRemoteAddress() + ":" + getRemotePort() + 
				       ") lastTimeReceived=" + DF.format(new Date(getLastTimeReceivedMillis())) + " reveived=" + getNumberOfReceivedBytes() + 
				       " send=" + getNumberOfSendBytes() + " ops={" + getRegisteredOpsInfo() + "}";
			} else {
				return "id=" + getId() + " (closed)";
			}
		} catch (Exception e) {
			return super.toString();
		}
	}




	private static IoChainableHandler createClientIoHandler(InetSocketAddress remoteAddress, int connectTimeoutMillis, Map<String, Object> options, SSLContext sslContext, boolean sslOn) throws IOException {
		IoChainableHandler ioHandler = null;
		if (sslContext != null) {
			ioHandler = ConnectionUtils.getIoProvider().createSSLClientIoHandler(remoteAddress, connectTimeoutMillis, options, sslContext, sslOn);
		} else {
			ioHandler = ConnectionUtils.getIoProvider().createClientIoHandler(remoteAddress, connectTimeoutMillis, options);
		}

		return ioHandler;
	}




	private final class IoHandlerCallback implements IIoHandlerCallback {

		public void onWritten(ByteBuffer data) {
			NonBlockingConnection.this.onWritten(data);
		}
		
		public void onWriteException(IOException ioException, ByteBuffer data) {
			NonBlockingConnection.this.onWriteException(ioException, data);
		}
		
		public void onData(ByteBuffer[] data, int size) {
			NonBlockingConnection.this.onData(data, size);
		}
		
		public void onPostData() {
			NonBlockingConnection.this.onPostData();
		}

		public void onConnectionAbnormalTerminated() {
			NonBlockingConnection.this.onConnectionAbnormalTerminated();
		}

		public void onConnect() {
			NonBlockingConnection.this.onConnect();
		}

		public void onDisconnect() {
			NonBlockingConnection.this.onDisconnect();
		}
	}


	private static class DefaultThreadFactory implements ThreadFactory {
		private static final AtomicInteger POOL_NUMBER = new AtomicInteger(1);
		private final ThreadGroup group;
		private final AtomicInteger threadNumber = new AtomicInteger(1);
		private final String namePrefix;

		DefaultThreadFactory() {
			SecurityManager s = System.getSecurityManager();
			group = (s != null)? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
			namePrefix = "xNbcPool-" + POOL_NUMBER.getAndIncrement() + "-thread-";
        }

		public Thread newThread(Runnable r) {
			Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
            if (!t.isDaemon()) {
                t.setDaemon(true);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }
}
