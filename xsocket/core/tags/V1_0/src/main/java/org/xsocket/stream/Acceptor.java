// $Id: Acceptor.java 1049 2007-03-21 16:42:48Z grro $
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
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.xsocket.DataConverter;
import org.xsocket.IDispatcher;



/**
 * The acceptor ist responsible to accept new incomming connections, and
 * register these on the dispatcher 
 *
 * @author grro@xsocket.org
 */
final class Acceptor {
	
	private static final Logger LOG = Logger.getLogger(Acceptor.class.getName());

	// running flag
	private boolean isRunning = true;
		
	// Socket
	private ServerSocketChannel serverChannel = null;
	
	// app handler
	private IHandler appHandlerPrototype = null;
	private boolean isConnectionScoped = false;
	private boolean isConnectHandler = false;
    private boolean isDisconnectHandler = false;
	private boolean isDataHandler = false;
	private boolean isTimeoutHandler = false;

	//	 SSL
	private boolean sslOn = false;
	private SSLContext sslContext = null;

	
	// id
	private String localIdPrefix = null;

	
	// dispatcher management
	private IoSocketDispatcherPool dispatcherPool = null;
	
	
	// timout
	private final TimeoutWatchdog timeoutWatchDog = new TimeoutWatchdog();
	private int idleTimeoutSec = Integer.MAX_VALUE;
	private int connectionTimeoutSec = Integer.MAX_VALUE;
	

	// statistics
	private long handledConnections = 0;



	/**
	 * constructor
	 * 
	 * @param bindAddress    the local address  
	 * @param port           the local port
	 * @param sslContext     the sslContext or null
	 * @param sslOn          true, is SSL mode should be used
	 * @param localIdPrefix  the local id prefix
	 * @throws IOException If some other I/O error occurs
	 */
	public Acceptor(InetAddress bindAddress, int port, SSLContext sslContext, boolean sslOn, String localIdPrefix, IoSocketDispatcherPool dispatcherPool) throws IOException {
		this.sslContext = sslContext;
		this.sslOn = sslOn;
		this.localIdPrefix = localIdPrefix;
		this.dispatcherPool = dispatcherPool;
		
		try {
			LOG.fine("try to bind server on " + bindAddress + ":" + port);
			
			// create a new server socket 
			serverChannel = ServerSocketChannel.open();
			serverChannel.configureBlocking(true);
			
			// and bind it to the port
			serverChannel.socket().bind(new InetSocketAddress(bindAddress, port));
			serverChannel.socket().setReuseAddress(true);
			
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("acceptor has been bound on " + bindAddress + ":" + port);
			}
		} catch (BindException be) {
			if (serverChannel != null) {
				serverChannel.close();
			}
			LOG.info("error occured while binding server on on " + bindAddress + ":" + port + ". Reason: " + be.toString());
			throw be;
		}
	}
	
	
	
	
	
	/**
	 * return the local port
	 * 
	 * @return the local port
	 */
	int getLocalePort() {
		return serverChannel.socket().getLocalPort();
	}

	
	/**
	 * set the handler to use
	 * 
	 * @param appHandler           the appHandler
	 * @param isConnectionScoped   true, if connection scoped
	 * @param isConnectHandler     true, if connect handler
	 * @param isDisconnectHandler  true, if disconnect handler
	 * @param isDataHandler        true, if data handler
	 * @param isTimeoutHandler     true, if timeout handler
	 */
	void setHandler(IHandler appHandler, boolean isConnectionScoped, boolean isConnectHandler, boolean isDisconnectHandler, boolean isDataHandler, boolean isTimeoutHandler) {
		this.appHandlerPrototype = appHandler;
		this.isConnectionScoped = isConnectionScoped;
		this.isConnectHandler = isConnectHandler;
		this.isDisconnectHandler = isDisconnectHandler;
		this.isDataHandler = isDataHandler;
		this.isTimeoutHandler = isTimeoutHandler;
	}
	
	

	/**
	 * set the max time for a connections. By 
	 * exceeding this time the connection will be
	 * terminated
	 * 
	 * @param timeout the connection timeout in sec
	 */
	void setConnectionTimeoutSec(int connectionTimeout) {
		if (connectionTimeout <= 0) {
			throw new RuntimeException("connection timeout must be larger than 0");
		}

		
		this.connectionTimeoutSec = connectionTimeout;
		updateTimeoutCheckPeriod();
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("connection time out has ben update to " + DataConverter.toFormatedDuration(this.connectionTimeoutSec * 1000));
		}
	}

	int getConnectionTimeoutSec() {
		return connectionTimeoutSec;
	}
	
	
	/**
	 * set the idle timeout in sec 
	 * 
	 * @param timeout idle timeout in sec
	 */
	void setIdleTimeoutSec(int idleTimeout) {
		if (idleTimeout <= 0) {
			throw new RuntimeException("idle timeout must be larger than 0");
		}
		
		this.idleTimeoutSec = idleTimeout;
		updateTimeoutCheckPeriod();
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("idle time out has been update to " + DataConverter.toFormatedDuration(this.idleTimeoutSec * 1000));
		}
	}
	
	
	/**
	 * get the idle timeout in sec 
	 * 
	 * @return the idle timeout in sec
	 */
	int getIdleTimeoutSec() {
		return idleTimeoutSec;
	}
	
	int getNumberOfIdleTimeout() {
		return timeoutWatchDog.getNumberOfIdleTimeout();
	}
	
	
	int getNumberOfConnectionTimeout() {
		return timeoutWatchDog.getNumberOfConnectionTimeout();
	}
	
	
	void updateTimeoutCheckPeriod() {
		timeoutWatchDog.updateTimeoutCheckPeriod((long) idleTimeoutSec * 1000, (long) connectionTimeoutSec * 1000);
	}	
	
	long getNumberOfHandledConnections() {
		return handledConnections;
	}

	/**
	 * shutdown   
	 */
	public final void shutdown() {
		if (isRunning) {
			isRunning = false;
			
			timeoutWatchDog.shutdown();
			
			
	        if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("closing acceptor");
			}
	        try {
	        	// closes the server socket
	        	serverChannel.close();
	        } catch (Exception ignore) { }
	        
	        dispatcherPool.shutdown();
		}
	}



	/**
	 * {@inheritDoc}
	 */
	public final void run() {
		
		dispatcherPool.run();
		
		
		// acceptor loop
		while (isRunning) {
			try {
			
				// blocking accept call
				SocketChannel channel = serverChannel.accept();

				// clone app handler if neccesary
				IHandler appHandler = null;
				if (isConnectionScoped) {
					appHandler = (IHandler) ((IConnectionScoped) appHandlerPrototype).clone();
				} else {
					appHandler = appHandlerPrototype;
				}
					
				// create IoSocketHandler
				IoSocketDispatcher dispatcher = dispatcherPool.nextDispatcher();
				IoSocketHandler socketIOHandler = new IoSocketHandler(channel, "s." + localIdPrefix + ".", dispatcher);

				// create a new connection based on the socketHandler and set timeouts
				NonBlockingConnection connection = new NonBlockingConnection(socketIOHandler, sslContext, sslOn, dispatcherPool.getMultithreadedMemoryManager(), false, appHandler, isConnectHandler, isDisconnectHandler, isDataHandler, isTimeoutHandler);
				connection.setIdleTimeoutSec(idleTimeoutSec);
				connection.setConnectionTimeoutSec(connectionTimeoutSec);	
				
				// statistic
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
	
	

	private final class TimeoutWatchdog {

		private final Timer watchdogTimer = new Timer("AcceptorWatchdogTimer", true);
		private TimerTask watchdogTimerTask = null;
		
		private int numberOfConnectionTimeout = 0;
		private int numberOfIdleTimeout = 0;
		
		public TimeoutWatchdog() {
			// reduce timer priority 
			TimerTask task = new TimerTask() {
				@Override
				public void run() {
					Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
				}
			};
			watchdogTimer.schedule(task, 0);
		}
		
		void updateTimeoutCheckPeriod(long idleTimeoutMillis, long connectionTimeoutMillis) {
			long period = idleTimeoutMillis;
			if (connectionTimeoutMillis < idleTimeoutMillis) {
				period = connectionTimeoutMillis;
			}

			setTimeoutCheckPeriod((int) (((double) period) / 5));
		}	
		
		
		private void setTimeoutCheckPeriod(long period) {
			if (watchdogTimerTask != null) {
				watchdogTimerTask.cancel();
			}
			
			watchdogTimerTask = new TimerTask() {
				@Override
				public void run() {
					checkDispatcherTimeout();
				}
			};
			
			watchdogTimer.schedule(watchdogTimerTask, period, period);
		}	
		
		
		void shutdown() {
			if (watchdogTimerTask != null) {
				watchdogTimerTask.cancel();
			}
		}

		
		private synchronized void checkDispatcherTimeout() {
			try {
				long current = System.currentTimeMillis();
				for (IDispatcher<IoSocketHandler> dispatcher : dispatcherPool.getDispatchers()) {
					Set<IoSocketHandler> socketHandlers = dispatcher.getRegistered();
					for (IoSocketHandler socketHandler : socketHandlers) {
						
						checkTimeout(socketHandler, current);	
					}	
				}
			} catch (Exception e) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured: " + e.toString());
				}
			}			
		}
		
		
		
		private void checkTimeout(IoSocketHandler ioSocketHandler, long current) {
			if (ioSocketHandler.checkIdleTimeout(current)) {
				numberOfIdleTimeout++;
			}

			if (ioSocketHandler.checkConnectionTimeout(current)) {
				numberOfConnectionTimeout++;
			}
		}

		

		/**
		 * gets the number of the detected connection timeouts
		 * 
		 * @return the number of the connection timeouts
		 */
		public int getNumberOfConnectionTimeout() {
			return numberOfConnectionTimeout;
		}

		/**
		 * gets the number of the detected idle timeouts
		 * 
		 * @return the number of the idle timeouts
		 */
		public int getNumberOfIdleTimeout() {
			return numberOfIdleTimeout;
		}
	}

}
