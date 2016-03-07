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
import java.net.SocketTimeoutException;
import java.nio.BufferUnderflowException;
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





/**
 * utility class for jmx support
 * 
 * @author grro@xsocket.org
 */
@SuppressWarnings("unchecked")
public final class ConnectionUtils {
	
	private static final Logger LOG = Logger.getLogger(ConnectionUtils.class.getName());
	
	public static final String DEFAULT_DOMAIN = "org.xsocket.connection";
	public static final String SERVER_TRHREAD_PREFIX = "xServer";


	private static String versionInfo = null;
	

	private ConnectionUtils() { }

	
	
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
	 * 60 sec a timeout exception will been thrown 
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
	 * <br/><br/><b>This is a xSocket preview functionality and subject to change. This
	 * method could be removed by the final version</b>
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
	 * <br/><br/><b>This is a xSocket preview functionality and subject to change. This
	 * method could be removed by the final version</b> 
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
	 * <br/><br/><b>This is a xSocket preview functionality and subject to change. This
	 * method could be removed by the final version</b> 
	 *
 	 * @param mbeanServer  the mbean server to use
	 * @param server       the server to register
	 * @param domain       the domain name to use
	 * @return the objectName
	 * @throws JMException  if an jmx exception occurs
	 */
	public static ObjectName registerMBean(IServer server, String domain, MBeanServer mbeanServer) throws JMException {
		return ServerMBeanProxyFactory.createAndRegister(server, domain, mbeanServer);
	}

	
	
	/**
	 * creates and registers a mbean for the given connection pool on the platform MBeanServer
	 * 
	 * <br/><br/><b>This is a xSocket preview functionality and subject to change. This
	 * method could be removed by the final version</b>
	 * 
	 * @param pool  the pool to register
	 * @return the objectName  
	 * @throws JMException  if an jmx exception occurs
	 */
	@SuppressWarnings("unchecked")
	public static ObjectName  registerMBean(IConnectionPool pool) throws JMException {
		return registerMBean(pool, DEFAULT_DOMAIN);
	}

	
	/**
	 * creates and registers a mbean for the given connection pool on the platform MBeanServer 
	 * under the given domain name 
	 * 
	 * <br/><br/><b>This is a xSocket preview functionality and subject to change. This
	 * method could be removed by the final version</b> 
	 * 
	 * @param pool     the pool to register 
	 * @param domain   the domain name to use
	 * @return the objectName 
	 * @throws JMException  if an jmx exception occurs
	 */
	@SuppressWarnings("unchecked")
	public static ObjectName  registerMBean(IConnectionPool pool, String domain) throws JMException {
		return registerMBean(pool, domain, ManagementFactory.getPlatformMBeanServer());
	}
	

	
	
	/**
	 * creates and registers a mbean for the given pool on the given MBeanServer
	 * under the given domain name 
	 * 
	 * <br/><br/><b>This is a xSocket preview functionality and subject to change. This
	 * method could be removed by the final version</b>
	 *
 	 * @param mbeanServer  the mbean server to use
	 * @param pool         the pool to register 
	 * @param domain       the domain name to use
	 * @return the objectName 
	 * @throws JMException  if an jmx exception occurs 
	 */
	@SuppressWarnings("unchecked")
	public static ObjectName  registerMBean(IConnectionPool pool, String domain, MBeanServer mbeanServer) throws JMException {
		return ConnectionPoolMBeanProxyFactory.createAndRegister(pool, domain, mbeanServer);
	}
	
	
	/**
	 * get the version string of xSocket (core) 
	 * 
	 * @return the version string
	 */
	public static String getVersionInfo() {
		if (versionInfo == null) {
			
			versionInfo = "<unknown>";
			
			try {
				InputStreamReader isr = new InputStreamReader(ConnectionUtils.class.getResourceAsStream("/org/xsocket/version.txt"));
				if (isr != null) {
					LineNumberReader lnr = new LineNumberReader(isr);
					String line = null;
					do {
						line = lnr.readLine();
						if (line != null) {
							if (line.startsWith("Implementation-Version=")) {
								versionInfo = line.substring("Implementation-Version=".length(), line.length()).trim();
							}
						}
					} while (line != null);
		
					lnr.close();
				}
			} catch (Exception ignore) { }
		}
		
		return versionInfo;
	}
	
	
	/**
	 * creates a new bound cache 
	 *  
	 * @param <T>      the map value type     
	 * @param maxSize  the max size of the cache 
	 * @return the new map cache 
	 */
	
	public static <T> Map<Class, T> newMapCache(int maxSize) {
		return new MapCache<T>(maxSize);
	}
	 

	@SuppressWarnings("unchecked")
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
}
