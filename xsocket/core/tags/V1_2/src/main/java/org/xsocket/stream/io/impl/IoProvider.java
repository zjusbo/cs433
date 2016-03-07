// $Id: ByteBufferParser.java 1333 2007-06-15 16:19:26Z grro $
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
package org.xsocket.stream.io.impl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.rmi.server.UID;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.xsocket.stream.io.spi.IAcceptor;
import org.xsocket.stream.io.spi.IAcceptorCallback;
import org.xsocket.stream.io.spi.IClientIoProvider;
import org.xsocket.stream.io.spi.IIoHandler;
import org.xsocket.stream.io.spi.IIoHandlerContext;
import org.xsocket.stream.io.spi.IServerIoProvider;




/**
 * Server and Client IoProvider<br><br>
 *
 * This class is a default implementation of the {@link org.xsocket.stream.io.spi} and shouldn't be used
 * outside this context. <br>
 * The readbuffer preallocation size and direct/non-direct mode should be set by System.properties. Please
 * note that current vm implementations (Juli/2007) could have problems by managing direct buffers. In this
 * case non-direct buffer should be used.
 * <pre>
 * ...
 * System.setProperty("org.xsocket.stream.ReadBufferPreallocationsizeServer", "32768");
 *
 * IMultithreadedServer server = new MultithreadedServer(new Handler());
 * StreamUtils.start(server);
 *
 *
 * ...
 * System.setProperty("org.xsocket.stream.ReadBufferPreallocationsizeClient", "4096");
 *
 * IBlockingConnection connection = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
 * connection.write(...);
 * </pre>
 *
 * @author grro@xsocket.org
 */
public final class IoProvider implements IClientIoProvider, IServerIoProvider {

	private static final Logger LOG = Logger.getLogger(IoProvider.class.getName());


	private static final Timer TIMER = new Timer("xIoTimer", true);
	private static IoSocketDispatcher globalDispatcher = null;

	// memory management
	public static final int DEFAULT_READ_BUFFER_PREALLOCATION_SIZE = 65536;
	public static final boolean DEFAULT_USE_DIRECT_BUFFER = true;
	public static final String USE_DIRECT_READ_BUFFER_CLIENT_KEY = "org.xsocket.stream.UseDirectReadBufferClient";
	public static final String READ_BUFFER_PREALLOCATIONSIZE_CLIENT_KEY = "org.xsocket.stream.ReadBufferPreallocationsizeClient";
	public static final String USE_DIRECT_READ_BUFFER_SERVER_KEY = "org.xsocket.stream.UseDirectReadBufferServer";
	public static final String READ_BUFFER_PREALLOCATIONSIZE_SERVER_KEY = "org.xsocket.stream.ReadBufferPreallocationsizeServer";
	private static Boolean useDirectReadBufferClient = null;
	private static int readBufferPreallocationsizeClient = DEFAULT_READ_BUFFER_PREALLOCATION_SIZE;
	private static Boolean useDirectReadBufferServer = null;
	private static int readBufferPreallocationsizeServer = DEFAULT_READ_BUFFER_PREALLOCATION_SIZE;

	private final IMemoryManager sslMemoryManagerServer = new SynchronizedMemoryManager(readBufferPreallocationsizeServer, useDirectReadBufferServer);
	private final IMemoryManager sslMemoryManagerClient = new SynchronizedMemoryManager(readBufferPreallocationsizeClient, useDirectReadBufferClient);


	////////////////////////////////////////////////
	// use direct buffer or non-direct buffer?
	//
	// current vm implementations (Juli/2007) seems to have
	// problems by gc direct buffers. mina therefore decided
	// to use non-direct allocated buffer by default with V2
	//
	// links
    // * [Java bugdatabase] http://bugs.sun.com/bugdatabase/view_bug.do;jsessionid=94d5403110224b692e5354bd87a92:WuuT?bug_id=6210541
	// * [forum thread]     http://forums.java.net/jive/thread.jspa?messageID=223706&tstart=0
	// * [mina]             https://issues.apache.org/jira/browse/DIRMINA-391
	//
	////////////////////////////////////////////////



    // id
	private final AtomicInteger nextId = new AtomicInteger();
	private static String idPrefix = null;


    static {

    	// Memory properties
    	try {
    		useDirectReadBufferClient = new Boolean(System.getProperty(IoProvider.USE_DIRECT_READ_BUFFER_CLIENT_KEY, Boolean.toString(DEFAULT_USE_DIRECT_BUFFER)));
    	} catch (Exception e) {
    		LOG.warning("invalid value for system property " + IoProvider.USE_DIRECT_READ_BUFFER_CLIENT_KEY + ": "
    				+ System.getProperty(IoProvider.USE_DIRECT_READ_BUFFER_CLIENT_KEY) + " (valid is true|false)"
    				+ " using direct buffer");
    		useDirectReadBufferClient = Boolean.TRUE;
    	}

   		try {
   			useDirectReadBufferServer = new Boolean(System.getProperty(IoProvider.USE_DIRECT_READ_BUFFER_SERVER_KEY, Boolean.toString(DEFAULT_USE_DIRECT_BUFFER)));
   		} catch (Exception e) {
   			LOG.warning("invalid value for system property " + IoProvider.USE_DIRECT_READ_BUFFER_SERVER_KEY + ": "
   					+ System.getProperty(IoProvider.USE_DIRECT_READ_BUFFER_SERVER_KEY) + " (valid is true|false)"
   					+ " using direct buffer");
   			useDirectReadBufferServer = Boolean.TRUE;
    	}

    	try {
    		readBufferPreallocationsizeClient = Integer.parseInt(System.getProperty(IoProvider.READ_BUFFER_PREALLOCATIONSIZE_CLIENT_KEY, Integer.toString(DEFAULT_READ_BUFFER_PREALLOCATION_SIZE)));
    	} catch (Exception e) {
    		LOG.warning("invalid value for system property " + IoProvider.READ_BUFFER_PREALLOCATIONSIZE_CLIENT_KEY + ": "
    				+ System.getProperty(IoProvider.READ_BUFFER_PREALLOCATIONSIZE_CLIENT_KEY)
    				+ " using default size " + DEFAULT_READ_BUFFER_PREALLOCATION_SIZE);
    		readBufferPreallocationsizeClient = DEFAULT_READ_BUFFER_PREALLOCATION_SIZE;
    	}

    	try {
    		readBufferPreallocationsizeServer = Integer.parseInt(System.getProperty(IoProvider.READ_BUFFER_PREALLOCATIONSIZE_SERVER_KEY, Integer.toString(DEFAULT_READ_BUFFER_PREALLOCATION_SIZE)));
    	} catch (Exception e) {
    		LOG.warning("invalid value for system property " + IoProvider.READ_BUFFER_PREALLOCATIONSIZE_SERVER_KEY + ": "
    				+ System.getProperty(IoProvider.READ_BUFFER_PREALLOCATIONSIZE_SERVER_KEY)
    				+ " using default size " + DEFAULT_READ_BUFFER_PREALLOCATION_SIZE);
    		readBufferPreallocationsizeServer = DEFAULT_READ_BUFFER_PREALLOCATION_SIZE;
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
    }


    /**
	 * {@inheritDoc}
	 */
	public IAcceptor createAcceptor(IAcceptorCallback callback, IIoHandlerContext handlerContext, InetSocketAddress address, int backlog, Map<String, Object> options) throws IOException {
		Acceptor acceptor = new Acceptor(callback, handlerContext, address, backlog);
		for (Entry<String, Object> entry : options.entrySet()) {
			acceptor.setOption(entry.getKey(), entry.getValue());
		}

		return acceptor;
	}


	/**
	 * {@inheritDoc}
	 */
	public IAcceptor create(IAcceptorCallback callback, IIoHandlerContext handlerContext, InetSocketAddress address,  int backlog, Map<String, Object> options, SSLContext sslContext, boolean sslOn) throws IOException {
		Acceptor acceptor = new Acceptor(callback, handlerContext, address, backlog, sslContext, sslOn);
		for (Entry<String, Object> entry : options.entrySet()) {
			acceptor.setOption(entry.getKey(), entry.getValue());
		}

		return acceptor;
	}


    /**
	 * {@inheritDoc}
	 */
    public IIoHandler createClientIoHandler(IIoHandlerContext ctx, InetSocketAddress remoteAddress, Map<String ,Object> options) throws IOException {
		return createIoHandler(ctx, true, getClientDispatcher(), openSocket(remoteAddress, options), null, false);
	}


    /**
	 * {@inheritDoc}
	 */
    public IIoHandler createSSLClientIoHandler(IIoHandlerContext ctx, InetSocketAddress remoteAddress, Map<String ,Object> options, SSLContext sslContext, boolean sslOn) throws IOException {
    	return createIoHandler(ctx, true, getClientDispatcher(), openSocket(remoteAddress, options), sslContext, sslOn);
    }


    /**
	 * {@inheritDoc}
	 */
    IIoHandler createIoHandler(IIoHandlerContext ctx, boolean isClient, IoSocketDispatcher dispatcher, SocketChannel channel, SSLContext sslContext, boolean sslOn) throws IOException {

    	String connectionId = null;

    	if (isClient) {
    		connectionId = idPrefix + ".c." + nextId.incrementAndGet();
    	} else {
    		connectionId = idPrefix + ".s." + nextId.incrementAndGet();
    	}

		ChainableIoHandler ioHandler = new IoSocketHandler(channel, dispatcher, ctx, connectionId);

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

		// supports multithread?
		if (ctx.isMultithreaded()) {
			ioHandler = new IoMultithreadedHandler(ioHandler, ctx);
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
				LOG.warning("connection is not SSL activatable (non IoActivateableHandler in chain");
				return false;
			}
		} catch (ClassCastException cce) {
			throw new IOException("only ioHandler of tpye " + ChainableIoHandler.class.getName() + " are supported");
		}
	}

	public void startSecuredMode(IIoHandler ioHandler, LinkedList<ByteBuffer> buffers) throws IOException {
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
		return useDirectReadBufferServer;
	}


	static int getReadBufferPreallocationsizeServer() {
		return readBufferPreallocationsizeServer;
	}


    private static SocketChannel openSocket(InetSocketAddress remoteAddress, Map<String ,Object> options) throws IOException {
    	SocketChannel channel = SocketChannel.open();

		for (Entry<String, Object> entry : options.entrySet()) {
			setOption(channel.socket(), entry.getKey(), entry.getValue());
		}


		try {
			channel.socket().connect(remoteAddress);
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by bindung socket to remote address " + remoteAddress + " " + ioe.toString());
			}
			throw ioe;
		}

		return channel;
    }




	private static void setOption(Socket socket, String name, Object value) throws IOException {

		if (name.equals(IClientIoProvider.SO_RCVBUF)) {
			socket.setReceiveBufferSize((Integer) value);

		} else if (name.equals(IClientIoProvider.SO_REUSEADDR)) {
			socket.setReuseAddress((Boolean) value);

		} else if (name.equals(IClientIoProvider.SO_SNDBUF)) {
			socket.setSendBufferSize((Integer) value);

		} else if (name.equals(IClientIoProvider.SO_KEEPALIVE)) {
			socket.setKeepAlive((Boolean) value);

		} else if (name.equals(IClientIoProvider.TCP_NODELAY)) {
			socket.setTcpNoDelay((Boolean) value);

		} else if (name.equals(IClientIoProvider.SO_LINGER)) {
			if (value instanceof Integer) {
				socket.setSoLinger(true, (Integer) value);
			} else if (value instanceof Boolean) {
				if (((Boolean) value).equals(Boolean.FALSE)) {
					socket.setSoLinger(Boolean.FALSE, 0);
				}
			}

		} else {
			LOG.warning("option " + name + " is not supported");
		}
	}



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
			globalDispatcher = new IoSocketDispatcher( new UnsynchronizedMemoryManager(readBufferPreallocationsizeClient, useDirectReadBufferClient));
			Thread t = new Thread(globalDispatcher);
			t.setName(IoSocketDispatcher.DISPATCHER_PREFIX + "#" + "CLIENT");
			t.setDaemon(true);
			t.start();

			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("client dispatcher created (readbuffer preallocation size=" + readBufferPreallocationsizeClient + ", useDirectBuffer=" + useDirectReadBufferClient + ")");
			}
		}
		return globalDispatcher;
	}
}