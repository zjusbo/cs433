// $Id: Dispatcher.java 41 2006-06-22 06:30:23Z grro $
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
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.ObjectName;
import javax.management.StandardMBean;



/**
 * Implementation of the IDispatcher-Interface
 * 
 * @author grro@xsocket.org
 */
final class Dispatcher implements IDispatcher {
	
	private static final Logger LOG = Logger.getLogger(Dispatcher.class.getName());	
	
	private boolean isRunning = true;

	private static ThreadLocal<DirectMemoryManager> memoryManager = new ThreadLocal<DirectMemoryManager>(); 
	
	// name
	private String name = null;
	private  String domain = null;

	
	// socket handling
	private Selector selector = null;
	private final List<NonBlockingConnection> newConnections = Collections.synchronizedList(new ArrayList<NonBlockingConnection>());
	private int preallocationSize = 1024;	



	// workers 
	private WorkerPool workerPool = null;

	
	// timeout check
	private int numberOfConnectionTimeouts = 0;
	private int numberOfIdleTimeouts = 0;
	private TimeoutWatchdog watchdog = new TimeoutWatchdog();
	private long timeoutCheckPeriod = 30 * 1000;
		
	
	// handler
	private InternalHandler handler = null;



	// statistics
	private ObjectName mbeanName = null;
	private long handledConnections = 0;

		
	
	
	/**
	 * constructor 
	 * 
	 * @param preallocationSize the preallocation size for the incomming memory pool
	 * @param workers the worker pool to use
	 * @param domain the domain name
	 * @param name the instance name
	 * @param idleTimeout  the idle timeout
	 * @param connectionTimeout the connection timeout
	 */
	Dispatcher(int preallocationSize, WorkerPool workerPool, String domain, String name)  {
		this.preallocationSize = preallocationSize;
		this.workerPool = workerPool;
		this.domain = domain;
		this.name = name;		

	}

	

	/**
	 * @see IDispatcher
	 */
	public final void setHandler(InternalHandler hdl) {
		this.handler = hdl;
		if (handler != null) {
			handler.setWorkerPool(workerPool);
		}
	}

	/**
	 * accepts new connections 
	 * 
	 * @param connection the new connection
	 * @throws IOException If some other I/O error occurs
	 */
	public final void acceptNewConnection(NonBlockingConnection connection) throws IOException {
		newConnections.add(connection);
		wakeup();
	}
	

	private void wakeup() {
		selector.wakeup();
	}

	
	private void init() {

        LOG.fine("opening selector to accept data");
		try {
			selector = Selector.open();
		} catch (IOException ioe) {
			String text = "exception occured while opening selector. Reason: " + ioe.toString();
			LOG.severe(text);
			throw new RuntimeException(text, ioe);
		}
		
		
        LOG.fine("starting timeout watchdog");
        watchdog.setPriority(Thread.MIN_PRIORITY);
        watchdog.setName(name + "#" + "watchdog");
        watchdog.start();
		


        try {        	
        	StandardMBean mbean = new StandardMBean(this, IDispatcher.class);
        	mbeanName = new ObjectName(domain + ":type=Dispatcher,name=" + name);
			ManagementFactory.getPlatformMBeanServer().registerMBean(mbean, mbeanName);
        } catch (Exception mbe) {
        	LOG.warning("error " + mbe.toString() + " occured while registering mbean");
        }        
	}

	
	/**
	 * @see IDispatcher
	 */
	public void shutdown() {
		if (isRunning) {
			isRunning = false;
			
	        try {
   				ManagementFactory.getPlatformMBeanServer().unregisterMBean(mbeanName);
	        } catch (Exception mbe) {
	        	LOG.warning("error " + mbe.toString() + " occured while unregistering mbean");
	        }
		
	        LOG.fine("closing connections");
	        if (selector != null) {
	        	for (SelectionKey sk : selector.keys()) {
	        		try {
	        			NonBlockingConnection connection = getAssignedConnection(sk);
	        			connection.close();
	        		} catch (Exception ignore) { }
				}
	        }
	        
	        
	        LOG.fine("stopping timeout watchdog");
	        watchdog.shutdown();
	        	        
	        	        
			LOG.fine("closing open connections");	
			for(SelectionKey sk : selector.keys()) {
				try {
					NonBlockingConnection connection = getAssignedConnection(sk);
					connection.close();
				} catch (Exception ignore) { }
			}	

			
			if (selector != null) {
				try {
					selector.close();
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("error occured by close selector within tearDown " + ioe.toString());
					}
				}
				selector = null;
			}
		}
	}
	
	
	private NonBlockingConnection getAssignedConnection(SelectionKey sk) throws IOException {
		NonBlockingConnection connection = (NonBlockingConnection) sk.attachment();
		return connection;
	}

		
	/**
	 * @see Runnable
	 */
	public final void run() {
		init();
		
		while(isRunning) {
			try {
				processing();
			} catch (Throwable e) {
				LOG.warning("exception occured while handling keys. Reason " + e.toString());
			}				

		}
		
	}
	
	
	private void processing() throws IOException {

		getMemoryManager().setPreallocationSize(preallocationSize);
		
		// waiting for new data 	
		int n = selector.select();

		if (n > 0) {
			Set selectedKeys = selector.selectedKeys();
			Iterator it = selectedKeys.iterator();
			
			// handle read & write
			while (it.hasNext()) {
				SelectionKey sk = (SelectionKey) it.next();
				it.remove();
		
				NonBlockingConnection connection = getAssignedConnection(sk);

				
				// read data
				if (sk.isValid() && sk.isReadable()) {
					try {
						if (connection.isOpen()) {
							connection.handleNonBlockingRead();
						}
					} catch (Throwable e) {
						if (connection != null) {
							if (LOG.isLoggable(Level.FINER)) {
								LOG.finer("exception occured while reading data. Reason " + e.toString() 
										  + "\nclosing connection " + connection.toCompactString());
							}
							connection.close();
						}
					} 
				}
				
				
				
				// write data
				if (sk.isValid() && sk.isWritable()) {
					try {
						connection.updateSelectionKeyOps(SelectionKey.OP_READ);
						if (connection.hasDataToSend()) {
							connection.handleNonBlockingWrite();
						}
					} catch (Throwable e) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("error occured while sending. " + e.toString());
						}
						connection.close();
					}
				}	
			}
		}
			
		// handle new connections
		while (!newConnections.isEmpty()) {
			do {
				handleNewConnections(newConnections.remove(0));
			} while(!newConnections.isEmpty());
			
		}
	}		
	
	
	private void handleNewConnections(final NonBlockingConnection connection) {
		try {
			handledConnections++;
			connection.registerSelector(selector,  SelectionKey.OP_READ);

			
			// attach the handler to the connection
			InternalHandler hdl = (InternalHandler) handler.clone();
			connection.init(hdl);
			
		} catch (Throwable e) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("exception occured while accepting connection. Reason " + e.toString() 
						  + "\nclosing connection " + connection.toCompactString());
			}
			connection.close();
		} 
	}

	

	
	
	/**
	 * @see IDispatcher
	 */
	public final int getNumberOfOpenConnections() {
		return selector.keys().size();
	}
	
	
	
	/**
	 * @see IDispatcher
	 */
	public List<String> getOpenConnections() {
		List<String> result = new ArrayList<String>();
		for (SelectionKey key : selector.keys()) {
			result.add(((INonBlockingConnection) key.attachment()).toString());
		}		
		return result;
	}
	
	
	/**
	 * @see IDispatcher
	 */
	public final long getNumberOfHandledConnections() {
		return handledConnections;
	}
	
	
	/**
	 * @see IDispatcher
	 */
	public void setReceiveBufferPreallocationSize(int size) {
		this.preallocationSize = size;
	}

	
	/**
	 * @see IDispatcher
	 */
	public int getReceiveBufferPreallocationSize() {
		return preallocationSize;
	}
	
	
	/**
	 * @see IDispatcher
	 */
	public int getNumberOfConnectionTimeout() {
		return numberOfConnectionTimeouts;
	}

	
	/**
	 * @see IDispatcher
	 */
	public int getNumberOfIdleTimeout() {
		return numberOfIdleTimeouts;
	}


	/**
	 * @see IDispatcher
	 */
	public void setTimeoutCheckPeriod(long period) {
		timeoutCheckPeriod = period;
	}
	
	
	/**
	 * @see IDispatcher
	 */
	public long getTimeoutCheckPeriod() {
		return timeoutCheckPeriod;
	}
	
	static DirectMemoryManager getMemoryManager() {
		
		DirectMemoryManager mm = memoryManager.get();
		
		if (mm == null) {
			mm = new DirectMemoryManager();
			memoryManager.set(mm);
		}
		
		return mm;
	}
	
	
	private class TimeoutWatchdog extends Thread {
		private boolean isRunning = true;
		
		public void run() {
			while (isRunning) {
				check();
				
				try {
					Thread.sleep(timeoutCheckPeriod);
				} catch (InterruptedException igonre) { }
			}
		}
		
		void shutdown() {
			isRunning = false;
			this.interrupt();
		}
		
		private void check() {
			
			long currentTime = System.currentTimeMillis();
			
			try {
				Set<SelectionKey> keySet = selector.keys();
				SelectionKey[]selKeys = keySet.toArray(new SelectionKey[keySet.size()]);
				
				for (SelectionKey key : selKeys) {
					NonBlockingConnection connection = (NonBlockingConnection) key.attachment();
					
					if (connection.getConnectionTimeout() != Long.MAX_VALUE) {
						if (currentTime > (connection.getConnectionOpenedTime() + connection.getConnectionTimeout())) {
							connection.handleConnectionTimeout(); 
							numberOfConnectionTimeouts++;
						}
					}
	
					if (connection.getIdleTimeout() != Long.MAX_VALUE) {
						if (currentTime > (connection.getLastReceivingTime() + connection.getIdleTimeout())) {
							connection.handleIdleTimeout();
							numberOfIdleTimeouts++;
						}
					}
				}
			} catch (Throwable ignore) { }
		}
	}
}
