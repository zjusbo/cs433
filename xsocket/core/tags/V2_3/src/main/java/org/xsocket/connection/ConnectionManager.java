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
    private static final long DEFAULT_WATCHDOG_PERIOD_CONNECTION_CHECK_MILLISTION_CHECK_MILLIS =  1L * 60L * 1000L;
    private long watchDogPeriodConCheckMillis = DEFAULT_WATCHDOG_PERIOD_CONNECTION_CHECK_MILLISTION_CHECK_MILLIS;
    private TimerTask conCheckWatchDogTask;
	private int watchDogRuns;


	private int countIdleTimeouts;
	private int countConnectionTimeouts;
	
	private AtomicInteger currentSize = new AtomicInteger(0);
	private final ISizeListener sizeListener;
	
	
	public ConnectionManager(ISizeListener sizeListener) {
		this.sizeListener = sizeListener;
		updateTimeoutCheckPeriod(watchDogPeriodConCheckMillis);
	}

	
	

	public TimeoutMgmHandle register(NonBlockingConnection connection) {
		TimeoutMgmHandle mgnCon = new TimeoutMgmHandle(connection);
		
		WeakReference<TimeoutMgmHandle> ref = mgnCon.getWeakRef();
		if (ref != null) {
		    synchronized (handles) {
		        handles.add(ref);
		    }
		} else {
		    if (LOG.isLoggable(Level.FINE)) {
		        LOG.fine("did not get the weak ref");
		    }
		}
		
		currentSize.incrementAndGet();
		sizeListener.sizeChanged();
		
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("connection registered");
		}
		return mgnCon;
	}
	
	long getWatchDogPeriodMillis() {
		return watchDogPeriodConCheckMillis;
	}
	
	int getWatchDogRuns() {
		return watchDogRuns;
	}
	
		
	private void remove(WeakReference<TimeoutMgmHandle> handleRef) {
	    synchronized (handles) {
	        handles.remove(handleRef);
        }
		
		currentSize.decrementAndGet();
		sizeListener.sizeChanged();
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("handle deregistered (connections size=" + computeSize() + ")");
		}
	}
	
	
	int getSize() {
		return currentSize.get();
	}
	
	
	private int computeSize() {
        synchronized (handles) {
            int size = handles.size();
            currentSize.set(size);
            return size;
        }
	}

	
	@SuppressWarnings("unchecked")
	Set<NonBlockingConnection> getConnections() {
		final Set<NonBlockingConnection> cons = new HashSet<NonBlockingConnection>();
		
		ArrayList<WeakReference<TimeoutMgmHandle>> connectionsCopy = null;
		synchronized (handles) {
			 connectionsCopy = (ArrayList<WeakReference<TimeoutMgmHandle>>) handles.clone();
		}
		
		for (WeakReference<TimeoutMgmHandle> handleRef : connectionsCopy) {
			TimeoutMgmHandle handle = handleRef.get();
			if (handle != null) {
				NonBlockingConnection con = handle.getConnection();
				if (con != null) {
				    cons.add(con);
				}
			}
		}
		
		return cons;
	}
	
	

	
	void close() {
		if (conCheckWatchDogTask != null) {
		    conCheckWatchDogTask.cancel();
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
        if ((conCheckWatchDogTask != null) && (watchDogPeriodConCheckMillis <= requiredMinPeriod)) {
            return;
        }

        // set watch dog period
        watchDogPeriodConCheckMillis = requiredMinPeriod;
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("update watchdog period " + DataConverter.toFormatedDuration(watchDogPeriodConCheckMillis));
        }

        // if watchdog task task already exits -> terminate it
        if (conCheckWatchDogTask != null) {
            conCheckWatchDogTask.cancel();
        }


        
        // create and run new watchdog task
        conCheckWatchDogTask = new TimerTask() {
            
            @Override
            public void run() {
                try {
                    check();
                } catch (Exception e) {
                    
                    // eat and log exception 
                    
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine("error occured by running check: " + e.toString());
                    }
                }
            }
        };
        
        
        IoProvider.getTimer().schedule(conCheckWatchDogTask, watchDogPeriodConCheckMillis, watchDogPeriodConCheckMillis);
	}	
	
	

	
	
	@SuppressWarnings("unchecked")
	void check() {
		
		watchDogRuns++;
		
		long current = System.currentTimeMillis();
            
		ArrayList<WeakReference<TimeoutMgmHandle>> connectionsCopy = null;
		synchronized (handles) {
		    connectionsCopy = (ArrayList<WeakReference<TimeoutMgmHandle>>) handles.clone();
		}
    		
		for (WeakReference<TimeoutMgmHandle> handleRef : connectionsCopy) {
            	
		    TimeoutMgmHandle handle = handleRef.get();
		    if (handle == null) {
		        remove(handleRef);
            		
		    } else {
		        NonBlockingConnection con = handle.getConnection();
		        if (con.isOpen()) {
		            checkTimeout(con, current);	
	            		
		        } else {
		            remove(handleRef);
		        }
		    }
		}
		
		computeSize();
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
   

	
	
	final class TimeoutMgmHandle {
		
		private final NonBlockingConnection con;
		private WeakReference<TimeoutMgmHandle> handleRef;
		
		public TimeoutMgmHandle(NonBlockingConnection connection) {
			con = connection;
			handleRef = new WeakReference<TimeoutMgmHandle>(this);
		}
		
		WeakReference<TimeoutMgmHandle> getWeakRef() {
		    return handleRef;
		}
		
		
		void updateCheckPeriod(long period) {
			updateTimeoutCheckPeriod(period);
		}
		
		void destroy() {
		    if (handleRef != null) {
		        remove(handleRef);
		        handleRef = null;
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
