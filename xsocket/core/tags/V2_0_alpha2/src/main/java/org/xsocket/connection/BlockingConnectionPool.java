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
import java.net.InetAddress;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.xsocket.ILifeCycle;
import org.xsocket.WaitTimeoutException;




/**
 * A blocking connection pool implementation.  <br> <br>
 * 
 * This class is thread safe <br>
 *
 * <pre>
 *  // create a unbound connection pool 
 *  BlockingConnectionPool pool = new BlockingConnectionPool();
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
 * 	} catch (IOException e) {
 *     if (con != null) {
 *        try {
 *          // if the connection is invalid -> destroy it (it will not return into the pool)
 *          pool.destroy(con);
 *        } catch (Exception ignore) { }
 *     }
 *  }
 * </pre>
 *
 * @author grro@xsocket.org
 */
public final class BlockingConnectionPool implements IConnectionPool {
	
	private NonBlockingConnectionPool pool = null;


	/**
	 * constructor
	 */
	public BlockingConnectionPool() {
		pool = new NonBlockingConnectionPool();
	}

	
	/**
	 * constructor
	 *
	 * @param sslContext   the ssl context or <code>null</code> if ssl should not be used
	 */
	public BlockingConnectionPool(SSLContext sslContext) {
		pool = new NonBlockingConnectionPool(sslContext);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public boolean isOpen() {
		return pool.isOpen();
	}
	

	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 * a new one will be created 
	 *
	 * @param host   the server address
	 * @param port   the server port
	 * @return the connection
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
	 */
	public IBlockingConnection getBlockingConnection(String host, int port) throws IOException, WaitTimeoutException {
		return new BlockingConnection(pool.getNonBlockingConnection(host, port, false));
	}

	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 * a new one will be created 
	 *
	 * @param host   the server address
	 * @param port   the server port
	 * @param isSSL  true, if ssl connection       
	 * @return the connection
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
	 */
	public IBlockingConnection getBlockingConnection(String host, int port, boolean isSSL) throws IOException, WaitTimeoutException {
		return new BlockingConnection(pool.getNonBlockingConnection(host, port, isSSL));
	}

	
	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 * a new one will be created 
	 * 
	 * @param host                   the server address
	 * @param port                   the server port
	 * @param connectTimeoutMillis   the connection timeout
	 * @return the connection
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
	 */
	public IBlockingConnection getBlockingConnection(String host, int port, int connectTimeoutMillis) throws IOException, WaitTimeoutException {
		return new BlockingConnection(pool.getNonBlockingConnection(host, port, connectTimeoutMillis, false));
	}
	
	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 * a new one will be created 
	 * 
	 * @param host                   the server address
	 * @param port                   the server port
	 * @param connectTimeoutMillis   the connection timeout
	 * @param isSSL                  true, if ssl connection      
	 * @return the connection
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
	 */
	public IBlockingConnection getBlockingConnection(String host, int port, int connectTimeoutMillis, boolean isSSL) throws IOException, WaitTimeoutException {
		return new BlockingConnection(pool.getNonBlockingConnection(host, port, connectTimeoutMillis, isSSL));
	}

	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 *  a new one will be created 
	 *
	 * @param address the server address
	 * @param port    the server port
	 * @return the connection
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
	 */
	public IBlockingConnection getBlockingConnection(InetAddress address, int port) throws IOException, WaitTimeoutException {
		return new BlockingConnection(pool.getNonBlockingConnection(address, port, false));
	}

	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 *  a new one will be created 
	 *
	 * @param address  the server address
	 * @param port     the server port
	 * @param isSSL    true, if ssl connection    
	 * @return the connection
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
	 */
	public IBlockingConnection getBlockingConnection(InetAddress address, int port, boolean isSSL) throws IOException, WaitTimeoutException {
		return new BlockingConnection(pool.getNonBlockingConnection(address, port, isSSL));
	}
	
	
	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 * a new one will be created 
	 * 
	 * @param address                the server address
	 * @param port                   the server port
	 * @param connectTimeoutMillis   the connection timeout
	 * @return the connection
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
	 */
	public IBlockingConnection getBlockingConnection(InetAddress address, int port, int connectTimeoutMillis) throws IOException, WaitTimeoutException {
		return new BlockingConnection(pool.getNonBlockingConnection(address, port, connectTimeoutMillis, false));
	}
	
	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 * a new one will be created 
	 * 
	 * @param address                the server address
	 * @param port                   the server port
	 * @param connectTimeoutMillis   the connection timeout
	 * @param isSSL                  true, if ssl connection   
	 * @return the connection
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
	 */
	public IBlockingConnection getBlockingConnection(InetAddress address, int port, int connectTimeoutMillis, boolean isSSL) throws IOException, WaitTimeoutException {
		return new BlockingConnection(pool.getNonBlockingConnection(address, port, connectTimeoutMillis, isSSL));
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public void addListener(ILifeCycle listener) {
		pool.addListener(listener);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public boolean removeListener(ILifeCycle listener) {
		return pool.removeListener(listener);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getPooledIdleTimeoutSec() {
		return pool.getPooledIdleTimeoutSec();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void setPooledIdleTimeoutSec(int idleTimeoutSec) {
		pool.setPooledIdleTimeoutSec(idleTimeoutSec);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getPooledLifeTimeoutSec() {
		return pool.getPooledLifeTimeoutSec();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void setPooledLifeTimeoutSec(int lifeTimeoutSec) {
		pool.setPooledLifeTimeoutSec(lifeTimeoutSec);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getMaxActivePooled() {
		return pool.getMaxActivePooled();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void setMaxActivePooled(int maxActive) {
		pool.setMaxActivePooled(maxActive);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getMaxIdlePooled() {
		return pool.getMaxIdlePooled();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void setMaxIdlePooled(int maxIdle) {
		pool.setMaxIdlePooled(maxIdle);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public long getCreationMaxWaitMillis() {
		return pool.getCreationMaxWaitMillis();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void setCreationMaxWaitMillis(long maxWaitMillis) {
		pool.setCreationMaxWaitMillis(maxWaitMillis);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getNumPooledActive() {
		return pool.getNumPooledActive();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getNumPooledIdle() {
		return pool.getNumPooledIdle();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public List<String> getActiveConnectionInfos() {
		return pool.getActiveConnectionInfos();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public List<String> getIdleConnectionInfos() {
		return pool.getIdleConnectionInfos();
	}
	
	/**
	 * {@inheritDoc}
	 */
    public int getNumCreated() {
    	return pool.getNumCreated();
    }
    
    /**
	 * {@inheritDoc}
	 */
    public int getNumDestroyed() {
    	return pool.getNumDestroyed();
    }
    
    public int getNumTimeoutConnectionLifetime() {
    	return pool.getNumTimeoutConnectionLifetime();
    }
    
    
    public int getNumTimeoutConnectionIdle() {
    	return pool.getNumTimeoutConnectionIdle();
    }
    
    /**
	 * {@inheritDoc}
	 */
    public int getNumTimeoutPooledIdle() {
    	return pool.getNumTimeoutPooledIdle();
    }

    /**
	 * {@inheritDoc}
	 */
    public int getNumTimeoutPooledLifetime() {
    	return pool.getNumTimeoutPooledLifetime();
    }
    

	
	
	/**
	 * get the current number of pending get operations to retrieve a resource
	 * 
	 * @return the current number of pending get operations
	 */
	public int getNumPendingGet() {
		return pool.getNumPendingGet();
	}
	
	public void close() {
		pool.close();
	}
	
	public void destroy(IBlockingConnection resource) throws IOException {
		if (resource instanceof BlockingConnection) {
			NonBlockingConnectionPool.destroy(((BlockingConnection) resource).getDelegee());
		}
		resource.close();
	}
}
