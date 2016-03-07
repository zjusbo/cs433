/*
 *  Copyright (c) xsocket.org, 2006 - 2009. All rights reserved.
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
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;



/**
 * Implementation of the {@link IDispatcher}
 *
 * <br/><br/><b>This is a xSocket internal class and subject to change</b>
 *
 * @author grro@xsocket.org
 */
final class IoSocketDispatcher implements Runnable, Closeable {

	private static final Logger LOG = Logger.getLogger(IoSocketDispatcher.class.getName());

    static final String DISPATCHER_PREFIX = "xDispatcher";


	private static final long TIMEOUT_SHUTDOWN_MILLIS = 5L * 1000L;

	private static final int MAX_DIRECT_CALLS = 10;
	
	private static final boolean IS_DETACH_HANDLE_ON_NO_OPS = IoProvider.getDetachHandleOnNoOps();
	private static boolean isBypassingWriteAllowed = IoProvider.isBypassingWriteAllowed();

	// queues
	private final ConcurrentLinkedQueue<Runnable> registerQueue = new ConcurrentLinkedQueue<Runnable>();
	private final ConcurrentLinkedQueue<IoSocketHandler> deregisterQueue = new ConcurrentLinkedQueue<IoSocketHandler>();
	private final ConcurrentLinkedQueue<Runnable> keyUpdateQueue = new ConcurrentLinkedQueue<Runnable>();

	// id
	private static int nextId = 1;
	private final String name;
	private final int id;
	private final static ThreadLocal<Integer> THREADBOUND_ID = new ThreadLocal<Integer>();
	private final static ThreadLocal<Integer> DIRECT_CALL_COUNTER = new ThreadLocal<Integer>();
	

	// flags
	private volatile boolean isOpen = true;
	private volatile boolean isIdle = true;
	
	


	// closer
	private Closer closer = new Closer();


	// connection handling
	private final Selector selector;
	private boolean isSelectedKeysSetModified = false;

	// memory management
	private final AbstractMemoryManager memoryManager;



	private static final Integer MAX_HANDLES = IoProvider.getMaxHandles();;
	private TimerTask sizeUpdateTask;
	private int registeredHandles = 0;




	// statistics
    private long statisticsStartTime = System.currentTimeMillis();
    private long countIdleTimeouts = 0;
    private long countConnectionTimeouts = 0;
	private long handledRegistractions = 0;
	private long handledReads = 0;
	private long handledWrites = 0;

	private long lastRequestReceiveRate = System.currentTimeMillis();
	private long lastRequestSendRate = System.currentTimeMillis();
	private long receivedBytes = 0;
	private long sentBytes = 0;

	private long countUnregisteredWrite = 0;



	public IoSocketDispatcher(AbstractMemoryManager memoryManager, String name)  {
		this.memoryManager = memoryManager;
		this.name = IoSocketDispatcher.DISPATCHER_PREFIX + name;

		synchronized (this) {
			id = nextId;
			nextId++;
		}


		if (MAX_HANDLES != null) {
			sizeUpdateTask = new TimerTask() {
				@Override
				public void run() {
					int registered = selector.keys().size();
					if (registered < registeredHandles) {
						registeredHandles--;
					}
				}
			};
			IoProvider.getTimer().schedule(sizeUpdateTask, 2000, 2000);

		}


		try {
			selector = Selector.open();
		} catch (IOException ioe) {
			String text = "exception occured while opening selector. Reason: " + ioe.toString();
			LOG.severe(text);
			throw new RuntimeException(text, ioe);
		}
	}
	
	static void setBypassingWriteAllowed(boolean allowed) {
	    isBypassingWriteAllowed = allowed;
	}
	

	String getName() {
		return name;
	}
	
	int getId() {
		return id;
	}

	private static Integer getThreadBoundId() {
		return THREADBOUND_ID.get();
	}

	private static Integer getDirectCallCounter() {
	    return DIRECT_CALL_COUNTER.get();
	}
	
	private static void incDirectCallCounter() {
	    int i = getDirectCallCounter();
	    i++;
        DIRECT_CALL_COUNTER.set(i);
    }
	
	private static void resetDirectCallCounter() {
        DIRECT_CALL_COUNTER.set(0);
    }
	
	long getCountUnregisteredWrite() {
		return countUnregisteredWrite;
	}

	Integer getHandlesMaxCount() {
		return MAX_HANDLES;
	}

	int getRegisteredHandles() {
		return registeredHandles;
	}

	boolean isDetachHandleOnNoOps() {
		return IS_DETACH_HANDLE_ON_NO_OPS;
	}

	
	boolean isBypassingWriteAllowed() {
	    return isBypassingWriteAllowed;
	}

	

	/**
	 * {@inheritDoc}
	 */
	public void run() {

		// set thread name and attach dispatcher id to thread
		Thread.currentThread().setName(name);
		THREADBOUND_ID.set(id);
		
		DIRECT_CALL_COUNTER.set(0);

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("selector " + name + " listening ...");
		}

		int handledTasks = 0;

		while(isOpen) {
			try {

				isIdle = true;
				int eventCount = selector.select(1000);
				isIdle = false;
				
				handledTasks = performRegisterHandlerTasks();
				handledTasks += performKeyUpdateTasks();

				if (eventCount > 0) {
					handleReadWriteKeys();
				}

				handledTasks += performDeregisterHandlerTasks();

			} catch (Exception e) {
                // eat and log exception
				LOG.warning("[" + Thread.currentThread().getName() + "] exception occured while processing. Reason " + DataConverter.toString(e));
			}
		}



		try {
			selector.close();
		} catch (Exception e) {
            // eat and log exception
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by close selector within tearDown " + DataConverter.toString(e));
			}
		}
	}


	private void handleReadWriteKeys() {
		Set<SelectionKey> selectedEventKeys = selector.selectedKeys();
		isSelectedKeysSetModified  = false;

		Iterator<SelectionKey> it = selectedEventKeys.iterator();

		// handle read & write
		while (it.hasNext() && ! isSelectedKeysSetModified) {
		    
		    try {
    			SelectionKey eventKey = it.next();
    			it.remove();
    
    			IoSocketHandler socketHandler = (IoSocketHandler) eventKey.attachment();
    
    			// read data
    			if (eventKey.isValid() && eventKey.isReadable()) {
    				onReadableEvent(socketHandler);
    			}
    
    			// write data
    			if (eventKey.isValid() && eventKey.isWritable()) {
    				onWriteableEvent(socketHandler);
    			}
		    } catch (Exception e) {
                // eat and log exception
		        if (LOG.isLoggable(Level.FINE)) {
		            LOG.fine("error occured by handling selection keys + " + e.toString());
		        }
		    }
		}
	}


	private void onReadableEvent(IoSocketHandler socketHandler) {
		try {
			long read = socketHandler.onReadableEvent();
			receivedBytes += read;
			handledReads++;

		} catch (ClosedChannelException ce) {
			socketHandler.closeSilence(false);

		} catch (Exception t) {
			socketHandler.closeSilence(false);
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + socketHandler.getId() + "] error occured by handling readable event. reason: " + t.toString());
			}
		}
	}


	private void onWriteableEvent(IoSocketHandler socketHandler) {

		try {
			onWriteableEventUnprotected(socketHandler);

		} catch (ClosedChannelException ce) {
			socketHandler.closeSilence(false);

		} catch (Exception t) {
			socketHandler.closeSilence(false);
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + socketHandler.getId() + "] error occured by handling readable event. reason: " + t.toString());
			}
		}
	}

	
	private void onWriteableEventUnprotected(IoSocketHandler socketHandler) throws IOException {
		socketHandler.onWriteableEvent();
		handledWrites++;
	}
	
	
	void incSentBytes(int addSize) {
	    sentBytes += addSize;
	}


	boolean preRegister() {
		if (MAX_HANDLES == null) {
			return true;
		}

		synchronized (this) {
			if (registeredHandles < MAX_HANDLES) {
				registeredHandles++;
				return true;
			}
		}

		return false;
	}


	/**
	 * {@inheritDoc}
	 */
	public boolean register(IoSocketHandler handler, int ops) throws IOException {
		assert (!handler.getChannel().isBlocking());

		synchronized (handler) {
			handler.setDetached(false);
		}

		handler.setMemoryManager(memoryManager);

		Integer tbid = getThreadBoundId();
		if ((tbid != null) && (tbid == id)) {
			registerHandlerNow(handler, ops);
			return true;
		}

		addToRegisterQueue(handler, ops);

		return true;
	}


	private void addToRegisterQueue(final IoSocketHandler socketHandler, final int ops) {

		Runnable registerTask = new Runnable() {

			public void run() {
				if (LOG.isLoggable(Level.FINE)) {
			        LOG.fine("registering handler " + socketHandler.getId());
				}

				try {
					registerHandlerNow(socketHandler, ops);
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("error occured by registering handler " + socketHandler.getId() + " " + DataConverter.toString(ioe));
					}
				}
			}
		};
		registerQueue.add(registerTask);
		wakeUp();
	}


	/**
	 * {@inheritDoc}
	 */
	public void deregister(IoSocketHandler handler) {

		Integer tbid = getThreadBoundId();
		if ((tbid != null) && (tbid == id)) {
			deregisterHandlerNow(handler);
			return;
		}

		addToDeregisterQueue(handler);
	}


	private void addToDeregisterQueue(IoSocketHandler handler) {

		deregisterQueue.add(handler);
		wakeUp();
	}



	void wakeUp() {
		selector.wakeup();
	}



	void suspendRead(final IoSocketHandler socketHandler) throws IOException {

		Integer tbid = getThreadBoundId();
		if ((tbid != null) && (tbid == id)) {
			setReadSelectionKeyNow(socketHandler, false);
			return;
		}


		Runnable keyUpdateTask = new Runnable() {

			public void run() {
				try {
					setReadSelectionKeyNow(socketHandler, false);
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("error occured by set read key now for " + socketHandler.getId() + " " + DataConverter.toString(ioe));
					}
				}
			}
			
			@Override
			public String toString() {
				return "unsetReadSelectionKeyUpdateTask#" + super.toString();
			}

		};

		addToKeyUpdateQueue(keyUpdateTask);
	}


	void resumeRead(final IoSocketHandler socketHandler) throws IOException {

		Integer tbid = getThreadBoundId();
		if ((tbid != null) && (tbid == id)) {
			setReadSelectionKeyNow(socketHandler, true);
			return;
		}


		Runnable keyUpdateTask = new Runnable() {

			public void run() {
				try {
					setReadSelectionKeyNow(socketHandler, true);
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("error occured by set read key now for " + socketHandler.getId() + " " + DataConverter.toString(ioe));
					}
				}
			}
			
			@Override
			public String toString() {
				return "setReadSelectionKeyUpdateTask#" + super.toString();
			}
		};

		addToKeyUpdateQueue(keyUpdateTask);
	}





	void initializeWrite(final IoSocketHandler socketHandler, final boolean unregisteredWriteAllowed, boolean isBypassingSelectorAllowed) throws IOException {
	    
	    // write without wake up allowed?
	    if (isBypassingSelectorAllowed && isDispatcherThread()) {
	    
    		// [performance optimization] is handler detached? -> direct write without registering
    		if (unregisteredWriteAllowed) {
    			synchronized (socketHandler) {
    				if (socketHandler.isDetached()) {
    					socketHandler.onDirectUnregisteredWriteEvent();
    					countUnregisteredWrite++;
    					return;
    				}
    			}
    		}
        		
    		// [performance optimization] running within dispatcher thread? -> no wakeup required
    		setWriteSelectionKeyNow(socketHandler, unregisteredWriteAllowed);
    		return;
	    }

	    
		// handler is attached and write is initialized by a worker thread
		Runnable keyUpdateTask = new Runnable() {

			public void run() {
				try { 
					setWriteSelectionKeyNow(socketHandler, unregisteredWriteAllowed);
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("error occured by set write key now for " + socketHandler.getId() + " " + DataConverter.toString(ioe));
					}
				}
			}
			
			@Override
			public String toString() {
				return "setWriteSelectionKeyUpdateTask#" + super.toString();
			}
		};
		
		addToKeyUpdateQueue(keyUpdateTask);
	}


	private boolean isDispatcherThread() {
		Integer tbid = getThreadBoundId();
		if ((tbid != null) && (tbid == id)) {
			return true;
		}

		return false;
	}


	private void addToKeyUpdateQueue(Runnable keyUpdateTask) {
		if (!isIdle()) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine(keyUpdateTask + " added to key update queue. This will take a moment because " + getName() + " is busy");
			}
		}
		
		keyUpdateQueue.add(keyUpdateTask);
		wakeUp();
	}


	private int performKeyUpdateTasks() {

		int handledTasks = 0;


		while (true) {

			Runnable keyUpdateTask = keyUpdateQueue.poll();

			if (keyUpdateTask == null) {
				return handledTasks;

			} else {
				keyUpdateTask.run();
				handledTasks++;
			}
		}
	}



	void setReadSelectionKeyNow(final IoSocketHandler socketHandler, boolean activate) throws IOException {
		assert (isDispatcherThread());

		if (activate) {
			updateInterestOps(socketHandler, SelectionKey.OP_READ, true, true);
		} else {
			updateInterestOps(socketHandler, SelectionKey.OP_READ, false, true);
		}
    }




	void setWriteSelectionKeyNow(final IoSocketHandler socketHandler, boolean allowDirectCall) throws IOException {
        try {
	        updateInterestOps(socketHandler, SelectionKey.OP_WRITE, true, allowDirectCall);
        } catch (NullPointerException npe) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("error occured by updating interested ops");
            }
        }
    }


	void unsetWriteSelectionKeyNow(final IoSocketHandler socketHandler, boolean allowDirectCall) throws IOException {
		updateInterestOps(socketHandler, SelectionKey.OP_WRITE, false, allowDirectCall);
    }


	
	private void updateInterestOps(IoSocketHandler socketHandler, int ops, boolean isAdd, boolean allowDirectCall) throws IOException {
	    assert (isDispatcherThread());
	    
		SelectionKey key = socketHandler.getChannel().keyFor(selector);

		// key not valid? -> flush the selector and fetch the key again
		if ((key != null) && !key.isValid()) {
			selector.selectNow();
			isSelectedKeysSetModified = true;

			key = socketHandler.getChannel().keyFor(selector);
		}


		// is handler not registered (key is null)?
		if (key == null) {

		    ///////////////////////////////////////
		    // register handler with given ops 
		    
			if (socketHandler.isOpen()) {

				// register handler if add operation (remove operation will be ignored)
				if (isAdd) {

				    // [performance optimization] direct call allowed?
					if (allowDirectCall && (ops == SelectionKey.OP_WRITE) && (getDirectCallCounter() < MAX_DIRECT_CALLS)) {
						Integer tbid = getThreadBoundId();
						if ((tbid != null) && (tbid == id)) {				
                            
						    incDirectCallCounter();  // inc counter to avoid deep call stack
						    
                            // key of the socket handler is not set -> writing by bypassing the selector
							onWriteableEvent(socketHandler);
							return;
						}
					}

					
					// no, register handler 
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + socketHandler.getId() + "] is not registered. register it now");
					}
					registerHandlerNow(socketHandler, ops);
				}

			} else {
				throw new IOException("[" +socketHandler.getId() + "] invalid socket handler (socket is already closed)");
			}

			
		// handler is registered (key is != null)
		} else {

		    // invalid key?
			if (!key.isValid()) {
                key.cancel();
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("[" + socketHandler.getId() + " key for of handle is not valid. canceling key and ignore update task " + printSelectionKeyValue(ops));
                }
                return;
            }
			    
			
			/////////////////////////////////
		    // update with given ops

            
            // is add operation?
            if (isAdd) {

                // [performance optimization] direct call allowed?
                if (isBypassingWriteAllowed && allowDirectCall && (ops == SelectionKey.OP_WRITE) && (getDirectCallCounter() < MAX_DIRECT_CALLS)) {
                    Integer tbid = getThreadBoundId();
                    if ((tbid != null) && (tbid == id)) {

                        incDirectCallCounter();   // inc counter to avoid deep call stack
                        
                        // current thread is the dispatcher thread -> write by bypassing the selector
                        try {
                            onWriteableEventUnprotected(socketHandler);
                            return;
                        } catch (IOException ignore) { 
                            // it hasn't worked, so do it by using the standard way 
                        }
                    }
                }

                try {
                    key.interestOps(key.interestOps() | ops);
                    resetDirectCallCounter();
                } catch (CancelledKeyException e) {
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine("couldn't update key with " + printSelectionKeyValue(ops) + " reason " + e.toString()); 
                    }
                }


            // is remove operation?
            } else {

                // no ops? -> deregister handle if env param is set
                if (IS_DETACH_HANDLE_ON_NO_OPS && (key.interestOps() & ~ops) == 0) {
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine("[" + socketHandler.getId() + "] deregistering handle because no ops are set");
                    }
                    deregisterHandlerNow(socketHandler);

                // update key
                } else {
                    key.interestOps(key.interestOps() & ~ops);
                    resetDirectCallCounter();
                }
            }
        }
	}


	String getRegisteredOpsInfo(IoSocketHandler socketHandler) {
		SelectionKey key = socketHandler.getChannel().keyFor(selector);

		if (key == null) {
			return "<not registered>";
		} else {
			return printSelectionKeyValue(key.interestOps());
		}
	}


	boolean isReadable(IoSocketHandler socketHandler) {
		SelectionKey key = socketHandler.getChannel().keyFor(selector);
		if ((key != null) && key.isValid() && ((key.interestOps() & SelectionKey.OP_READ) == SelectionKey.OP_READ)) {
			return true;
		}
		
		return false;
	}

	
	boolean isWriteable(IoSocketHandler socketHandler) {
		SelectionKey key = socketHandler.getChannel().keyFor(selector);
		if ((key != null)&& key.isValid() && ((key.interestOps() & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE)) {
			return true;
		}

		return false;
	}


	private int performRegisterHandlerTasks() throws IOException {

		int handledTasks = 0;

		while (true) {

			Runnable registerTask = registerQueue.poll();

			if (registerTask == null) {
				return handledTasks;

			} else {
				registerTask.run();
				handledTasks++;
			}
		}
	}


	private void registerHandlerNow(IoSocketHandler socketHandler, int ops) throws IOException {

		if (socketHandler.isOpen()) {
			
			// prevent repeated registration 
			if (socketHandler.getChannel().keyFor(selector) != null) {
				updateInterestOps(socketHandler, ops, true, false);
			}

			synchronized (socketHandler) {
				socketHandler.setDetached(false);
				socketHandler.getChannel().register(selector, ops, socketHandler);
			}

			socketHandler.onRegisteredEvent();

			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine(socketHandler.getId() +  " registered (ops=" + printSelectionKeyValue(ops) + ")");
			}


			handledRegistractions++;

		} else {
		    socketHandler.onRegisteredFailedEvent(new IOException("could not register handler " + socketHandler.getId() + " because the channel is closed"));
		}
	}





	private int performDeregisterHandlerTasks() {

		int handledTasks = 0;

		while (true) {

			IoSocketHandler socketHandler = deregisterQueue.poll();

			if (socketHandler == null) {
				return handledTasks;

			} else {
				if (LOG.isLoggable(Level.FINE)) {
			        LOG.fine("deregistering handler " + socketHandler.getId());
				}

				deregisterHandlerNow(socketHandler);
				handledTasks++;
			}
		}
	}



	private void deregisterHandlerNow(IoSocketHandler socketHandler) {
		SelectionKey key = socketHandler.getChannel().keyFor(selector);

		if ((key != null) && key.isValid()) {
			key.cancel();
		}

		synchronized (socketHandler) {
			socketHandler.setDetached(true);
		}

		handledRegistractions++;
	}



	/**
	 * {@inheritDoc}
	 */
	public Set<IoSocketHandler> getRegistered() {

		Set<IoSocketHandler> registered = new HashSet<IoSocketHandler>();

		Set<SelectionKey> keys = selector.keys();

		for (SelectionKey key : keys) {
			IoSocketHandler socketHandler = (IoSocketHandler) key.attachment();
			registered.add(socketHandler);
		}

		return registered;
	}



	/**
	 * returns if the dispatcher is open
	 *
	 * @return true, if the dispatcher is open
	 */
	public boolean isOpen() {
		return isOpen;
	}

	
	/**
	 * returns true, if selector is in idle mode
	 * 
	 * @return true, if selector is in idle mode
	 */
	public boolean isIdle() {
		return isIdle;
	}
	

	/**
	 * statistic method which returns the number handled registrations
	 * @return the number handled registrations
	 */
	public long getNumberOfHandledRegistrations() {
		return handledRegistractions;
	}



	/**
	 * statistic method which returns the number of handled reads
	 *
	 * @return the number of handled reads
	 */
	public long getNumberOfHandledReads() {
		return handledReads;
	}


	/**
	 * statistic method which returns the number of handled writes
	 *
	 * @return the number of handled writes
	 */
	public long getNumberOfHandledWrites() {
		return handledWrites;
	}


    long getReceiveRateBytesPerSec() {
    	long rate = 0;

    	long elapsed = System.currentTimeMillis() - lastRequestReceiveRate;

    	if (receivedBytes == 0) {
    		rate = 0;

    	} else if (elapsed == 0) {
    		rate = Long.MAX_VALUE;

    	} else {
    		rate = ((receivedBytes * 1000) / elapsed);
    	}

    	lastRequestReceiveRate = System.currentTimeMillis();
    	receivedBytes = 0;

    	return rate;
    }



    long getSendRateBytesPerSec() {
    	long rate = 0;

    	long elapsed = System.currentTimeMillis() - lastRequestSendRate;

    	if (sentBytes == 0) {
    		rate = 0;

    	} else if (elapsed == 0) {
    		rate = Long.MAX_VALUE;

    	} else {
    		rate = ((sentBytes * 1000) / elapsed);
    	}

    	lastRequestSendRate = System.currentTimeMillis();
    	sentBytes = 0;

    	return rate;
    }

    long getCountIdleTimeout() {
        return countIdleTimeouts;
    }

    long getCountConnectionTimeout() {
        return countConnectionTimeouts;
    }

    public int getPreallocatedReadMemorySize() {
        return memoryManager.getCurrentSizePreallocatedBuffer();
    }


    boolean getReceiveBufferPreallocationMode() {
    	return memoryManager.isPreallocationMode();
    }

    void setReceiveBufferPreallocationMode(boolean mode) {
    	memoryManager.setPreallocationMode(mode);
    }

    void setReceiveBufferPreallocatedMinSize(Integer minSize) {
   		memoryManager.setPreallocatedMinBufferSize(minSize);
	}

	Integer getReceiveBufferPreallocatedMinSize() {
    	if (memoryManager.isPreallocationMode()) {
    		return memoryManager.getPreallocatedMinBufferSize();
    	} else {
    		return null;
    	}
	}

	Integer getReceiveBufferPreallocatedSize() {
    	if (memoryManager.isPreallocationMode()) {
    		return memoryManager.getPreallocationBufferSize();
    	} else {
    		return null;
    	}
	}

	void setReceiveBufferPreallocatedSize(Integer size) {
		memoryManager.setPreallocationBufferSize(size);
	}

	boolean getReceiveBufferIsDirect() {
		return memoryManager.isDirect();
	}

	void setReceiveBufferIsDirect(boolean isDirect) {
		memoryManager.setDirect(isDirect);
	}

	/**
	 * reset the statistics (number of ...)
	 */
	public void resetStatistics() {
	    statisticsStartTime = System.currentTimeMillis();

		handledRegistractions = 0;
		handledReads = 0;
		handledWrites = 0;
	}





    @Override
    public String toString() {
    	return "open channels  " + getRegistered().size();
    }


	/**
	 * returns the statistics record start time (will be reset by the {@link IoSocketDispatcher#resetStatistics()} method)
	 * @return
	 */
	protected long getStatisticsStartTime() {
		return statisticsStartTime;
	}





	private String printSelectionKeyValue(int ops) {

		StringBuilder sb = new StringBuilder();

		if ((ops & SelectionKey.OP_ACCEPT) == SelectionKey.OP_ACCEPT) {
			sb.append("OP_ACCEPT, ");
		}

		if ((ops & SelectionKey.OP_CONNECT) == SelectionKey.OP_CONNECT) {
			sb.append("OP_CONNECT, ");
		}

		if ((ops & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE) {
			sb.append("OP_WRITE, ");
		}

		if ((ops & SelectionKey.OP_READ) == SelectionKey.OP_READ) {
			sb.append("OP_READ, ");
		}

		String txt = sb.toString();
		txt = txt.trim();

		if (txt.length() > 0) {
			txt = txt.substring(0, txt.length() - 1);
		}

		return txt + " (" + ops + ")";
	}

	/**
	 * {@inheritDoc}
	 */
	public void close() throws IOException {

		if (selector != null) {

			if (sizeUpdateTask != null) {
				sizeUpdateTask.cancel();
			}

			if (closer != null) {
				// start closer thread
				new Thread(closer).start();

				closer = null;
			}
		}
	}




	private class Closer implements Runnable {


		public void run() {
			Thread.currentThread().setName("xDispatcherCloser");

			long start = System.currentTimeMillis();


			// waiting until the pending connections are closed or timeout is reached
			while (getRegistered().size() > 0) {

				try {
					Thread.sleep(100);
				} catch (InterruptedException ignore) { }


				if (System.currentTimeMillis() > (start + TIMEOUT_SHUTDOWN_MILLIS)) {
					LOG.warning("shutdown timeout reached (" + DataConverter.toFormatedDuration(TIMEOUT_SHUTDOWN_MILLIS) + "). kill pending connections");

					Set<SelectionKey> keys = selector.keys();
					Set<SelectionKey> keysCopy = new HashSet<SelectionKey>();
					keysCopy.addAll(keys);

					for (SelectionKey sk : keysCopy) {
						try {
							sk.channel().close();
						} catch (IOException ioe) { 
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("error occured by closing channel " + ioe.toString());
							}
						}
					}

					break;
				}
			}


			// wake up selector, so that isOpen loop can be terminated
			isOpen = false;
			selector.wakeup();


			if (LOG.isLoggable(Level.FINE)) {
		        LOG.fine("dispatcher " + this.hashCode() + " has been closed (shutdown time = " + DataConverter.toFormatedDuration(System.currentTimeMillis() - start) + ")");
			}
		}
	}
}
