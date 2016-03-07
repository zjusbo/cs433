// $Id: IoSocketDispatcherPool.java 1304 2007-06-02 13:26:34Z grro $
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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.xsocket.IDispatcher;


/**
 * Dispatcher Pool
 * 
 * 
 * @author grro@xsocket.org
 */
final class IoSocketDispatcherPool {
	
	private static final Logger LOG = Logger.getLogger(IoSocketDispatcherPool.class.getName());
	
	private boolean isRunning = true;
	

	
	// memory management
	private int preallocationSize = 65536;	
	private boolean useDirect = false;
	
	
	// listeners
	private final List<IIoSocketDispatcherPoolListener> listeners = new ArrayList<IIoSocketDispatcherPoolListener>();

	
	
	// dispatcher management
	private final LinkedList<IoSocketDispatcher> dispatchers = new LinkedList<IoSocketDispatcher>();
	private int size = 0;
	private int pointer = 0;

	
	IoSocketDispatcherPool(int preallocationSize, boolean useDirect) {
		this.preallocationSize = preallocationSize;
		this.useDirect = useDirect;
	}
	
	
	synchronized void setSize(int size) {
		this.size = size;
		updateDispatcher();
	}
	
	synchronized void addListener(IIoSocketDispatcherPoolListener listener) {
		listeners.add(listener);
	}
	
	
	synchronized boolean removeListener(IIoSocketDispatcherPoolListener listener) {
		return listeners.remove(listener);
	}


	void run() {
		isRunning = true;
		updateDispatcher();
	}
	
	
	
	/**
	 * get the size of the preallocation buffer, 
	 * for reading incomming data
	 *   
	 * @return preallocation buffer size
	 */
	synchronized int getReceiveBufferPreallocationSize() {
		return preallocationSize;
	}

	/**
	 * set the size of the preallocation buffer, 
	 * for reading incomming data
	 *   
	 * @param size the preallocation buffer size
	 */
	synchronized void setReceiveBufferPreallocationSize(int size) {
		preallocationSize = size;
	}
	

	@SuppressWarnings("unchecked")
	List<IDispatcher<IoSocketHandler>> getDispatchers() {
		List<IDispatcher<IoSocketHandler>> result = null;
		synchronized (dispatchers) {
			result = (List<IDispatcher<IoSocketHandler>>) dispatchers.clone();
		}
		return result;
	}
	
	


	@SuppressWarnings("unchecked")
	private synchronized void updateDispatcher() {
		if (isRunning) {
			int currentRunning = dispatchers.size();
			
			if (currentRunning != size) {
				if (currentRunning > size) {
					for (int i = size; i <  currentRunning; i++) {
						IDispatcher<IoSocketHandler> dispatcher = dispatchers.getLast();
						dispatchers.remove(dispatcher);
						try {
							dispatcher.close();
						} catch (IOException ioe) {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("error occured by closing the dispatcher " + dispatcher + ". reason " + ioe.toString());
							}
						}
						
						for (IIoSocketDispatcherPoolListener listener : listeners) {
							listener.onDispatcherRemoved(dispatcher);
						}
					}
		
				} else if ( currentRunning < size) {
					for (int i = currentRunning; i < size; i++) {
						IoSocketDispatcher dispatcher = new IoSocketDispatcher(new UnsynchronizedMemoryManager(preallocationSize, useDirect));
						dispatchers.addLast(dispatcher);
		
						Thread t = new Thread(dispatcher);
						t.setDaemon(false);
						t.setName(IoSocketDispatcher.DISPATCHER_PREFIX + "#" + i);
						t.start();
						
						for (IIoSocketDispatcherPoolListener listener : listeners) {
							listener.onDispatcherAdded(dispatcher);
						}

					}
				}
			}
			
			IDispatcher<IoSocketHandler>[] connectionDispatchers = new IDispatcher[dispatchers.size()]; 
			for (int i = 0; i < connectionDispatchers.length; i++) {
				connectionDispatchers[i] = dispatchers.get(i);
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

		for (IDispatcher<IoSocketHandler> dispatcher : dispatchers) {
			try {
				dispatcher.close();
				
				for (IIoSocketDispatcherPoolListener listener : listeners) {
					listener.onDispatcherRemoved(dispatcher);
				}
				
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by closing the dispatcher " + dispatcher + ". reason " + ioe.toString());
				}
			}
        }
		
		dispatchers.clear();
	}
	


	
	IoSocketDispatcher nextDispatcher() {
		// round-robin approach
		pointer++;
		if (pointer >= size) {
			pointer = 0;
		}

		return dispatchers.get(pointer);
	}
	
	

	@SuppressWarnings("unchecked")
	long getNumberOfConnectionTimeouts() {
		long timeouts = 0;
		
		LinkedList<IoSocketDispatcher> copy = (LinkedList<IoSocketDispatcher>) dispatchers.clone();
		for (IoSocketDispatcher dispatcher : copy) {
			timeouts += dispatcher.getCountConnectionTimeout();
		}
		return timeouts;
	}
	
	
	@SuppressWarnings("unchecked")
	public long getNumberOfIdleTimeouts() {
		long timeouts = 0;
		
		LinkedList<IoSocketDispatcher> copy = (LinkedList<IoSocketDispatcher>) dispatchers.clone();
		for (IoSocketDispatcher dispatcher : copy) {
			timeouts += dispatcher.getCountIdleTimeout();
		}
		return timeouts;
	}
	
}
