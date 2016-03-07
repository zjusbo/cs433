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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
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
final class IoSocketDispatcher extends MonitoredSelector implements Runnable, Closeable {

	private static final Logger LOG = Logger.getLogger(IoSocketDispatcher.class.getName());

    static final String DISPATCHER_PREFIX = "xDispatcher";

	
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
	private final AtomicBoolean isOpen = new AtomicBoolean(true);

	
	// connection handling
	private static final Integer MAX_HANDLES = IoProvider.getMaxHandles();
	private int roughNumOfRegisteredHandles;
	private Selector selector;


	// memory management
	private final AbstractMemoryManager memoryManager;

	
	// wakeup 
	private long lastTimeWokeUp = System.currentTimeMillis();



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

		try {
			selector = Selector.open();
		} catch (IOException ioe) {
			String text = "exception occured while opening selector. Reason: " + ioe.toString();
			LOG.severe(text);
			throw new RuntimeException(text, ioe);
		}
		

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("dispatcher " + this.hashCode() + " has been closed");
        }
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

	
	long getCountUnregisteredWrite() {
		return countUnregisteredWrite;
	}

	Integer getMaxRegisterdHandles() {
		return MAX_HANDLES;
	}


    @Override
    int getNumRegisteredHandles() {
        int hdls = selector.keys().size();
        roughNumOfRegisteredHandles = hdls;
        return hdls;
    }

    
    int getRoughNumRegisteredHandles() {
        return roughNumOfRegisteredHandles;
    }

	
	@Override
	void reinit() throws IOException {

	    // save old selector
	    Selector oldSelector = selector;
	    
	    // retrieve all keys
	    HashSet<SelectionKey> keys = new HashSet<SelectionKey>();
	    keys.addAll(oldSelector.keys());
	    
	    // create new selector
	    selector = Selector.open();

	    // "move" all sockets to the new selector
        for (SelectionKey key : keys) {
            // get info
            int ops = key.interestOps();
            IoSocketHandler socketHandler = (IoSocketHandler) key.attachment();

            // cancel old key 
            key.cancel();
            
            try {
                socketHandler.getChannel().register(selector, ops, socketHandler);
            } catch (IOException ioe) {
                LOG.warning("could not reinit " + socketHandler.toString() + " " + DataConverter.toString(ioe));
            }
        }

        // close old selector
        oldSelector.close();
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("selector has been reinitialized");
        }
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

		while(isOpen.get()) {
			try {
				int eventCount = selector.select(5000); 
			
				handledTasks = performRegisterHandlerTasks();
				handledTasks += performKeyUpdateTasks();

				if (eventCount > 0) {
					handleReadWriteKeys();
				}

				handledTasks += performDeregisterHandlerTasks();
			
				checkForLooping(eventCount + handledTasks, lastTimeWokeUp);
				
			} catch (Throwable e) {
                // eat and log exception
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + Thread.currentThread().getName() + "] exception occured while processing. Reason " + DataConverter.toString(e));
				}
			}
		}


        for (IoSocketHandler socketHandler : getRegistered()) {
            socketHandler.onDeregisteredEvent();
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
		Iterator<SelectionKey> it = selectedEventKeys.iterator();

		// handle read & write
		while (it.hasNext()) {
		    
		    try {
    			SelectionKey eventKey = it.next();
    			it.remove();
    
    			IoSocketHandler socketHandler = (IoSocketHandler) eventKey.attachment();
   
    			try {
    			    
	    			// read data
	    			if (eventKey.isValid() && eventKey.isReadable()) {
	    				onReadableEvent(socketHandler);
	    			}
	    
	    			// write data
	    			if (eventKey.isValid() && eventKey.isWritable()) {
	    				onWriteableEvent(socketHandler);
	    			}
    			} catch (Exception e) {
    				socketHandler.close(e);
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
			
		} catch (Exception t) {
		    SelectionKey key = getSelectionKey(socketHandler);
		    if ((key != null) && key.isValid()) {
                key.cancel();               
            }
		    
		    if (LOG.isLoggable(Level.FINE)) {
		    	LOG.fine("error occured by handling readable event " + DataConverter.toString(t));
		    }
			socketHandler.closeSilence(true);
		}
	}


	private void onWriteableEvent(IoSocketHandler socketHandler) {
		try {
			socketHandler.onWriteableEvent();
			handledWrites++;

		} catch (ClosedChannelException ce) {
			IOException ioe = ConnectionUtils.toIOException("error occured by handling readable event. reason closed channel exception " + ce.toString(), ce); 
			socketHandler.close(ioe);

		} catch (Exception e) {
			e = ConnectionUtils.toIOException("error occured by handling readable event. reason " + e.toString(), e);
			socketHandler.close(e);
		}
	}

	

	private void wakeUp() {
		lastTimeWokeUp = System.currentTimeMillis();
		selector.wakeup();
	}


	boolean preRegister() {

        // inc rough num of registered handles
        roughNumOfRegisteredHandles++;

        // check if max size reached
	    if ((MAX_HANDLES != null) &&
	        (roughNumOfRegisteredHandles >= MAX_HANDLES) &&  // rough check 
	        (getNumRegisteredHandles() >= MAX_HANDLES)) {    // accurate check
	        return false;
	        
	    } else {
	        return true;
	    }
	}

	

	/**
	 * {@inheritDoc}
	 */
	public boolean register(IoSocketHandler socketHandler, int ops) throws IOException {
		assert (!socketHandler.getChannel().isBlocking());

		socketHandler.setMemoryManager(memoryManager);

		if (isDispatcherInstanceThread()) {
			registerHandlerNow(socketHandler, ops);
		} else {
		    if (LOG.isLoggable(Level.FINE)) {
		        LOG.fine("[" + socketHandler.getId() + "] add new connection to register task queue");
		    }
			registerQueue.add(new RegisterTask(socketHandler, ops));
			wakeUp();
		}

		return true;
	}


	
	private final class RegisterTask implements Runnable {

		private final IoSocketHandler socketHandler;
		private final int ops;
				
		public RegisterTask(IoSocketHandler socketHandler, int ops) {
			this.socketHandler = socketHandler;
			this.ops = ops;
		}
		
		
		public void run() {
			try {
				registerHandlerNow(socketHandler, ops);
			} catch (IOException ioe) {
				ioe = ConnectionUtils.toIOException("error occured by registering handler " + socketHandler.getId() + " " + ioe.toString(), ioe);
				socketHandler.close(ioe);
			}
		}
	}
	

	/**
	 * {@inheritDoc}
	 */
	public void deregisterAndClose(IoSocketHandler handler) {
	    
	    if (isOpen.get()) {
    		if (isDispatcherInstanceThread()) {
    			deregisterAndCloseNow(handler);
    			
    		} else {
    			deregisterQueue.add(handler);
    			wakeUp();
    		}
    		
	    } else {
	        handler.onDeregisteredEvent();
	    }
	}



	private int performDeregisterHandlerTasks() {

		int handledTasks = 0;

		while (true) {
			IoSocketHandler socketHandler = deregisterQueue.poll();

			if (socketHandler == null) {
				return handledTasks;

			} else {
				deregisterAndCloseNow(socketHandler);
				handledTasks++;
			}
		}
	}



	private void deregisterAndCloseNow(IoSocketHandler socketHandler) {

		try {
			SelectionKey key = socketHandler.getChannel().keyFor(selector);
				
			if ((key != null) && key.isValid()) {
				key.cancel();	
				if (roughNumOfRegisteredHandles > 0) {
				    roughNumOfRegisteredHandles--;
				}
			}
		} catch (Exception e) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by deregistering socket handler " + e.toString());
			}
		}

		socketHandler.onDeregisteredEvent();
	}

	
	
	
	public void addKeyUpdateTask(Runnable task) {
		keyUpdateQueue.add(task);
		wakeUp();
	}
	
	
	public void flushKeyUpdate() {
		wakeUp();
	}


	public void suspendRead(final IoSocketHandler socketHandler) throws IOException {
		addKeyUpdateTask(new UpdateReadSelectionKeyTask(socketHandler, false));
	}


	public void resumeRead(IoSocketHandler socketHandler) throws IOException {
		addKeyUpdateTask(new UpdateReadSelectionKeyTask(socketHandler, true));
	}

	
	private final class	UpdateReadSelectionKeyTask implements Runnable {

		private final IoSocketHandler socketHandler;
		private final boolean isSet; 
		
		public UpdateReadSelectionKeyTask(IoSocketHandler socketHandler, boolean isSet) {
			this.socketHandler = socketHandler;
			this.isSet = isSet;
		}
		
		public void run() {
			assert (isDispatcherInstanceThread());
			
			try { 
				if (isSet) {
					setReadSelectionKeyNow(socketHandler);
					
				} else {
					unsetReadSelectionKeyNow(socketHandler);
				}
				
			} catch (Exception e) {
				e = ConnectionUtils.toIOException("Error by set read selection key now " + e.toString(), e);
				socketHandler.close(e);
			}			
		}
		
		@Override
		public String toString() {
			return "setReadSelectionKeyTask#" + super.toString();
		}
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


	

	public boolean isDispatcherInstanceThread() {
		Integer tbid = getThreadBoundId();
		if ((tbid != null) && (tbid == id)) {
			return true;
		}

		return false;
	}


	private SelectionKey getSelectionKey(IoSocketHandler socketHandler) {
		 SelectionKey key = socketHandler.getChannel().keyFor(selector);
		 
		 if (LOG.isLoggable(Level.FINE)) {
			 if (key == null) {
				 LOG.fine("[" + socketHandler.getId() + "] key is null");
			 } else if (!key.isValid()) {
				 LOG.fine("[" + socketHandler.getId() + "] key is not valid");
			 }
		 }
		 
		 return key;
	}
	
 
	public boolean setWriteSelectionKeyNow(final IoSocketHandler socketHandler) throws IOException {
	    assert (isDispatcherInstanceThread());
	    
	    SelectionKey key = getSelectionKey(socketHandler);
	    if (key != null) {
	    	if (!isWriteable(key)) {
	    		key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);

	    		return true;
	    	}
	    } else {
	    	throw new IOException("[" + socketHandler.getId() + "] Error occured by setting write selection key. key is null");
	    }
	    
    	return false;
    }
	
	
	public boolean unsetWriteSelectionKeyNow(final IoSocketHandler socketHandler) throws IOException {
	    assert (isDispatcherInstanceThread());
	    
	    SelectionKey key = getSelectionKey(socketHandler);
	    if (key != null) {
	    	if (isWriteable(key)) {
	    		key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
	   
	    		return true;
	    	}
	    	
	    } else {
	    	throw new IOException("[" + socketHandler.getId() + "] Error occured by unsetting write selection key. key is null");
	    }
	    return false;
    }
	

	public boolean setReadSelectionKeyNow(final IoSocketHandler socketHandler) throws IOException {
	    assert (isDispatcherInstanceThread());
	    
	    SelectionKey key = getSelectionKey(socketHandler);
	    if (key != null) {
		    if (!isReadable(key)) {
		    	key.interestOps(key.interestOps() | SelectionKey.OP_READ);
		    	
		    	if (LOG.isLoggable(Level.FINE)) {
		    	    LOG.fine("[" + socketHandler.getId() + "] key set to " + printSelectionKey(socketHandler));
		    	}

		        onReadableEvent(socketHandler);
		    	return true;
		    }
	    } else {
	    	throw new IOException("[" + socketHandler.getId() + "] Error occured by setting read selection key. key is null");
	    }
	    
	    return false;
    }


	private void unsetReadSelectionKeyNow(final IoSocketHandler socketHandler) throws IOException {
	    assert (isDispatcherInstanceThread());
	    
	    SelectionKey key = getSelectionKey(socketHandler);
	    if (key != null) {
	    	if (isReadable(key)) {
	    		key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
	    		
	    	}
	    } else {
	    	throw new IOException("[" + socketHandler.getId() + "] Error occured by unsetting read selection key. key is null");
	    }
    }


	String getRegisteredOpsInfo(IoSocketHandler socketHandler) {
		SelectionKey key = getSelectionKey(socketHandler);

		if (key == null) {
			return "<not registered>";
		} else {
			return ConnectionUtils.printSelectionKeyValue(key.interestOps());
		}
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
		    if (LOG.isLoggable(Level.FINE)) {
		        LOG.fine("[" + socketHandler.getId() + "] registering connection");
		    }

			try {
				socketHandler.getChannel().register(selector, ops, socketHandler);				
				socketHandler.onRegisteredEvent();

				handledRegistractions++;
			} catch (Exception e) {
				socketHandler.close(e);
			}

		} else {
		    socketHandler.onRegisteredFailedEvent(new IOException("could not register handler " + socketHandler.getId() + " because the channel is closed"));
		}
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
		return isOpen.get();
	}

	
	boolean isReadable(IoSocketHandler socketHandler) {
		SelectionKey key = getSelectionKey(socketHandler);
		if ((key != null) && ((key.interestOps() & SelectionKey.OP_READ) == SelectionKey.OP_READ)) {
			return true;
		}
		
		return false;
	}

	
	
	private boolean isReadable(SelectionKey key) {
		if ((key != null) && ((key.interestOps() & SelectionKey.OP_READ) == SelectionKey.OP_READ)) {
			return true;
		}
		
		return false;
	}

	
	private boolean isWriteable(SelectionKey key) {
		if ((key != null) && ((key.interestOps() & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE)) {
			return true;
		}

		return false;
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


	@Override
	String printRegistered() {
	    StringBuilder sb = new StringBuilder();
	    for (IoSocketHandler handler : getRegistered()) {
	        sb.append(handler.toString() + " (key: " + printSelectionKey(handler) + ")\r\n");
	    }
	    
	    return sb.toString();
	}

	
	
	String printSelectionKey(IoSocketHandler socketHandler) {
	    return ConnectionUtils.printSelectionKey(socketHandler.getChannel().keyFor(selector));
	}

	

	/**
	 * {@inheritDoc}
	 */
	public void close() throws IOException {

        // wake up selector, so that isOpen loop can be terminated
	    if (isOpen.getAndSet(false)&& (selector != null)) {
	        selector.wakeup();
		}
	}
}
