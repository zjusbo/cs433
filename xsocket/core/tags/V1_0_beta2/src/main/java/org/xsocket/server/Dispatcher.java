// $Id: Dispatcher.java 449 2006-12-09 07:02:10Z grro $
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
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.ObjectName;
import javax.management.StandardMBean;

import org.xsocket.server.management.DispatcherMBean;



/**
 * A Dispatcher implementation 
 *
 * @author grro@xsocket.org
 */
final class Dispatcher implements IDispatcher, Runnable, DispatcherMBean {
	
	private static final Logger LOG = Logger.getLogger(Dispatcher.class.getName());
	
	private static final boolean TIME_TRACE_ON = false;
	
	static final String THREAD_PREXIX = "Dispatcher"; 
	
	
	
	// is running flag
	private boolean isRunning = true;

	
	// guard object for synchronizing
 	private Object dispatcherThreadGuard = new Object();

	
	// name
	private String name = null;
	private  String appDomain = null;


	// connection handling
	private Selector demultiplexer = null;

	
	// event handler
	private final EventHandler eventHandler = new EventHandler(this);
	
	
	// memory management 
	private final DirectMemoryManager ioMemoryManager = new DirectMemoryManager(65536);


	
	// statistics
	private ObjectName mbeanName = null;
	private long handledConnections = 0;
	private int numberOfConnectionTimeouts = 0;
	private int numberOfIdleTimeouts = 0;
	


	
	/**
	 * constructor
	 * 
	 * @param appDomain   the application domain
	 * @param name        the disptacher name
	 * @param workerPool  the worker pool
	 */
	Dispatcher(String appDomain, String name, WorkerPool workerPool)  {
		this.appDomain = appDomain;
		this.name = name;
		
		eventHandler.setWorkerPool(workerPool);
	}
		

	/**
	 * {@inheritDoc}
	 */
	public void registerConnection(ManagedConnection connection) throws IOException {
		connection.setIOMemoryManager(ioMemoryManager);
		connection.setConnectionListener(eventHandler);
		
		synchronized (dispatcherThreadGuard) {
			demultiplexer.wakeup();
			
			connection.getChannel().register(demultiplexer, SelectionKey.OP_READ, connection);
		}
		
		eventHandler.onDispatcherRegisteredEvent(connection);
		handledConnections++;
	}

	
	/**
	 * {@inheritDoc}
	 */
	public void deregisterConnection(ManagedConnection connection) throws IOException {

		synchronized (dispatcherThreadGuard) {
			demultiplexer.wakeup();
			
			SelectionKey key = connection.getChannel().keyFor(demultiplexer);
			key.interestOps(0);
			key.cancel();
		}
	}

		

	

	/**
	 * {@inheritDoc}
	 */
	public void announceWriteDemand(ManagedConnection connection) {
		SelectionKey key = connection.getChannel().keyFor(demultiplexer);
		
		if (key != null) {
			synchronized (dispatcherThreadGuard) {
				timeTrace("set readwrite op start");
	
				if (key.isValid()) {
					key.selector().wakeup();	
					key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
				}
					
				timeTrace("set readwrite op end");				
			}
		}
	}

	

	
	private void init() {

        LOG.fine("opening selector to accept data");
		try {
			demultiplexer = Selector.open();
		} catch (IOException ioe) {
			String text = "exception occured while opening selector. Reason: " + ioe.toString();
			LOG.severe(text);
			throw new RuntimeException(text, ioe);
		}

        try {
        	StandardMBean mbean = new StandardMBean(this, DispatcherMBean.class);
        	mbeanName = new ObjectName(appDomain + ":type=Dispatcher,name=" + name);
			ManagementFactory.getPlatformMBeanServer().registerMBean(mbean, mbeanName);
        } catch (Exception mbe) {
        	LOG.warning("error " + mbe.toString() + " occured while registering mbean");
        }
	}



	
	/**
	 * {@inheritDoc}
	 */
	public final void run() {
		init();
				
		while(isRunning) {
			try {
				
				// empty synchronized block is required to 
				// to prevent that the dispatcher thread will
				// call the blocking select call (and sleeping) 
				// while another thread is holding the gard object. 
				// This is required because selector operations (register channel,
				// update interestOp) doesn't work, while the 
				// dispatcher thread is hanging in the selection call 
				// see http://developers.sun.com/learning/javaoneonline/2006/coreplatform/TS-1315.pdf
				synchronized (dispatcherThreadGuard) { /* suspend the dispatcher thead */	}
				
				
				// waiting for new events (data, ...)
				int eventCount = demultiplexer.select();
							
				
				// handle read write events
				if (eventCount > 0) {
					Set selectedEventKeys = demultiplexer.selectedKeys();
					Iterator it = selectedEventKeys.iterator();

					// handle read & write
					while (it.hasNext()) {
						SelectionKey eventKey = (SelectionKey) it.next();
						it.remove();

						ManagedConnection connection = (ManagedConnection) eventKey.attachment();
						
						// read data
						if (eventKey.isValid() && eventKey.isReadable()) {
							timeTrace(null);
							timeTrace("read start ");
								
							// notify event handler
							eventHandler.onDispatcherReadableEvent(connection);

							timeTrace("read end ");
						}

						// write data
						if (eventKey.isValid() && eventKey.isWritable()) {
							timeTrace("write start ");

							// reset interest ops
							eventKey.interestOps(SelectionKey.OP_READ);
							
							// notify event handler
							eventHandler.handleWriteableEvent(connection);
							
							timeTrace("write end ");
						}
					}
				}

			} catch (Throwable e) {
				LOG.warning("exception occured while processing. Reason " + e.toString());
			}
		}

				
        closeDispatcher();
	}


	private void closeDispatcher() {
		try {
        	ManagementFactory.getPlatformMBeanServer().unregisterMBean(mbeanName);
        } catch (Exception mbe) {
        	LOG.warning("error " + mbe.toString() + " occured while unregistering mbean");
        }

        LOG.fine("closing connections");
	        
        if (demultiplexer != null) {
        	for (SelectionKey sk : demultiplexer.keys()) {
        		try {
        			final ManagedConnection connection = (ManagedConnection) sk.attachment();
        			connection.close();
   					
        		} catch (Exception ignore) { 
        			// ignore
        		}
			}
        }
	        
			
		if (demultiplexer != null) {
			try {
				demultiplexer.close();
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by close selector within tearDown " + ioe.toString());
				}
			}
		}
	}

	
	/**
	 * {@inheritDoc}
	 */
	public void shutdown() {
		if (isRunning) {
			isRunning = false;
			
			if (demultiplexer != null) {
				// wake up selector, so that isRunning-loop can be terminated
				demultiplexer.wakeup();
			}
		}
	}

	public boolean isClosed() {
		return !isRunning;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getReceiveBufferPreallocationSize() {
		return ioMemoryManager.getPreallocationSize();
	}

	
	/**
	 * set the receive preallocation buffer size 
	 * 
	 * @param size the receive preallocation buffer size 
	 */
	void setReceiveBufferPreallocationSize(int size) {
		ioMemoryManager.setPreallocationSize(size);
	}

	
	/**
	 * {@inheritDoc}
	 */
	public int getCurrentPreallocatedBufferSize() {
		return ioMemoryManager.getCurrentPreallocationBufferSize();
	}
	
	
	/**
	 * returns a list of info strings for each open connection
	 * 
	 * @return a list of info strings
	 */
	List<String> getOpenConnectionInfo() {
		List<String> result = new ArrayList<String>();
		
		for (SelectionKey sk : demultiplexer.keys()) {
			ManagedConnection connection = (ManagedConnection) sk.attachment();
			result.add(connection.toString());
		}
		return result;
	}

		
	/**
	 * {@inheritDoc}
	 */
	public int getNumberOfIdleTimeout() {
		return numberOfIdleTimeouts;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public int getNumberOfOpenConnections() {
		return demultiplexer.keys().size();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public int getNumberOfConnectionTimeout() {
		return numberOfConnectionTimeouts;
	}

	/**
	 * {@inheritDoc}
	 */
	public long getNumberOfHandledConnections() {
		return handledConnections;
	}

		
	static boolean isDispatcherThread() {
		return Thread.currentThread().getName().startsWith(THREAD_PREXIX);
	}
	
	
	/**
	 * perform the timeout checks for the open connections,
	 * which are registered on this dispatcher 
	 * 
	 * @param current  the current time
	 */
	void checkTimeouts(long current, long idleTimeout, long connectionTimeout) {
		assert (!isDispatcherThread());
		
		try {
			if (demultiplexer != null) {
				try {
					Set<SelectionKey> keySet = demultiplexer.keys();
					SelectionKey[]selKeys = keySet.toArray(new SelectionKey[keySet.size()]);
			
					for (SelectionKey key : selKeys) {
						ManagedConnection connection = (ManagedConnection) key.attachment();
			
						
						if (connection.checkIdleTimeoutOccured(current, idleTimeout)) {
							numberOfIdleTimeouts++;
						}
		
						if (connection.checkConnectionTimeoutOccured(current, connectionTimeout)) {
							numberOfConnectionTimeouts++;
						}
					}
				} catch (Exception ignore) { }
			}
		} catch (Exception ignore) {
			// ignore
		}
	}
	
	
	private static void timeTrace(String msg) {
		if (TIME_TRACE_ON) {
			if (msg == null) {
				System.out.println("");
			} else {
				System.out.println((System.nanoTime() / 1000) + " microsec [" + Thread.currentThread().getName() + "] " + msg);
			}
		}
	}
}
