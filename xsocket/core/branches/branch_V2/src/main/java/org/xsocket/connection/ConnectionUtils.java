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
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.nio.BufferUnderflowException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.xsocket.DataConverter;
import org.xsocket.Execution;
import org.xsocket.ILifeCycle;
import org.xsocket.Resource;




/**
 * utility class 
 * 
 * @author grro@xsocket.org
 */
@SuppressWarnings("unchecked")
public final class ConnectionUtils {
	
	private static final Logger LOG = Logger.getLogger(ConnectionUtils.class.getName());
	
	public static final String DEFAULT_DOMAIN = "org.xsocket.connection";
	public static final String SERVER_TRHREAD_PREFIX = "xServer";
	
	private static final IoProvider IO_PROVIDER = new IoProvider();
	
	private static final Map<Class, HandlerInfo> handlerInfoCache = newMapCache(25);

	private static String implementationVersion = null;
	private static String implementationDate = null;
	

	private ConnectionUtils() { }

	

	static IoProvider getIoProvider() {
		return IO_PROVIDER;
	}
	
	
	/**
	 * validate, based on a leading int length field. The length field will be removed
	 * 
	 * @param connection     the connection
	 * @return the length 
	 * @throws IOException if an exception occurs
	 * @throws BufferUnderflowException if not enough data is available
	 */
	public static int validateSufficientDatasizeByIntLengthField(INonBlockingConnection connection) throws IOException, BufferUnderflowException {

		connection.resetToReadMark();
		connection.markReadPosition();
		
		// check if enough data is available
		int length = connection.readInt();
		if (connection.available() < length) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + connection.getId() + "]insufficient data. require " + length + " got "  + connection.available());
			}
			throw new BufferUnderflowException();
	
		} else { 
			// ...yes, remove mark
			connection.removeReadMark();
			return length;
		}
	}  


	
	/**
	 * validate, based on a leading int length field, that enough data (getNumberOfAvailableBytes() >= length) is available. If not,
	 * an BufferUnderflowException will been thrown. Example:
	 * <pre>
	 * //client
	 * connection.setAutoflush(false);  // avoid immediate write
	 * ...
	 * connection.markWritePosition();  // mark current position
	 * connection.write((int) 0);       // write "emtpy" length field
	 *  
	 * // write and count written size
	 * int written = connection.write(CMD_PUT);
	 * written += ...
	 *  
	 * connection.resetToWriteMark();  // return to length field position
	 * connection.write(written);      // and update it
	 * connection.flush(); // flush (marker will be removed implicit)
	 * ...
	 * 
	 * 
	 * // server
	 * class MyHandler implements IDataHandler {
	 *    ...
	 *    public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
	 *       int length = ConnectionUtils.validateSufficientDatasizeByIntLengthField(connection);
	 *       
	 *       // enough data (BufferUnderflowException hasn`t been thrown)
	 *       byte cmd = connection.readByte();
	 *       ...
	 *    }
	 *  }      
	 * </pre>
	 * 
	 * @param connection         the connection
	 * @param removeLengthField  true, if length field should be removed
	 * @return the length 
	 * @throws IOException if an exception occurs
	 * @throws BufferUnderflowException if not enough data is available
	 */
	public static int validateSufficientDatasizeByIntLengthField(INonBlockingConnection connection, boolean removeLengthField) throws IOException, BufferUnderflowException {

		connection.resetToReadMark();
		connection.markReadPosition();
		
		// check if enough data is available
		int length = connection.readInt();
		if (connection.available() < length) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + connection.getId() + "]insufficient data. require " + length + " got "  + connection.available());
			}
			throw new BufferUnderflowException();
	
		} else { 
			// ...yes, remove mark
			if (!removeLengthField) {
				connection.resetToReadMark();
			}
			
			connection.removeReadMark();
			return length;
		}
	}  
	
	
	/**
	 * starts the given server within a dedicated thread. This method blocks 
	 * until the server is open. If the server hasn't been started within 
	 * 60 sec a timeout exception will been thrown. 
	 * 
	 * @param server  the server to start
	 * @throws SocketTimeoutException is the timeout has been reached 
	 */
	public static void start(IServer server) throws SocketTimeoutException  {
		start(server, 60);
	}
	
	/**
	 * starts the given server within a dedicated thread. This method blocks 
	 * until the server is open.
	 * 
	 * @param server     the server to start
	 * @param timeoutSec the maximum time to wait
	 * 
	 * @throws SocketTimeoutException is the timeout has been reached
	 */
	public static void start(IServer server, int timeoutSec) throws SocketTimeoutException {
		
		final CountDownLatch startedSignal = new CountDownLatch(1);
		
		// create and add startup listener 
		IServerListener startupListener = new IServerListener() {
			
			public void onInit() {
				startedSignal.countDown();
			};
			
			
			public void onDestroy() {};
		};
		server.addListener(startupListener);
		
		
		// start server within a dedicated thread 
		Thread t = new Thread(server);
		t.setName("xServer");
		t.start();
	
		
		// wait until server has been started (onInit has been called)
		boolean isStarted = false;
		try {
			isStarted = startedSignal.await(timeoutSec, TimeUnit.SECONDS);
		} catch (InterruptedException e) { 
			throw new RuntimeException("start signal doesn't occured. " + e.toString());
		}

		// timeout occurred?
		if (!isStarted) {
			throw new SocketTimeoutException("start timeout (" + DataConverter.toFormatedDuration((long) timeoutSec * 1000) + ")");
		}
		
		
		// update thread name
		t.setName(SERVER_TRHREAD_PREFIX + ":" + server.getLocalPort());
		
		// remove the startup listener
		server.removeListener(startupListener);
	}
	
	
	
	
	
	
	
	/**
	 * creates and registers a mbean for the given server on the platform MBeanServer
	 *
	 * @param server  the server to register
	 * @return the objectName
	 * @throws JMException  if an jmx exception occurs
	 */
	public static ObjectName registerMBean(IServer server) throws JMException {
		return registerMBean(server, DEFAULT_DOMAIN);
	}


	/**
	 * creates and registers a mbean for the given server on the platform MBeanServer
	 * under the given domain name
	 * 
	 * @param server   the server to register
	 * @param domain   the domain name to use
	 * @return the objectName
	 * @throws JMException  if an jmx exception occurs
	 */
	public static ObjectName registerMBean(IServer server, String domain) throws JMException {
		return registerMBean(server, domain, ManagementFactory.getPlatformMBeanServer());
	}


	/**
	 * creates and registers a mbean for the given server on the given MBeanServer
	 * under the given domain name
	 * 
 	 * @param mbeanServer  the mbean server to use
	 * @param server       the server to register
	 * @param domain       the domain name to use
	 * @return the objectName
	 * @throws JMException  if an jmx exception occurs
	 */
	public static ObjectName registerMBean(IServer server, String domain, MBeanServer mbeanServer) {
		try {
			return ServerMBeanProxyFactory.createAndRegister(server, domain, mbeanServer);
		} catch (Exception e) {
			throw new RuntimeException(DataConverter.toString(e));
		}
	}

	
	
	/**
	 * creates and registers a mbean for the given connection pool on the platform MBeanServer
	 * 
	 * @param pool  the pool to register
	 * @return the objectName  
	 * @throws JMException  if an jmx exception occurs
	 */
	public static ObjectName  registerMBean(IConnectionPool pool) throws JMException {
		return registerMBean(pool, DEFAULT_DOMAIN);
	}

	
	/**
	 * creates and registers a mbean for the given connection pool on the platform MBeanServer 
	 * under the given domain name 
	 * 
	 * @param pool     the pool to register 
	 * @param domain   the domain name to use
	 * @return the objectName 
	 * @throws JMException  if an jmx exception occurs
	 */
	public static ObjectName  registerMBean(IConnectionPool pool, String domain) throws JMException {
		return registerMBean(pool, domain, ManagementFactory.getPlatformMBeanServer());
	}
	

	
	
	/**
	 * creates and registers a mbean for the given pool on the given MBeanServer
	 * under the given domain name 
	 * 
 	 * @param mbeanServer  the mbean server to use
	 * @param pool         the pool to register 
	 * @param domain       the domain name to use
	 * @return the objectName 
	 * @throws JMException  if an jmx exception occurs 
	 */
	public static ObjectName  registerMBean(IConnectionPool pool, String domain, MBeanServer mbeanServer) throws JMException {
		return ConnectionPoolMBeanProxyFactory.createAndRegister(pool, domain, mbeanServer);
	}
	
	
	/**
	 * @deprecated use {@link ConnectionUtils#getImplementationVersion()} instead
	 */
	public static String getVersionInfo() {
		return getImplementationVersion();
	}
	

	/**
	 * get the implementation version
	 * 
	 * @return the implementation version
	 */
	public static String getImplementationVersion() {
		
		if (implementationVersion == null) {
			readVersionFile();
		}
		
		return implementationVersion;
	}

	
	/**
	 * get the implementation date
	 * 
	 * @return the implementation date
	 */
	public static String getImplementationDate() {
		
		if (implementationDate== null) {
			readVersionFile();
		}
		
		return implementationDate;
	}

	
	private static void readVersionFile() {
		
		implementationVersion = "<unknown>";
		implementationDate = "<unknown>";
			
		try {
			InputStreamReader isr = new InputStreamReader(ConnectionUtils.class.getResourceAsStream("/org/xsocket/version.txt"));
			if (isr != null) {
				LineNumberReader lnr = new LineNumberReader(isr);
				String line = null;
				do {
					line = lnr.readLine();
					if (line != null) {
						if (line.startsWith("Implementation-Version=")) {
							implementationVersion = line.substring("Implementation-Version=".length(), line.length()).trim();
						} else if (line.startsWith("Implementation-Date=")) {
							implementationDate = line.substring("Implementation-Date=".length(), line.length()).trim();
						}
					}
				} while (line != null);
				lnr.close();
			}
		} catch (Exception ignore) { }
	}
	
	
	
	
	
	/**
	 * creates a thread-safe new bound cache 
	 *  
	 * @param <T>      the map value type     
	 * @param maxSize  the max size of the cache 
	 * @return the new map cache 
	 */
	public static <T> Map<Class, T> newMapCache(int maxSize) {
		return Collections.synchronizedMap(new MapCache<T>(maxSize));
	}
	
	
	static void injectServerField(IServer server, Object handler) {
		Field[] fields = handler.getClass().getDeclaredFields();
		for (Field field : fields) {
			if (field.isAnnotationPresent(Resource.class)) {
				Resource res = field.getAnnotation(Resource.class);
				if ((field.getType() == IServer.class) || (res.type() == IServer.class)) {
					field.setAccessible(true);
					try {
						field.set(handler, server);
					} catch (IllegalAccessException iae) {
						LOG.warning("could not inject server for attribute " + field.getName() + ". Reason " + iae.toString());
					}
				}
			}
		}
	}
	
	
	/**
     * returns if current thread is  dispatcher thread
     * @return true, if current thread is a dispatcher thread
     */
    static boolean isDispatcherThread() {
        return Thread.currentThread().getName().startsWith(IoSocketDispatcher.DISPATCHER_PREFIX);
    }

    

	
	
	static HandlerInfo getHandlerInfo(IHandler handler) {
		HandlerInfo handlerInfo = handlerInfoCache.get(handler.getClass());

		if (handlerInfo == null) {
			handlerInfo = new  HandlerInfo(handler);
			handlerInfoCache.put(handler.getClass(), handlerInfo);
		}

		return handlerInfo;
	}

	
	private static boolean isMethodThreaded(Class clazz, String methodname, boolean dflt, Class... paramClass) {
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
	
	private static boolean isHandlerMultithreaded(IHandler handler) {
		Execution execution = handler.getClass().getAnnotation(Execution.class);
		if (execution != null) {
			if(execution.value() == Execution.NONTHREADED) {
				return false;
				
			} else {
				return true;
			}

		} else {
			return true;
		}
	}
	 

	private static final class MapCache<T> extends LinkedHashMap<Class , T> {
		
		private static final long serialVersionUID = 4513864504007457500L;
		
		private int maxSize = 0;
		
		MapCache(int maxSize) {
			this.maxSize = maxSize;
		}
		

		@Override
		protected boolean removeEldestEntry(Entry<Class, T> eldest) {
			return size() > maxSize;
		}	
	}
	
	
	static final class HandlerInfo {
		
		private boolean isConnectHandler = false; 
		private boolean isDataHandler = false;
		private boolean isDisconnectHandler = false;
		private boolean isIdleTimeoutHandler = false;
		private boolean isConnectionTimeoutHandler = false;
		private boolean isLifeCycle = false;
		
		private boolean isConnectionScoped = false;
		
		private boolean isHandlerMultithreaded = false;
		private boolean isConnectHandlerMultithreaded = false;
		private boolean isDataHandlerMultithreaded = false;
		private boolean isDisconnectHandlerMultithreaded = false;
		private boolean isIdleTimeoutHandlerMultithreaded = false;
		private boolean isConnectionTimeoutHandlerMultithreaded = false;
		
		private boolean isNonThreaded = false;
		

		HandlerInfo(IHandler handler) {
			isConnectHandler = (handler instanceof IConnectHandler);
			isDataHandler = (handler instanceof IDataHandler);
			isDisconnectHandler = (handler instanceof IDisconnectHandler);
			isIdleTimeoutHandler = (handler instanceof IIdleTimeoutHandler);
			isConnectionTimeoutHandler = (handler instanceof IConnectionTimeoutHandler);
			isLifeCycle = (handler instanceof ILifeCycle);
			
			isConnectionScoped = (handler instanceof IConnectionScoped);
			
			
			isHandlerMultithreaded = ConnectionUtils.isHandlerMultithreaded(handler);
			if (isConnectHandler) {
				isConnectHandlerMultithreaded = isMethodThreaded(handler.getClass(), "onConnect", isHandlerMultithreaded, INonBlockingConnection.class);
			}
			
			if (isDataHandler) {
				isDataHandlerMultithreaded = isMethodThreaded(handler.getClass(), "onData", isHandlerMultithreaded, INonBlockingConnection.class);
			}
			
			if (isDisconnectHandler) {
				isDisconnectHandlerMultithreaded = isMethodThreaded(handler.getClass(), "onDisconnect", isHandlerMultithreaded, INonBlockingConnection.class);
			}
			
			if (isIdleTimeoutHandler) {
				isIdleTimeoutHandlerMultithreaded =isMethodThreaded(handler.getClass(), "onIdleTimeout", isHandlerMultithreaded, INonBlockingConnection.class);
			}
			
			if (isConnectionTimeoutHandler) {
				isConnectionTimeoutHandlerMultithreaded =isMethodThreaded(handler.getClass(), "onConnectionTimeout", isHandlerMultithreaded, INonBlockingConnection.class);
			}


			if (isConnectionTimeoutHandler) {
				isConnectHandlerMultithreaded = isMethodThreaded(handler.getClass(), "onConnectionTimeout", isHandlerMultithreaded, INonBlockingConnection.class);
			}
			
			
			isNonThreaded = !isHandlerMultithreaded && !isConnectHandlerMultithreaded && 
			                !isDataHandlerMultithreaded && !isDisconnectHandlerMultithreaded &&
			                !isIdleTimeoutHandlerMultithreaded && !isConnectionTimeoutHandlerMultithreaded;
		}

		public boolean isConnectHandler() {
			return isConnectHandler;
		}

		public boolean isDataHandler() {
			return isDataHandler;
		}

		public boolean isDisconnectHandler() {
			return isDisconnectHandler;
		}

		public boolean isIdleTimeoutHandler() {
			return isIdleTimeoutHandler;
		}

		public boolean isConnectionTimeoutHandler() {
			return isConnectionTimeoutHandler;
		}
		
		public boolean isLifeCycle() {
			return isLifeCycle; 
		}

		public boolean isConnectionScoped() {
			return isConnectionScoped;
		}

		public boolean isNonthreaded() {
			return isNonThreaded;
		}
		
		public boolean isHandlerMultithreaded() {
			return isHandlerMultithreaded;
		}

		public boolean isConnectHandlerMultithreaded() {
			return isConnectHandlerMultithreaded;
		}

		public boolean isDataHandlerMultithreaded() {
			return isDataHandlerMultithreaded;
		}

		public boolean isDisconnectHandlerMultithreaded() {
			return isDisconnectHandlerMultithreaded;
		}

		public boolean isIdleTimeoutHandlerMultithreaded() {
			return isIdleTimeoutHandlerMultithreaded;
		}

		public boolean isConnectionTimeoutHandlerMultithreaded() {
			return isConnectionTimeoutHandlerMultithreaded;
		}
	}
}
