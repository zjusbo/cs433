/*
 *  Copyright (c) xsocket.org, 2006 - 2008. All rights reserved.
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
package org.xsocket.connection.spi;

import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
import java.util.Set;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;
import org.xsocket.Dispatcher;
import org.xsocket.IDispatcherEventHandler;


final class IoSocketDispatcher extends Dispatcher<IoSocketHandler> {

    private static final Logger LOG = Logger.getLogger(IoSocketDispatcher.class.getName());

    static final String DISPATCHER_PREFIX = "xDispatcher";


    // watch dog
    private static final long DEFAULT_WATCHDOG_PERIOD_MILLIS =  5L * 60L * 1000L;
    private long watchDogPeriod = DEFAULT_WATCHDOG_PERIOD_MILLIS;
    private TimerTask watchDogTask = null;

    private IMemoryManager memoryManager = null;

    // statistics
    private long countIdleTimeouts = 0;
    private long countConnectionTimeouts = 0;



    IoSocketDispatcher(IMemoryManager memoryManager) {
        super(new DispatcherEventHandler(memoryManager));

        this.memoryManager = memoryManager;
        updateTimeoutCheckPeriod(DEFAULT_WATCHDOG_PERIOD_MILLIS);
    }

    
    long getReceiveRateBytesPerSec() {
		return ((DispatcherEventHandler) getEventHandler()).getReceiveRateBytesPerSec();
	}

    long getSendRateBytesPerSec() {
		return ((DispatcherEventHandler) getEventHandler()).getSendRateBytesPerSec();
	}

    
    public void resetStatistics() {
    	super.resetStatistics();

    	countIdleTimeouts = 0;
        countConnectionTimeouts = 0;
    }
    
    
    @Override
    public void register(IoSocketHandler handle, int ops) throws IOException {
        handle.setMemoryManager(memoryManager);
        super.register(handle, ops);
    }



    /**
	 * {@inheritDoc}
	 */
	public long getNumberOfHandledRegistrations() {
		return super.getNumberOfHandledRegistrations();
	}


	/**
	 * {@inheritDoc}
	 */
	public long getNumberOfHandledReads() {
		return super.getNumberOfHandledReads();
	}


	/**
	 * {@inheritDoc}
	 */
	public long getNumberOfHandledWrites() {
		return super.getNumberOfHandledWrites();
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
    		return memoryManager.gettPreallocationBufferSize();
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
    

    public void updateTimeoutCheckPeriod(long requiredMinPeriod) {

        // if not watch dog already exists and required period is smaller than current one return
        if ((watchDogTask != null) && (watchDogPeriod <= requiredMinPeriod)) {
            return;
        }

        // set watch dog period
        watchDogPeriod = requiredMinPeriod;
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("update dispatcher`s watchdog task " + DataConverter.toFormatedDuration(watchDogPeriod));
        }

        // if watchdog task task already exits -> terminate it
        if (watchDogTask != null) {
            watchDogTask.cancel();
        }


        // create and run new watchdog task
        watchDogTask = new TimerTask() {
            @Override
            public void run() {
                checkTimeouts();
            }
        };
        DefaultIoProvider.getTimer().schedule(watchDogTask, watchDogPeriod, watchDogPeriod);
    }




    @Override
    public void close() {
        super.close();

        if (watchDogTask != null) {
            watchDogTask.cancel();
        }
    }


    long getCountIdleTimeout() {
        return countIdleTimeouts;
    }

    long getCountConnectionTimeout() {
        return countConnectionTimeouts;
    }


    void checkTimeouts() {
        try {
            long current = System.currentTimeMillis();
            Set<IoSocketHandler> socketHandlers = getRegistered();
            for (IoSocketHandler socketHandler : socketHandlers) {
                checkTimeout(socketHandler, current);
            }

        } catch (ClosedSelectorException cse) {
            watchDogTask.cancel();

        } catch (Exception e) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("error occured: " + e.toString());
            }
        }
    }

    private void checkTimeout(IoSocketHandler ioSocketHandler, long current) {
        ioSocketHandler.checkConnection();


        boolean timeoutOccured = ioSocketHandler.checkIdleTimeout(current);
        if (timeoutOccured) {
            countIdleTimeouts++;
        }

        timeoutOccured = ioSocketHandler.checkConnectionTimeout(current);
        if (timeoutOccured) {
            countConnectionTimeouts++;
        }
    }
    
    @Override
    public String toString() {
    	return "open channels  " + super.getRegistered().size();
    }



    /**
     * returns if current thread is  disptacher thread
     * @return true, if current thread is a dispatcher thread
     */
    static boolean isDispatcherThread() {
        return Thread.currentThread().getName().startsWith(DISPATCHER_PREFIX);
    }


    private static final class DispatcherEventHandler implements IDispatcherEventHandler<IoSocketHandler> {

        private IMemoryManager memoryManager = null;
        

        // statistics
        private long receivedBytes = 0;
        private long sentBytes = 0;
    	private long lastRequestReceiveRate = System.currentTimeMillis();
    	private long lastRequestSendRate = System.currentTimeMillis();


        

        DispatcherEventHandler(IMemoryManager memoryManager) {
            this.memoryManager = memoryManager;
        }

        IMemoryManager getMemoryManager() {
            return memoryManager;
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
        

        public void onHandleReadableEvent(final IoSocketHandler socketIOHandler) throws IOException {
           long read = socketIOHandler.onReadableEvent();
           receivedBytes += read;
        }


        @SuppressWarnings("unchecked")
        public void onHandleWriteableEvent(final IoSocketHandler socketIOHandler) throws IOException {
            long written = socketIOHandler.onWriteableEvent();
            sentBytes += written;
        }


        public void onHandleRegisterEvent(final IoSocketHandler socketIOHandler) throws IOException {
            socketIOHandler.onConnectEvent();
        }


        public void onDispatcherCloseEvent(final IoSocketHandler socketIOHandler) {
            socketIOHandler.onDispatcherClose();
        }
    }
}
