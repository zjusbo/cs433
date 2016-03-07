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
package org.xsocket.stream.io.spi;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

import org.xsocket.stream.IConnection;



/**
 * Client IO Provider, which is responsible to create and manage client-side {@link IIoHandler}  <br><br>
 * 
 * <b>This class is experimental and is subject to change</b>
 * 
 * 
 * @author grro@xsocket.org
 */
public interface IClientIoProvider extends IHandlerIoProvider {
		
	public static final String PROVIDER_CLASSNAME_KEY = "org.xsocket.stream.io.spi.ClientIoProviderClass";

	
	public static final String SO_SNDBUF = IConnection.SO_SNDBUF;
	public static final String SO_RCVBUF = IConnection.SO_RCVBUF;
	public static final String SO_REUSEADDR = IConnection.SO_REUSEADDR;
	public static final String SO_KEEPALIVE = IConnection.SO_KEEPALIVE;
	public static final String SO_LINGER = IConnection.SO_LINGER;
	public static final String TCP_NODELAY = IConnection.TCP_NODELAY;

	
	/**
	 * creates a client-site {@link IIoHandler}
	 * 
	 * @param ctx             the handler context
	 * @param remoteAddress   the remote address
	 * @param options         the socket options 
	 * @return the new IoHandler-instance
	 * @throws IOException If some other I/O error occurs 
	 */
	public IIoHandler createClientIoHandler(IIoHandlerContext ctx, InetSocketAddress remoteAddress, Map<String ,Object> options) throws IOException;	
}
