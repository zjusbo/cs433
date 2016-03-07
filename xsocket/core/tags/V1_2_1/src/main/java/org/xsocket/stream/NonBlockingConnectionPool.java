// $Id: BlockingConnection.java 1134 2007-04-05 17:44:43Z grro $
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
import java.lang.annotation.Inherited;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.ClosedConnectionException;
import org.xsocket.MaxReadSizeExceededException;




/**
 * A connection pool implementation. A connection pool will be used on the
 * client-side, if connections for the same server (address) will be created
 * in a serial manner in a sort period of time. By pooling such connections the overhead of establishing
 * a connection will be avoided
 * For pool management reasons, timeouts can be defined. The
 * IdleTimeout defines the max idle time in the pool. After this time the
 * free connection will be closed. In the same way, the max living time
 * defines the timout of the connection. If a free connection exceeds
 * this time, the connection will be closed.  <br>
 * Additional the max size of the active connections can be defined.
 * If a connection is requested and the max limit of the active connection
 * is reached, the request will be blocked until a connection becomes free
 * or the maxWaitTime will be reached. <br>
 *
 * <pre>
 *  // create  a connection pool with idle timeout 60 sec
 *  NonBlockingConnectionPool pool = new NonBlockingConnectionPool(60L * 1000L);
 *
 *  INonBlockingCinnection con = null;
 *
 *  try {
 *     // retrieve a connection (if no connection is in pool, a new one will be created)
 *     con = pool.getNonBlockingConnection(host, port);
 *     con.write("Hello");
 *     ...
 *
 *     // always close the connection! (the connection will be returned into the connection pool)
 *     con.close();
 *
 * 	} catch (IOException) {
 *     if (con != null) {
 *        try {
 *          // if the connection is invalid -> destroy it (it will not return into the pool)
 *          pool.destroyConnection(con);
 *        } catch (Exception ignore) { }
 *     }
 *  }
 * </pre>
 *
 * @author grro@xsocket.org
 */
public final class NonBlockingConnectionPool extends AbstractConnectionPool {

	private static final Logger LOG = Logger.getLogger(NonBlockingConnectionPool.class.getName());

	public static final long UNLIMITED_TIMEOUT = AbstractConnectionPool.MAX_TIMEOUT;


	/**
	 * constructor
	 *
	 * @param timeToIdleMillis  the max idle time in the pool. After this time the free connection will be closed
	 */
	public NonBlockingConnectionPool(long timeToIdleMillis) {
		this(timeToIdleMillis, Integer.MAX_VALUE, NULL);
	}


	public NonBlockingConnectionPool(long timeToIdleMillis, int maxActive, long maxWaitTimeMillis) {
		super(timeToIdleMillis, MAX_TIMEOUT, maxActive, maxWaitTimeMillis, MAX_SIZE);
	}


	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 * a new one will be created <br> <br>
	 *
	 * This method is thread safe
	 *
	 * @param host   the server address
	 * @param port   the sever port
	 * @return the connection
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
	 */
	public INonBlockingConnection getNonBlockingConnection(String host, int port) throws IOException, WaitTimeoutException {
		return (INonBlockingConnection) getConnection(new InetSocketAddress(host, port), null);
	}


	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 * a new one will be created <br> <br>
	 *
	 * This method is thread safe
	 *
	 * @param address  the server address
	 * @param port     the sever port
	 * @return the connection
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
	 */
	public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port) throws IOException, WaitTimeoutException {
		return (INonBlockingConnection) getConnection(new InetSocketAddress(address, port), null);
	}



	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 *  a new one will be created <br> <br>
	 *
	 * This method is thread safe
	 *
	 * @param address     the server address
	 * @param port        the sever port
 	 * @param appHandler  the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler and ITimeoutHandler)
	 * @return the connection
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
	 */
	public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port, IHandler appHandler) throws IOException, WaitTimeoutException {
		return getNonBlockingConnection(address, port, appHandler, null);
	}



	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 * a new one will be created <br> <br>
	 *
	 * This method is thread safe
	 *
	 * @param address     the server address
	 * @param port        the sever port
 	 * @param appHandler  the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler and ITimeoutHandler)
	 * @param workerPool  the worker pool to use
	 * @return the connection
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
	 */
	public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port, IHandler appHandler, Executor workerPool) throws IOException, WaitTimeoutException {
		return (INonBlockingConnection) getConnection(new InetSocketAddress(address, port), appHandler, workerPool);
	}


	private INonBlockingConnection getConnection(InetSocketAddress address, IHandler appHandler, Executor workerPool) throws IOException, WaitTimeoutException {
		PoolableNonBlockingConnection connection = (PoolableNonBlockingConnection) getConnection(address, workerPool);
		if (connection != null) {
			connection.setHandler(appHandler);
			connection.notiyConnect();
			return connection;
		} else {
			throw new IOException("couldn't create a connection to " + address);
		}
	}




	/**
	 * {@link Inherited}
	 */
	@Override
	PoolableConnection createConnection(InetSocketAddress address, Executor workerPool) throws IOException {
		if (workerPool != null) {
			return new PoolableNonBlockingConnection(address, workerPool);
		} else {
			return new PoolableNonBlockingConnection(address);
		}
	}



	private final class PoolableNonBlockingConnection extends PoolableConnection implements INonBlockingConnection {

		public PoolableNonBlockingConnection(InetSocketAddress address) throws IOException {
			super(NonBlockingConnectionPool.this, new NonBlockingConnection(address.getAddress(), address.getPort(), new HandlerProxy()), address);
			getHandlerProxy().init(this);
		}

		public PoolableNonBlockingConnection(InetSocketAddress address, Executor workerPool) throws IOException {
			super(NonBlockingConnectionPool.this, new NonBlockingConnection(address.getAddress(), address.getPort(), new HandlerProxy(), workerPool), address);
			getHandlerProxy().init(this);
		}

		public INonBlockingConnection setOption(String name, Object value) throws IOException {
			return getUnderlyingConnection().setOption(name, value);
		}

		void setHandler(IHandler handler) {
			getHandlerProxy().setHandler(handler);
		}

		private HandlerProxy getHandlerProxy() {
			return (HandlerProxy) getUnderlyingConnection().getAppHandler();
		}

		void notiyConnect() throws IOException {
			HandlerProxy hdlProxy = (HandlerProxy) getUnderlyingConnection().getAppHandler();
			hdlProxy.notifyConnect(getUnderlyingConnection());
		}

		@Override
		void reset() throws IOException {
			setHandler(null);

			super.reset();
		}

		public void setFlushmode(FlushMode flushMode) {
			getUnderlyingConnection().setFlushmode(flushMode);
		}

		public FlushMode getFlushmode() {
			return getUnderlyingConnection().getFlushmode();
		}

		public void setWriteTransferRate(int bytesPerSecond) throws ClosedConnectionException, IOException {
			getUnderlyingConnection().setWriteTransferRate(bytesPerSecond);
		}

		public ByteBuffer[] readAvailable() throws IOException, ClosedConnectionException {
			return getUnderlyingConnection().readAvailable();
		}

		public boolean readAvailableByDelimiter(String delimiter, WritableByteChannel outputChannel) throws IOException, ClosedConnectionException {
			return getUnderlyingConnection().readAvailableByDelimiter(delimiter, outputChannel);
		}

		public boolean readAvailableByDelimiter(String delimiter, String encoding, WritableByteChannel outputChannel) throws IOException, ClosedConnectionException {
			return getUnderlyingConnection().readAvailableByDelimiter(delimiter, encoding, outputChannel);
		}

		public TransferResult transferToAvailableByDelimiter(String delimiter, String encoding, WritableByteChannel outputChannel) throws ClosedConnectionException, IOException, SocketTimeoutException {
			return getUnderlyingConnection().transferToAvailableByDelimiter(delimiter, encoding, outputChannel);
		}
		
		public TransferResult transferToAvailableByDelimiter(String delimiter, WritableByteChannel outputChannel) throws ClosedConnectionException, IOException, SocketTimeoutException {
			return getUnderlyingConnection().transferToAvailableByDelimiter(delimiter, outputChannel);
		}
		
		public int getNumberOfAvailableBytes() {
			return getUnderlyingConnection().getNumberOfAvailableBytes();
		}

		@SuppressWarnings("deprecation")
		public int indexOf(String str) {
			return getUnderlyingConnection().indexOf(str);
		}

		private NonBlockingConnection getUnderlyingConnection() {
			return (NonBlockingConnection) getDelegee();
		}
	}


	private static final class HandlerProxy implements IDataHandler, IDisconnectHandler, ITimeoutHandler {

		private PoolableNonBlockingConnection poolableConnection = null;

		private IHandler handler = null;
		private boolean isDataHandler = false;
		private boolean isConnectHandler = false;
		private boolean isDisconnectHandler = false;
		private boolean isTimeoutHandler = false;

		void init(PoolableNonBlockingConnection poolableConnection) {
			this.poolableConnection = poolableConnection;
		}

		void setHandler(IHandler handler) {
			this.handler = handler;

			if (handler != null) {
				isDataHandler = (handler instanceof IDataHandler);
				isConnectHandler = (handler instanceof IConnectHandler);
				isDisconnectHandler = (handler instanceof IDisconnectHandler);
				isTimeoutHandler = (handler instanceof ITimeoutHandler);
			}
		}


		void notifyConnect(INonBlockingConnection connection) throws IOException {
			if (isConnectHandler) {
				((IConnectHandler) handler).onConnect(poolableConnection);
			}
		}

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			if (isDataHandler) {
				((IDataHandler) handler).onData(poolableConnection);
			}
			return true;
		}


		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			if (isDisconnectHandler) {
				((IDisconnectHandler) handler).onDisconnect(poolableConnection);
			}

			poolableConnection.reallyClose();
			return true;
		}


		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
			poolableConnection.setReuseable(false);

			boolean isHandled = false;
			if(isTimeoutHandler) {
				isHandled = ((ITimeoutHandler) handler).onConnectionTimeout(poolableConnection);
			}

			if (!isHandled) {
				poolableConnection.reallyClose();
			}

			return true;
		}


		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
			poolableConnection.setReuseable(false);

			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("idle timout occured for pooled connection " + poolableConnection.getId());
			}

			boolean isHandled = false;
			if(isTimeoutHandler) {
				isHandled = ((ITimeoutHandler) handler).onIdleTimeout(poolableConnection);
			}

			if (!isHandled) {
				poolableConnection.reallyClose();
			}

			return true;
		}
	}
}
