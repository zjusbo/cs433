//$Id: NonBlockingConnectionClientTest.java 1522 2007-07-15 10:51:35Z grro $
/*
 *  Copyright (c) xsocket.org, 2006. All rights reserved.
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


import java.io.IOException;

import org.jruby.exceptions.RaiseException;
import org.xsocket.stream.IConnectHandler;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.INonBlockingConnection;




/**
* Workaround support class for usage within JRuby
*
* Implementation base for handler implemented in JRuby. The adapter restores
* the native exception of a JRuby org.jruby.exceptions.RaiseException.
* @author grro@xsocket.org
*/
public final class JRubyHandlerAdapter implements IConnectHandler, IDisconnectHandler, IDataHandler, ITimeoutHandler, IConnectionScoped, org.xsocket.ILifeCycle {

	private IHandler delegee = null;

	private boolean isConnectionScoped = false;
	private boolean isConnectHandler = false;
	private boolean isDisconnectHandler = false;
	private boolean isDataHandler = false;
	private boolean isTimeoutHandler = false;
	private boolean isLifeCycleHandler = false;


	public JRubyHandlerAdapter(IHandler handler) {
		this.delegee = handler;

		isConnectionScoped = (handler instanceof IConnectionScoped);
		isConnectHandler = (handler instanceof IConnectHandler);
		isDisconnectHandler = (handler instanceof IDisconnectHandler);
		isDataHandler = (handler instanceof IDataHandler);
		isTimeoutHandler = (handler instanceof ITimeoutHandler);
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
			if (isTimeoutHandler) {
				return ((ITimeoutHandler) delegee).onConnectionTimeout(connection);
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
			if (isTimeoutHandler) {
				return ((ITimeoutHandler) delegee).onIdleTimeout(connection);
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
			((org.xsocket.ILifeCycle) delegee).onInit();
		}
	}


	public void onDestroy() {
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


	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		if (isConnectionScoped) {
			JRubyHandlerAdapter copy = (JRubyHandlerAdapter) super.clone();
			copy.delegee = (IHandler) ((IConnectionScoped) this.delegee).clone();
			return copy;

		} else {
			return this;
		}
	}
}
