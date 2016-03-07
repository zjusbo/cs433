// $Id: NonBlockingConnection.java 1754 2007-09-22 15:56:25Z grro $
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
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.xsocket.ClosedConnectionException;
import org.xsocket.DataConverter;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.stream.io.spi.IHandlerIoProvider;
import org.xsocket.stream.io.spi.IIoHandler;
import org.xsocket.stream.io.spi.IIoHandlerContext;




/**
 * Implementation of the <code>INonBlockingConnection</code> interface. <br><br>
 *
 * A newly created connection is in the open state. Write or rad methods can be called immediately
 *
 * The methods of this class are not thread-safe.
 *
 * @author grro@xsocket.org
 */
public class NonBlockingConnection extends Connection implements INonBlockingConnection {

	private static final Logger LOG = Logger.getLogger(BlockingConnection.class.getName());

	private static final Executor DEFAULT_WORKER_POOL = Executors.newCachedThreadPool();



	private IHandler appHandler = null;
	private boolean disconnectOccured = false;




	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
	 * For multithreading issues see {@link NonBlockingConnection#NonBlockingConnection(InetAddress, int, IHandler, Executor)}.
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
	 * For multithreading issues see {@link NonBlockingConnection#NonBlockingConnection(InetAddress, int, IHandler, Executor)}.
	 *
	 * @param address   the remote host
	 * @param port		the port of the remote host to connect
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port) throws IOException {
		this(new InetSocketAddress(address, port), Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, (Object) null, (Executor) null);
	}



	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 * For multithreading issues see {@link NonBlockingConnection#NonBlockingConnection(InetAddress, int, IHandler, Executor)}.
	 *
	 * @param address                the remote host
	 * @param port		             the port of the remote host to connect
	 * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, int connectTimeoutMillis) throws IOException {
		this(new InetSocketAddress(address, port), connectTimeoutMillis, new HashMap<String, Object>(), null, false, (Object) null, (Executor) null);
	}



	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 * For multithreading issues see {@link NonBlockingConnection#NonBlockingConnection(InetAddress, int, IHandler, Executor)}.
	 *
	 * @param address              the remote host
	 * @param port	          	   the port of the remote host to connect
	 * @param options              the socket options
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, Map<String, Object> options) throws IOException {
		this(new InetSocketAddress(address, port), Integer.MAX_VALUE, options, null, false, (Object) null, (Executor) null);
	}


	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 * For multithreading issues see {@link NonBlockingConnection#NonBlockingConnection(InetAddress, int, IHandler, Executor)}.
	 *
	 * @param address                the remote host
	 * @param port	          	     the port of the remote host to connect
	 * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @param options                the socket options
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, int connectTimeoutMillis, Map<String, Object> options) throws IOException {
		this(new InetSocketAddress(address, port), connectTimeoutMillis, options, null, false, (Object) null, (Executor) null);
	}





	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 * For multithreading issues see {@link NonBlockingConnection#NonBlockingConnection(InetAddress, int, IHandler, Executor)}.
	 *
	 *
	 * @param address          the remote address
	 * @param port             the remote port
	 * @param appHandler       the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler and ITimeoutHandler)
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler) throws IOException {
		this(new InetSocketAddress(address, port), Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, appHandler, DEFAULT_WORKER_POOL);
	}





	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 * For multithreading issues see {@link NonBlockingConnection#NonBlockingConnection(InetAddress, int, IHandler, Executor)}.
	 *
	 *
	 * @param address                the remote address
	 * @param port                   the remote port
 	 * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @param appHandler             the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler and ITimeoutHandler)
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, int connectTimeoutMillis, IHandler appHandler) throws IOException {
		this(new InetSocketAddress(address, port), connectTimeoutMillis, new HashMap<String, Object>(), null, false, appHandler, DEFAULT_WORKER_POOL);
	}



	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
	 * For multithreading issues see {@link NonBlockingConnection#NonBlockingConnection(InetAddress, int, IHandler, Executor)}.
	 *
	 * @param address              the remote address
	 * @param port                 the remote port
	 * @param options              the socket options
	 * @param appHandler           the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler and ITimeoutHandler)
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, Map<String, Object> options, IHandler appHandler) throws IOException {
		this(new InetSocketAddress(address, port), Integer.MAX_VALUE, options, null, false, appHandler, DEFAULT_WORKER_POOL);
	}


	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
	 * For multithreading issues see {@link NonBlockingConnection#NonBlockingConnection(InetAddress, int, IHandler, Executor)}.
	 *
	 * @param address                the remote address
	 * @param port                   the remote port
 	 * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @param options                the socket options
	 * @param appHandler             the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler and ITimeoutHandler)
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, int connectTimeoutMillis, Map<String, Object> options, IHandler appHandler) throws IOException {
		this(new InetSocketAddress(address, port), connectTimeoutMillis, options, null, false, appHandler, DEFAULT_WORKER_POOL);
	}





	/**
	 * constructor <br><br>
	 *
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
	 * For multithreading issues see {@link NonBlockingConnection#NonBlockingConnection(InetAddress, int, IHandler, Executor)}.
	 *
	 * @param hostname         the remote host
	 * @param port             the remote port
	 * @param appHandler       the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler and ITimeoutHandler)
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port, IHandler appHandler) throws IOException {
		this(new InetSocketAddress(hostname, port), Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, appHandler, DEFAULT_WORKER_POOL);
	}


	/**
	 * constructor <br><br>
	 *
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
	 * For multithreading issues see {@link NonBlockingConnection#NonBlockingConnection(InetAddress, int, IHandler, Executor)}.
	 *
	 * @param hostname         the remote host
	 * @param port             the remote port
	 * @param appHandler       the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler and ITimeoutHandler)
	 * @param workerPool       the worker pool to use or <null>
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port, IHandler appHandler, Executor workerPool) throws IOException {
		this(new InetSocketAddress(hostname, port), Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, appHandler, workerPool);
	}




	public NonBlockingConnection(String hostname, int port, Object appHandler) throws IOException {
		this(new InetSocketAddress(hostname, port), Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, appHandler, DEFAULT_WORKER_POOL);
	}



	/**
	 * constructor <br><br>
	 *
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
	 * For multithreading issues see {@link NonBlockingConnection#NonBlockingConnection(InetAddress, int, IHandler, Executor)}.
	 *
	 * @param hostname             the remote host
	 * @param port                 the remote port
	 * @param options              the socket options
	 * @param appHandler           the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler and ITimeoutHandler)
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port, Map<String, Object> options, IHandler appHandler) throws IOException {
		this(new InetSocketAddress(hostname, port), Integer.MAX_VALUE, options, null, false, appHandler, DEFAULT_WORKER_POOL);
	}


	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 * For multithreading issues see {@link NonBlockingConnection#NonBlockingConnection(InetAddress, int, IHandler, Executor)}.
	 *
	 *
	 * @param address              the remote address
	 * @param port                 the remote port
	 * @param sslContext           the ssl context to use
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IConnection#activateSecuredMode()})
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(address, port), Integer.MAX_VALUE, new HashMap<String, Object>(), sslContext, sslOn, (Object) null, (Executor) null);
	}



	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 * For multithreading issues see {@link NonBlockingConnection#NonBlockingConnection(InetAddress, int, IHandler, Executor)}.
	 *
	 *
	 * @param address              the remote address
	 * @param port                 the remote port
	 * @param options              the socket options
	 * @param sslContext           the ssl context to use
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IConnection#activateSecuredMode()})
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, Map<String, Object> options, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(address, port), Integer.MAX_VALUE, options, sslContext, sslOn, (Object) null, (Executor) null);
	}



	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 * For multithreading issues see {@link NonBlockingConnection#NonBlockingConnection(InetAddress, int, IHandler, Executor)}.
	 *
	 *
	 * @param hostname             the remote host
	 * @param port                 the remote port
	 * @param sslContext           the ssl context to use
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IConnection#activateSecuredMode()})
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(hostname, port), Integer.MAX_VALUE, new HashMap<String, Object>(), sslContext, sslOn, (Object) null, (Executor) null);
	}



	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 * For multithreading issues see {@link NonBlockingConnection#NonBlockingConnection(InetAddress, int, IHandler, Executor)}.
	 *
	 *
	 * @param hostname             the remote host
	 * @param port                 the remote port
	 * @param options              the socket options
	 * @param sslContext           the ssl context to use
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IConnection#activateSecuredMode()})
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port, Map<String, Object> options, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(hostname, port), Integer.MAX_VALUE, options, sslContext, sslOn, (Object) null, (Executor) null);
	}



	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
	 * <b>Multithreading note</b><br>
	 * The data of the </code>NonBlockingConnection</code> will be read and written by using a central dispatcher (selector) thread.
	 * The handler`s call back methods (onData, onConnect, ...) will be call by the worker pool`s worker thread.
	 * By using this (client-side) constructor, the workerpool will be set manually. For a construtor which doesn`t support the workerpool
	 * parameter, a default (vm singleton) CachedThreadPool {@link Executors#newCachedThreadPool()} will be used. <br>
	 * By setting the workerPool with <code>null</code>, the multithreading is "switched off". This means the call back methods will
	 * be executed by the central dispatcher thread. The workerPool can also be shared with a server, which runs in the same process. E.g.
	 * <pre>
	 * ...
	 * // create a new server instance (a associated WorkerPool will be created automatically)
	 * IServer server = new Server(new TestHandler());
	 * StreamUtils.start(server);
	 * ...
	 *
	 * INonBlockingConnection connection = new NonBlockingConnection(host, port, clientHandler, server.getWorkerpool());
	 * ...
	 *
	 * </pre>
	 *
	 *
	 *
	 * @param address          the remote address
	 * @param port             the remote port
	 * @param appHandler       the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler and ITimeoutHandler)
	 * @param workerPool       the worker pool to use or <null>
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, Executor workerPool) throws IOException {
		this(new InetSocketAddress(address, port), Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, appHandler, workerPool);
	}


	public NonBlockingConnection(InetAddress address, int port, int connectTimeoutMillis, IHandler appHandler, Executor workerPool) throws IOException {
		this(new InetSocketAddress(address, port), connectTimeoutMillis, new HashMap<String, Object>(), null, false, appHandler, workerPool);
	}


	/**
	 *  client constructor, which uses a specific dispatcher
	 */

	private NonBlockingConnection(InetSocketAddress remoteAddress, int connectTimeoutMillis, Map<String, Object> options, SSLContext sslContext, boolean sslOn, Object appHandler, Executor workerPool) throws IOException {
		this(remoteAddress, connectTimeoutMillis, options, sslContext, sslOn, new IoHandlerContext(appHandler, workerPool), appHandler);
	}


	private NonBlockingConnection(InetSocketAddress remoteAddress, int connectTimeoutMillis, Map<String, Object> options, SSLContext sslContext, boolean sslOn, IoHandlerContext handlerCtx, Object appHandler) throws IOException {
		super(handlerCtx, remoteAddress, connectTimeoutMillis, options, sslContext, sslOn);

		if (handlerCtx.isDynamicHandler()) {
			this.appHandler = DynamicHandlerAdapterFactory.getInstance().createHandlerAdapter(handlerCtx, appHandler);
		} else {
			this.appHandler= (IHandler) appHandler;
		}


		if (LOG.isLoggable(Level.FINE)) {
			if ((appHandler instanceof IConnectionScoped)) {
				LOG.fine("handler type IConnectionScoped is not supported in the client context");
			}

			if ((appHandler instanceof org.xsocket.ILifeCycle)) {
				LOG.fine("ILifeCycle is not supported in the client context");
			}
		}

		init();
	}


	/**
	 *  server-side constructor
	 */
	protected NonBlockingConnection(IIoHandlerContext ctx, IIoHandler ioHandler, IHandler appHandler, IHandlerIoProvider ioProvider) throws IOException {
		super(ctx, ioHandler, ioProvider);
		this.appHandler = appHandler;

		init();
	}


	final IHandler getAppHandler() {
		return appHandler;
	}



	@Override
	final void reset() throws IOException {
		try {
			setWriteTransferRate(UNLIMITED);
		} catch (Exception e) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by reseting (setWriteTransferRate). Reason: " + e.toString());
			}
		}
		super.reset();

		setFlushmode(INonBlockingConnection.INITIAL_FLUSH_MODE);
	}




	/**
	 * {@inheritDoc}
	 */
	public final void setWriteTransferRate(int bytesPerSecond) throws ClosedConnectionException, IOException {

		if (bytesPerSecond != UNLIMITED) {
			if (getFlushmode() != FlushMode.ASYNC) {
				LOG.warning("setWriteTransferRate is only supported for FlushMode ASYNC. Ignore update of the transfer rate");
				return;
			}
		}

		setIoHandler(getIoProvider().setWriteTransferRate(getIoHandler(), bytesPerSecond));
	}





	/**
	 * {@inheritDoc}
	 */
	public final int getNumberOfAvailableBytes() {
		return getReadQueue().getSize();
	}


	/**
	 * {@inheritDoc}
	 */
	public final ByteBuffer[] readAvailable() throws IOException {
		LinkedList<ByteBuffer> buffers = extractAvailableFromReadQueue();
		if (buffers != null) {
			return buffers.toArray(new ByteBuffer[buffers.size()]);
		} else {
			return new ByteBuffer[0];
		}
	}


	/**
	 * {@inheritDoc}
	 */
	public final TransferResult transferAvailableByDelimiter(String delimiter, WritableByteChannel outputChannel) throws IOException, ClosedConnectionException {
		return transferAvailableByDelimiter(delimiter, getDefaultEncoding(), outputChannel);
	}

	/**
	 * {@inheritDoc}
	 */
	public final TransferResult transferAvailableByDelimiter(String delimiter, String encoding, WritableByteChannel outputChannel) throws IOException, ClosedConnectionException {
		return transferAvailableFromReadQueue(delimiter.getBytes(encoding), outputChannel);
	}


	/**
	 * {@inheritDoc}
	 */
	public final int read(ByteBuffer buffer) throws IOException {
		int size = buffer.remaining();

		int available = getNumberOfAvailableBytes();
		if (available < size) {
			size = available;
		}

		ByteBuffer[] bufs = readByteBufferByLength(size);
		for (ByteBuffer buf : bufs) {
			while (buf.hasRemaining()) {
				buffer.put(buf);
			}
		}

		return size;
	}


	/**
	 * {@inheritDoc}
	 */
	public final byte readByte() throws IOException, ClosedConnectionException, BufferUnderflowException {
		return extractByteFromReadQueue();
	}

	/**
	 * {@inheritDoc}
	 */
	public final List<ByteBuffer[]> readAvailableByteBufferByDelimiter(String delimiter) throws IOException {
		List<ByteBuffer[]> result = new ArrayList<ByteBuffer[]>();

		try {
			while (true) {
				result.add(readByteBufferByDelimiter(delimiter));
			}
		} catch (BufferUnderflowException bue) {

		} catch (ClosedConnectionException cce) { }

		return result;
	}

	/**
	 * {@inheritDoc}
	 */
	public final List<ByteBuffer[]> readAvailableByteBufferByDelimiter(String delimiter, String encoding) throws IOException {
		List<ByteBuffer[]> result = new ArrayList<ByteBuffer[]>();

		try {
			while (true) {
				result.add(readByteBufferByDelimiter(delimiter, encoding));
			}
		} catch (BufferUnderflowException bue) {

		} catch (ClosedConnectionException cce) { }

		return result;
	}


	/**
	 * {@inheritDoc}
	 */
	public final ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException, ClosedConnectionException, BufferUnderflowException {
		return readByteBufferByDelimiter(delimiter, Integer.MAX_VALUE);
	}


	/**
	 * {@inheritDoc}
	 */
	public final ByteBuffer[] readByteBufferByDelimiter(String delimiter, int maxLength) throws IOException, ClosedConnectionException, MaxReadSizeExceededException, BufferUnderflowException {
		return readByteBufferByDelimiter(delimiter, getDefaultEncoding(), maxLength);
	}

	/**
	 * {@inheritDoc}
	 */
	public final ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding) throws IOException, ClosedConnectionException, MaxReadSizeExceededException, BufferUnderflowException {
		return readByteBufferByDelimiter(delimiter, encoding, Integer.MAX_VALUE);
	}


	/**
	 * {@inheritDoc}
	 */
	public final ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, ClosedConnectionException, MaxReadSizeExceededException {
		LinkedList<ByteBuffer> result = extractBytesByDelimiterFromReadQueue(delimiter.getBytes(encoding), maxLength);
		return result.toArray(new ByteBuffer[result.size()]);
	}

	/**
	 * {@inheritDoc}
	 */
	public final ByteBuffer[] readByteBufferByLength(int length) throws IOException, ClosedConnectionException, BufferUnderflowException {
		LinkedList<ByteBuffer> extracted = extractBytesByLength(length);

		return extracted.toArray(new ByteBuffer[extracted.size()]);
	}

	/**
	 * {@inheritDoc}
	 */
	public final List<byte[]> readAvailableBytesByDelimiter(String delimiter) throws IOException {
		List<byte[]> result = new ArrayList<byte[]>();

		try {
			while (true) {
				result.add(readBytesByDelimiter(delimiter));
			}
		} catch (BufferUnderflowException bue) {

		} catch (ClosedConnectionException cce) { }

		return result;
	}


	/**
	 * {@inheritDoc}
	 */
	public final List<byte[]> readAvailableBytesByDelimiter(String delimiter, String encoding) throws IOException {
		List<byte[]> result = new ArrayList<byte[]>();

		try {
			while (true) {
				result.add(readBytesByDelimiter(delimiter, encoding));
			}
		} catch (BufferUnderflowException bue) {

		} catch (ClosedConnectionException cce) { }

		return result;
	}



	/**
	 * {@inheritDoc}
	 */
	public final byte[] readBytesByDelimiter(String delimiter) throws IOException, ClosedConnectionException, BufferUnderflowException {
		return readBytesByDelimiter(delimiter, Integer.MAX_VALUE);
	}


	/**
	 * {@inheritDoc}
	 */
	public final byte[] readBytesByDelimiter(String delimiter, String encoding) throws IOException, ClosedConnectionException, MaxReadSizeExceededException, BufferUnderflowException {
		return readBytesByDelimiter(delimiter, encoding, Integer.MAX_VALUE);
	}


	/**
	 * {@inheritDoc}
	 */
	public final byte[] readBytesByDelimiter(String delimiter, int maxLength) throws IOException, ClosedConnectionException, MaxReadSizeExceededException, BufferUnderflowException {
		return readBytesByDelimiter(delimiter, getDefaultEncoding(), maxLength);
	}


	/**
	 * {@inheritDoc}
	 */
	public final byte[] readBytesByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, ClosedConnectionException, MaxReadSizeExceededException {
		return DataConverter.toBytes(readByteBufferByDelimiter(delimiter, encoding, maxLength));
	}


	/**
	 * {@inheritDoc}
	 */
	public final byte[] readBytesByLength(int length) throws IOException, ClosedConnectionException, BufferUnderflowException {
		return DataConverter.toBytes(readByteBufferByLength(length));
	}


	/**
	 * {@inheritDoc}
	 */
	public final double readDouble() throws IOException, ClosedConnectionException, BufferUnderflowException {
		return extractDoubleFromReadQueue();
	}


	/**
	 * {@inheritDoc}
	 */
	public final int readInt() throws IOException, ClosedConnectionException, BufferUnderflowException {
		return extractIntFromReadQueue();
	}

	/**
	 * {@inheritDoc}
	 */
	public final short readShort() throws IOException, ClosedConnectionException, BufferUnderflowException {
		return extractShortFromReadQueue();
	}



	/**
	 * {@inheritDoc}
	 */
	public final long readLong() throws IOException, ClosedConnectionException, BufferUnderflowException {
		return extractLongFromReadQueue();
	}


	/**
	 * {@inheritDoc}
	 */
	public final String readStringByDelimiter(String delimiter) throws IOException, ClosedConnectionException, BufferUnderflowException, UnsupportedEncodingException {
		return readStringByDelimiter(delimiter, Integer.MAX_VALUE);
	};


	/**
	 * {@inheritDoc}
	 */
	public final List<String> readAvailableStringsByDelimiter(String delimiter) throws IOException, UnsupportedEncodingException {
		List<String> result = new ArrayList<String>();

		try {
			while (true) {
				result.add(readStringByDelimiter(delimiter));
			}
		} catch (BufferUnderflowException bue) {

		} catch (ClosedConnectionException cce) { }

		return result;
	};


	/**
	 * {@inheritDoc}
	 */
	public final List<String> readAvailableStringsByDelimiter(String delimiter, String encoding) throws IOException, UnsupportedEncodingException {
		List<String> result = new ArrayList<String>();

		try {
			while (true) {
				result.add(readStringByDelimiter(delimiter, encoding));
			}
		} catch (BufferUnderflowException bue) {

		} catch (ClosedConnectionException cce) { }

		return result;
	};



	/**
	 * {@inheritDoc}
	 */
	public final String readStringByDelimiter(String delimiter, int maxLength) throws IOException, ClosedConnectionException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException {
		return readStringByDelimiter(delimiter, getDefaultEncoding(), maxLength);
	};


	/**
	 * {@inheritDoc}
	 */
	public final String readStringByDelimiter(String delimiter, String encoding) throws IOException, ClosedConnectionException, BufferUnderflowException, UnsupportedEncodingException {
		return readStringByDelimiter(delimiter, encoding, Integer.MAX_VALUE);
	}


	/**
	 * {@inheritDoc}
	 */
	public final String readStringByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, ClosedConnectionException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException {
		LinkedList<ByteBuffer> extracted = extractBytesByDelimiterFromReadQueue(delimiter.getBytes(encoding), maxLength);

		return DataConverter.toString(extracted, encoding);
	}


	/**
	 * {@inheritDoc}
	 */
	public final String readStringByLength(int length) throws IOException, ClosedConnectionException, BufferUnderflowException, UnsupportedEncodingException {
		return readStringByLength(length, getDefaultEncoding());
	}


	/**
	 * {@inheritDoc}
	 */
	public final String readStringByLength(int length, String encoding) throws IOException, ClosedConnectionException, BufferUnderflowException, UnsupportedEncodingException {
		LinkedList<ByteBuffer> extracted = extractBytesByLength(length);
		return DataConverter.toString(extracted, encoding);
	}




	/**
	 * {@inheritDoc}
	 */
	public final int indexOf(String str) throws IOException, ClosedConnectionException, BufferUnderflowException {
		return indexOf(str, Integer.MAX_VALUE);
	}


	/**
	 * {@inheritDoc}
	 */
	public final int indexOf(String str, int maxLength) throws IOException, ClosedConnectionException, BufferUnderflowException, MaxReadSizeExceededException {
		return indexOf(str, getDefaultEncoding(), maxLength);
	}


	/**
	 * {@inheritDoc}
	 */
	public final int indexOf(String str, String encoding, int maxLength) throws IOException, ClosedConnectionException, MaxReadSizeExceededException {
		return readIndexOf(str.getBytes(encoding), maxLength);
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public final INonBlockingConnection setOption(String name, Object value) throws IOException {
		return (INonBlockingConnection) super.setOption(name, value);
	}



	@Override
	protected final int onDataEvent() {

		int addSize = super.onDataEvent();

		if (addSize > 0) {
			if (appHandler != null) {
				boolean remaingDataToHandle = false;
				try {
					do {
						remaingDataToHandle = false;
						int insertVersion = getReadQueue().getInsertVersionVersion();
						int sizeBeforeHandle = getReadQueue().getSize();

						// calling onData method of the handler (return value will be ignored)
						try {

							((IDataHandler) appHandler).onData(NonBlockingConnection.this);

						} catch (MaxReadSizeExceededException mee) {
							try {
								close();
							} catch (Exception fe) {
								// ignore
							}

							return addSize;

						} catch (BufferUnderflowException bue) {
							// 	ignore
							return addSize;


						} catch (Exception e) {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("[" + getId() + "] closing connection because an error has been occured by handling data by appHandler. " + appHandler + " Reason: " + e.toString());
							}
							try {
								close();
							} catch (IOException ignore) { }
							return addSize;
						}


						// check if there is more data in readQueue, to decide if handle should be called again
						if (!getReadQueue().isEmpty()) {

							// has data be inserted meanwhile?
							if (insertVersion  != getReadQueue().getInsertVersionVersion()) {
								// yes ... re-run loop
								remaingDataToHandle = true;

							// no, than ...
							} else {
								// has data size of queue been changed?
								if (sizeBeforeHandle != getReadQueue().getSize()) {
									// yes ... re-run loop
									remaingDataToHandle = true;
								}
							}
						}

					} while (remaingDataToHandle);


				} catch (Exception e) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + getId() + "] closing connection because an error has been occured by handling data. Reason: " + e.toString());
					}
					try {
						close();
					} catch (IOException ignore) { }
				}
			}
		}

		return addSize;
	}







	@Override
	protected final void onConnectEvent() {

		try {
			if (appHandler != null) {
				if (getIoHandlerContext().isAppHandlerListenForConnectEvent()) {
					((IConnectHandler) appHandler).onConnect(NonBlockingConnection.this);
				}
			}

		} catch (MaxReadSizeExceededException mee) {
			try {
				close();
			} catch (Exception fe) {
				// ignore
			}

		} catch (BufferUnderflowException bue) {
			// 	ignore

		} catch (Exception e) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + getId() + "] closing connection because an error has been occured by on connect data. Reason: " + e.toString());
			}
			try {
				close();
			} catch (IOException ignore) { }
		}
	}



	@Override
	protected final void onDisconnectEvent() {
		if (!disconnectOccured) {
			disconnectOccured = true;
			try {
				if (appHandler != null) {
					if (getIoHandlerContext().isAppHandlerListenforDisconnectEvent()) {
						((IDisconnectHandler) appHandler).onDisconnect(NonBlockingConnection.this);
					}
				}
			} catch (Exception e) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + getId() + "] error occured by handling connect. Reason: " + e.toString());
				}
			}
		}
	}


	@Override
	protected final boolean onConnectionTimeoutEvent() {
		if (getIoHandlerContext().isAppHandlerListenForTimeoutEvent()) {
			try {
				if (appHandler != null) {
					boolean isHandled = ((ITimeoutHandler) appHandler).onConnectionTimeout(NonBlockingConnection.this);
					return isHandled;
				}
			} catch (MaxReadSizeExceededException mee) {
				try {
					close();
				} catch (Exception fe) {
					// ignore
				}

			} catch (BufferUnderflowException bue) {
				// 	ignore

			} catch (Exception e) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + getId() + "] closing connection because an error has been occured by on connect timeout. Reason: " + e.toString());
				}
				try {
					close();
				} catch (IOException ignore) { }
			}
		}

		return false;
	}


	@Override
	protected final boolean onIdleTimeoutEvent() {
		if (getIoHandlerContext().isAppHandlerListenForTimeoutEvent()) {
			try {
				if (appHandler != null) {
					boolean isHandled = ((ITimeoutHandler) appHandler).onIdleTimeout(NonBlockingConnection.this);
					return isHandled;
				}
			} catch (MaxReadSizeExceededException mee) {
				try {
					close();
				} catch (Exception fe) {
					// ignore
				}

			} catch (BufferUnderflowException bue) {
				// 	ignore

			} catch (Exception e) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + getId() + "] closing connection because an error has been occured by on idle timeout. Reason: " + e.toString());
				}
				try {
					close();
				} catch (IOException ignore) { }
			}
		};

		return false;
	}
}
