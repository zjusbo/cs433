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

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.Execution;
import org.xsocket.ILifeCycle;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.Resource;
import org.xsocket.connection.spi.DefaultIoProvider;


/**
*
* @author grro@xsocket.org
*/
final class HandlerProxy implements Cloneable {

	private static final Logger LOG = Logger.getLogger(HandlerProxy.class.getName());

	
	@SuppressWarnings("unchecked")
	private static final Map<Class, HandlerProxy> cache = ConnectionUtils.newMapCache(25);
	private static final HandlerProxy NULL_HANDLER_PROXY_PROTOTYPE = new HandlerProxy(newNullHandler()); 

	
	private enum TaskType { ON_CONNECT, ON_DATA, ON_DISCONNECT, ON_IDLE_TIMEOUT, ON_CONNECTION_TIMEOUT }
	
	
	
	private static final int MODE_NON_THREADED = 0;
	private static final int MODE_MULTI_THREADED = 1;
	private static final int MODE_MIXED_THREADED = 2;
	private static final int MODE_NON_THREADED_NON_PROXIED = 3;
	
	private int threadMode = MODE_NON_THREADED;

	private Boolean isHandlerMultithreaded = null;

	private boolean isConnectHandler = false;	
	private boolean isConnectHandlerThreaded = false;
	
	private boolean isDataHandler = false;
	private boolean isDataHandlerThreaded = false;
	
	private boolean isDisconnectHandler = false;
	private boolean isDisconnectHandlerThreaded = false;
	
	private boolean isIdleTimeoutHandler = false;
	private boolean isIdleTimeoutHandlerThreaded = false;
	
	private boolean isConnectionTimeoutHandler = false;
	private boolean isConnectionTimeoutHandlerThreaded = false;

	private IHandler handler = null;


	private boolean isLifeCycle = false;
	private boolean isConnectionScoped = false;

	private IHandler cachedNonThreadedDelegator = null;
	
	
	/**
	 * constructor 
	 * 
	 * @param handler he underlying handler
	 */
	HandlerProxy(IHandler handler) {
		this.handler = handler;
	
		
		if (isHandlerMultithreaded()) {
			isConnectHandlerThreaded = true;
			isDisconnectHandlerThreaded = true;
			isDataHandlerThreaded = true;
			isConnectionTimeoutHandlerThreaded = true;
			isIdleTimeoutHandlerThreaded = true;
		}
		
		boolean isFullHandler = (handler instanceof IInternalHandler);
		
		if (handler instanceof IConnectHandler) {
			isConnectHandler = true;
			isConnectHandlerThreaded = isThreaded(handler.getClass(), "onConnect", isHandlerMultithreaded(), INonBlockingConnection.class);
		}
		
		if (handler instanceof IDataHandler) {
			isDataHandler = true;
			isDataHandlerThreaded = isThreaded(handler.getClass(), "onData", isHandlerMultithreaded(), INonBlockingConnection.class);
		}
	
		
		if (handler instanceof IDisconnectHandler) {
			isDisconnectHandler = true;
			isDisconnectHandlerThreaded = isThreaded(handler.getClass(), "onDisconnect", isHandlerMultithreaded(), INonBlockingConnection.class);
		}
		
		if (handler instanceof IIdleTimeoutHandler) {
			isIdleTimeoutHandler = true;
			isIdleTimeoutHandlerThreaded = isThreaded(handler.getClass(), "onIdleTimeout", isHandlerMultithreaded(), INonBlockingConnection.class);
		}

		if (handler instanceof IConnectionTimeoutHandler) {
			isConnectionTimeoutHandler = true;
			isConnectionTimeoutHandlerThreaded = isThreaded(handler.getClass(), "onConnectionTimeout", isHandlerMultithreaded(), INonBlockingConnection.class);			
		}

				
		isConnectionScoped = (handler instanceof IConnectionScoped);				
		isLifeCycle = (handler instanceof ILifeCycle);
		
		
		
		boolean isMixedThreaded = (isHandlerMultithreaded() != isConnectHandlerThreaded != isDisconnectHandlerThreaded != isDataHandlerThreaded != isIdleTimeoutHandlerThreaded != isConnectionTimeoutHandlerThreaded);
		
		if (isMixedThreaded) {
			threadMode = MODE_MIXED_THREADED;
			
		} else if (isHandlerMultithreaded) {
			threadMode = MODE_MULTI_THREADED;
			
		} else if (isFullHandler) {
			threadMode = MODE_NON_THREADED_NON_PROXIED;
			
		} else {
			threadMode = MODE_NON_THREADED;
		}
		
				
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("handler analyzed: " + this.toString());
		}
	}
	

	private boolean isHandlerMultithreaded() {
		if (isHandlerMultithreaded == null) {
			Execution execution = handler.getClass().getAnnotation(Execution.class);
			if (execution != null) {
				if(execution.value() == Execution.NONTHREADED) {
					isHandlerMultithreaded = false;
				} else {
					isHandlerMultithreaded = true;
				}
			} else {
				isHandlerMultithreaded = true;
			}
		}
		
		return isHandlerMultithreaded;
	}
	
	
	/**
	 * creates a connection specific proxy 
	 * 
	 * @param connection  the connection 
	 * @return the new proxy
	 */
	public IHandler newProxy(INonBlockingConnection connection) {
		
		HandlerProxy proxy = this; 
		
		if (isConnectionScoped) {
			try {
				proxy = (HandlerProxy) this.clone();
				proxy.handler = (IHandler) ((IConnectionScoped) this.handler).clone(); 				
			} catch (CloneNotSupportedException cnse) {
				throw new RuntimeException("error occured by cloning handler " + handler + " " + cnse.toString());
			}
		}
		
		
		switch (threadMode) {

		case MODE_NON_THREADED_NON_PROXIED:
			return proxy.handler; 

		case MODE_NON_THREADED:
			return proxy.newNonThreadedDelegator(); 
				
		case MODE_MULTI_THREADED:
			return proxy.newMultiThreadedDelegator(connection);
	
		default:
			return newMixedThreadedDelegator(connection); 
			
		}
	}

	
	
	private IHandler newNonThreadedDelegator() {
		if (isConnectionScoped) {
			return new NonThreadedDelegator();
		} else {
			if (cachedNonThreadedDelegator == null) {
				cachedNonThreadedDelegator = new NonThreadedDelegator();
			}
			return cachedNonThreadedDelegator;
		}
	}

	private IHandler newMultiThreadedDelegator(INonBlockingConnection connection) {
		return new MultiThreadedDelegator(connection);
	}

	private IHandler newMixedThreadedDelegator(INonBlockingConnection connection) {
		return new MixedThreadedDelegator(connection);
	}
	
	
	/**
	 * {@link ILifeCycle#onInit()}
	 */
	public final void onInit() {
		if (isLifeCycle) {
			((ILifeCycle) handler).onInit();
		}
	}

	/**
	 * {@link ILifeCycle#onDestroy()}
	 */
	public final void onDestroy() {
		if (isLifeCycle) {
			try {
				((ILifeCycle) handler).onDestroy();
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("exception occured by destroying " + handler + " " + ioe.toString());
				}
			}
		}		
	}
	
	
	/**
	 * returns the underlying handler
	 * @return the underlying handler
	 */
	IHandler getHandler() {
		return handler;
	}


	/**
	 * sets the underlying handler 
	 * @param handler the underlying handler
	 */
	final void setHandler(IHandler handler) {
		this.handler = handler;
	}
	
	
	

	private void performTask(TaskType taskType, INonBlockingConnection connection) {
		try  {
			switch (taskType) {
			case ON_CONNECT:
				callOnConnect(connection);
				break;

			case ON_DISCONNECT:
				callOnDisconnect(connection);
				break;
				
			case ON_DATA:
				callOnData(connection);
				break;
				
			case ON_IDLE_TIMEOUT:
				callOnIdleTimeout(connection);
				break;
						
			case ON_CONNECTION_TIMEOUT:
				callOnConnectionTimeout(connection);
				break;
				
			default:
				LOG.warning("error unknown task type " + taskType);
				break;
			}
			
		} catch (Exception e) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by preforming call back " + e.toString());
			}
		}
	}
		


	
	private boolean callOnConnect(INonBlockingConnection connection) throws IOException {
		try {
			((IConnectHandler) handler).onConnect(connection);
		} catch (MaxReadSizeExceededException mee) {
			closeSilence(connection);

		} catch (BufferUnderflowException bue) {
			// 	ignore

		} catch (RuntimeException re) {		
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onConnect by appHandler. " + handler + " Reason: " + re.toString());
			}
			closeSilence(connection);
			throw re;
			
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onConnect by appHandler. " + handler + " Reason: " + ioe.toString());
			}
			closeSilence(connection);
			throw ioe;
		}

		return false;
	}

	

	
	private boolean callOnConnectionTimeout(INonBlockingConnection connection) throws IOException {
		try {
			boolean isHandled = ((IConnectionTimeoutHandler) handler).onConnectionTimeout(connection);
			if (!isHandled) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + connection.getId() + "] closing connection because connection timeout has been occured and timeout handler returns true)");
				}
				closeSilence(connection);
			}

		} catch (MaxReadSizeExceededException mee) {
			closeSilence(connection);

		} catch (BufferUnderflowException bue) {
			// 	ignore

		} catch (RuntimeException re) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onConnectionTimeout by appHandler. " + handler + " Reason: " + re.toString());
			}
			closeSilence(connection);
			throw re;
		
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onConnectionTimeout by appHandler. " + handler + " Reason: " + ioe.toString());
			}
			closeSilence(connection);
			throw ioe;
		}


		return true;
	}
	
	
	
	private boolean callOnData(INonBlockingConnection connection) throws IOException {

		
		// loop until readQueue is empty or nor processing has been done (readQueue has not been modified)
		while (connection.available() != 0) {
			int version = connection.getReadBufferVersion();
			
			// calling onData method of the handler (return value will be ignored)
			try {
				((IDataHandler) handler).onData(connection);

				int newVersion = connection.getReadBufferVersion();
				if (newVersion != version) {
					version = newVersion;
				} else {
					return true;
				}
				
			} catch (MaxReadSizeExceededException mee) {
				closeSilence(connection);
				return true;

			} catch (BufferUnderflowException bue) {
				// 	ignore
				return true;

			} catch (RuntimeException re) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling data by appHandler. " + handler + " Reason: " + re.toString());
				}
				closeSilence(connection);
				throw re;
						
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling data by appHandler. " + handler + " Reason: " + ioe.toString());
				}
				closeSilence(connection);
				throw ioe;
			}
		} 
				
		return true;
	}
	
	private boolean callOnDisconnect(INonBlockingConnection connection) throws IOException {
		try {
			return ((IDisconnectHandler) handler).onDisconnect(connection);
			
		}  catch (RuntimeException re) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onDisconnect by appHandler. " + handler + " Reason: " + re.toString());
			}
			closeSilence(connection);
			throw re;
			
		}  catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onDisconnect by appHandler. " + handler + " Reason: " + ioe.toString());
			}
			closeSilence(connection);
			throw ioe;
		}	
	}
	
	

	
	
	private boolean callOnIdleTimeout(INonBlockingConnection connection) throws IOException {
		try {
			boolean isHandled = ((IIdleTimeoutHandler) handler).onIdleTimeout(connection);
			if (!isHandled) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + connection.getId() + "] closing connection because idle timeout has been occured and timeout handler returns true)");
				}
				closeSilence(connection);
			}

		} catch (MaxReadSizeExceededException mee) {
			closeSilence(connection);

		} catch (BufferUnderflowException bue) {
			// 	ignore

		} catch (RuntimeException re) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onIdleTimeout by appHandler. " + handler + " Reason: " + re.toString());
			}
			closeSilence(connection);
			throw re;
			
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onIdleTimeout by appHandler. " + handler + " Reason: " + ioe.toString());
			}
			closeSilence(connection);
			throw ioe;
		}
		
		return true;
	}


	
	private void closeSilence(INonBlockingConnection connection) {
		try {
			connection.close();
		} catch (Exception e) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by closing connection " + connection + " " + e.toString());
			}
		}
	}
	

	
	@Override
	public Object clone() throws CloneNotSupportedException {
		return super.clone();
	}

	
	
	private static IHandler newNullHandler() {
		return new NullHandler();
	}
	
	

	
	private static void injectServerField(IServer server, Object handler) {
		Field[] fields = handler.getClass().getDeclaredFields();
		for (Field field : fields) {
			if (field.isAnnotationPresent(Resource.class)) {
				Resource res = field.getAnnotation(Resource.class);
				if ((field.getType() == IServer.class) || (res.type() == IServer.class)) {
					field.setAccessible(true);
					try {
						field.set(handler, server);
					} catch (IllegalAccessException iae) {
						LOG.warning("could not set HandlerContext for attribute " + field.getName() + ". Reason " + iae.toString());
					}
				}
			}
		}
	}
	
	

	
	@SuppressWarnings("unchecked")
	private static boolean isThreaded(Class clazz, String methodname, boolean dflt, Class... paramClass) {
		try {
			Method meth = clazz.getMethod(methodname, paramClass);
			Execution execution = meth.getAnnotation(Execution.class);
			if (execution != null) {
				if(execution.value() == Execution.NONTHREADED) {
					return false;
				} else {
					return true;
				}
			} else {
				return dflt;
			}
			
		} catch (NoSuchMethodException nsme) {
			return dflt;
		}
	}

	
	

	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("handler=" + handler.getClass().getName() + "#" + handler.hashCode());
		sb.append( " isConnectHandler=" + isConnectHandler +
				   " isConnectHandlerThreaded=" + isConnectHandlerThreaded +
  		           " isDataHandler=" + isDataHandler +
  		           " isDataHandlerThreaded=" + isDataHandlerThreaded +
		           " isDisconnectHandler=" + isDisconnectHandler +
		           " isDisconnectHandlerThreaded=" + isDisconnectHandlerThreaded +
		           " isIdleTimeoutHandler=" + isIdleTimeoutHandler +
		           " isIdleTimeoutHandlerThreaded=" + isIdleTimeoutHandlerThreaded +
		           " isConnectionTimeoutHandler=" + isConnectionTimeoutHandler +
		           " isConnetionTimeoutHandlerThreaded=" + isConnectionTimeoutHandlerThreaded +
		           " isConnectionSCoped=" + isConnectionScoped +
		           " isLifeCycle=" + isLifeCycle);
		
		return sb.toString();
	}	
	
	


	@SuppressWarnings("unchecked")
	static HandlerProxy newPrototype(IHandler handler, IServer server) {
		if (handler == null) {
			return NULL_HANDLER_PROXY_PROTOTYPE;
		}
		
		
		HandlerProxy proxyPrototype = cache.get(handler.getClass());
		
		if (proxyPrototype == null) {
			proxyPrototype = new HandlerProxy(handler);
			cache.put(handler.getClass(), proxyPrototype);
		}
		
		try {
			HandlerProxy result = (HandlerProxy) proxyPrototype.clone();
			result.setHandler(handler);
			if (server != null) {
				injectServerField(server, result.getHandler());
			}
			
			
			return result;
		} catch (CloneNotSupportedException cnse) {
			throw new RuntimeException("error occured by cloning hndler proxy " + proxyPrototype + " " + cnse.toString());
		}
	}	
	
	
	

	
	
	private final class NonThreadedDelegator implements IConnectHandler, IDataHandler, IDisconnectHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler {
		
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			if (isConnectHandler) {
				return callOnConnect(connection);
			}
			return false;
		}
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			if (isDataHandler) {
				return callOnData(connection);
			} 
			return false;
		}
		

		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			if (isDisconnectHandler) {
				return callOnDisconnect(connection);
			} 
			return false;
		}
		
		
		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
			if (isIdleTimeoutHandler) {
				return callOnIdleTimeout(connection);
			} else {
				closeSilence(connection);
				return true;
			}
		}
		
		
		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
			if (isConnectionTimeoutHandler) {
				return callOnConnectionTimeout(connection);
			} else {
				closeSilence(connection);
				return true;
			}
		}
		
		
		@Override
		public String toString() {
			return super.toString() + "-> " + handler.toString();
		}
	}
	
	

	private final class MultiThreadedDelegator implements IConnectHandler, IDataHandler, IDisconnectHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler {
		
		private final LinkedList<TaskType> taskQueue = new LinkedList<TaskType>();
		private INonBlockingConnection connection = null;
		
		
		public MultiThreadedDelegator(INonBlockingConnection connection) {
			this.connection = connection;
		}
		
		
		private void processThreaded(TaskType taskType) {
			synchronized (taskQueue) {
				
				// no running worker
				if (taskQueue.isEmpty()) {
					taskQueue.addLast(taskType);
					
					Runnable task = new Runnable() {
						public void run() {
							performPendingTasks();
						}
					};
					connection.getWorkerpool().execute(task);				
				
				// worker is currently running -> add task to queue (the task will be handled by the running worker)
				} else {
					taskQueue.addLast(taskType);
				}				
			}
		}
		
		
		private void performPendingTasks() {
			assert (DefaultIoProvider.isDispatcherThread() == false);
			
			boolean taskToProcess = true;
			while(taskToProcess) {
				
				// get task from queue
				TaskType task = null;
				synchronized (taskQueue) {
					task = taskQueue.getFirst();
					assert (task != null) : "a task should always be available";
					
					// if there are duplicate entries, remove it 
					if (taskQueue.size() > 1) {
						List<TaskType> tasksToRemove = new ArrayList<TaskType>();
						for (int i = 1; i < taskQueue.size(); i++) {
							if (taskQueue.get(i).equals(task)) {
								tasksToRemove.add(taskQueue.get(i));
							}
						}

						if (LOG.isLoggable(Level.FINE)) {
							if (tasksToRemove.size() > 0) {
								LOG.fine("removing " + tasksToRemove.size() + " duplicate task entries");
							}
						}
						
						for (TaskType taskToRemove : tasksToRemove) {
							taskQueue.remove(taskToRemove);
						}
						
						assert (taskQueue.size() > 0);
					}	
				}
				
				// perform it 
				performTask(task, connection);
				
				
				// remove task
				synchronized (taskQueue) {
					// remaining tasks to process
					if (taskQueue.size() > 1) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("more task to process. process next task");
						}
						taskQueue.remove(task);
						taskToProcess = true;
						
					} else {
						taskQueue.remove(task);
						taskToProcess = false;
					}
				}
			}
		}
		
			
		
		public boolean onConnect(final INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			assert (this.connection == connection);
			
			if (isConnectHandler) {
				processThreaded(TaskType.ON_CONNECT);
				return true;
			}
			return false;
		}
		
		
		public boolean onData(final INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			assert (this.connection == connection);
		
			if (isDataHandler) {
				processThreaded(TaskType.ON_DATA);
				return true;
			}
			return false;
		}
		

		public boolean onDisconnect(final INonBlockingConnection connection) throws IOException {
			assert (this.connection == connection);
			
			if (isDisconnectHandler) {
				processThreaded(TaskType.ON_DISCONNECT);
				return true;
			}
			return false;
		}
		
		
		public boolean onIdleTimeout(final INonBlockingConnection connection) throws IOException {
			assert (this.connection == connection);
			
			if (isIdleTimeoutHandler) {
				processThreaded(TaskType.ON_IDLE_TIMEOUT);
				return true;
				
			} else {
				closeSilence(connection);
				return true;
			}
		}
		
		
		public boolean onConnectionTimeout(final INonBlockingConnection connection) throws IOException {
			assert (this.connection == connection);
			
			if (isConnectionTimeoutHandler) {
				processThreaded(TaskType.ON_CONNECTION_TIMEOUT);
				return true;
				
			} else {
				closeSilence(connection);
				return true;
			}
		}
		
		@Override
		public String toString() {
			return super.toString() + "-> " + handler.toString();
		}
	}

	


	private final class MixedThreadedDelegator implements IConnectHandler, IDataHandler, IDisconnectHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler {
		
		private final LinkedList<TaskType> taskQueue = new LinkedList<TaskType>();
		private INonBlockingConnection connection = null;
		
		
		public MixedThreadedDelegator(INonBlockingConnection connection) {
			this.connection = connection;
		}

		
		private void processNonThreaded(TaskType taskType) {
			synchronized (taskQueue) {
				
				// no running worker -> process non threaded
				if (taskQueue.isEmpty()) {
					performTask(taskType, connection);
				
				// worker is currently running -> add task to queue (the task will be handled by the running worker)
				} else {
					taskQueue.addLast(taskType);
				}				
			}
		}

		
		private void processThreaded(TaskType taskType) {
			synchronized (taskQueue) {
				
				// no running worker
				if (taskQueue.isEmpty()) {
					taskQueue.addLast(taskType);
					
					Runnable task = new Runnable() {
						public void run() {
							performPendingTasks();
						}
					};
					connection.getWorkerpool().execute(task);				
				
				// worker is currently running -> add task to queue (the task will be handled by the running worker)
				} else {
					taskQueue.addLast(taskType);
				}				
			}
		}
		
		
		private void performPendingTasks() {
			assert (DefaultIoProvider.isDispatcherThread() == false);
			
			boolean taskToProcess = true;
			while(taskToProcess) {
				
				// get task from queue
				TaskType task = null;
				synchronized (taskQueue) {
					task = taskQueue.getFirst();
					assert (task != null) : "a task should always be available";
					
					// if there are duplicate entries, remove it 
					if (taskQueue.size() > 1) {
						List<TaskType> tasksToRemove = new ArrayList<TaskType>();
						for (int i = 1; i < taskQueue.size(); i++) {
							if (taskQueue.get(i).equals(task)) {
								tasksToRemove.add(taskQueue.get(i));
							}
						}

						if (LOG.isLoggable(Level.FINE)) {
							if (tasksToRemove.size() > 0) {
								LOG.fine("removing " + tasksToRemove.size() + " duplicate task entries");
							}
						}
						
						for (TaskType taskToRemove : tasksToRemove) {
							taskQueue.remove(taskToRemove);
						}
						
						assert (taskQueue.size() > 0);
					}	
				}
				
				// perform it 
				performTask(task, connection);
				
				
				// remove task
				synchronized (taskQueue) {
					// remaining tasks to process
					if (taskQueue.size() > 1) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("more task to process. process next task");
						}
						taskQueue.remove(task);
						taskToProcess = true;
						
					} else {
						taskQueue.remove(task);
						taskToProcess = false;
					}
				}
			}
		}
		
			
		
		public boolean onConnect(final INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			assert (this.connection == connection);
			
			if (isConnectHandler) {
				if (isConnectHandlerThreaded) {
					processThreaded(TaskType.ON_CONNECT);
					return true;
				} else {
					processNonThreaded(TaskType.ON_CONNECT);
					return true;
				}
			}
			return false;
		}
		
		
		public boolean onData(final INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			assert (this.connection == connection);
		
			if (isDataHandler) {
				if (isDataHandlerThreaded) {
					processThreaded(TaskType.ON_DATA);
					return true;
				} else {
					processNonThreaded(TaskType.ON_DATA);
					return true;
				}
			}
			return false;
		}
		

		public boolean onDisconnect(final INonBlockingConnection connection) throws IOException {
			assert (this.connection == connection);
			
			if (isDisconnectHandler) {
				if (isDisconnectHandlerThreaded) {
					processThreaded(TaskType.ON_DISCONNECT);
					return true;
				} else {
					processNonThreaded(TaskType.ON_DISCONNECT);
					return true;
				}
			}
			return false;
		}
		
		
		public boolean onIdleTimeout(final INonBlockingConnection connection) throws IOException {
			assert (this.connection == connection);
			
			if (isIdleTimeoutHandler) {
				if (isIdleTimeoutHandlerThreaded) {
					processThreaded(TaskType.ON_IDLE_TIMEOUT);
					return true;
				} else {
					processNonThreaded(TaskType.ON_IDLE_TIMEOUT);
					return true;
				}
				
			} else {
				closeSilence(connection);
				return true;
			}
		}
		
		
		public boolean onConnectionTimeout(final INonBlockingConnection connection) throws IOException {
			assert (this.connection == connection);
			
			if (isConnectionTimeoutHandler) {
				if (isConnectionTimeoutHandlerThreaded) {
					processThreaded(TaskType.ON_CONNECTION_TIMEOUT);
					return true;
				} else {
					processNonThreaded(TaskType.ON_CONNECTION_TIMEOUT);
					return true;					
				}
				
			} else {
				closeSilence(connection);
				return true;
			}
		}
		
		@Override
		public String toString() {
			return super.toString() + "-> " + handler.toString();
		}
	}

	

	
	
	@Execution(Execution.NONTHREADED)
	private static final class NullHandler implements IInternalHandler {
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			return false;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			return false;
		}
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			return false;
		}
		
		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
			connection.close();
			return true;
		}
		
		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
			connection.close();
			return true;
		}
	}
}
