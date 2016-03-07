// $Id: IoSocketDispatcherPool.java 1276 2007-05-28 15:38:57Z grro $
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
import java.nio.ByteBuffer;
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
	
	static final String DISPATCHER_PREFIX = "xDispatcher"; 
	
	private boolean isRunning = true;
	

	
	// memory management
	private int preallocationSize = 65536;	
	private final IMemoryManager multiThreadedMemoryManager = new MultiThreadedMemoryManager(); 
	
	
	// listeners
	private final List<IMutlithreadedServerListener> listeners = new ArrayList<IMutlithreadedServerListener>();

	
	
	// dispatcher management
	private final LinkedList<IoSocketDispatcher> dispatchers = new LinkedList<IoSocketDispatcher>();
	private int size = 0;
	private int pointer = 0;

	
	IoSocketDispatcherPool(int preallocationSize) {
		this.preallocationSize = preallocationSize;
	}
	
	
	synchronized void setSize(int size) {
		this.size = size;
		updateDispatcher();
	}
	
	synchronized void addListener(IMutlithreadedServerListener listener) {
		listeners.add(listener);
	}
	
	
	synchronized boolean removeListener(IMutlithreadedServerListener listener) {
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
	
	
	IMemoryManager getMultithreadedMemoryManager() {
		return multiThreadedMemoryManager;
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
						
						for (IMutlithreadedServerListener listener : listeners) {
							listener.onDispatcherRemoved(dispatcher);
						}
					}
		
				} else if ( currentRunning < size) {
					for (int i = currentRunning; i < size; i++) {
						IoSocketDispatcher dispatcher = new IoSocketDispatcher(new SingleThreadedMemoryManager());
						dispatchers.addLast(dispatcher);
		
						Thread t = new Thread(dispatcher);
						t.setDaemon(false);
						t.setName(DISPATCHER_PREFIX + "#" + i);
						t.start();
						
						for (IMutlithreadedServerListener listener : listeners) {
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
