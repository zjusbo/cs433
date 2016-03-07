package org.xsocket.stream.io.impl;

import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
import java.util.Set;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;
import org.xsocket.Dispatcher;
import org.xsocket.IEventHandler;



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
	
	
	@Override
	public void register(IoSocketHandler handle, int ops) throws IOException {
		handle.setMemoryManager(memoryManager);
		super.register(handle, ops);
	}
	
	public int getPreallocatedReadMemorySize() {
		return memoryManager.getFreeBufferSize();
	}
		
	
	void updateTimeoutCheckPeriod(long requiredMinPeriod) {

		// if not watch dog already exists and required period is smaller than current one return 
		if ((watchDogTask != null) && (watchDogPeriod <= requiredMinPeriod)) {
			return;
		}  
		
		// set watch dog period
		watchDogPeriod = requiredMinPeriod;
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("update dispatcher's watchdog task " + DataConverter.toFormatedDuration(watchDogPeriod));
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

	

	/**
	 * returns if current thread is  disptacher thread
	 * @return true, if current thread is a dispatcher thread 
	 */
	static boolean isDispatcherThread() {
		return Thread.currentThread().getName().startsWith(DISPATCHER_PREFIX);
	}

	
	private static final class DispatcherEventHandler implements IEventHandler<IoSocketHandler> {
			
		private IMemoryManager memoryManager = null;
		
		DispatcherEventHandler(IMemoryManager memoryManager) {
			this.memoryManager = memoryManager;
		}
		
		IMemoryManager getMemoryManager() {
			return memoryManager;
		}
		
		
		public void onHandleReadableEvent(final IoSocketHandler socketIOHandler) throws IOException {			
			socketIOHandler.onReadableEvent();
		}
		
		
		@SuppressWarnings("unchecked")
		public void onHandleWriteableEvent(final IoSocketHandler socketIOHandler) throws IOException {
			socketIOHandler.onWriteableEvent();
		}
		

		public void onHandleRegisterEvent(final IoSocketHandler socketIOHandler) throws IOException {
			socketIOHandler.onConnectEvent();
		}
		
		
		public void onDispatcherCloseEvent(final IoSocketHandler socketIOHandler) {
			socketIOHandler.onDispatcherClose();	
		}
	}	
}
