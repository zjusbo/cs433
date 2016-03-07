// $Id: BlockingConnection.java 1134 2007-04-05 17:44:43Z grro $
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

import java.io.IOException;
import java.lang.annotation.Inherited;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLContext;




/**
 * A connection pool implementation. A connection pool will be used on the 
 * client-side, if connections for the same server (address) will be created
 * in a serial manner in a sort period of time. By pooling such connections 
 * the overhead of establishing a connection will be avoided
 * For pool management reasons, timeouts can be defined. The 
 * IdleTimeout defines the max idle time in the pool. After this time the 
 * free connection will be closed. In the same way, the max living time 
 * defines the timout of the connection. If a free connection exceeds 
 * this time, the connection will be closed.  <br>
 * Additional the max size of the active connections can be defined. 
 * If a connection is requested and the max limit of the active connection 
 * is reached, the request will be blocked until a connection becomes free 
 * or the maxWaitTime will be reached. <br>
 * 
 * <pre>
 *  // create a unlimited connection pool with idle timeout 60 sec 
 *  BlockingConnectionPool pool = new BlockingConnectionPool(60L * 1000L);
 * 
 * 
 *  IBlockingConnection con = null;
 *  
 *  try {
 *     // retrieve a connection (if no connection is in pool, a new one will be created)  
 *     con = pool.getBlockingConnection(host, port);
 *     con.write("Hello");
 *     ...
 *     
 *     // always close the connection! (the connection will be returned into the connection pool)
 *     con.close();
 *     
 * 	} catch (IOException) {
 *     if (con != null) {
 *        try {
 *          // if the connection is invalid -> destroy it (it will not return into the pool)
 *          pool.destroyConnection(con);
 *        } catch (Exception ignore) { }
 *     }
 *  }
 * </pre>
 *  
 * @author grro@xsocket.org
 */
public final class BlockingConnectionPool extends AbstractConnectionPool {
	
	public static final long UNLIMITED_TIMEOUT = AbstractConnectionPool.MAX_TIMEOUT; 

	private SSLContext sslContext = null;
	private boolean sslOn = false;
	
	
	/**
	 * constructor
	 *  
	 * @param timeToIdleMillis  the max idle time in the pool. After this time the free connection will be closed 
	 */
	public BlockingConnectionPool(long timeToIdleMillis) {
		this(timeToIdleMillis, Integer.MAX_VALUE, NULL);
	}

	/**
	 * constructor
	 * 
	 * @param timeToIdleMillis  the max idle time in the pool. After this time the free connection will be closed 
	 * @param timeToLiveMillis  the max living time of the connection. If a free connection exeeded this time, the connection will be closed (if it is in pool)
	 * @param maxWaitMillis     the max wait time by acquiring a connection from the pool
	 */
	public BlockingConnectionPool(long timeToIdleMillis, int maxActive, long maxWaitTimeMillis) {
		super(timeToIdleMillis, MAX_TIMEOUT, maxActive, maxWaitTimeMillis, MAX_SIZE);
	}


	/**
	 * constructor
	 * 
	 * @param timeToIdleMillis  the max idle time in the pool. After this time the free connection will be closed 
	 * @param timeToLiveMillis  the max living time of the connection. If a free connection exeeded this time, the connection will be closed (if it is in pool)
	 * @param maxWaitMillis     the max wait time by acquiring a connection from the pool
	 * @param maxIdle           the max number of free connection in the pool 
	 */
	public BlockingConnectionPool(long timeToIdleMillis, int maxActive, long maxWaitTimeMillis, int maxIdle) {
		super(timeToIdleMillis, MAX_TIMEOUT, maxActive, maxWaitTimeMillis, maxIdle);
	}
	


	/**
	 * constructor
	 *
	 * @param timeToIdleMillis  the max idle time in the pool. After this time the free connection will be closed
	 * @param timeToLiveMillis  the max living time of the connection. If a free connection exceeded this time, the connection will be closed (if it is in pool)
	 * @param maxWaitMillis     the max wait time by acquiring a connection from the pool
	 * @param maxIdle           the max number of free connection in the pool
	 * @param sslContext        the ssl context or <code>null</code> if ssl should not be used
	 */
	public BlockingConnectionPool(long timeToIdleMillis, int maxActive, long maxWaitTimeMillis, int maxIdle, SSLContext sslContext) {
		super(timeToIdleMillis, MAX_TIMEOUT, maxActive, maxWaitTimeMillis, maxIdle);
		this.sslContext = sslContext;
		if (sslContext != null) {
			sslOn = true;
		}
	}
	
	
	/**
	 * get a pool connection for the given address. If no free connection is in the pool, 
	 * a new one will be created <br> <br>
	 * 
	 * This method is thread safe
	 * 
	 * @param host   the server address
	 * @param port   the sever port 
	 * @return the connection
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)   
	 * @throws IOException  if an exception occurs
	 */
	public IBlockingConnection getBlockingConnection(String host, int port) throws IOException, WaitTimeoutException {
		return (IBlockingConnection) getConnection(new InetSocketAddress(host, port), null);
	}

	
	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 *  a new one will be created <br> <br>
	 * 
	 * This method is thread safe
	 * 
	 * @param address the server address
	 * @param port    the sever port 
	 * @return the connection
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)   
	 * @throws IOException  if an exception occurs
	 */
	public IBlockingConnection getBlockingConnection(InetAddress address, int port) throws IOException, WaitTimeoutException {
		return (IBlockingConnection) getConnection(new InetSocketAddress(address, port), null);
	}


	/**
	 * {@link Inherited}
	 */
	@Override
	PoolableConnection createConnection(InetSocketAddress address, Executor workerPool) throws IOException {
		return new PoolableBlockingConnection(address);
	}

	
	
	private final class PoolableBlockingConnection extends PoolableConnection implements IBlockingConnection {
		
		public PoolableBlockingConnection(InetSocketAddress address) throws IOException {
			super(BlockingConnectionPool.this, new BlockingConnection(address.getAddress(), address.getPort(), sslContext, sslOn), address);
		}
		
		public void setReceiveTimeoutMillis(long timeout) {
			((BlockingConnection) getDelegee()).setReceiveTimeoutMillis(timeout);
		}
		
		public IBlockingConnection setOption(String name, Object value) throws IOException {
			return ((BlockingConnection) getDelegee()).setOption(name, value);
		}
	}
}	
