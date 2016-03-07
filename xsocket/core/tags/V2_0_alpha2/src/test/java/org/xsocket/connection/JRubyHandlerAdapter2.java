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
import java.util.logging.Logger;

import org.jruby.exceptions.RaiseException;

import org.xsocket.ILifeCycle;
import org.xsocket.Resource;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.IDisconnectHandler;
import org.xsocket.connection.IHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;




/**
* Workaround support class for usage within JRuby
*
* Implementation base for handler implemented in JRuby. The adapter restores
* the native exception of a JRuby org.jruby.exceptions.RaiseException.
* @author grro@xsocket.org
*/
public final class JRubyHandlerAdapter2 implements IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, ILifeCycle {

	private static final Logger LOG = Logger.getLogger(JRubyHandlerAdapter2.class.getName());
	
	
	@Resource
	private IServer server = null;
	
	private IHandler delegee = null;

	private boolean isConnectHandler = false;
	private boolean isDisconnectHandler = false;
	private boolean isDataHandler = false;
	private boolean isIdleTimeoutHandler = false;
	private boolean isConnectionTimeoutHandler = false;
	private boolean isLifeCycleHandler = false;

	
	public static String getVersion() {
		return "2.0 (2008-Jan-27)";
	}
	

	public JRubyHandlerAdapter2(IHandler handler) {
		this.delegee = handler;

		isConnectHandler = (handler instanceof IConnectHandler);
		isDisconnectHandler = (handler instanceof IDisconnectHandler);
		isDataHandler = (handler instanceof IDataHandler);
		isIdleTimeoutHandler = (handler instanceof IIdleTimeoutHandler);
		isConnectionTimeoutHandler = (handler instanceof IIdleTimeoutHandler);
		isLifeCycleHandler = (handler instanceof org.xsocket.ILifeCycle);
	}


	public boolean onConnect(INonBlockingConnection connection) throws IOException {

		try {
			if (isConnectHandler) {
				return ((IConnectHandler) delegee).onConnect(connection);
			} else {
				return false;
			}
		} catch (RaiseException jrubyEx) {
			mapException(jrubyEx);
			return false;  // will not be reached. just inserted to make the compiler happy
		}
	}


	public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
		try {
			if (isDisconnectHandler) {
				return ((IDisconnectHandler) delegee).onDisconnect(connection);
			} else {
				return false;
			}
		} catch (RaiseException jrubyEx) {
			mapException(jrubyEx);
			return false;  // will not be reached. just inserted to make the compiler happy
		}

	}

	public boolean onData(INonBlockingConnection connection) throws IOException {
		try {
			if (isDataHandler) {
				return ((IDataHandler) delegee).onData(connection);
			} else {
				return false;
			}
		} catch (RaiseException jrubyEx) {
			mapException(jrubyEx);
			return false;  // will not be reached. just inserted to make the compiler happy
		}
	}

	public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
		try {
			if (isConnectionTimeoutHandler) {
				return ((IConnectionTimeoutHandler) delegee).onConnectionTimeout(connection);
			} else {
				return false;
			}
		} catch (RaiseException jrubyEx) {
			mapException(jrubyEx);
			return false;  // will not be reached. just inserted to make the compiler happy
		}
	}

	public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
		try {
			if (isIdleTimeoutHandler) {
				return ((IIdleTimeoutHandler) delegee).onIdleTimeout(connection);
			} else {
				return false;
			}
		} catch (RaiseException jrubyEx) {
			mapException(jrubyEx);
			return false;  // will not be reached. just inserted to make the compiler happy
		}
	}


	public void onInit() {
		if (isLifeCycleHandler) {
			injectServerField(server, delegee);
			((org.xsocket.ILifeCycle) delegee).onInit();
		}
	}


	public void onDestroy() throws IOException {
		if (isLifeCycleHandler) {
			((org.xsocket.ILifeCycle) delegee).onDestroy();
		}
	}


	private void mapException(RaiseException jrubyEx) throws IOException {
		if (jrubyEx.getCause() == null) {
			throw new IOException(jrubyEx.getMessage());
		}

		Class nativeExceptionclass = jrubyEx.getCause().getClass();

		if (RuntimeException.class.isAssignableFrom(nativeExceptionclass)) {
			throw (RuntimeException) jrubyEx.getCause();
		}

		if (IOException.class.isAssignableFrom(nativeExceptionclass)) {
			throw (IOException) jrubyEx.getCause();
		}

		throw new RuntimeException(jrubyEx.getMessage());
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
}