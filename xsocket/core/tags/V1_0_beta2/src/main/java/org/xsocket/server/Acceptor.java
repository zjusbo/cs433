// $Id: Acceptor.java 447 2006-12-07 14:01:33Z grro $
/*
 *  Copyright (c) xsocket.org, 2006. All rights reserved.
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
package org.xsocket.server;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;




/**
 * The acceptor ist responsible to accept new incomming connections, and
 * register these on the dispatcher 
 *
 * @author grro@xsocket.org
 */
final class Acceptor {
	
	private static final Logger LOG = Logger.getLogger(Acceptor.class.getName());
	
	private boolean isRunning = true;
	
	
	// Socket
	private Selector selector = null;
	private ServerSocketChannel serverChannel = null;

	
	// dispatcher 
	private DispatcherPool dispatcherPool = null;
	
	
	// app handler
	private IHandler appHandlerPrototype = null;
	private IHandlerTypeInfo appHandlerTypeInfo = null;

	
	//	 SSL
	private boolean sslOn = false;
	private SSLContext sslContext = null;
	
	
	// id
	private String localIdPrefix = null;
	private long nextId = 0;
	
	

	// statistics
	private long handledConnections = 0;



	

	/**
	 * constructor
	 * 
	 * @param serverPort       the server port
	 * @param dispatcherPool   the dispatcher pool
	 * @param sslContext       the ssl context
	 * @param sslOn            true, if ssl should be used
	 * @param localIdPrefix    the local id
	 * @throws IOException If I/O error occurs     
	 */
	public Acceptor(int serverPort, DispatcherPool dispatcherPool, SSLContext sslContext, boolean sslOn, String localIdPrefix) throws IOException {
		this.dispatcherPool = dispatcherPool; 
		this.sslContext = sslContext;
		this.sslOn = sslOn;
		this.localIdPrefix = localIdPrefix;
		
		try {
			LOG.fine("try to bind server on port " + serverPort);
			
			// create a new server socket 
			serverChannel = ServerSocketChannel.open();
			serverChannel.configureBlocking(false);
			
			// and bind it to the port
			serverChannel.socket().bind(new InetSocketAddress(serverPort));
			
			selector = Selector.open();
			serverChannel.register(selector, SelectionKey.OP_ACCEPT);
			
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("acceptor has been bound on port " + serverPort);
			}
		} catch (BindException be) {
			if (serverChannel != null) {
				serverChannel.close();
			}
			LOG.info("error occured while binding server on port " + serverPort + ". Reason: " + be.toString());
			throw be;
		}
	}

	
	/**
	 * set the assigned handler and handler info
	 * 
	 * @param appHandler          the assigned handler 
	 * @param appHandlerTypeInfo  the handle type info regarding to the handler
	 */
	void setHandler(IHandler appHandler, IHandlerTypeInfo appHandlerTypeInfo) {
		this.appHandlerPrototype = appHandler;
		this.appHandlerTypeInfo = appHandlerTypeInfo;
	}
	
	

	/**
	 * shutdown   
	 */
	public final void shutdown() {
		if (isRunning) {
			isRunning = false;
			selector.wakeup();
		}
	}



	/**
	 * {@inheritDoc}
	 */
	public final void run() {
		while (isRunning) {
			try {
				
				// wait for incoming connection event
				int count = selector.select();
				if (count > 0) {
					Set selectedKeys = selector.selectedKeys();
					Iterator it = selectedKeys.iterator();

					// handle accept
					while (it.hasNext()) {
						SelectionKey sk = (SelectionKey) it.next();
						it.remove();
						
						if (sk.isValid() && sk.isAcceptable()) {	
							handleAcceptEvent((ServerSocketChannel) sk.channel());
						}					
					}
				}
			} catch (Throwable t) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured while accepting connection: " + t.toString());
				}
			}
		}
		
		
		// close the acceptor
		try {
	        if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("closing acceptor");
			}
	        selector.close();
	        serverChannel.close();

		} catch (IOException ioe) {
			LOG.warning("Exception occured during unbind acceptor (selector). Reason: " + ioe.toString());
			throw new RuntimeException(ioe);
		}
	}
	
	
	
	private void handleAcceptEvent(ServerSocketChannel ssc) {
		try {
			// retrieve socket channel
			SocketChannel sc = ssc.accept();
			
			// clone app handler if neccesary
			IHandler appHandler = null;
			if (appHandlerTypeInfo.isConnectionScoped()) {
				appHandler = (IHandler) ((IConnectionScoped) appHandlerPrototype).clone();
			} else {
				appHandler = appHandlerPrototype;
			}
			
			// create a new connection
			ManagedConnection connection  = new ManagedConnection(sc, localIdPrefix + "." + (++nextId), sslContext, sslOn);
			connection.setAttachedAppHandler(appHandler);
			connection.setAttachedAppHandlerTypeInfo(appHandlerTypeInfo);
			
			// register the new connection on the dispatcher
			dispatcherPool.nextDispatcher().registerConnection(connection);
        	
			// statistic
			handledConnections++;
			
		} catch (Throwable t) {
			if (isRunning) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("Exception occured while accepting new incomming connection. Reason: " + t.toString());
				}
			}
		}
	}
}
