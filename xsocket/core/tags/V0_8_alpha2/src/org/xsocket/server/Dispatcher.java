// $Id$
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.ObjectName;
import javax.management.StandardMBean;

import org.xsocket.util.TextUtils;



/**
 * Implementation of the IDispatcher-Interface
 * 
 * @author grro@xsocket.org
 */
final class Dispatcher implements IDispatcher {
	
	private static final Logger LOG = Logger.getLogger(Dispatcher.class.getName());	
	
	private boolean isRunning = true;

	
	// name
	private String name = null;
	private  String domain = null;

	
	
	// socket handling
	private Selector selector = null;
	private final List<NonBlockingConnectionImpl> newConnections = Collections.synchronizedList(new ArrayList<NonBlockingConnectionImpl>());
	private int preallocationSize = 1024;	

	

	// workers 
	private int workerSize = 0;
	private ExecutorService pool = Executors.newFixedThreadPool(3);

	
	// timeout check
	private long receivedTimeout = 0;
	private long connectionTimeout = 0;
	private int numberOfConnectionTimeouts = 0;
	private int numberOfReceivedTimeouts = 0;
	private TimeoutWatchdog watchdog = new TimeoutWatchdog();
	//private long timeoutCheckPeriod = 60 * 60 * 1000;
	private long timeoutCheckPeriod = 10 * 1000;
		
	
	// handler
	private IHandler handler = null;
	private boolean isLifeCycleHandler = false;
	private boolean isDataHandler = false;
	private boolean isHandlerConnetionScoped = false;


	// statistics
	private ObjectName mbeanName = null;
	private long handledConnections = 0;

	
	/**
	 * constructor 
	 * 
	 * @param preallocationSize the preallocation size for the incomming memory pool
	 * @param workerSize the worker size
	 * @param domain the domain name
	 * @param name the instance name
	 * @param receivedTimeout  the receive timeout
	 * @param connectionTimeout the connection timeout
	 */
	Dispatcher(int preallocationSize, int workerSize, String domain, String name, long receivedTimeout, long connectionTimeout)  {
		this.preallocationSize = preallocationSize;
		this.domain = domain;
		this.name = name;		
		this.receivedTimeout = receivedTimeout;
		this.connectionTimeout = connectionTimeout;
		
		setWorkerSize(workerSize);
	}

	/**
	 * @see IDispatcher
	 */
	public void setWorkerSize(int workerSize) {
		pool.shutdown();

		this.workerSize = workerSize;
		pool = Executors.newFixedThreadPool(workerSize);
	}
	

	/**
	 * @see IDispatcher
	 */
	public int getWorkerSize() {
		return workerSize;
	}


	/**
	 * @see IDispatcher
	 */
	public final void setHandler(IHandler handler) {
		if (handler != null) {
			this.handler = handler;
	
			if (handler instanceof IConnectHandler) {
				isLifeCycleHandler = true;
			}
			if (handler instanceof IDataHandler) {
				isDataHandler = true;
			} 
			
			if (handler instanceof IConnectionScoped) {
				isHandlerConnetionScoped = true;
			}
		}
	}

	/**
	 * accepts new connections 
	 * 
	 * @param connection the new connection
	 * @throws IOException If some other I/O error occurs
	 */
	public final void acceptNewConnection(NonBlockingConnectionImpl connection) throws IOException {
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
		Thread t = new Thread(watchdog);
		t.setPriority(Thread.MIN_PRIORITY);
		t.setName(name + "#" + "watchdog");
		t.start();
		


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
		
	        
	        LOG.fine("stopping timeout watchdog");
	        watchdog.shutdown();
	        	        
	        
	        LOG.fine("shuting down worker pool");
	        pool.shutdown();

	        
			LOG.fine("closing open connections");	
			for(SelectionKey sk : selector.keys()) {
				try {
					NonBlockingConnectionImpl connection = (NonBlockingConnectionImpl) sk.attachment();
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

		NonBlockingConnectionImpl.setReceivebufferPreallocationSize(preallocationSize);
		
		// waiting for new data 	
		int n = selector.select();

		if (n > 0) {
			Set selectedKeys = selector.selectedKeys();
			Iterator it = selectedKeys.iterator();
			
			// handle read & write
			while (it.hasNext()) {
				SelectionKey sk = (SelectionKey) it.next();
				it.remove();
		
				NonBlockingConnectionImpl connection = (NonBlockingConnectionImpl) sk.attachment();

				
				// read data
				if (sk.isValid() && sk.isReadable()) {
					try {
						if (connection.isOpen()) {
							boolean isDataAvailable = connection.readFromChannel();	
							if (isDataAvailable) {
								if (isDataHandler) {
									IDataHandler hdl = (IDataHandler) connection.getHandler();
									onData(connection, hdl);
								}
							}
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
						if (connection.hasDataToSend()) {
							connection.writeToChannel();
						}
					} catch (Throwable e) {
						connection.close();
					}
				}	
			}
		}
			
		// handle new connections
		while (!newConnections.isEmpty()) {
			NonBlockingConnectionImpl connection = newConnections.get(0);
			newConnections.remove(connection);
			handleNewConnections(connection);
		}
	}		
	
	
	private void handleNewConnections(final NonBlockingConnectionImpl connection) {
		try {
			handledConnections++;
			connection.registerSelector(selector,  SelectionKey.OP_READ);

			
			// attach the handler to the connection
			IHandler hdl = handler;
			if (isHandlerConnetionScoped) {
				hdl = (IHandler) ((IConnectionScoped) handler).clone();
			} 	
			
			
			connection.setHandler(hdl);
			
			
			// handle the opening event
			if (isLifeCycleHandler) {
				onConnectionOpening(connection, (IConnectHandler) hdl);
			}
			
		} catch (Throwable e) {
			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("exception occured while accepting connection. Reason " + e.toString() 
						  + "\nclosing connection " + connection.toCompactString());
			}
			connection.close();
		} 
	}

	
	
	private void onConnectionOpening(final NonBlockingConnectionImpl connection, final IConnectHandler handler) throws IOException {
		pool.execute(new Runnable() {
			public void run() {
				try {
					handler.onConnectionOpening(connection);
				} catch (Throwable e) {
					if (LOG.isLoggable(Level.FINER))  {
						LOG.finer("error occured by handling connection opening by handler " + handler.getClass().getName() + "#" + handler.hashCode() + ". Reason: "+ e.toString());
					}
				}
			}
		});	
	}
	
	
	private void onData(final INonBlockingConnection connection, final IDataHandler handler) throws IOException {
		pool.execute(new Runnable() {
			public void run() {
				try {
					handler.onData(connection);
				} catch (Throwable e) {
					if (LOG.isLoggable(Level.FINER))  {
						LOG.finer("error occured by handling connection opening by handler " + handler.getClass().getName() + "#" + handler.hashCode() + ". Reason: "+ e.toString());
					}
				}
			}
		});	
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
	public long getConnectionTimeout() {
		return connectionTimeout;
	}
	
	
	/**
	 * @see IDispatcher
	 */
	public void setConnectionTimeout(long timeout) {
		this.connectionTimeout = timeout;
		
	}
	
	
	/**
	 * @see IDispatcher
	 */
	public long getReceivingTimeout() {
		return receivedTimeout;
	}
	
	
	/**
	 * @see IDispatcher
	 */
	public void setReceivingTimeout(long timeout) {
		this.receivedTimeout = timeout;
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
	public int getNumberOfReceivingTimeout() {
		return numberOfReceivedTimeouts;
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
	
	
	private class TimeoutWatchdog implements Runnable {
		private boolean isRunning = true;
		
		public void run() {
			while (isRunning) {
				try {
					Thread.sleep(timeoutCheckPeriod);
				} catch (InterruptedException igonre) { }
				
				check();
			}
		}
		
		void shutdown() {
			isRunning = false;
		}
		
		private void check() {
			
			long currentTime = System.currentTimeMillis();
			
			try {
				Set<SelectionKey> keySet = selector.keys(); 
				SelectionKey[] selKeys = keySet.toArray(new SelectionKey[keySet.size()]);
				
				for (SelectionKey key : selKeys) {
					NonBlockingConnectionImpl connection = (NonBlockingConnectionImpl) key.attachment();
					
					if (connectionTimeout != Long.MAX_VALUE) {
						if (currentTime > (connection.getConnectionOpenedTime() + connectionTimeout)) {
							if (LOG.isLoggable(Level.WARNING)) {
								LOG.warning("connection timeout (" + TextUtils.printFormatedDuration(connectionTimeout) + ") reached for connection " + connection.toString() 
							            	+ ". Closing connection ");
							}
							numberOfConnectionTimeouts++;
							connection.close(); 
						}
					}
	
					if (receivedTimeout != Long.MAX_VALUE) {
						if (currentTime > (connection.getLastReceivingTime() + receivedTimeout)) {
							if (LOG.isLoggable(Level.WARNING)) {
								LOG.warning("last data received timeout (" + TextUtils.printFormatedDuration(receivedTimeout) + ") reached for connection " + connection.toString() 
									        + ". Closing connection ");
							}
							numberOfReceivedTimeouts++;
							connection.close();					
						}
					}
				}
			} catch (Exception ignore) { }
		}
	}
}
