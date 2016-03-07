// $Id: DataConverter.java 1546 2007-07-23 06:07:56Z grro $

/*
 *  Copyright (c) xcache.org, 2007. All rights reserved.
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
 * The latest copy of this software may be found on http://www.xcache.org/
 */
package org.xcache;

import java.io.IOException;

import org.xsocket.stream.BlockingConnectionPool;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IConnection;




/**
 * 
 * 
 * @author grro@xcache.org
 */
final class ConnectionPool  {
	
	private static final ConnectionPool INSTANCE = new ConnectionPool();
	
	private final BlockingConnectionPool pool = new BlockingConnectionPool(60L * 1000L);

	
	private ConnectionPool() { }
	
	static ConnectionPool getInstance() {
		return INSTANCE;
	}
	
	public IBlockingConnection getConnection(Address address) throws IOException {
		IBlockingConnection connection = pool.getBlockingConnection(address.getAddress(), address.getPort());
		connection.setAutoflush(false);
		connection.setOption(IConnection.TCP_NODELAY, true);
		connection.setIdleTimeoutSec(CacheServer.IDLE_TIMEOUT_SEC * 5);
		connection.setConnectionTimeoutSec(CacheServer.CONNECTION_TIMEOUT_SEC);

		return connection;
	}
	
	public void destroyConnection(IBlockingConnection connection) throws IOException {
		pool.destroyConnection(connection);
	}
}
