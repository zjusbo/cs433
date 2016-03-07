// $Id: DispatcherPool.java 447 2006-12-07 14:01:33Z grro $
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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.util.TextUtils;



/**
 * A pool of <code>Dispatcher</code>
 * 
 * @author grro@xsocket.org
 */
final class DispatcherPool {

	private static final Logger LOG = Logger.getLogger(DispatcherPool.class.getName());
	
	
	private boolean isRunning = false;
	
	
	// domain name
	private  String appDomain = null;
		
	
	// dispatcher management
	private final LinkedList<Dispatcher> dispatchers = new LinkedList<Dispatcher>();
	private int size = 0;
	private int pointer = 0;
	
	
	// worker pool
	private WorkerPool workerPool = null;
	
	
	// timout
	private long idleTimeout = Long.MAX_VALUE;
	private long connectionTimeout = Long.MAX_VALUE;
	private Timer watchdogTimer = new Timer(true);
	private WatchdogTask watchdogTask = null;

	
	// memory managemnt
	private int preallocationSize = 65536;	

	
	/**
	 * constructor
	 * 
	 * @param size               the pool size
	 * @param appDomain          the application domain
	 * @param workerPool         the worker pool
	 * @param idleTimeout        the idle timeout
	 * @param connectionTimeout  the connection timeout
	 */
	public DispatcherPool(int size, String appDomain, WorkerPool workerPool, long idleTimeout, long connectionTimeout) {
		this.appDomain = appDomain;
		this.workerPool = workerPool;
		setSize(size);
		setIdleTimeout(idleTimeout);
		setConnectionTimeout(connectionTimeout);
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
		for (Dispatcher dispatcher : dispatchers) {
			dispatcher.setReceiveBufferPreallocationSize(size);
		}
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
		updateTimeoutCheckPeriod();
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("idle time out has ben update to " + TextUtils.printFormatedDuration(this.idleTimeout));
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
		updateTimeoutCheckPeriod();
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("connection time out has ben update to " + TextUtils.printFormatedDuration(this.connectionTimeout));
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
	
	
	
	private void updateTimeoutCheckPeriod() {
		long period = getIdleTimeout();
		if (getConnectionTimeout() < getIdleTimeout()) {
			period = getConnectionTimeout();
		}

		setTimeoutCheckPeriod((int) (((double) period) / 5));
	}	
	
	
	/**
	 * get the pool size
	 * 
	 * @return the pool size
	 */
	int getSize() {
		return size;
	}
	
	
	/**
	 * set the pool size
	 * 
	 * @param size the pool size
	 */
	void setSize(int size) {
		this.size = size;
		updateDisptacher();
	}
	
	
	/**
	 * run the pool
	 *
	 */
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
						Dispatcher dispatcher = dispatchers.getLast();
						dispatchers.remove(dispatcher);
						dispatcher.shutdown();
					}
		
				} else if ( currentRunning < size) {
					for (int i = currentRunning; i < size; i++) {
						String dispatcherName = Dispatcher.THREAD_PREXIX + "_" + i;
						Dispatcher dispatcher = null;
						dispatcher = new Dispatcher(appDomain, dispatcherName, workerPool);
						dispatchers.addLast(dispatcher);
		
						Thread t = new Thread(dispatcher);
						t.setName(dispatcherName);
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

		for (Dispatcher dispatcher : dispatchers) {
        	dispatcher.shutdown();
        }
		
		dispatchers.clear();
	}
	
	

	/**
	 * get the next free <code>Dispatcher</code>
	 * 
	 * @return a dispatcher
	 */
	public IDispatcher nextDispatcher() {
		// round-robin approach
		pointer++;
		if (pointer >= size) {
			pointer = 0;
		}

		return dispatchers.get(pointer);
	}

	

	/**
	 * get the number of the terminated connection, 
	 * caused by the connection timeout 
	 *  
	 * @return terminated connections
	 */
	int getNumberOfConnectionTimeout() {
		int result = 0;
		for (Dispatcher disptacher : dispatchers) {
			result += disptacher.getNumberOfConnectionTimeout();
		}
		return result;
	}
	
	
	/**
	 * get the number of the terminated connection, 
	 * caused by the connection timeout 
	 *  
	 * @return terminated connections
	 */
	int getNumberOfIdleTimeout() {
		int result = 0;
		for (Dispatcher disptacher : dispatchers) {
			result += disptacher.getNumberOfIdleTimeout();
		}
		return result;
	}

	
	/**
	 * get connection info about the open 
	 * connections
	 * 
	 * @return open connection info
	 */
	List<String> getOpenConnections() {
		List<String> result = new ArrayList<String>();
		for (Dispatcher dispatcher : dispatchers) {
			result.addAll(dispatcher.getOpenConnectionInfo());
		}
		return result;
	}


	
	/**
	 * get the number of open connections
	 * 
	 * @return the number of open connections
	 */
	int getNumberOfOpenConnections() {
		int result = 0;
		for (Dispatcher dispatcher : dispatchers) {
			result += dispatcher.getNumberOfOpenConnections();
		}
		return result;
	}

	
	/**
	 * get the number of handled connections
	 * 
	 * @return the number of handled connections
	 */
	long getNumberOfHandledConnections() {
		long result = 0;
		for (Dispatcher dispatcher : dispatchers) {
			result += dispatcher.getNumberOfHandledConnections();
		}
		return result;
	}

	
	private void setTimeoutCheckPeriod(long period) {
		if (watchdogTask != null) {
			watchdogTask.cancel();
		}
		
		watchdogTask = new WatchdogTask();
		watchdogTimer.schedule(watchdogTask, period, period);
	}	
	

	@SuppressWarnings("unchecked")
	private void checkDispatcherTimeout() {
		try {
			long current = System.currentTimeMillis();
			LinkedList<Dispatcher> disps = (LinkedList<Dispatcher>) dispatchers.clone();
			for (Dispatcher dispatcher : disps) {
				dispatcher.checkTimeouts(current, idleTimeout, connectionTimeout);
			}
		} catch (Exception e) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured: " + e.toString());
			}
		}			
	}
	
		
	
	private final class WatchdogTask extends TimerTask {
		@Override
		public void run() {
			if (isRunning) {
				checkDispatcherTimeout();
			}
		}
	}
}
