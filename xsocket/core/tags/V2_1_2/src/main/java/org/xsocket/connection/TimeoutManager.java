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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;





/**
 * Connection manager 
 *
 * @author grro@xsocket.org
 */
final class TimeoutManager {
	
	private static final Logger LOG = Logger.getLogger(TimeoutManager.class.getName());
	

	// connections
	private final ArrayList<TimeoutMgmHandle> handles = new ArrayList<TimeoutMgmHandle>();
	
    // watch dog
    private static final long DEFAULT_WATCHDOG_PERIOD_MILLIS =  5L * 60L * 1000L;
    private long watchDogPeriod = DEFAULT_WATCHDOG_PERIOD_MILLIS;
    private TimerTask watchDogTask = null;

	private int countIdleTimeouts = 0;
	private int countConnectionTimeouts = 0;
	
	
	public TimeoutManager() {
		updateTimeoutCheckPeriod(DEFAULT_WATCHDOG_PERIOD_MILLIS);
	}
	

	

	public TimeoutMgmHandle register(NonBlockingConnection connection) {
		TimeoutMgmHandle mgnCon = new TimeoutMgmHandle(this, connection);
		
		synchronized (handles) {
			handles.add(mgnCon);
		}
		
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("connection registered");
		}
		return mgnCon;
	}
	
	long getWatchDogPeriod() {
		return watchDogPeriod;
	}
	
	
	void remove(TimeoutMgmHandle handle) {
		synchronized (handles) {
			handles.remove(handle);
		}
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("handle deregistered (connections size=" + handles.size() + ")");
		}
	}
	

	@SuppressWarnings("unchecked")
	Set<NonBlockingConnection> getConnections() {
		Set<NonBlockingConnection> cons = new HashSet<NonBlockingConnection>();
		
		ArrayList<TimeoutMgmHandle> connectionsCopy = null;
		synchronized (handles) {
			 connectionsCopy = (ArrayList<TimeoutMgmHandle>) handles.clone();
		}
		
		for (TimeoutMgmHandle handle : connectionsCopy) {
			NonBlockingConnection con = handle.getConnection();
			if (con != null) {
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

        // if not watch dog already exists and required period is smaller than current one return
        if ((watchDogTask != null) && (watchDogPeriod <= requiredMinPeriod)) {
            return;
        }

        // set watch dog period
        watchDogPeriod = requiredMinPeriod;
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("update watchdog period " + DataConverter.toFormatedDuration(watchDogPeriod));
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
        
        
        IoProvider.getTimer().schedule(watchDogTask, watchDogPeriod, watchDogPeriod);
	}	
	
	
	@SuppressWarnings("unchecked")
	void checkTimeouts() {
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("checking timeouts");
		}
		
        try {
            long current = System.currentTimeMillis();
            
        	ArrayList<TimeoutMgmHandle> connectionsCopy = null;
    		synchronized (handles) {
    			 connectionsCopy = (ArrayList<TimeoutMgmHandle>) handles.clone();
    		}
    		
            for (TimeoutMgmHandle handle : connectionsCopy) {
            	NonBlockingConnection con = handle.getConnection();
            	if (con != null) {
            		checkTimeout(con, current);	
            	}
            }
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
		
		private TimeoutManager connectionManager;
		private NonBlockingConnection con;
		
		
		public TimeoutMgmHandle(TimeoutManager connectionManager, NonBlockingConnection connection) {
			this.connectionManager = connectionManager;
			con = connection;
		}
		
		
		void updateCheckPeriod(long period) {
			connectionManager.updateTimeoutCheckPeriod(period);
		}
		
		void destroy() {
			connectionManager.remove(this);
		}
		
		
		NonBlockingConnection getConnection() {
			if ((!con.isOpen())) {
				destroy();
			} 
			
			return con;
		}
	}
}
