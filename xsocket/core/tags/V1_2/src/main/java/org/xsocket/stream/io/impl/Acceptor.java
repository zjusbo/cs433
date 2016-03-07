// $Id: Acceptor.java 1347 2007-06-17 15:51:55Z grro $
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
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;



import org.xsocket.IDispatcher;
import org.xsocket.stream.io.spi.IAcceptor;
import org.xsocket.stream.io.spi.IAcceptorCallback;
import org.xsocket.stream.io.spi.IIoHandler;
import org.xsocket.stream.io.spi.IIoHandlerContext;



/**
 * The acceptor ist responsible to accept new incomming connections, and
 * register these on the dispatcher.<br><br>
 * 
 * This class is a default implementation of the {@link org.xsocket.stream.io.spi} and shouldn't be used
 * outside this context. This is a framework-internal class  
 *
 * @author grro@xsocket.org
 */
final class Acceptor implements IAcceptor {

	private static final Logger LOG = Logger.getLogger(Acceptor.class.getName());
	
	private static final Map<String ,Class> SUPPORTED_OPTIONS = new HashMap<String, Class>();
	
	static {
		SUPPORTED_OPTIONS.put(SO_RCVBUF, Integer.class);
		SUPPORTED_OPTIONS.put(SO_REUSEADDR, Boolean.class);
	}


	// io handler
	private static final IoProvider IO_PROVIDER = new IoProvider();
	private IAcceptorCallback callback = null;
	private IIoHandlerContext handlerContext = null;
	
	// running flag
	private boolean isRunning = true;
		
	
	// Socket 
	private InetSocketAddress address = null;
	private ServerSocketChannel serverChannel = null;
	private int backlog = 0;

	
	// SSL
	private boolean sslOn = false;
	private SSLContext sslContext = null;


	// dispatcher management
	private final IoSocketDispatcherPool dispatcherPool = new IoSocketDispatcherPool(IoProvider.getReadBufferPreallocationsizeServer(), IoProvider.isUseDirectReadBufferServer());

	// statistics
	private long handledConnections = 0;

	
	public Acceptor(IAcceptorCallback callback, IIoHandlerContext handlerContext, InetSocketAddress address, int backlog) throws IOException {
		this(callback, handlerContext, address, backlog, null, false);
	}
	
	
	
	public Acceptor(IAcceptorCallback callback, IIoHandlerContext handlerContext, InetSocketAddress address, int backlog, SSLContext sslContext, boolean sslOn) throws IOException {
		this.callback = callback;
		this.address = address;
		this.handlerContext = handlerContext;
		this.backlog = backlog;
		this.sslContext = sslContext;
		this.sslOn = sslOn;
		
		
		LOG.fine("try to bind server on " + address);
		
		// create a new server socket 
		serverChannel = ServerSocketChannel.open();
		serverChannel.configureBlocking(true);
		
		serverChannel.socket().setReuseAddress(true); // set reuse address by default (can be override by socketConfig)
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public InetSocketAddress getLocalAddress() {
		return new InetSocketAddress(serverChannel.socket().getInetAddress(), serverChannel.socket().getLocalPort());
	}
	

	void setOption(String name, Object value) throws IOException {
	
		if (name.equals(IAcceptor.SO_RCVBUF)) {
			serverChannel.socket().setReceiveBufferSize((Integer) value);
			
		} else if (name.equals(IAcceptor.SO_REUSEADDR)) {
			serverChannel.socket().setReuseAddress((Boolean) value);
						
		} else {
			LOG.warning("option " + name + " is not supproted for " + this.getClass().getName());
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 */	
	public Object getOption(String name) throws IOException {

		if (name.equals(IAcceptor.SO_RCVBUF)) {
			return serverChannel.socket().getReceiveBufferSize();
			
		} else if (name.equals(IAcceptor.SO_REUSEADDR)) {
			return serverChannel.socket().getReuseAddress();
			
		} else {
			LOG.warning("option " + name + " is not supproted for " + this.getClass().getName());
			return null;
		}
	}
	
	
	public Map<String, Class> getOptions() {
		return Collections.unmodifiableMap(SUPPORTED_OPTIONS);
	}
	
		
	/**
	 * {@inheritDoc}
	 */
	public void listen() throws IOException {
		
		try {
			setDispatcherPoolSize(Runtime.getRuntime().availableProcessors() + 1);		
			
			// and bind it to the port and notify callback 
			serverChannel.socket().bind(address, backlog);
			
			callback.onConnected();
			
			// now accepting 
			accept();
			
		} catch (BindException be) {
			if (serverChannel != null) {
				serverChannel.close();
			}
			LOG.info("error occured while binding server on on " + address + ". Reason: " + be.toString());
			throw be;
		}
	}
	
	
	private void accept() {
		dispatcherPool.run();
		
		// acceptor loop
		while (isRunning) {
			try {
			
				// blocking accept call
				SocketChannel channel = serverChannel.accept();
				
				// create IoSocketHandler
				IoSocketDispatcher dispatcher = dispatcherPool.nextDispatcher();
				IIoHandler ioHandler = IO_PROVIDER.createIoHandler(handlerContext, false, dispatcher, channel, sslContext, sslOn);

				// notify callback
				callback.onConnectionAccepted(ioHandler);
				handledConnections++;
					
			} catch (Throwable t) {
				if (LOG.isLoggable(Level.FINE)) {
					// if acceptor is running (<socket>.close() causes that any 
					// thread currently blocked in accept() will throw a SocketException)
					if (serverChannel.isOpen()) {
						LOG.fine("error occured while accepting connection: " + t.toString());
					}
				}
			}
			
		} // acceptor loop	
	}

	
	public void setDispatcherPoolSize(int size) {
		dispatcherPool.setSize(size);
	}

	
	public int getDispatcherPoolSize() {
		return dispatcherPool.getDispatchers().size();
	}
	
	
	public int getReceiveBufferPreallocationSize() {
		return dispatcherPool.getReceiveBufferPreallocationSize();
	}

	

	public void setReceiveBufferPreallocationSize(int size) {
		dispatcherPool.setReceiveBufferPreallocationSize(size);
	}
	

	public List<String> getOpenConnections() {
		List<String> result = new ArrayList<String>();

		for (IDispatcher<IoSocketHandler> dispatcher : dispatcherPool.getDispatchers()) {
			for (IoSocketHandler handler : dispatcher.getRegistered()) {
				result.add(handler.toString());
			}
		}
			
		return result;
	}

	
	List<IDispatcher<IoSocketHandler>> getDispatchers() {
		return dispatcherPool.getDispatchers();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getNumberOfOpenConnections() {
		return getOpenConnections().size();
	}
	
	IoSocketDispatcherPool getDispatcherPool() {
		return dispatcherPool;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public long getNumberOfHandledConnections() {
		return handledConnections;
	}
	
	
	public long getNumberOfConnectionTimeouts() {
		return dispatcherPool.getNumberOfConnectionTimeouts();
	}
	
	public long getNumberOfIdleTimeouts() {
		return dispatcherPool.getNumberOfIdleTimeouts();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void close() throws IOException {
		if (isRunning) {
			isRunning = false;
			
	        if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("closing acceptor");
			}
	        
	        try {
	        	// closes the server socket
	        	serverChannel.close();
	        } catch (Exception ignore) { }
	        
	        try { 
	        	dispatcherPool.shutdown();
	        } catch (Exception ignore) { }
	        
	        callback.onDisconnected();
		}
	}
}
