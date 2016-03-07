/*
 * Copyright (c) xlightweb.org, 2006 - 2010. All rights reserved.
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
package org.xsocket.connection;


import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;



/**
 * Dispacher pool
 * 
 *
 * @author grro@xsocket.org
 */
final class IoSocketDispatcherPool implements Closeable {

	private static final Logger LOG = Logger.getLogger(IoSocketDispatcherPool.class.getName());
	
    
    // open flag
    private volatile boolean isOpen = true;

    // name 
    private final String name;

    
	// listeners
	private final ArrayList<IIoDispatcherPoolListener> listeners = new ArrayList<IIoDispatcherPoolListener>();

    
	// memory management
	private int preallocationSize = IoProvider.DEFAULT_READ_BUFFER_PREALLOCATION_SIZE;
	private int bufferMinsize = IoProvider.DEFAULT_READ_BUFFER_MIN_SIZE;
	private boolean preallocation = true; 
	private boolean useDirect = false;

 
	// dispatcher management
	private final LinkedList<IoSocketDispatcher> dispatchers = new LinkedList<IoSocketDispatcher>();
	private int size;
	private int pointer;

    
	
    // statistics
    private long acceptedConnections;
	private long lastRequestAccpetedRate = System.currentTimeMillis();


	public IoSocketDispatcherPool(String name, int size) {
		this.name = name;
		setDispatcherSize(size);
    }
    

    void addListener(IIoDispatcherPoolListener listener) {
		listeners.add(listener);
	}


	boolean removeListener(IIoDispatcherPoolListener listener) {
		return listeners.remove(listener);
	}



	IoSocketDispatcher nextDispatcher() throws IOException {
		return nextDispatcher(0);
	}
	
	
	private IoSocketDispatcher nextDispatcher(int currentTrial) throws IOException {
		IoSocketDispatcher dispatcher = null;
		
		if (!isOpen) {
		    throw new IOException("dispatcher is already closed");
		}
		
		try {
			// round-robin approach
			pointer++;
			if (pointer >= size) { // unsynchronized access of size is OK here (findbugs will criticize this)
				pointer = 0;
			}
	
			dispatcher = dispatchers.get(pointer);
			boolean peregistered = dispatcher.preRegister();
			
			if (peregistered) {
				return dispatcher;
				
			} else {
				if (currentTrial < size) {
					return nextDispatcher(++currentTrial);
				} else {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("increasing dispatcher size because max handle size " + dispatcher.getMaxRegisterdHandles() + " of all " + size + " dispatcher reached");
					}
					incDispatcherSize();
					return nextDispatcher(0);
				}
			}
			
		} catch (Exception concurrentException) {
			if (isOpen) {
				if (currentTrial < 3) {
					dispatcher = nextDispatcher(++currentTrial);
				} else {
					throw new IOException(concurrentException.toString());
				}
			} 
		}
		
		return dispatcher;
	}
	
	
	
	

	private synchronized void updateDispatcher() {
		if (isOpen) {
			synchronized (dispatchers) {
				int currentRunning = dispatchers.size();
	
				if (currentRunning != size) {
					if (currentRunning > size) {
						for (int i = size; i <  currentRunning; i++) {
							IoSocketDispatcher dispatcher = dispatchers.getLast();
							dispatchers.remove(dispatcher);
							try {
								dispatcher.close();
							} catch (IOException ioe) {
								if (LOG.isLoggable(Level.FINE)) {
									LOG.fine("error occured by closing the dispatcher " + dispatcher + ". reason " + ioe.toString());
								}
							}
	
							for (IIoDispatcherPoolListener listener : listeners) {
								listener.onDispatcherRemoved(dispatcher);
							}
						}
	
					} else if ( currentRunning < size) {
						for (int i = currentRunning; i < size; i++) {
							IoUnsynchronizedMemoryManager memoryManager = null;
							if (preallocation) {
								memoryManager = IoUnsynchronizedMemoryManager.createPreallocatedMemoryManager(preallocationSize, bufferMinsize, useDirect);
							} else {
								memoryManager = IoUnsynchronizedMemoryManager.createNonPreallocatedMemoryManager(useDirect);
							}
							
							IoSocketDispatcher dispatcher = new IoSocketDispatcher(memoryManager, name + "#" + i);
							dispatchers.addLast(dispatcher);
	
							Thread t = new Thread(dispatcher);
							t.setDaemon(true);
							t.start();
	
							for (IIoDispatcherPoolListener listener : listeners) {
								listener.onDispatcherAdded(dispatcher);
							}
	
						}
					}
				}
	
				IoSocketDispatcher[] connectionDispatchers = new IoSocketDispatcher[dispatchers.size()];
				for (int i = 0; i < connectionDispatchers.length; i++) {
					connectionDispatchers[i] = dispatchers.get(i);
				}
			}
		}
	}


    

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        if (isOpen) {
            isOpen = false;

            shutdownDispatcher();
        }
    }

    
    

	/**
	 * shutdown the pool
	 *
	 */
	private void shutdownDispatcher() {
        if (LOG.isLoggable(Level.FINER)) {
			LOG.fine("terminate dispatchers");
		}

        synchronized (dispatchers) {
			for (IoSocketDispatcher dispatcher : dispatchers) {
				try {
					dispatcher.close();
	
					for (IIoDispatcherPoolListener listener : listeners) {
						listener.onDispatcherRemoved(dispatcher);
					}
	
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("error occured by closing the dispatcher " + dispatcher + ". reason " + ioe.toString());
					}
				}
	        }
        }

		dispatchers.clear();
	}

	
	int getNumRegisteredHandles() {
	    int num = 0;
	    for (IoSocketDispatcher dispatcher : getDispatchers()) {
	        num += dispatcher.getNumRegisteredHandles();
        }
	    return num;
	}

	    
	int getRoughNumRegisteredHandles() {
        int num = 0;
        for (IoSocketDispatcher dispatcher : getDispatchers()) {
            num += dispatcher.getRoughNumRegisteredHandles();
        }
        return num;
	}


    
    public List<String> getOpenConntionInfos() {
    	List<String> result = new ArrayList<String>();
    	
        for (IoSocketDispatcher dispatcher : getDispatchers()) {
            for (IoSocketHandler handler : dispatcher.getRegistered()) {
                result.add(handler.toString());
            }
        }
        return result;
    }
    
    
	@SuppressWarnings("unchecked")
	List<IoSocketDispatcher> getDispatchers() {
		List<IoSocketDispatcher> result = null;
		synchronized (dispatchers) {
			result = (List<IoSocketDispatcher>) dispatchers.clone();
		}
		return result;
	}

    
	
	synchronized void setDispatcherSize(int size) {
    	this.size = size;
		updateDispatcher();
    }
    
	synchronized int getDispatcherSize() {
    	return size;
    }
	
	synchronized void incDispatcherSize() {
    	setDispatcherSize(getDispatcherSize() + 1);
    }
	

    
	boolean getReceiveBufferIsDirect() {
		return useDirect;
	}

	void setReceiveBufferIsDirect(boolean isDirect) {
		this.useDirect = isDirect; 
		for (IoSocketDispatcher dispatcher: dispatchers) {
			dispatcher.setReceiveBufferIsDirect(isDirect);
		}
	}

	
	boolean isReceiveBufferPreallocationMode() {
    	return preallocation;
	}
	    
	void setReceiveBufferPreallocationMode(boolean mode) {
		this.preallocation = mode;

		synchronized (dispatchers) {
			for (IoSocketDispatcher dispatcher: dispatchers) {
				dispatcher.setReceiveBufferPreallocationMode(mode);
			}
		}
	}
	
	void setReceiveBufferPreallocatedMinSize(Integer minSize) {
		this.bufferMinsize = minSize;
		
		synchronized (dispatchers) {
			for (IoSocketDispatcher dispatcher: dispatchers) {
				dispatcher.setReceiveBufferPreallocatedMinSize(minSize);
			}
		}
	}
	
	
	Integer getReceiveBufferPreallocatedMinSize() {
   		return bufferMinsize;
 	}
	
	
	
	/**
	 * get the size of the preallocation buffer,
	 * for reading incoming data
	 *
	 * @return preallocation buffer size
	 */
	Integer getReceiveBufferPreallocationSize() {
		return preallocationSize;
	}

	/**
	 * set the size of the preallocation buffer,
	 * for reading incoming data
	 *
	 * @param size the preallocation buffer size
	 */
	void setReceiveBufferPreallocationSize(int size) {
		preallocationSize = size;
		
		for (IoSocketDispatcher dispatcher: dispatchers) {
			dispatcher.setReceiveBufferPreallocatedSize(size);
		}
	}


	  
    double getAcceptedRateCountPerSec() {
     	double rate = 0;
		
    	long elapsed = System.currentTimeMillis() - lastRequestAccpetedRate;
    	
    	if (acceptedConnections == 0) {
    		rate = 0;
    		
    	} else if (elapsed == 0) {
    		rate = Integer.MAX_VALUE;
    		
    	} else {
    		rate = (((double) (acceptedConnections * 1000)) / elapsed);
    	}
    		
    	lastRequestAccpetedRate = System.currentTimeMillis();
    	acceptedConnections = 0;

    	return rate;
    }
    
	
	long getSendRateBytesPerSec() {
		long rate = 0;
		for (IoSocketDispatcher dispatcher : dispatchers) {
			rate += dispatcher.getSendRateBytesPerSec();
		}
		
		return rate;
	}
	
	
	long getReceiveRateBytesPerSec() {
		long rate = 0;
		for (IoSocketDispatcher dispatcher : dispatchers) {
			rate += dispatcher.getReceiveRateBytesPerSec();
		}
		
		return rate;
	}
	


	@SuppressWarnings("unchecked")
	long getNumberOfConnectionTimeouts() {
		long timeouts = 0;

		LinkedList<IoSocketDispatcher> copy = null;
		synchronized (dispatchers) {
			copy = (LinkedList<IoSocketDispatcher>) dispatchers.clone();
		}
		for (IoSocketDispatcher dispatcher : copy) {
			timeouts += dispatcher.getCountConnectionTimeout();
		}
		return timeouts;
	}


	@SuppressWarnings("unchecked")
	public long getNumberOfIdleTimeouts() {
		long timeouts = 0;

		LinkedList<IoSocketDispatcher> copy = null;
		synchronized (dispatchers) {
			copy = (LinkedList<IoSocketDispatcher>) dispatchers.clone();
		}
		for (IoSocketDispatcher dispatcher : copy) {
			timeouts += dispatcher.getCountIdleTimeout();
		}
		return timeouts;
	}


}
