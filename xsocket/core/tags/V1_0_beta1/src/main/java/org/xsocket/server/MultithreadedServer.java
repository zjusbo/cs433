// $Id: MultithreadedServer.java 41 2006-06-22 06:30:23Z grro $
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
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.ObjectName;
import javax.management.StandardMBean;

import org.xsocket.util.TextUtils;





/**
 * Implementation of the <code>IMultithreadedServer</code>
 * 
 * @author grro@xsocket.org
 */
public class MultithreadedServer implements IMultithreadedServer {
	
	private static final Logger LOG = Logger.getLogger(MultithreadedServer.class.getName());

	public static final int DEFAULT_DISPATCHER_SIZE = 2;
	
	public static final int DEFAULT_PREALLOCATION_SIZE = 64768;
	
	public static final long DEFAULT_IDLE_TIMEOUT = Long.MAX_VALUE;
	public static final long DEFAULT_CONNECTION_TIMEOUT = Long.MAX_VALUE;
	
	public static final long DEFAULT_TIMEOUT_CHECK_PERIOD = 20 * 1000;


	
	private boolean isRunning = true;
	
	
	// id 
	private String idPrefix = null;
	private long nextId = 1;
 
	
	// Socket
	private int port = 0;
	private ServerSocketChannel serverChannel = null;
	private long idleTimeout = DEFAULT_IDLE_TIMEOUT;
	private long connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
	private long timeoutCheckPeriod = DEFAULT_TIMEOUT_CHECK_PERIOD;
	
	
	// handler 
	private InternalHandler handler = null;

	
	// workers
	private WorkerPool workerPool = new WorkerPool();


	// disptacher
	private final LinkedList<Dispatcher> dispatchers = new LinkedList<Dispatcher>();
	private int dispatcherSize = DEFAULT_DISPATCHER_SIZE;
	private int preallocationSize = DEFAULT_PREALLOCATION_SIZE;
	private int dispatcherPointer = 0;

	
	// statistics & management
	private String name = null;
	private String domain = null;
	private ObjectName mbeanName = null;
	private long handledConnections = 0;
	

	
	/**
	 * constructor
	 * 
	 * @param port the server port
	 * @throws UnknownHostException if the locale host cannot determined 
	 */
	public MultithreadedServer(int port) throws UnknownHostException, IOException {
		this(port, "xsocket" + "." + port);
	}
	
	
	/**
	 * constructor
	 * 
	 * @param port the server port
	 * @param domain the domain
	 * @throws UnknownHostException if the locale host cannot determined 
	 */
	public MultithreadedServer(int port, String domain) throws UnknownHostException, IOException {
		this.port = port;
		this.domain = domain;
		
		this.name = InetAddress.getLocalHost().getCanonicalHostName() + "." + port;
		initBaseID(InetAddress.getLocalHost(), port);
		
		serverChannel = ServerSocketChannel.open();
		serverChannel.socket().bind(new InetSocketAddress(port));
		serverChannel.configureBlocking(true);
	}
	
		
	
	private void initBaseID(InetAddress address, int port) {
		int id = (new String(address.getAddress()) + ":" + port).hashCode();
		if (id < 0) {
			id = 0 - id;
		}
		
		int random = 0;
		do {
			random = new Random().nextInt();
		} while (random < 0);
		
		idPrefix = id + "." + System.currentTimeMillis() + "." + random; 
	}
	

	private final String generatedId() {
		return idPrefix + "." + (nextId++);
	}

	
	/**
	 * @see Runnable
	 */
	public final void run() {
		init();
	
		while (isRunning) {
			processing();
		}		
	}
	
	
	private void init() {
		if (handler == null) {
			LOG.warning("no handler has been set. Call setHandler-method to set an assigned handler");
		}
			
			
		// start the disptachers by setting 
		setDispatcherPoolSize(dispatcherSize);

			
			
		LOG.info("server has been bound on port " + port + "  (" + getVersionIfo() + ")");
		LOG.fine("connectionTimeout=" + TextUtils.printFormatedDuration(connectionTimeout) + "; idleTimeout=" + TextUtils.printFormatedDuration(idleTimeout));
	
			
        try {
        	StandardMBean mbean = new StandardMBean(this, IMultithreadedServer.class);
        	mbeanName = new ObjectName(domain + ":type=MultithreadedServer,name=" + name);
  				ManagementFactory.getPlatformMBeanServer().registerMBean(mbean, mbeanName);
        } catch (Exception mbe) {
        	LOG.warning("error " + mbe.toString() + " occured while registering mbean");
        }
	}

	private String getVersionIfo() {
		Package p = Package.getPackage("org.xsocket");
		if (p != null) {
			return p.getSpecificationTitle() + " " + p.getImplementationVersion();
		} else {
			return "";
		}
	}

	
	private void processing() {
		try {
			SocketChannel sc = serverChannel.accept();
			newConnection(sc);
		} catch (Throwable t) {
			if (isRunning) {
				LOG.warning("Exception occured while accepting new incomming connection. Reason: " + t.toString());
			}
		}
	}
		
	private void newConnection(SocketChannel sc) throws IOException {
		if (sc != null) {
			NonBlockingConnection connection = newNonBlockingConnection(sc, generatedId());
			connection.setConnectionTimeout(connectionTimeout);
			connection.setIdleTimeout(idleTimeout);
			
        	if (LOG.isLoggable(Level.FINE)) {
        		LOG.fine("[" + connection.getId() + "] new incoming connection " + connection);
        	}
			getNextDispatcher().acceptNewConnection(connection);								
			handledConnections++;
		}
	}
	
	
	/**
	 * creates a new connection
	 *  
	 * @param sc  the underlyin channel
	 * @param id  the assigned id
	 * @return the connection 
	 * @throws IOException If some other I/O error occurs
	 */
	protected NonBlockingConnection newNonBlockingConnection(SocketChannel sc, String id) throws IOException {
		return new NonBlockingConnection(sc, id);
	}

	
	private Dispatcher getNextDispatcher() {		
		// round-robin approach
		dispatcherPointer++;
		if (dispatcherPointer >= dispatchers.size()) {
			dispatcherPointer = 0;
		}
		
		return dispatchers.get(dispatcherPointer);
	}

	
	/**
	 * @see IMultithreadedServer
	 */
	public final void shutdown() {
		if (isRunning) {
			isRunning = false;
			
	        try {
   				ManagementFactory.getPlatformMBeanServer().unregisterMBean(mbeanName);
	        } catch (Exception mbe) {
	        	LOG.warning("error " + mbe.toString() + " occured while unregistering mbean");
	        }

			
			try {
		        if (LOG.isLoggable(Level.FINER)) {
					LOG.fine("close handler");
				}	
		        handler.close();

				
		        if (LOG.isLoggable(Level.FINER)) {
					LOG.fine("close selector and socket");
				}	
		        serverChannel.close();
	
		        
		        LOG.fine("shuting down worker pool");
		        workerPool.shutdownNow();

		        
		        if (LOG.isLoggable(Level.FINER)) {
					LOG.fine("terminate dispatchers");
				}	        
		        for (Dispatcher dispatcher : getDispatchers()) {
		        	dispatcher.shutdown();
		        }
		        
	
				LOG.info("unbind port " + port);
			} catch (IOException ioe) {
				LOG.warning("Exception occured while tear down TcpServer. Reason: " + ioe.toString());
				throw new RuntimeException(ioe);
			}
		}
	}

		
	/**
	 * @see IMultithreadedServer
	 */
	public final int getPort() {
		return port;
	}
	
		
	/**
	 * @see IMultithreadedServer
	 */
	public synchronized void setHandler(IHandler hdl) {
		this.handler = new InternalHandler(hdl);
		
		for (Dispatcher dispatcher : dispatchers) {
			dispatcher.setHandler(handler);
		}
	}
	


	/**
	 * @see IMultithreadedServer
	 */
	public boolean isRunning() {
		return isRunning;
	}

	
	/**
	 * @see IMultithreadedServer
	 */
	public void setWorkerPoolSize(int workerSize) {
		workerPool.setSize(workerSize);
	}


	/**
	 * @see IMultithreadedServer
	 */
	public int getWorkerPoolSize() {
		return workerPool.getSize();
	}

	
	/**
	 * @see IMultithreadedServer
	 */
	public final int getReceiveBufferPreallocationSize() {
		return preallocationSize;
	}
	
	
	/**
	 * @see IMultithreadedServer
	 */
	public final void setReceiveBufferPreallocationSize(int size) {
		this.preallocationSize = size;

		for (Dispatcher dispatcher : dispatchers) {
			dispatcher.setReceiveBufferPreallocationSize(preallocationSize); 
		}
	}
	
	
	/**
	 * @see IMultithreadedServer
	 */
	public final List<Dispatcher> getDispatchers() {
		return dispatchers;
	}
	
	
	/**
	 * @see IMultithreadedServer
	 */
	public final synchronized void setDispatcherPoolSize(int size) {
		dispatcherSize = size;
		int poolsize = dispatchers.size();
		
		if (poolsize > dispatcherSize) {
			for (int i = dispatcherSize; i < poolsize; i++) {
				Dispatcher dispatcher = dispatchers.getLast();
				dispatchers.remove(dispatcher);
				dispatcher.shutdown();
			}
			
		} else if (poolsize < dispatcherSize) {
			for (int i = poolsize; i < dispatcherSize; i++) {
				String dispatcherName = "Dispatcher_" + i;
				Dispatcher dispatcher = null;
				dispatcher = new Dispatcher(preallocationSize, workerPool, domain, dispatcherName);
				
				dispatcher.setHandler(handler);
				dispatchers.addLast(dispatcher);

				Thread t = new Thread(dispatcher);
				t.setName(dispatcherName);
				t.setDaemon(false);
				t.start();
			}
		}	
	}
	
	
	/**
	 * @see IMultithreadedServer
	 */
	public synchronized final int getDispatcherPoolSize() {
		return dispatcherSize;
	}
	
		
	
	
	/**
	 * @see IMultithreadedServer
	 */
	public final long getNumberOfHandledConnections() {
		long result = 0;
		for (Dispatcher dispatcher : dispatchers) {
			result += dispatcher.getNumberOfHandledConnections();
		}
		return result;
	}

	
	
	/**
	 * @see IMultithreadedServer
	 */
	public final int getNumberOfOpenConnections() {
		int result = 0;
		for (Dispatcher dispatcher : dispatchers) {
			result += dispatcher.getNumberOfOpenConnections();
		}
		return result;
	}

	
	/**
	 * @see IMultithreadedServer
	 */
	public final List<String> getOpenConnections() {
		List<String> result = new ArrayList<String>();
		for (Dispatcher dispatcher : dispatchers) {
			result.addAll(dispatcher.getOpenConnections());
		}		
		return result;
	}
	
	
	/**
	 * @see IMultithreadedServer
	 */
	public final long getConnectionTimeout() {
		return connectionTimeout;
	}
	
	
	/**
	 * @see IMultithreadedServer
	 */
	public final void setConnectionTimeout(long timeout) {
		this.connectionTimeout = timeout;	
	}
	
	
	/**
	 * @see IMultithreadedServer
	 */
	public final long getIdleTimeout() {
		return idleTimeout;
	}
	
	
	/**
	 * @see IMultithreadedServer
	 */
	public final void setIdleTimeout(long timeout) {
		this.idleTimeout = timeout;
	}
	
	
	/**
	 * @see IMultithreadedServer
	 */
	public final int getNumberOfConnectionTimeout() {
		int result = 0;
		for (Dispatcher disptacher : dispatchers) {
			result += disptacher.getNumberOfConnectionTimeout();
		}
		return result;
	}
	
	
	/**
	 * @see IMultithreadedServer
	 */
	public final int getNumberOfReceivingTimeout() {
		int result = 0;
		for (Dispatcher disptacher : dispatchers) {
			result += disptacher.getNumberOfIdleTimeout();
		}
		return result;
	}

	
	/**
	 * @see IMultithreadedServer
	 */	
	public final long getTimeoutCheckPeriod() {
		return timeoutCheckPeriod;
	}
	
	
	/**
	 * @see IMultithreadedServer
	 */
	public final void setTimeoutCheckPeriod(long period) {
		timeoutCheckPeriod = period;
		for (Dispatcher disptacher : dispatchers) {
			disptacher.setTimeoutCheckPeriod(period);
		}		
	}
}
