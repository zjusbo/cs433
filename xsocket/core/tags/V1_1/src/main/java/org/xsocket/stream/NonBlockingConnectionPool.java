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
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import org.xsocket.ClosedConnectionException;



/**
 * A connection pool implementation. A connection pool will be used on the 
 * client-side, if connections for the same server (address) will be created
 * in a serial manner in a sort period of time. By pooling such connections, 
 * the connections can be reused to reduce the overhead of establishing a 
 * connection. For pool management reasons, timeouts can be defined. The 
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
 *  // create  a connection pool
 *  NonBlockingConnectionPool pool = new NonBlockingConnectionPool(500);
 * 
 *  INonBlockingCinnection con = null;
 *  
 *  try {
 *     // retrieve a connection (if no connection is in pool, a new one will be created)   
 *     con = pool.getNonBlockingConnection(host, port);
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
public final class NonBlockingConnectionPool extends AbstractConnectionPool {

	public static final long UNLIMITED_TIMEOUT = AbstractConnectionPool.MAX_TIMEOUT; 


	/**
	 * constructor
	 * 
	 * @param timeToIdleMillis  the max idle time in the pool. After this time the free connection will be closed 
	 */
	public NonBlockingConnectionPool(long timeToIdleMillis) {
		this(timeToIdleMillis, Integer.MAX_VALUE, NULL);
	}
	

	public NonBlockingConnectionPool(long timeToIdleMillis, int maxActive, long maxWaitTimeMillis) {
		super(timeToIdleMillis, MAX_TIMEOUT, maxActive, maxWaitTimeMillis, MAX_SIZE);
	}

	
	/**
	 * get a pool connection for the given address. If no free connection is in the pool, a new one will be created
	 * 
	 * @param host   the server address
	 * @param port   the sever port 
	 * @return the connection 
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)    
	 * @throws IOException  if an exception occurs
	 */
	public INonBlockingConnection getNonBlockingConnection(String host, int port) throws IOException, WaitTimeoutException {
		return (INonBlockingConnection) getConnection(new InetSocketAddress(host, port));
	}


	/**
	 * get a pool connection for the given address. If no free connection is in the pool, anew one will be created
	 * 
	 * @param address  the server address
	 * @param port     the sever port 
	 * @return the connection 
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)    
	 * @throws IOException  if an exception occurs
	 */
	public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port) throws IOException, WaitTimeoutException {
		return (INonBlockingConnection) getConnection(new InetSocketAddress(address, port));
	}
	/**
	 * {@link Inherited}
	 */
	@Override
	PoolableConnection createConnection(InetSocketAddress address) throws IOException {
		return new PoolableNonBlockingConnection(address);
	}
	
	
	
	private final class PoolableNonBlockingConnection extends PoolableConnection implements INonBlockingConnection {
		

		public PoolableNonBlockingConnection(InetSocketAddress address) throws IOException {
			super(NonBlockingConnectionPool.this, new NonBlockingConnection(address.getAddress(), address.getPort()), address);
		}
	
		public void setFlushmode(FlushMode flushMode) {
			getUnderlyingConnection().setFlushmode(flushMode);
		}
		
		public FlushMode getFlushmode() {
			return getUnderlyingConnection().getFlushmode();
		}
		
		public void setIdleTimeoutSec(int timeoutInSec) {
			getUnderlyingConnection().setIdleTimeoutSec(timeoutInSec);
		}
		
		public int getIdleTimeoutSec() {
			return getUnderlyingConnection().getIdleTimeoutSec();
		}
		
		public void setConnectionTimeoutSec(int timeoutSec) {
			getUnderlyingConnection().setConnectionTimeoutSec(timeoutSec);
		}
		
		
		public int getConnectionTimeoutSec() {
			return getUnderlyingConnection().getConnectionTimeoutSec();
		}

		public void setWriteTransferRate(int bytesPerSecond) throws ClosedConnectionException, IOException {
			getUnderlyingConnection().setWriteTransferRate(bytesPerSecond);
		}
		
		public ByteBuffer[] readAvailable() throws IOException, ClosedConnectionException {
			return getUnderlyingConnection().readAvailable();
		}
		
		public boolean readAvailableByDelimiter(String delimiter, WritableByteChannel outputChannel) throws IOException, ClosedConnectionException {
			return getUnderlyingConnection().readAvailableByDelimiter(delimiter, outputChannel);
		}
		
		public int getNumberOfAvailableBytes() {
			return getUnderlyingConnection().getNumberOfAvailableBytes();
		}
		
		public int indexOf(String str) {
			return getUnderlyingConnection().indexOf(str);
		}
		
		private NonBlockingConnection getUnderlyingConnection() {
			return (NonBlockingConnection) getDelegee();
		}
	}
}	
