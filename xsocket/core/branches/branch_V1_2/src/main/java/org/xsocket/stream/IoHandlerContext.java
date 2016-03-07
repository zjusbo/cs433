// $Id: IoHandlerBase.java 1315 2007-06-10 08:05:00Z grro $
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

import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.ILifeCycle;
import org.xsocket.Synchronized;
import org.xsocket.Synchronized.Mode;
import org.xsocket.stream.io.spi.IIoHandlerContext;



/**
 * 
 * @author grro@xsocket.org
 */
final class IoHandlerContext implements IIoHandlerContext {
	
	private static final Logger LOG = Logger.getLogger(IoHandlerContext.class.getName());
	
	private static final SingleThreadedWorkerPool SINGLE_THREADED_POOL = new SingleThreadedWorkerPool();
	
	private boolean isConnectHandler = false;
    private boolean isDisconnectHandler = false;
	private boolean isDataHandler = false;
	private boolean isTimeoutHandler = false;
	private boolean isLifeCycleHandler = false;
	private boolean isConnectionScoped = false;
    private boolean isHandlerThreadSave = false;
    private boolean isMultithreaded = false;

	private Executor workerpool = null;
	

	/**
	 * constructor  
	 * 
	 * @param appHandler   the app handler 
	 */
	IoHandlerContext(IHandler appHandler, Executor workerpool) {
		updateAppHandler(appHandler);
		updateWorkerpool(workerpool);
	}
	
	
	void updateAppHandler(IHandler appHandler) {
		introspectHandler(appHandler);
	}
	
	
	void updateWorkerpool(Executor workerpool) {
		if (workerpool != null) {
			this.workerpool = workerpool;
			isMultithreaded = true;
		} else {
			this.workerpool = SINGLE_THREADED_POOL;
			isMultithreaded = false;
		}
	}
	
	
	public Executor getWorkerpool() {
		return workerpool;
	}
	
	public boolean isAppHandlerListenForConnectEvent() {
		return isConnectHandler;
	}
	
	public boolean isAppHandlerListenForDataEvent() {
		return isDataHandler;
	}
	
	public boolean isAppHandlerListenforDisconnectEvent() {
		return isDisconnectHandler;
	}
	
	public boolean isAppHandlerListenForTimeoutEvent() {
		return isTimeoutHandler;
	}

	public boolean isAppHandlerConnectionScoped() {
		return isConnectionScoped;
	}
	
	public boolean isAppHandlerThreadSave() {
		return isHandlerThreadSave;
	}
	
	boolean isConnectionScoped() {
		return isConnectionScoped;
	}

	boolean isLifeCycleHandler() {
		return isLifeCycleHandler;
	}
	
	public boolean isAppHandlerThreadSafe() {
		return isHandlerThreadSave;
	}

	
	public boolean isMultithreaded() {
		return isMultithreaded;
	}
	
		
	private void introspectHandler(IHandler appHandler) {
		
		if (appHandler == null) {
			isDataHandler = true;
			isConnectHandler = false;
		    isDisconnectHandler = false;
			isTimeoutHandler = false;
			isConnectionScoped = false;
		    isHandlerThreadSave = false;
		    isLifeCycleHandler = false;
		    
		} else {
			
			isConnectHandler = (appHandler instanceof IConnectHandler);
			isDisconnectHandler = (appHandler instanceof IDisconnectHandler);
			isDataHandler = (appHandler instanceof IDataHandler);
			isTimeoutHandler = (appHandler instanceof ITimeoutHandler);
			isConnectionScoped = (appHandler instanceof IConnectionScoped);
			isLifeCycleHandler = (appHandler instanceof ILifeCycle);
			
			Synchronized sync = appHandler.getClass().getAnnotation(Synchronized.class);
			if (sync != null) {
				Mode scope = sync.value();
				isHandlerThreadSave = (scope == Synchronized.Mode.OFF); 
			} else {
				isHandlerThreadSave = false;
			}
		}
	}	
	
	
	private static final class SingleThreadedWorkerPool implements Executor {
		public void execute(Runnable command) {
			try {
				
				command.run();
				
			} catch (Exception e) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured within worker thread " + e.toString());
				}
			}
			
		}
	}
}
