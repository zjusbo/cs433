// $Id: Acceptor.java 776 2007-01-15 17:15:41Z grro $
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
import java.lang.management.ManagementFactory;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.ObjectName;
import javax.management.StandardMBean;
import javax.net.ssl.SSLContext;

import org.xsocket.Dispatcher;
import org.xsocket.IHandle;
import org.xsocket.DataConverter;
import org.xsocket.WorkerPool;




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
	
	// domain name
	private String appDomain = null;
	
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
	private ConnectionDispatcherPool dispatcherPool = null;
	
	// worker pool
	private WorkerPool workerPool = null;
	
	// timout
	private long idleTimeout = Long.MAX_VALUE;
	private long connectionTimeout = Long.MAX_VALUE;
	private Timer watchdogTimer = new Timer(true);
	private WatchdogTask watchdogTask = null;
	
	// memory management
	private int preallocationSize = 65536;	
	

	// statistics
	private long handledConnections = 0;
	private int numberOfConnectionTimeout = 0;
	private int numberOfIdleTimeout = 0;



	/**
	 * constructor
	 * 
	 * @param serverPort     the server port
	 * @param workerPool     the worker pool to use
	 * @param sslContext     the sslContext or null
	 * @param sslOn          true, is SSL mode should be used
	 * @param localIdPrefix  the local id prefix
	 * @throws IOException If some other I/O error occurs
	 */
	public Acceptor(int serverPort, WorkerPool workerPool, SSLContext sslContext, boolean sslOn, String localIdPrefix) throws IOException {
		this.sslContext = sslContext;
		this.workerPool = workerPool;
		this.sslOn = sslOn;
		this.localIdPrefix = localIdPrefix;
		
		try {
			LOG.fine("try to bind server on port " + serverPort);
			
			// create a new server socket 
			serverChannel = ServerSocketChannel.open();
			serverChannel.configureBlocking(true);
			
			// and bind it to the port
			serverChannel.socket().bind(new InetSocketAddress(serverPort));
			
			
			dispatcherPool = new ConnectionDispatcherPool();

			
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("acceptor has been bound on port " + serverChannel.socket().getLocalPort());
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
	 * set the application domain name
	 *  
	 * @param appDomain the application domain name 
	 */
	void setAppDomain(String appDomain) {
		this.appDomain = appDomain;
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
	 * shutdown   
	 */
	public final void shutdown() {
		if (isRunning) {
			isRunning = false;
			
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
					
				// det the next disptacher 
				ConnectionDispatcher dispatcher = dispatcherPool.nextDispatcher();
				
				// create a new connection
				IoSocketHandler socketIOHandler = new IoSocketHandler(channel, "s." + localIdPrefix + ".", dispatcher.getMemoryManager(), dispatcher.getNativeDispatcher(), workerPool);
				new NonBlockingConnection(socketIOHandler, sslContext, sslOn, dispatcher.getSSLMemoryManager(), false, appHandler, isConnectHandler, isDisconnectHandler, isDataHandler, isTimeoutHandler, false);
					
		        	
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
	
	
	
	/**
	 * get the size of the preallocation buffer, 
	 * for reading incomming data
	 *   
	 * @return preallocation buffer size
	 */
	int getReceiveBufferPreallocationSize() {
		return preallocationSize;
	}

	/**
	 * set the size of the preallocation buffer, 
	 * for reading incomming data
	 *   
	 * @param size the preallocation buffer size
	 */
	void setReceiveBufferPreallocationSize(int size) {
		preallocationSize = size;
	}
	
	
	/**
	 * set the idle timeout in sec 
	 * 
	 * @param timeout idle timeout in sec
	 */
	void setIdleTimeout(long idleTimeout) {
		if (idleTimeout <= 0) {
			throw new RuntimeException("idle timeout must be larger than 0");
		}
		
		this.idleTimeout = idleTimeout;
		dispatcherPool.updateTimeoutCheckPeriod();
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("idle time out has been update to " + DataConverter.toFormatedDuration(this.idleTimeout));
		}
	}
	
	
	/**
	 * get the idle timeout in sec 
	 * 
	 * @return the idle timeout in sec
	 */
	long getIdleTimeout() {
		return idleTimeout;
	}


	
	/**
	 * set the max time for a connections. By 
	 * exceeding this time the connection will be
	 * terminated
	 * 
	 * @param timeout the connection timeout in sec
	 */
	void setConnectionTimeout(long connectionTimeout) {
		if (connectionTimeout <= 0) {
			throw new RuntimeException("connection timeout must be larger than 0");
		}

		
		this.connectionTimeout = connectionTimeout;
		dispatcherPool.updateTimeoutCheckPeriod();
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("connection time out has ben update to " + DataConverter.toFormatedDuration(this.connectionTimeout));
		}
	}

	
	/**
	 * get the max time for a connections. By 
	 * exceeding this time the connection will be
	 * terminated
	 * 
	 * @return the connection timeout in sec
	 */
	long getConnectionTimeout() {
		return connectionTimeout;
	}
	

	
	/**
	 * get the number of the terminated connection, 
	 * caused by the connection timeout 
	 *  
	 * @return terminated connections
	 */
	int getNumberOfConnectionTimeout() {
		return numberOfConnectionTimeout;
	}
	
	
	/**
	 * get the number of the terminated connection, 
	 * caused by the connection timeout 
	 *  
	 * @return terminated connections
	 */
	int getNumberOfIdleTimeout() {
		return numberOfIdleTimeout;	
	}

	
	/**
	 * get connection info about the open 
	 * connections
	 * 
	 * @return open connection info
	 */
	List<String> getOpenConnections() {
		return dispatcherPool.getOpenConnections();	
	}


	
	/**
	 * get the number of open connections
	 * 
	 * @return the number of open connections
	 */
	int getNumberOfOpenConnections() {
		return dispatcherPool.getNumberOfOpenConnections();	
	}

	
	/**
	 * get the number of handled connections
	 * 
	 * @return the number of handled connections
	 */
	long getNumberOfHandledConnections() {
		return dispatcherPool.getNumberOfHandledConnections();
	}
	
	
	/**
	 * set the dispatcher pool size
	 * 
	 * @param size the dispatcher pool size
	 */
	void setDispatcherPoolSize(int size) {
		dispatcherPool.setSize(size);
	}
	
	
	/**
	 * returns the dispatcher pool size
	 * 
	 * @return the dispatcher pool size
	 */
	int getDispatcherPoolSize() {
		return dispatcherPool.getSize();
	}
	
	
	private void checkConnectionTimeout(IoSocketHandler ioSocketHandler, long current, long idleTimeout, long connectionTimeout) {

		if (ioSocketHandler.checkIdleTimeout(current, idleTimeout)) {
			numberOfIdleTimeout++;
		}

		if (ioSocketHandler.checkConnectionTimeout(current, connectionTimeout)) {
			numberOfConnectionTimeout++;
		}
	}
	
	
	
	private void setTimeoutCheckPeriod(long period) {
		if (watchdogTask != null) {
			watchdogTask.cancel();
		}
		
		watchdogTask = new WatchdogTask();
		watchdogTimer.schedule(watchdogTask, period, period);
	}	
	
	
	private final class WatchdogTask extends TimerTask {
		@Override
		public void run() {
			if (isRunning) {
				dispatcherPool.checkDispatcherTimeout();
			}
		}
	}
	
	
	
	
	
	private final class ConnectionDispatcherPool {
		
		// dispatcher management
		private final LinkedList<ConnectionDispatcher> dispatchers = new LinkedList<ConnectionDispatcher>();
		private int size = 0;
		private int pointer = 0;
		
		

		void updateTimeoutCheckPeriod() {
			long period = getIdleTimeout();
			if (getConnectionTimeout() < getIdleTimeout()) {
				period = getConnectionTimeout();
			}

			setTimeoutCheckPeriod((int) (((double) period) / 5));
		}	
		
		
		
		int getSize() {
			return size;
		}
	
		
		void setSize(int size) {
			this.size = size;
			updateDisptacher();
		}
		

		void run() {
			isRunning = true;
			updateDisptacher();
		}
		
		

		private void updateDisptacher() {
			if (isRunning) {
				int currentRunning = dispatchers.size();
				
				if (currentRunning != size) {
					if (currentRunning > size) {
						for (int i = size; i <  currentRunning; i++) {
							ConnectionDispatcher dispatcher = dispatchers.getLast();
							dispatchers.remove(dispatcher);
							dispatcher.shutdown();
						}
			
					} else if ( currentRunning < size) {
						for (int i = currentRunning; i < size; i++) {
							ConnectionDispatcher dispatcher = new ConnectionDispatcher(appDomain, i);							
							dispatchers.addLast(dispatcher);
			
							Thread t = new Thread(dispatcher);
							t.setDaemon(false);
							t.start();
						}
					}
				}
			}
		}
		

		
		/**
		 * shutdown the pool 
		 *
		 */
		void shutdown() {
			isRunning = false;
			
	        if (LOG.isLoggable(Level.FINER)) {
				LOG.fine("terminate dispatchers");
			}

			for (ConnectionDispatcher dispatcher : dispatchers) {
	        	dispatcher.shutdown();
	        }
			
			dispatchers.clear();
		}
		
		

		/**
		 * get the next free <code>Dispatcher</code>
		 * 
		 * @return a dispatcher
		 */
		public ConnectionDispatcher nextDispatcher() {
			// round-robin approach
			pointer++;
			if (pointer >= size) {
				pointer = 0;
			}

			return dispatchers.get(pointer);
		}

		

		

		List<String> getOpenConnections() {
			List<String> result = new ArrayList<String>();
			for (ConnectionDispatcher dispatcher : dispatchers) {
				for (IHandle selectable : dispatcher.getNativeDispatcher().getRegistered()) {
					result.add(selectable.toString());
				}
			}
			return result;
		}



		int getNumberOfOpenConnections() {
			int result = 0;
			for (ConnectionDispatcher dispatcher : dispatchers) {
				result += dispatcher.getNumberOfRegistered();
			}
			return result;
		}


		long getNumberOfHandledConnections() {
			long result = 0;
			for (ConnectionDispatcher dispatcher : dispatchers) {
				result += dispatcher.getNativeDispatcher().getNumberOfHandledRegistrations();
			}
			return result;
		}

		
	
		

		@SuppressWarnings("unchecked")
		private void checkDispatcherTimeout() {
			try {
				long current = System.currentTimeMillis();
				LinkedList<ConnectionDispatcher> disps = (LinkedList<ConnectionDispatcher>) dispatchers.clone();
				for (ConnectionDispatcher dispatcher : disps) {
					Set<IoSocketHandler> openConnections = dispatcher.getNativeDispatcher().getRegistered();
					for (IoSocketHandler ioSocketHandler : openConnections) {
						checkConnectionTimeout(ioSocketHandler,current, idleTimeout, connectionTimeout);	
					}
					
				}
			} catch (Exception e) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured: " + e.toString());
				}
			}			
		}
	}

	
	
	public interface DispatcherMBean {
		public long getNumberOfHandledRegistrations();

		public long getNumberOfHandledReads();

		public long getNumberOfHandledWrites();
		
		public int getNumberOfRegistered();
		
		public int getFreeReceiveBufferSize();
		
		public int getFreeSSLBufferSize();
	}

	private final class ConnectionDispatcher implements DispatcherMBean, Runnable {
		private Dispatcher<IoSocketHandler> dispatcher = null;
		private IMemoryManager memoryManager = new SingleThreadedMemoryManager();
		private IMemoryManager sslMemoryManager = new MultiThreadedMemoryManager();
		
		private ObjectName mbeanName = null;
		
		ConnectionDispatcher(String appDomain, int number) {
			dispatcher = IoSocketHandler.createDispatcher(Integer.toString(number));
			
	        try {
	        	String name = "Dispatcher_" + number;
	        	StandardMBean mbean = new StandardMBean(this, DispatcherMBean.class);
	        	mbeanName = new ObjectName(appDomain + ":type=Dispatcher,name=" + name);
				ManagementFactory.getPlatformMBeanServer().registerMBean(mbean, mbeanName);
	        } catch (Exception mbe) {
	        	LOG.warning("error " + mbe.toString() + " occured while registering mbean");
	        }
		}
		
		Dispatcher<IoSocketHandler> getNativeDispatcher() {
			return dispatcher;
		}

		IMemoryManager getMemoryManager() {
			return memoryManager;
		}
		
		IMemoryManager getSSLMemoryManager() {
			return sslMemoryManager;
		}
		
		public void run() {
			dispatcher.run();
		}
		
		void shutdown() {
			try {
	        	ManagementFactory.getPlatformMBeanServer().unregisterMBean(mbeanName);
	        } catch (Exception mbe) {
	        	LOG.warning("error " + mbe.toString() + " occured while unregistering mbean");
	        }

			dispatcher.shutdown();			
		}
		
		public long getNumberOfHandledReads() {
			return dispatcher.getNumberOfHandledReads();
		}
		
		public long getNumberOfHandledRegistrations() {
			return dispatcher.getNumberOfHandledRegistrations();
		}
		
		public long getNumberOfHandledWrites() {
			return dispatcher.getNumberOfHandledWrites();
		}
		
		public int getNumberOfRegistered() {
			return dispatcher.getRegistered().size();
		}
		
		public int getFreeReceiveBufferSize() {
			return memoryManager.getFreeBufferSize();
		}
		
		public int getFreeSSLBufferSize() {
			return sslMemoryManager.getFreeBufferSize();
		}
		
	}

	
	private final class MultiThreadedMemoryManager extends MemoryManager {
		
		MultiThreadedMemoryManager() {
			super(preallocationSize, true);	
		}
		
		
		@Override
		int getPreallocationSize() {
			return preallocationSize;
		}
	}
	
	private final class SingleThreadedMemoryManager implements IMemoryManager {
		
		private ByteBuffer freeBuffer = null;

		
		/**
		 * get the current preallocation buffer size
		 *  
		 * @return the current preallocation buffer size
		 */
		public int getCurrentPreallocationBufferSize() {
			ByteBuffer b = freeBuffer;
			if (b == null) {
				return 0;
			} else {
				return b.remaining();
			}
		}
			

		public void recycleMemory(ByteBuffer buffer) {
			if (buffer.hasRemaining()) {
				freeBuffer = buffer;
			}
		}
		

		public final ByteBuffer acquireMemory(int minSize) {
			ByteBuffer buffer = null;
			
			if (freeBuffer != null) {
				if (freeBuffer.remaining() >= minSize) {
					buffer = freeBuffer;
				}
				freeBuffer = null;
			}
			
			
			if (buffer == null) {
				buffer = newBuffer(preallocationSize);
			}
			
			return buffer;
		}
		
		

		final ByteBuffer newBuffer(int preallocationSize) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("allocate new physical memory (size: " + preallocationSize + ") by thread " + Thread.currentThread().getName());
			}

			return ByteBuffer.allocateDirect(preallocationSize);
		}
		
		public int getFreeBufferSize() {
			if (freeBuffer != null) {
				return freeBuffer.remaining();
			} else {
				return 0;
			}
		}
	}
}
