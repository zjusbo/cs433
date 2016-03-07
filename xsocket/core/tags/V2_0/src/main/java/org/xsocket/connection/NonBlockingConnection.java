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
import java.util.Arrays;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.xsocket.DataConverter;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.TimeoutManager.TimeoutMgmHandle;



/**
 * Implementation of the <code>INonBlockingConnection</code> interface. <br><br>
 *
 * A newly created connection is in the open state. Write or rad methods can be called immediately
 *
 * The methods of this class are not thread-safe.
 *
 * @author grro@xsocket.org
 */
public final class NonBlockingConnection extends AbstractNonBlockingStream implements INonBlockingConnection {

	private static final Logger LOG = Logger.getLogger(NonBlockingConnection.class.getName());

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



	// flags
	private volatile boolean isOpen = true;
	private AtomicBoolean isSuspended = new AtomicBoolean(false);
	

	
	// connection manager
	private static final TimeoutManager DEFAULT_CONNECTION_MANAGER = new TimeoutManager();
	private TimeoutMgmHandle timeoutMgmHandle = null;
	

	// io handler
	private final IoHandlerCallback ioHandlerCallback = new IoHandlerCallback();
	private IoChainableHandler ioHandler = null;


	// app handler
	private IHandler appHandler = null;


	// execution
	private Executor workerpool = null;
	

	// write thread handling
	private final Object writeSynchronizer = new Object();


	// sync flush support
	private IOException writeException = null;
	private final List<ByteBuffer> pendingWriteConfirmations = new ArrayList<ByteBuffer>();


	// timeout support
	private long idleTimeoutMillis = IoProvider.DEFAULT_IDLE_TIMEOUT_MILLIS;
	private long idleTimeoutDateMillis = Long.MAX_VALUE;
	private long connectionTimeoutMillis = IoProvider.DEFAULT_CONNECTION_TIMEOUT_MILLIS;
	private long connectionTimeoutDateMillis = Long.MAX_VALUE;
	private boolean idleTimeoutOccured = false;
	private boolean connectionTimeoutOccured = false;
	private boolean disconnectOccured = false;


	// is secured
	private boolean isSecured = false;

	// cached values
	private Integer cachedSoSndBuf = null;

	
	// max read buffer size
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
		this(new InetSocketAddress(address, port), Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, null, getDefaultWorkerpool());
	}


	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 * @param address   the remote host address
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetSocketAddress address) throws IOException {
		this(address, Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, null, getDefaultWorkerpool());
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
		this(new InetSocketAddress(address, port), connectTimeoutMillis, new HashMap<String, Object>(), null, false, null, getDefaultWorkerpool());
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
		this(new InetSocketAddress(address, port), Integer.MAX_VALUE, options, null, false, null, getDefaultWorkerpool());
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
		this(new InetSocketAddress(address, port), connectTimeoutMillis, options, null, false, null, getDefaultWorkerpool());
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
		this(new InetSocketAddress(address, port), Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, appHandler, getDefaultWorkerpool());
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
		this(new InetSocketAddress(address, port), connectTimeoutMillis, new HashMap<String, Object>(), null, false, appHandler, getDefaultWorkerpool());
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
		this(new InetSocketAddress(address, port), connectTimeoutMillis, new HashMap<String, Object>(), sslContext, sslOn, appHandler, getDefaultWorkerpool());
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
		this(new InetSocketAddress(address, port), Integer.MAX_VALUE, options, null, false, appHandler, getDefaultWorkerpool());
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
		this(new InetSocketAddress(address, port), connectTimeoutMillis, options, null, false, appHandler, getDefaultWorkerpool());
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
		this(new InetSocketAddress(hostname, port), Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, appHandler, getDefaultWorkerpool());
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
		this(new InetSocketAddress(hostname, port), Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, appHandler, workerPool);
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
		this(new InetSocketAddress(hostname, port), Integer.MAX_VALUE, options, null, false, appHandler, getDefaultWorkerpool());
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
		this(new InetSocketAddress(address, port), Integer.MAX_VALUE, new HashMap<String, Object>(), sslContext, sslOn, null, getDefaultWorkerpool());
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
		this(new InetSocketAddress(address, port), Integer.MAX_VALUE, options, sslContext, sslOn, null, getDefaultWorkerpool());
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
		this(new InetSocketAddress(hostname, port), Integer.MAX_VALUE, new HashMap<String, Object>(), sslContext, sslOn, null, getDefaultWorkerpool());
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
		this(new InetSocketAddress(hostname, port), Integer.MAX_VALUE, options, sslContext, sslOn, null, getDefaultWorkerpool());
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
		this(new InetSocketAddress(address, port), Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, appHandler, workerPool);
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
		this(new InetSocketAddress(address, port), connectTimeoutMillis, new HashMap<String, Object>(), null, false, appHandler, workerPool);
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
		this(new InetSocketAddress(address, port), connectTimeoutMillis, new HashMap<String, Object>(), sslContext, sslOn, appHandler, workerPool);
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
		this(new InetSocketAddress(address, port), Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, appHandler, getDefaultWorkerpool(), autoflush, flushmode);
	}



	/**
	 *  intermediate client constructor, which uses a specific dispatcher
	 */
	NonBlockingConnection(InetSocketAddress remoteAddress, int connectTimeoutMillis, Map<String, Object> options, SSLContext sslContext, boolean sslOn, IHandler appHandler, Executor workerpool) throws IOException {
		this(remoteAddress, connectTimeoutMillis, options, sslContext, sslOn, appHandler, workerpool, DEFAULT_AUTOFLUSH, DEFAULT_FLUSH_MODE);
	}


	/**
	 *  client constructor, which uses a specific dispatcher
	 */
	private NonBlockingConnection(InetSocketAddress remoteAddress, int connectTimeoutMillis, Map<String, Object> options, SSLContext sslContext, boolean isSecured, IHandler appHandler, Executor workerpool, boolean autoflush, FlushMode flushmode) throws IOException {
		this.isSecured = isSecured;
		setFlushmode(flushmode);
		setAutoflush(autoflush);
		
		IoChainableHandler ioHandler = createClientIoHandler(remoteAddress, connectTimeoutMillis, options, sslContext, isSecured);
		init(ioHandler, HandlerAdapter.newInstance(appHandler), workerpool);

		timeoutMgmHandle = DEFAULT_CONNECTION_MANAGER.register(this);
		setIdleTimeoutMillis(IConnection.MAX_TIMEOUT_MILLIS);
		setConnectionTimeoutMillis(IConnection.MAX_TIMEOUT_MILLIS);
	}



	/**
	 *  server-side constructor
	 */
	protected NonBlockingConnection(TimeoutManager connectionManager) throws IOException {
		timeoutMgmHandle = connectionManager.register(this);
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
		return ioHandler.isOpen();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean isDataWriteable() {
		return ioHandler.isOpen();
	}

	
	final void init(IoChainableHandler ioHandler, IHandler appHandler, Executor workerpool) throws IOException, SocketTimeoutException {
		this.ioHandler = ioHandler;
		this.appHandler = appHandler;
		setWorkerpool(workerpool);

		ioHandler.init(ioHandlerCallback);

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("connection " + getId() + " created. IoHandler: " + ioHandler.toString());
		}
	}
	


	/**
	 * {@inheritDoc}
	 */
	public final void setHandler(IHandler hdl) throws IOException {	
		appHandler = HandlerAdapter.newInstance(hdl);
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
			synchronized (writeSynchronizer) {
				if (!pendingWriteConfirmations.isEmpty()) {
					LOG.warning("Updating flush mode to " + flushMode + ". A sync flush write operation is currently running which will be updated to async mode");
					pendingWriteConfirmations.clear();
					ioHandlerCallback.onWritten(null);
				}

				super.setFlushmode(flushMode);
			}

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
			if (!pendingWriteConfirmations.isEmpty()) {
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
		return isOpen;
	}

	boolean isConnected() {
		return ioHandler.isOpen();
	}

	
	@Override
	void onPostAppend() {
		if (maxReadBufferSize != null) {
			if (getReadQueueSize() >= maxReadBufferSize) {
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
	}

	
	@Override
	protected void onPostRead() throws IOException {
		if ((maxReadBufferSize != null) && ioHandler.isReadSuspended()) {
			if (getReadQueueSize() < maxReadBufferSize) {
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
				((IIdleTimeoutHandler) appHandler).onIdleTimeout(NonBlockingConnection.this);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by performing onIdleTimeout callback on " + appHandler + " " + ioe.toString());
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
				((IConnectionTimeoutHandler) appHandler).onConnectionTimeout(NonBlockingConnection.this);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by performing onConnectionTimeout callback on " + appHandler + " " + ioe.toString());
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


	long getNumberOfReceivedBytes() {
		return ioHandler.getNumberOfReceivedBytes();
	}
	
	long getNumberOfSendBytes() {
		return ioHandler.getNumberOfSendBytes();
	}
	

	private long getRemainingMillisToIdleTimeout(long currentMillis) {
		
		long remaining = idleTimeoutDateMillis - currentMillis;

		// time out received
		if (remaining > 0) {
			return remaining;

		// ... yes
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

		if (bytesPerSecond != UNLIMITED) {
			if (getFlushmode() != FlushMode.ASYNC) {
				LOG.warning("setWriteTransferRate is only supported for FlushMode ASYNC. Ignore update of the transfer rate");
				return;
			}
		}

		ioHandler = ConnectionUtils.getIoProvider().setWriteTransferRate(ioHandler, bytesPerSecond);
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
	public final void activateSecuredMode() throws IOException {

		boolean isPrestarted = ConnectionUtils.getIoProvider().preStartSecuredMode(ioHandler);

		if (isPrestarted) {
			FlushMode currentFlushMode = getFlushmode();
			setFlushmode(FlushMode.ASYNC);

			internalFlush();

			setFlushmode(currentFlushMode);

			ByteBuffer[] buffer = readByteBufferByLength(available());
			ConnectionUtils.getIoProvider().startSecuredMode(ioHandler, buffer);
			isSecured = true;
		}
	}


	/**
	 * {@inheritDoc}
	 */
	public boolean isSecure() {
		return isSecured;
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
			ByteBuffer[] buffers = super.readByteBufferByLength(length);
			return buffers;


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

		long period = idleTimeoutMillis;
		if (idleTimeoutMillis > 500) {
			period = idleTimeoutMillis / 5;
		}
		
		timeoutMgmHandle.updateCheckPeriod(period);
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


		long period = connectionTimeoutMillis;
		if (connectionTimeoutMillis > 500) {
			period = connectionTimeoutMillis / 5;
		}
		
		timeoutMgmHandle.updateCheckPeriod(period);
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
	public final void resumeRead() throws IOException {
		ioHandler.resumeRead();
		isSuspended.set(false);
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
			isOpen = false;
			ioHandler.close(true);
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
		
		if (isOpen == true) {
			isOpen = false;

			timeoutMgmHandle.destroy();
						
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("closing connection -> flush all remaining data");
			}

			if (!isWriteBufferEmpty()) {
				ByteBuffer[] buffers = drainWriteQueue();
				ioHandler.write(buffers);
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

		removeWriteMark();
		if (getFlushmode() == FlushMode.SYNC) {

			// flush write queue by using a write guard to wait until the onWrittenEvent has been occurred
			syncFlush();


		} else {

			// just flush the queue and return
			if (!isWriteBufferEmpty()) {
				ByteBuffer[] buffers = drainWriteQueue();
				ioHandler.write(buffers);
			}
		}

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + getId() + "] flushed");
		}
	}




	/**
	 * flush, and wait until data has been written to channel
	 */
	@SuppressWarnings("unchecked")
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
				return "id=" + getId() + ", remote=" + getRemoteAddress().getCanonicalHostName() + "(" + getRemoteAddress() + ":" + getRemotePort() + ")";
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
				       ") lastTimeReceived=" + DataConverter.toFormatedDate(getLastTimeReceivedMillis()) + " reveived=" + getNumberOfReceivedBytes() + 
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

		
		public void onWriteException(IOException ioException, ByteBuffer data) {
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
		
		
		public void onData(ByteBuffer[] data) {
			
			appendDataToReadBuffer(data);
			
			try {
				((IDataHandler) appHandler).onData(NonBlockingConnection.this);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by performing onData callback on " + appHandler + " " + ioe.toString());
				}
			}
		}

		
		

		public void onConnectionAbnormalTerminated() {
			NonBlockingConnection.this.forceClose();
		}


		public void onConnect() {
			try {
				((IConnectHandler) appHandler).onConnect(NonBlockingConnection.this);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by performing onConnect callback on " + appHandler + " " + ioe.toString());
				}
			}
		}


		public void onDisconnect() {
			if (!disconnectOccured) {
				disconnectOccured = true;
				
				
				// call first onData
				try {
					((IDataHandler) appHandler).onData(NonBlockingConnection.this);
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("error occured by performing onData callback on " + appHandler + " " + ioe.toString());
					}
				}

				// then onDisconnect
				try {
					((IDisconnectHandler) appHandler).onDisconnect(NonBlockingConnection.this);
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("error occured by performing onDisconnect callback on " + appHandler + " " + ioe.toString());
					}
				}
			}
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
			namePrefix = "xNbcPool-" + poolNumber.getAndIncrement() + "-thread-";
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
