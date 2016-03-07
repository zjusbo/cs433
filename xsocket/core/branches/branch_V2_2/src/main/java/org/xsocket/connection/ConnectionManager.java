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
package org.xsocket.connection;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;





/**
 * Connection manager 
 *
 * @author grro@xsocket.org
 */
final class ConnectionManager {
	
	private static final Logger LOG = Logger.getLogger(ConnectionManager.class.getName());
	

	// connections
	private final ArrayList<WeakReference<TimeoutMgmHandle>> handles = new ArrayList<WeakReference<TimeoutMgmHandle>>();
	
    // watch dog
    private static final long DEFAULT_WATCHDOG_PERIOD_MILLIS =  1L * 60L * 1000L;
    private long watchDogPeriodMillis = DEFAULT_WATCHDOG_PERIOD_MILLIS;
    private TimerTask watchDogTask = null;
	private int watchDogRuns = 0;


	private int countIdleTimeouts = 0;
	private int countConnectionTimeouts = 0;
	
	private AtomicInteger currentSize = new AtomicInteger(0);
	private ISizeListener sizeListener = null;
	
	
	public ConnectionManager(ISizeListener sizeListener) {
		this.sizeListener = sizeListener;
		updateTimeoutCheckPeriod(DEFAULT_WATCHDOG_PERIOD_MILLIS);
	}

	

	public TimeoutMgmHandle register(NonBlockingConnection connection) {
		TimeoutMgmHandle mgnCon = new TimeoutMgmHandle(this, connection);
		WeakReference<TimeoutMgmHandle> ref = new WeakReference<TimeoutMgmHandle>(mgnCon);
		mgnCon.setRef(ref);
		
		synchronized (handles) {
			handles.add(ref);
		}
		
		currentSize.incrementAndGet();
		sizeListener.sizeChanged();
		
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("connection registered");
		}
		return mgnCon;
	}
	
	long getWatchDogPeriodMillis() {
		return watchDogPeriodMillis;
	}
	
	int getWatchDogRuns() {
		return watchDogRuns;
	}
	
	
	void remove(WeakReference<TimeoutMgmHandle> handleRef) {
		synchronized (handles) {
			handles.remove(handleRef);
		}
		
		currentSize.decrementAndGet();
		sizeListener.sizeChanged();
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("handle deregistered (connections size=" + handles.size() + ")");
		}
	}
	
	
	int getSize() {
		return currentSize.get();
	}
	
	

	@SuppressWarnings("unchecked")
	Set<NonBlockingConnection> getConnections() {
		Set<NonBlockingConnection> cons = new HashSet<NonBlockingConnection>();
		
		ArrayList<WeakReference<TimeoutMgmHandle>> connectionsCopy = null;
		synchronized (handles) {
			 connectionsCopy = (ArrayList<WeakReference<TimeoutMgmHandle>>) handles.clone();
		}
		
		for (WeakReference<TimeoutMgmHandle> handleRef : connectionsCopy) {
			TimeoutMgmHandle handle = handleRef.get();
			if (handle != null) {
				NonBlockingConnection con = handle.getConnection();
				cons.add(con);
			}
		}
		
		return cons;
	}
	
	

	
	void close() {
		if (watchDogTask != null) {
			watchDogTask.cancel();
		}
	}
	
	

	int getNumberOfIdleTimeouts() {
		return countIdleTimeouts;
	}
	
	int getNumberOfConnectionTimeouts() {
		return countConnectionTimeouts;
	}
    
	void updateTimeoutCheckPeriod(long requiredMinPeriod) {

        // if non watchdog task already exists and the required period is smaller than current one -> return
        if ((watchDogTask != null) && (watchDogPeriodMillis <= requiredMinPeriod)) {
            return;
        }

        // set watch dog period
        watchDogPeriodMillis = requiredMinPeriod;
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("update watchdog period " + DataConverter.toFormatedDuration(watchDogPeriodMillis));
        }

        // if watchdog task task already exits -> terminate it
        if (watchDogTask != null) {
            watchDogTask.cancel();
        }


        // create and run new watchdog task
        watchDogTask = new TimerTask() {
            @Override
            public void run() {
                check();
            }
        };
        
        
        IoProvider.getTimer().schedule(watchDogTask, watchDogPeriodMillis, watchDogPeriodMillis);
	}	
	
	
	@SuppressWarnings("unchecked")
	void check() {
		
		watchDogRuns++;
		
        try {
            long current = System.currentTimeMillis();
            
        	ArrayList<WeakReference<TimeoutMgmHandle>> connectionsCopy = null;
    		synchronized (handles) {
    			 connectionsCopy = (ArrayList<WeakReference<TimeoutMgmHandle>>) handles.clone();
    		}
    		
            for (WeakReference<TimeoutMgmHandle> handleRef : connectionsCopy) {
            	
            	TimeoutMgmHandle handle = handleRef.get();
            	if (handle == null) {
            		handles.remove(handleRef);
            		
            	} else {
	            	NonBlockingConnection con = handle.getConnection();
	            	if (con.isOpen()) {
	            		checkTimeout(con, current);	
	            		
	            	} else {
	            		handle.destroy();
	            		handles.remove(handleRef);
	            	}
            	}
            }
            
            currentSize.set(handles.size());
            
        } catch (Exception e) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("error occured: " + e.toString());
            }
        }
    }
    

	
	private void checkTimeout(NonBlockingConnection connection, long current) {


       boolean timeoutOccured = connection.checkIdleTimeout(current);
       if (timeoutOccured) {
           countIdleTimeouts++;
       }

       timeoutOccured = connection.checkConnectionTimeout(current);
       if (timeoutOccured) {
           countConnectionTimeouts++;
       }
	}
   

	
	
	final static class TimeoutMgmHandle {
		
		private ConnectionManager connectionManager;
		private NonBlockingConnection con;
		private final AtomicBoolean isDestroyed = new AtomicBoolean(false);
		
		private WeakReference<TimeoutMgmHandle> ref = null;
		
		public TimeoutMgmHandle(ConnectionManager connectionManager, NonBlockingConnection connection) {
			this.connectionManager = connectionManager;
			con = connection;
		}
		
		void setRef(WeakReference<TimeoutMgmHandle> ref) {
			this.ref = ref;
		}
		
		void updateCheckPeriod(long period) {
			connectionManager.updateTimeoutCheckPeriod(period);
		}
		
		void destroy() {
			if (!isDestroyed.getAndSet(true)) {
				connectionManager.remove(ref);
			}
		}
		
		
		NonBlockingConnection getConnection() {
			return con;
		}
	}
	
	
	static interface ISizeListener {
		
		void sizeChanged();
	}
}
