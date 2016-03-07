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
package org.xsocket.connection.spi;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

import org.xsocket.connection.IConnection;



/**
 * Client IO Provider, which is responsible to create and manage client-side {@link IIoHandler}  <br><br>
 *
 *
 * @author grro@xsocket.org
 */
public interface IClientIoProvider extends IHandlerIoProvider {

	public static final String PROVIDER_CLASSNAME_KEY = "org.xsocket.stream.io.ClientIoProviderClass";


	public static final String SO_SNDBUF = IConnection.SO_SNDBUF;
	public static final String SO_RCVBUF = IConnection.SO_RCVBUF;
	public static final String SO_REUSEADDR = IConnection.SO_REUSEADDR;
	public static final String SO_TIMEOUT = "SOL_SOCKET.SO_TIMEOUT";
	public static final String SO_KEEPALIVE = IConnection.SO_KEEPALIVE;
	public static final String SO_LINGER = IConnection.SO_LINGER;
	public static final String TCP_NODELAY = IConnection.TCP_NODELAY;

	public static final long DEFAULT_CONNECTION_TIMEOUT_MILLIS = IConnection.DEFAULT_CONNECTION_TIMEOUT_MILLIS;
	public static final long DEFAULT_IDLE_TIMEOUT_MILLIS = IConnection.DEFAULT_IDLE_TIMEOUT_MILLIS;

	/**
	 * Return the version of this implementation. It consists of any string assigned
	 * by the vendor of this implementation and does not have any particular syntax
	 * specified or expected by the Java runtime. It may be compared for equality
	 * with other package version strings used for this implementation
	 * by this vendor for this package.
	 *
	 * @return the version of the implementation
	 */
	public String getImplementationVersion();



	/**
	 * creates a client-site {@link IIoHandler}
	 *
	 * @param remoteAddress   the remote address
	 * @param options         the socket options
	 * @return the new IoHandler-instance
	 * @throws IOException If some other I/O error occurs
	 *
	 */
	public IIoHandler createClientIoHandler(InetSocketAddress remoteAddress, int connectTimeoutMillis, Map<String ,Object> options) throws IOException;
}
