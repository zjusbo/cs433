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
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.xsocket.ClosedException;
import org.xsocket.DataConverter;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.spi.DefaultIoProvider;
import org.xsocket.connection.spi.IClientIoProvider;
import org.xsocket.connection.spi.IHandlerIoProvider;
import org.xsocket.connection.spi.IIoHandler;
import org.xsocket.connection.spi.IIoHandlerCallback;



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

	private static final Logger LOG = Logger.getLogger(BlockingConnection.class.getName());

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



	// closed flag
	private boolean isClosed = false;


	// io handler
	private static final DefaultIoProvider DEFAULT_CLIENT_IO_PROVIDER = new DefaultIoProvider(); // will be removed
	private static IClientIoProvider clientIoProvider = null;
	private final IoHandlerCallback ioHandlerCallback = new IoHandlerCallback();
	private IHandlerIoProvider ioProvider = null;
	private IIoHandler ioHandler = null;


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
	private boolean idleTimeoutOccured = false;
	private boolean connectionTimeoutOccured = false;
	private boolean disconnectOccured = false;


	// is secured
	private boolean isSecured = false;

	// cached values
	private Integer cachedSoSndBuf = null;


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

		IIoHandler ioHandler = createClientIoHandler(remoteAddress, connectTimeoutMillis, options, sslContext, isSecured);
		init(ioHandler, getClientIoProvider(), HandlerProxy.newPrototype(appHandler, null).newProxy(this), workerpool);

		setIdleTimeoutSec(Integer.MAX_VALUE);
		setConnectionTimeoutSec(Integer.MAX_VALUE);
	}



	/**
	 *  server-side constructor
	 */
	protected NonBlockingConnection() throws IOException {

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



	final void init(IIoHandler ioHandler, IHandlerIoProvider ioProvider, IHandler appHandler, Executor workerpool) throws IOException, SocketTimeoutException {
		this.ioHandler = ioHandler;
		this.ioProvider = ioProvider;
		this.appHandler = appHandler;
		setWorkerpool(workerpool);

		ioHandler.init(ioHandlerCallback);

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("connection " + getId() + " created. IoHandler: " + ioHandler.toString());
		}
	}
	


	/**
	 * sets he handler 
	 * 
	 * @param hdl the handler
	 */
	public final void setHandler(IHandler hdl) {	
		appHandler = HandlerProxy.newPrototype(hdl, null).newProxy(this);
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
		if (isClosed) {
			return false;
		} else {
			return ioHandler.isOpen();
		}
	}



	/**
	 * {@inheritDoc}
	 */
	public final void setWriteTransferRate(int bytesPerSecond) throws ClosedException, IOException {

		if (bytesPerSecond != UNLIMITED) {
			if (getFlushmode() != FlushMode.ASYNC) {
				LOG.warning("setWriteTransferRate is only supported for FlushMode ASYNC. Ignore update of the transfer rate");
				return;
			}
		}

		ioHandler = ioProvider.setWriteTransferRate(ioHandler, bytesPerSecond);
	}


	/**
	 * {@inheritDoc}
	 */
	public final void activateSecuredMode() throws IOException {

		boolean isPrestarted = DEFAULT_CLIENT_IO_PROVIDER.preStartSecuredMode(ioHandler);

		if (isPrestarted) {
			FlushMode currentFlushMode = getFlushmode();
			setFlushmode(FlushMode.ASYNC);

			internalFlush();

			setFlushmode(currentFlushMode);

			ByteBuffer[] buffer = readAvailableByteBuffer();
			DEFAULT_CLIENT_IO_PROVIDER.startSecuredMode(ioHandler, buffer);
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
				throw new ClosedException("data source is already closed");
			}
			
		} catch (BufferUnderflowException bue) {
			if (isOpen()) {
				throw bue;

			} else {
				throw new ClosedException("data source is already closed");
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
				throw new ClosedException("data source is already closed");
			}
		}
	}


	/**
	 * {@inheritDoc}
	 */
	protected ByteBuffer readSingleByteBuffer(int length) throws IOException, ClosedException, BufferUnderflowException {
		try {
			return super.readSingleByteBuffer(length);

		} catch (BufferUnderflowException bue) {

			if (isOpen()) {
				throw bue;

			} else {
				throw new ClosedException("data source is already closed");
			}
		}
	}




	/**
	 * {@inheritDoc}
	 *
	 */
	public final void setIdleTimeoutSec(int timeoutInSec) {
		ioHandler.setIdleTimeoutSec(timeoutInSec);
		idleTimeoutOccured = false;
	}


	/**
	 * {@inheritDoc}
	 */
	public final void setConnectionTimeoutSec(int timeoutSec) {
		ioHandler.setConnectionTimeoutSec(timeoutSec);
		connectionTimeoutOccured = false;
	}


	/**
	 * {@inheritDoc}
	 */
	public int getRemainingSecToConnectionTimeout() {
		return ioHandler.getRemainingSecToConnectionTimeout();
	}


	/**
	 * {@inheritDoc}
	 */
	public int getRemainingSecToIdleTimeout() {
		return ioHandler.getRemainingSecToIdleTimeout();
	}


	/**
	 * {@inheritDoc}
	 */
	public final int getConnectionTimeoutSec() {
		return ioHandler.getConnectionTimeoutSec();
	}



	/**
	 * {@inheritDoc}
	 *
	 */
	public final int getIdleTimeoutSec() {
		return ioHandler.getIdleTimeoutSec();
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
	}


	/**
	 * {@inheritDoc}
	 */
	public final void resumeRead() throws IOException {
		ioHandler.resumeRead();
	}



	/**
	 * {@inheritDoc}
	 */
	public long transferFrom(ReadableByteChannel sourceChannel) throws ClosedException, IOException {
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
	protected void onWriteDataInserted() throws IOException, ClosedException {
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



	private void forceClose() {
		try {
			isClosed = true;
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
		if (isOpen() && !isClosed) {
			isClosed = true;

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
	public final void flush() throws ClosedException, IOException {
		internalFlush();
	}


	private void internalFlush() throws ClosedException, IOException {

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
	private void syncFlush() throws ClosedException, IOException {

		long start = System.currentTimeMillis();
		long remainingTime = sendTimeoutMillis;

		if (DefaultIoProvider.isDispatcherThread()) {
			String msg = "synchronized flushing in NonThreaded mode could cause dead locks (hint: set flush mode to ASYNC)";
			LOG.warning(msg);
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


	


	@SuppressWarnings("unchecked")
	private static IClientIoProvider getClientIoProvider() {
		if (clientIoProvider == null) {
			// IoHandlerManager
			String clientIoManagerClassname = System.getProperty(IClientIoProvider.PROVIDER_CLASSNAME_KEY, DefaultIoProvider.class.getName());
			try {
				Class clientIoManagerClass = Class.forName(clientIoManagerClassname, true, Thread.currentThread().getContextClassLoader());
				clientIoProvider = (IClientIoProvider) clientIoManagerClass.newInstance();
			} catch (Exception e) {
				LOG.warning("error occured by creating ClientIoManager " + clientIoManagerClassname + ": " + e.toString());
				LOG.info("using default ClientIoManager " + DEFAULT_CLIENT_IO_PROVIDER.getClass().getName());
				clientIoProvider = DEFAULT_CLIENT_IO_PROVIDER;
			}
		}

		return clientIoProvider;
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


	/**
	 * {@inheritDoc}
	 */
	public String dump() {
		StringBuilder sb = new StringBuilder(toString());
		sb.append(" readQueueSize=" + available() + " writeQueueSize=" + super.getWriteBufferSize());
		//sb.append(" IOHandler " + ioHandler.toString());

		return sb.toString();
	}




	private static IIoHandler createClientIoHandler(InetSocketAddress remoteAddress, int connectTimeoutMillis, Map<String, Object> options, SSLContext sslContext, boolean sslOn) throws IOException {
		IIoHandler ioHandler = null;
		if (sslContext != null) {
			ioHandler = ((DefaultIoProvider) getClientIoProvider()).createSSLClientIoHandler(remoteAddress, connectTimeoutMillis, options, sslContext, sslOn);
		} else {
			ioHandler = getClientIoProvider().createClientIoHandler(remoteAddress, connectTimeoutMillis, options);
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
			if (LOG.isLoggable(Level.FINE)) {
				int size = 0;
				for (ByteBuffer byteBuffer : data) {
					size += byteBuffer.remaining();
				}
				LOG.fine("adding " + size + " bytes to receive buffer");
			}
			
			appendDataToReadBuffer(data);
			
			
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("calling app handler " + appHandler);
			}
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
				try {
					((IDisconnectHandler) appHandler).onDisconnect(NonBlockingConnection.this);
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("error occured by performing onDisconnect callback on " + appHandler + " " + ioe.toString());
					}
				}
			}
		}

		public void onConnectionTimeout() {
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
				setConnectionTimeoutSec(IConnection.MAX_TIMEOUT_SEC);
			}
		}


		public void onIdleTimeout() {
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
				setIdleTimeoutSec(IConnection.MAX_TIMEOUT_SEC);
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
