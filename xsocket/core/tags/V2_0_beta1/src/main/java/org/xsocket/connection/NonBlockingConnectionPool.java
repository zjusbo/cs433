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
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.xsocket.ClosedException;
import org.xsocket.DataConverter;
import org.xsocket.Execution;
import org.xsocket.ILifeCycle;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.WaitTimeoutException;





/**
 * A connection pool implementation.<br> <br>
 *
 * This class is thread safe <br>
 *
 * <pre>
 *  // create a unbound connection pool
 *  NonBlockingConnectionPool pool = new NonBlockingConnectionPool();
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
public final class NonBlockingConnectionPool implements IConnectionPool {

	private static final Logger LOG = Logger.getLogger(NonBlockingConnectionPool.class.getName());
	
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");

	private static final long WATCHDOG_PERIOD_MILLIS = 30 * 1000L;
	private static final Timer WATCHDOG_TIMER = new Timer("xResourcePoolTimer", true);

	private static final long MIN_REMAINING_MILLIS_TO_IDLE_TIMEOUT = 3 * 1000;
	private static final long MIN_REMAINING_MILLIS_TO_CONNECTION_TIMEOUT = 3 * 1000;

	protected static final long MIN_CHECKPERIOD_MILLIS = 60L * 1000L;
	protected static final int MAX_SIZE = Integer.MAX_VALUE;
	protected static final int MAX_TIMEOUT = Integer.MAX_VALUE;

	private static AtomicInteger nextProxyId = new AtomicInteger();


	// is open flag
	private boolean isOpen = true;


	// settings
	private int maxActive = IConnectionPool.DEFAULT_MAX_ACTIVE;
	private int maxIdle = IConnectionPool.DEFAULT_MAX_IDLE;
	private long maxWaitMillis = IConnectionPool.DEFAULT_MAX_WAIT_MILLIS;
	private int idleTimeoutSec = IConnectionPool.DEFAULT_IDLE_TIMEOUT_SEC;
	private int lifeTimeoutSec = IConnectionPool.DEFAULT_LIFE_TIMEOUT_SEC;


	// ssl support
	private SSLContext sslContext = null;


	// pool management
	private Executor workerpool = NonBlockingConnection.getDefaultWorkerpool();
	private final Pool pool = new Pool();


	// resource retrieve guard
	private final Object retrieveGuard = new Object();


	// listeners
	private final List<ILifeCycle> listeners = new ArrayList<ILifeCycle>();


	// statistics
	private final AtomicInteger countPendingGet  = new AtomicInteger(0);
	private int countCreated = 0;
	private int countDestroyed = 0;
	private int countTimeoutPooledLifetime = 0;
	private int countTimeoutPooledIdle = 0;
	private int countTimeoutConnectionIdle = 0;
	private int countTimeoutConnectionLifetime = 0;
	

	// watch dog
	private TimerTask watchDogTask = new TimerTask() {
		@Override
		public void run() {
			long currentTimeSec = System.currentTimeMillis() / 1000;

			for (PooledConnectionHolder pooledResource : pool.getIdleConnections()) {
				boolean isValid = pooledResource.isValid(currentTimeSec);
				if (!isValid) {
					// check if connection is still idling
					if (pool.getIdleConnection(pooledResource) != null) {
						pooledResource.destroy();
					}
				}
			}
		}
	};




	/**
	 * constructor
	 *
	 */
	public NonBlockingConnectionPool() {
		WATCHDOG_TIMER.schedule(watchDogTask, WATCHDOG_PERIOD_MILLIS, WATCHDOG_PERIOD_MILLIS);
	}

	/**
	 * constructor
	 *
	 * @param sslContext   the ssl context or <code>null</code> if ssl should not be used
	 */
	public NonBlockingConnectionPool(SSLContext sslContext) {
		this();

		this.sslContext = sslContext;
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
	public INonBlockingConnection getNonBlockingConnection(String host, int port) throws IOException, WaitTimeoutException {
		return getConnection(new InetSocketAddress(host, port), null, workerpool, Integer.MAX_VALUE, false);
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
	public INonBlockingConnection getNonBlockingConnection(String host, int port, boolean isSSL) throws IOException, WaitTimeoutException {
		return getConnection(new InetSocketAddress(host, port), null, workerpool, Integer.MAX_VALUE, isSSL);
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
	public INonBlockingConnection getNonBlockingConnection(String host, int port, int connectTimeoutMillis) throws IOException, WaitTimeoutException {
		return getConnection(new InetSocketAddress(host, port), null, workerpool, connectTimeoutMillis, false);
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
	public INonBlockingConnection getNonBlockingConnection(String host, int port, int connectTimeoutMillis, boolean isSSL) throws IOException, WaitTimeoutException {
		return getConnection(new InetSocketAddress(host, port), null, workerpool, connectTimeoutMillis, isSSL);
	}


	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 * a new one will be created
	 *
	 * @param address  the server address
	 * @param port     the sever port
	 * @return the connection
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
	 */
	public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port) throws IOException, WaitTimeoutException {
		return getConnection(new InetSocketAddress(address, port), null, workerpool, Integer.MAX_VALUE, false);
	}


	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 * a new one will be created
	 *
	 * @param address  the server address
	 * @param port     the sever port
	 * @param isSSL    true, if ssl connection
	 * @return the connection
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
	 */
	public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port, boolean isSSL) throws IOException, WaitTimeoutException {
		return getConnection(new InetSocketAddress(address, port), null, workerpool, Integer.MAX_VALUE, isSSL);
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
	public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port, int connectTimeoutMillis) throws IOException, WaitTimeoutException {
		return getConnection(new InetSocketAddress(address, port), null, workerpool, connectTimeoutMillis, false);
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
	public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port, int connectTimeoutMillis, boolean isSSL) throws IOException, WaitTimeoutException {
		return getConnection(new InetSocketAddress(address, port), null, workerpool, connectTimeoutMillis, isSSL);
	}

	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 *  a new one will be created
	 *
	 * @param address     the server address
	 * @param port        the server port
 	 * @param appHandler  the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @return the connection
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
	 */
	public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port, IHandler appHandler) throws IOException, WaitTimeoutException {
		return getConnection(new InetSocketAddress(address, port), appHandler, workerpool, Integer.MAX_VALUE, false);
	}



	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 *  a new one will be created
	 *
	 * @param address     the server address
	 * @param port        the server port
 	 * @param appHandler  the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param isSSL       true, if ssl connection
	 * @return the connection
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
	 */
	public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port, IHandler appHandler, boolean isSSL) throws IOException, WaitTimeoutException {
		return getConnection(new InetSocketAddress(address, port), appHandler, workerpool, Integer.MAX_VALUE, isSSL);
	}



	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 *  a new one will be created
	 *
	 * @param host        the server address
	 * @param port        the server port
 	 * @param appHandler  the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @return the connection
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
	 */
	public INonBlockingConnection getNonBlockingConnection(String host, int port, IHandler appHandler) throws IOException, WaitTimeoutException {
		return getConnection(new InetSocketAddress(host, port), appHandler, workerpool, Integer.MAX_VALUE, false);
	}


	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 *  a new one will be created
	 *
	 * @param host        the server address
	 * @param port        the server port
 	 * @param appHandler  the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param isSSL       true, if ssl connection
	 * @return the connection
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
	 */
	public INonBlockingConnection getNonBlockingConnection(String host, int port, IHandler appHandler, boolean isSSL) throws IOException, WaitTimeoutException {
		return getConnection(new InetSocketAddress(host, port), appHandler, workerpool, Integer.MAX_VALUE, isSSL);
	}


	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 *  a new one will be created
	 *
	 * @param address                the server address
	 * @param port                   the server port
 	 * @param appHandler             the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param connectTimeoutMillis  the connection creation timeout
	 * @return the connection
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
	 */
	public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port, IHandler appHandler, int connectTimeoutMillis) throws IOException, WaitTimeoutException {
		return getConnection(new InetSocketAddress(address, port), appHandler, workerpool, connectTimeoutMillis, false);
	}


	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 *  a new one will be created
	 *
	 * @param address                the server address
	 * @param port                   the server port
 	 * @param appHandler             the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param connectTimeoutMillis  the connection creation timeout
	 * @param isSSL                 true, if ssl connection
	 * @return the connection
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
	 */
	public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port, IHandler appHandler, int connectTimeoutMillis, boolean isSSL) throws IOException, WaitTimeoutException {
		return getConnection(new InetSocketAddress(address, port), appHandler, workerpool, connectTimeoutMillis, isSSL);
	}



	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 * a new one will be created <br> <br>
	 *
	 * This method is thread safe
	 *
	 * @param address               the server address
	 * @param port                  the sever port
 	 * @param appHandler            the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and ConnectionTimeoutHandler)
	 * @param workerPool            the worker pool to use
	 * @param connectTimeoutMillis  the connection creation timeout
	 * @return the connection
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
	 */
	public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port, IHandler appHandler, Executor workerPool, int connectTimeoutMillis) throws IOException, WaitTimeoutException {
		return getConnection(new InetSocketAddress(address, port), appHandler, workerPool, connectTimeoutMillis, false);
	}


	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 * a new one will be created <br> <br>
	 *
	 * This method is thread safe
	 *
	 * @param address               the server address
	 * @param port                  the sever port
 	 * @param appHandler            the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and ConnectionTimeoutHandler)
	 * @param workerPool            the worker pool to use
	 * @param connectTimeoutMillis  the connection creation timeout
	 * @param isSSL                 true, if ssl connection
	 * @return the connection
	 * @throws WaitTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
	 */
	public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port, IHandler appHandler, Executor workerPool, int connectTimeoutMillis, boolean isSSL) throws IOException, WaitTimeoutException {
		return getConnection(new InetSocketAddress(address, port), appHandler, workerPool, connectTimeoutMillis, isSSL);
	}


	private INonBlockingConnection getConnection(InetSocketAddress address, IHandler appHandler, Executor workerPool, int connectTimeoutMillis, boolean isSSL) throws IOException, WaitTimeoutException {

		PooledConnectionHolder pooledConnectionHolder = getPooledConnection(address, workerPool, connectTimeoutMillis, isSSL);
		if (pooledConnectionHolder != null) {
			return new NonBlockingConnectionProxy(pooledConnectionHolder, appHandler);

		} else {
			throw new IOException("could not create a connection to " + address);
		}
	}
	

	
	/**
	 * {@inheritDoc}
	 */
	public boolean isOpen() {
		return isOpen;
	}


	/**
	 * {@inheritDoc}
	 */
	public void addListener(ILifeCycle listener) {
		listeners.add(listener);
	}


	/**
	 * {@inheritDoc}
	 */
	public  boolean removeListener(ILifeCycle listener) {
		boolean result = listeners.remove(listener);

		return result;
	}

	
	/**
	 * set the worker pool which will be assigned to the connections for call back handling
	 * @param workerpool the worker pool
	 */
	public void setWorkerpool(Executor workerpool) {
		this.workerpool = workerpool;
	}
	
	/**
	 * get the worker pool which will be assigned to the connections for call back handling
	 * @return the worker pool
	 */
	public Executor getWorkerpool() {
		return workerpool;
	}
	


	/**
	 * {@inheritDoc}
	 */
	public int getMaxActivePooled() {
        return maxActive;
    }


	/**
	 * {@inheritDoc}
	 */
    public void setMaxActivePooled(int maxActive) {
        synchronized (this) {
            this.maxActive = maxActive;
        	notifyAll();
        }
    }


    /**
	 * {@inheritDoc}
	 */
    public long getCreationMaxWaitMillis() {
        return maxWaitMillis;
    }


    /**
	 * {@inheritDoc}
	 */
    public void setCreationMaxWaitMillis(long maxWaitMillis) {
        synchronized (this) {
            this.maxWaitMillis = maxWaitMillis;
        	notifyAll();
        }
    }


    /**
	 * {@inheritDoc}
	 */
    public int getMaxIdlePooled() {
        return maxIdle;
    }


    /**
	 * {@inheritDoc}
	 */
    public void setMaxIdlePooled(int maxIdle) {
    	synchronized (this) {
    		this.maxIdle = maxIdle;
    		notifyAll();
    	}
    }


    /**
	 * {@inheritDoc}
	 */
    public int getNumPooledActive() {
        return pool.getNumActive();
    }

    
    public List<String> getActiveConnectionInfos() {
    	return pool.getActiveConnectionInfos();
    }
    
    
    public List<String> getIdleConnectionInfos() {
    	return pool.getIdleConnectionInfos();
    }
    

    /**
	 * {@inheritDoc}
	 */
    public int getNumPooledIdle() {
    	return pool.getNumIdle();
	}

    /**
	 * {@inheritDoc}
	 */
    public int getNumCreated() {
    	return countCreated;
    }
    
    /**
	 * {@inheritDoc}
	 */
    public int getNumDestroyed() {
    	return countDestroyed;
    }

    /**
	 * {@inheritDoc}
	 */    
    public int getNumTimeoutConnectionLifetime() {
    	return countTimeoutConnectionLifetime;
    }
    
    /**
	 * {@inheritDoc}
	 */
    public int getNumTimeoutConnectionIdle() {
    	return countTimeoutConnectionIdle;
    }

    /**
	 * {@inheritDoc}
	 */
    public int getNumTimeoutPooledIdle() {
    	return countTimeoutPooledIdle;
    }

    /**
	 * {@inheritDoc}
	 */
    public int getNumTimeoutPooledLifetime() {
    	return countTimeoutPooledLifetime;
    }
    
    
    /**
	 * {@inheritDoc}
	 */
	public int getPooledIdleTimeoutSec() {
		return idleTimeoutSec;
	}


	/**
	 * {@inheritDoc}
	 */
	public void setPooledIdleTimeoutSec(int idleTimeoutSec) {
		this.idleTimeoutSec = idleTimeoutSec;
	}


	/**
	 * {@inheritDoc}
	 */
	public int getPooledLifeTimeoutSec() {
		return lifeTimeoutSec;
	}


	/**
	 * {@inheritDoc}
	 */
	public void setPooledLifeTimeoutSec(int lifeTimeoutSec) {
		this.lifeTimeoutSec = lifeTimeoutSec;
	}




	/**
	 * get the current number of pending get operations to retrieve a resource
	 *
	 * @return the current number of pending get operations
	 */
	public int getNumPendingGet() {
		return countPendingGet.get();
	}



	private PooledConnectionHolder getPooledConnection(InetSocketAddress address, Executor workerPool, int creationTimeoutMillis, boolean isSSL) throws IOException, WaitTimeoutException {

		// pool is open
		if (isOpen) {
			PooledConnectionHolder pooledConnectionHolder = retrievePooledConnection(address, workerPool, creationTimeoutMillis, isSSL);
			if (pooledConnectionHolder != null) {
				return pooledConnectionHolder;
			}


			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("no free resources available waiting max " + DataConverter.toFormatedDuration(maxWaitMillis) + " for a free resource (" + pool.toString() + ", idleTimeoutMillis=" + getPooledIdleTimeoutSec() + ", lifeTimeout=" + getPooledLifeTimeoutSec() + ")");
			}

			countPendingGet.incrementAndGet();
			long start = System.currentTimeMillis();
			while ((System.currentTimeMillis() - start) < maxWaitMillis) {
				long waitTime = maxWaitMillis - (System.currentTimeMillis() - start);
				if (waitTime > 0) {
					synchronized (retrieveGuard) {
						try {
							retrieveGuard.wait(waitTime);
						} catch (InterruptedException ignore) { }
					}
				}
				pooledConnectionHolder = retrievePooledConnection(address, workerPool, creationTimeoutMillis, isSSL);
				if (pooledConnectionHolder != null) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("now got a resource (waiting time " + DataConverter.toFormatedDuration((System.currentTimeMillis() - start)) + ")");
					}
					break;
				}
			}
			countPendingGet.decrementAndGet();

			if (pooledConnectionHolder == null) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("wait timeout reached (" + DataConverter.toFormatedDuration(maxWaitMillis) + ")");
				}
				throw new WaitTimeoutException("wait timeout reached (" + DataConverter.toFormatedDuration(maxWaitMillis) + ")");
			}

			return pooledConnectionHolder;

		// pool is already closed
		} else {
			throw new RuntimeException("pool is already closed");
		}
	}



	/**
	 * synchronized is required to manage max active size
	 */
	private synchronized PooledConnectionHolder retrievePooledConnection(InetSocketAddress address, Executor workerPool, int creationTimeoutMillis, boolean isSSL) throws IOException {
		PooledConnectionHolder pooledConnectionHolder = getConnectionFromPool(address, isSSL);

		// no holder retrieved?
		if (pooledConnectionHolder != null) {
			return pooledConnectionHolder;
		}


		// active size smaller than max size? -> create a new one
		if (pool.getNumActive() < maxActive) {
			return newPooledConnection(address, workerPool, creationTimeoutMillis, isSSL);
		}

		return null;
	}



	private PooledConnectionHolder getConnectionFromPool(InetSocketAddress address, boolean isSSL) throws IOException {

		PooledConnectionHolder pooledConnectionHolder = pool.getIdleConnection(address, isSSL);

		// got a resource?
		if (pooledConnectionHolder != null) {

			// reset resource (incl. check if the resource is valid)
			boolean isValid = pooledConnectionHolder.reset();
			if (isValid) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("got connection from pool (" + pool.toString() + ", idleTimeoutMillis=" + getPooledIdleTimeoutSec() + ", lifeTimeout=" + getPooledLifeTimeoutSec() + "): " + pooledConnectionHolder.getConnection());
				}

				return pooledConnectionHolder;

			// resource is not valid
			} else {
				return getConnectionFromPool(address, isSSL);
			}

		// ... no resource available
		} else {
			return null;
		}
	}




	/**
	 * {@inheritDoc}
	 */
	public static void destroy(INonBlockingConnection connection) throws IOException {
		if (connection == null) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("warning trying to destroy a <null> connection. destroy will be ignored");
			}
			return;
		}

		if (connection instanceof NonBlockingConnectionProxy) {
			((NonBlockingConnectionProxy) connection).destroy();

		} else {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("warning connection is not a pooled connection. closing it anyway");
			}
			connection.close();
		}
	}




	/**
	 * only for test purposes
	 */
	static  boolean isDestroyed(INonBlockingConnection connection) {
		if (connection instanceof NonBlockingConnectionProxy) {
			return ((NonBlockingConnectionProxy) connection).isDestroyed();

		} else {
			return connection.isOpen();
		}
	}
	
	

	/**
	 * {@inheritDoc}
	 */
	public final void close() {
		if (isOpen) {
			isOpen = false;

			watchDogTask.cancel();

			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("closing (idling=" + getNumPooledIdle() + ", active=" + getNumPooledActive() + ")");
			}

			for (PooledConnectionHolder pooledResource : pool.getManagedConnection()) {
				pooledResource.destroy();
			}

			for (ILifeCycle lifeCycle : listeners) {
				try {
					lifeCycle.onDestroy();
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("exception occured by destroying " + lifeCycle + " " + ioe.toString());
					}
				}
			}
		}
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return this.getClass().getSimpleName() + " idling=" + getNumPooledIdle() + ", active=" + getNumPooledActive() + ", maxActive=" + getMaxActivePooled() + ", idleTimeoutMillis=" + getPooledIdleTimeoutSec() + ", lifeTimeout=" + getPooledLifeTimeoutSec() + ")";
	}




	private PooledConnectionHolder newPooledConnection(Object address, Executor workerPool, int creationTimeoutMillis, boolean isSSL) throws IOException {
		int trials = 0;
		int sleepTime = 3;

		IOException ex = null;

		long start = System.currentTimeMillis();
		do {
			trials++;
			try {

				PooledConnectionHolder pooledConnectionHolder = new PooledConnectionHolder();
				NonBlockingConnection pooledConnection = new NonBlockingConnection((InetSocketAddress) address, creationTimeoutMillis, new HashMap<String, Object>(), sslContext, isSSL, pooledConnectionHolder, workerPool);
				pooledConnectionHolder.init(pooledConnection);
				
				countCreated++;
				return pooledConnectionHolder;

			} catch (IOException ioe) {
				ex = ioe;
				sleepTime = sleepTime * 3;
			}

			try {
				Thread.sleep(sleepTime);
			} catch (InterruptedException ignore) { }
		} while (System.currentTimeMillis() < (start + IConnectionPool.DEFAULT_CREATION_TIMEOUT_MILLIS));

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("error occured by creating connection to " + address
				   + ". creation timeout " + IConnectionPool.DEFAULT_CREATION_TIMEOUT_MILLIS + " reached. (" + trials + " trials done)");
		}

		if (ex != null) {
			throw ex;
		} else {
			throw new IOException("could not create a new connetion to " + address);
		}
	}





	@Execution(Execution.NONTHREADED)
	private final class PooledConnectionHolder implements IInternalHandler {

		private int creationTimeSec = (int) (System.currentTimeMillis() / 1000);
		private int lastUsageTimeSec = (int) (System.currentTimeMillis() / 1000);

		private InetSocketAddress address = null;
		private boolean isReusable = true;
		private NonBlockingConnection connection = null;
		private boolean isSSL = false;
		
		private NonBlockingConnectionProxy proxy = null;

		
		// statistics
		private int countUsage = 0; 

		
		
		void init(NonBlockingConnection connection) {
			this.connection = connection;
			
			if (connection.isSecure()) {
				isSSL = true;
			}

			address = new InetSocketAddress(connection.getRemoteAddress(), connection.getRemotePort());

			pool.register(this);
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("pooled connection created (" + pool.toString() + ", idleTimeoutMillis=" + getPooledIdleTimeoutSec() + ", lifeTimeout=" + getPooledLifeTimeoutSec() + "): " + connection);
			}
		}

		
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			// swallow the event
			return true;
		}
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			if (proxy != null) {
				return proxy.onData();
			} else {
				return true;
			}
		}
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			isReusable = false;
			if (proxy != null) {
				return proxy.onDisconnect();
			} else {
				return true;
			}
		}
		
		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
			isReusable = false;
			countTimeoutConnectionLifetime++;
			
			if (proxy != null) {
				return proxy.onConnectionTimeout();
			} else {
				return true;
			}
		}
		
		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
			isReusable = false;
			countTimeoutConnectionIdle++;
			
			if (proxy != null) {
				return proxy.onIdleTimeout();
			} else {
				return true;
			}
		}
		

		void lease(NonBlockingConnectionProxy proxy) {
			this.proxy = proxy;
			if (proxy != null) {
				proxy.onConnect();
			}
			
			countUsage++;
		}
		
		
		int getCountUsage() {
			return countUsage;
		}
		

		int getCreationTimeSec() {
			return creationTimeSec;
		}

		int getLastUsageTimeSec() {
			return lastUsageTimeSec;
		}

		void setReusable(boolean isReusable) {
			this.isReusable = isReusable;
		}

		NonBlockingConnection getConnection() {
			return connection;
		}

		InetSocketAddress getAddress() {
			return address;
		}

		
		boolean isSecure() {
			return isSSL;
		}

		
		void release() {
			if (proxy != null) {
				proxy.onDisconnect();
				proxy = null;
			}

			try {
				lastUsageTimeSec = (int) (System.currentTimeMillis() / 1000);

				// is connection is open?
				if (!connection.isOpen()) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("do not return pooled connection into to pool, because the connection is closed");
					}
					destroy();
					return;
				}


				// reset resource
				boolean isValid = reset();


				// check if connection it still valid
				if (!isValid || (isReusable == false)) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("do not return pooled connection into to pool, because connection is not valid/reuseable");
					}
					destroy();
					return;
				}

				// .. and return it to the pool if max idle size is not reached
				if (pool.getNumIdle() < maxIdle) {
					pool.addIdleConnection(this);
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("connection returned to pool ("+ pool.toString() + ", idleTimeoutMillis=" + getPooledIdleTimeoutSec() + ", lifeTimeout=" + getPooledLifeTimeoutSec() + "): " +  connection);
					}
				} else {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("destroy idling connection because max idle size " + maxIdle + " reached: " + this);
					}
					destroy();
				}


			} catch (Exception e) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by releasing a pooled connection " + e.toString());
				}
			}
		}

		void destroy() {
			if (proxy != null) {
				proxy.onDisconnect();
				proxy = null;
			}
			
			pool.remove(this);

			try {
				connection.close();
				countDestroyed++;
			} catch (Exception e) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by closing connection " + connection + ": " + e.toString());
				}
			}

			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("pooled connection destroyed (" + pool.toString() + ", idleTimeoutMillis=" + getPooledIdleTimeoutSec() + ", lifeTimeout=" + getPooledLifeTimeoutSec() + "): " + connection);
			}
		}


		public boolean reset() {
			int currentTimeSec = (int) (System.currentTimeMillis() / 1000);
			
			boolean isValid = connection.reset() && isValid(currentTimeSec);
			if (isValid) {
				return true;
			} else {
				destroy();
				return false;
			}
		}



		boolean isValid(long currentTimeSec) {

			if (!connection.isOpen()) {
				return false;
			}


			// check connection healthy
			if (connection.getRemainingMillisToIdleTimeout() < MIN_REMAINING_MILLIS_TO_IDLE_TIMEOUT) {
				return false;
			}

			if (connection.getRemainingMillisToConnectionTimeout() < MIN_REMAINING_MILLIS_TO_CONNECTION_TIMEOUT) {
				return false;
			}


			// check pool settings
			if (idleTimeoutSec != MAX_TIMEOUT) {
				if (currentTimeSec > (lastUsageTimeSec + idleTimeoutSec)) {
					countTimeoutPooledIdle++;
					return false;
				}
			}

			if (lifeTimeoutSec != MAX_TIMEOUT) {
				if (currentTimeSec > (creationTimeSec + lifeTimeoutSec)) {
					countTimeoutPooledLifetime++;
					return false;
				}
			}

			return true;
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();

			sb.append(connection.getLocalAddress().toString() + ":" + connection.getLocalPort() + " -> " +
		   			  connection.getRemoteAddress().toString() + ":" + connection.getRemotePort() + " " +
		   			  "[" + connection.getId() + "]");
			
		
			sb.append(" countUsage=" + getCountUsage() + 
					  ", creationTime=" + DATE_FORMAT.format((long) getCreationTimeSec() * 1000) + 
					  ", lastUsageTime=" + DATE_FORMAT.format((long) getLastUsageTimeSec() * 1000));

			return sb.toString();
		}
	}





	private static final class Pool {
		private final HashMap<InetSocketAddress, List<PooledConnectionHolder>> idlePool = new HashMap<InetSocketAddress, List<PooledConnectionHolder>>();
		private final HashSet<PooledConnectionHolder> managedPool = new HashSet<PooledConnectionHolder>();


	    public synchronized int getSize() {
	        return managedPool.size();
	    }


	    public synchronized int getNumIdle() {
			int size = 0;
			for (List<PooledConnectionHolder> pooledResources : idlePool.values()) {
				size += pooledResources.size();
			}

			return size;
	    }


	    public synchronized int getNumActive() {
			return getSize() - getNumIdle();
	    }
	    
	    
	    @SuppressWarnings("unchecked")
		public synchronized List<String> getActiveConnectionInfos() {
	    	List<String> result = new ArrayList<String>();
	    	
	    	HashSet<PooledConnectionHolder> managedPoolCopy = (HashSet<PooledConnectionHolder>) managedPool.clone();
	    	managedPoolCopy.removeAll(getIdleConnections());
	        for (PooledConnectionHolder connectionHolder : managedPoolCopy) {
        		 result.add(connectionHolder.toString());
	        }
	        
	        return result;
	    }
	    
  
	    
	    public synchronized List<String> getIdleConnectionInfos() {
	    	List<String> result = new ArrayList<String>();
	    	
	        for (PooledConnectionHolder connectionHolder : getIdleConnections()) {
        		 result.add(connectionHolder.toString());
	        }
	        
	        return result;
	    }
	    
	  
	  
	   

	    @SuppressWarnings("unchecked")
		public synchronized Set<PooledConnectionHolder> getManagedConnection() {
	    	return (Set<PooledConnectionHolder>) managedPool.clone();
	    }


	    public synchronized Set<PooledConnectionHolder> getIdleConnections() {
	    	Set<PooledConnectionHolder> idleResources = new HashSet<PooledConnectionHolder>();
	    	for (List<PooledConnectionHolder> pooledResources : idlePool.values()) {
				for (PooledConnectionHolder pooledResource : pooledResources) {
					idleResources.add(pooledResource);
				}
			}

	    	return idleResources;
	    }


	    public synchronized boolean remove(PooledConnectionHolder pooledResource) {
	    	boolean isRemoved = false;

	    	managedPool.remove(pooledResource);

	    	List<PooledConnectionHolder> pooledResources = idlePool.get(pooledResource.getAddress());
	    	if (pooledResources != null) {
		    	isRemoved = pooledResources.remove(pooledResource);
				if (pooledResources.isEmpty()) {
					idlePool.remove(pooledResource.getAddress());
				}
	    	}

	    	return isRemoved;
	    }


	    public synchronized void register(PooledConnectionHolder pooledResource) {
	    	managedPool.add(pooledResource);
	    }


	    public synchronized PooledConnectionHolder getIdleConnection(InetSocketAddress address, boolean isSSL) {
			List<PooledConnectionHolder> pooledResources = idlePool.get(address);

			if (pooledResources == null) {
				return null;

			} else {

				PooledConnectionHolder result = null;
				for (PooledConnectionHolder pooledResource : pooledResources) {
					if (pooledResource.isSSL == isSSL) {
						result = pooledResource;
						break;
					}
				}
				pooledResources.remove(result);
				if (pooledResources.isEmpty()) {
					idlePool.remove(address);
				}

				return result;
			}
	    }

	    public synchronized PooledConnectionHolder getIdleConnection(PooledConnectionHolder pooledResource) {
			List<PooledConnectionHolder> pooledResources = idlePool.get(pooledResource.getAddress());

			if (pooledResources != null) {
				boolean isRemoved = pooledResources.remove(pooledResource);
				if (pooledResources.isEmpty()) {
					idlePool.remove(pooledResource.getAddress());
				}

				if (isRemoved) {
					return pooledResource;
				}
			}

			return null;
	    }


	    public synchronized void addIdleConnection(PooledConnectionHolder pooledResource) {
	    	managedPool.add(pooledResource);

	    	List<PooledConnectionHolder> pooledResources = idlePool.get(pooledResource.getAddress());
	    	if (pooledResources == null) {
	    		pooledResources = new ArrayList<PooledConnectionHolder>();
				idlePool.put(pooledResource.getAddress(), pooledResources);
			}

	    	pooledResources.add(pooledResource);
    	}



	    @Override
	    public String toString() {
	    	return "size=" + getSize() + ", active=" + getNumActive();
	    }
	}

	
	


	private final class NonBlockingConnectionProxy implements INonBlockingConnection {

		private PooledConnectionHolder pooledConnectionHolder = null;
		private String id = null;
		private boolean isProxyOpen = true;

		private IHandler handler = null;

		
		// timeout support
		private boolean idleTimeoutOccured = false;
		private boolean connectionTimeoutOccured = false;
		private boolean disconnectOccured = false;

		
		
		NonBlockingConnectionProxy(PooledConnectionHolder pooledConnectionHolder, IHandler appHandler) throws IOException {
			this.pooledConnectionHolder = pooledConnectionHolder;
			id = pooledConnectionHolder.getConnection().getId() + "." + Integer.toHexString(nextProxyId.getAndIncrement());
			
			setHandler(appHandler);
			pooledConnectionHolder.lease(this); // onConnect will be called implicit
		}


		public void setHandler(IHandler hdl) {
			handler = HandlerProxy.newPrototype(hdl, null).newProxy(this);
			if (!pooledConnectionHolder.getConnection().isReadBufferEmpty()) {
				onData();
			}
		}
		
		boolean isDestroyed() {
			return !pooledConnectionHolder.getConnection().isOpen();
		}

		
		public boolean isOpen() {
			if (isProxyOpen) {
				return pooledConnectionHolder.getConnection().isOpen();
			} else {
				return false;
			}
		}

		

		public void close() throws IOException {
			if (isProxyOpen) {
				isProxyOpen = false;

				// release resource
				pooledConnectionHolder.release();   // onDisconnect will be called implicit 

				// ... and notify waiting consumer which tries to get a connection
				synchronized (retrieveGuard) {
					retrieveGuard.notifyAll();
				}

			// resource pool is already closed
			} else {
				destroy();
				return;
			}
		}



		void destroy() {

			if (isProxyOpen) {
				isProxyOpen = false;

				try {
					// notify handler
					onDisconnect();

					// release resource
					pooledConnectionHolder.destroy();

					// ... and notify waiting consumer which tries to get a connection
					synchronized (retrieveGuard) {
						retrieveGuard.notifyAll();
					}


				} catch (Exception e) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("error occured while destroying pooledConnectionHolder " + pooledConnectionHolder + " reason: " + e.toString());
					}

				}
			}
		}



		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();

			sb.append(getId());
			if (!isOpen()) {
				sb.append(" closed");
			}

			sb.append(" (" + pooledConnectionHolder.getAddress() + ") ");

			sb.append(" (proxy ");
			if (!isProxyOpen) {
				sb.append("closed ");
			}
			sb.append("creationTime=" + pooledConnectionHolder.getCreationTimeSec() + 
					  ", lastUsageTime=" + pooledConnectionHolder.getCreationTimeSec() + 
					  ", countUsage=" + pooledConnectionHolder.getCountUsage() + ")");

			return sb.toString();
		}

		public String getId() {
			return id;
		}

		
		private boolean onConnect() {
			try {
				return ((IConnectHandler) handler).onConnect(this);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("Error occured by perform onConnect callback on " + handler + " " + ioe.toString());
				}
				return false;
			}
		}
		

		private boolean onDisconnect() {
			if (!disconnectOccured) {
				disconnectOccured = true;
				try {
					return ((IDisconnectHandler) handler).onDisconnect(this);
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("Error occured by perform onDisconnect callback on " + handler + " " + ioe.toString());
					}
					return false;
				}

			} else {
				return true;
			}
		}
		
		
		private boolean onData() {
			try {
				return ((IDataHandler) handler).onData(this);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("Error occured by perform onData callback on " + handler + " " + ioe.toString());
				}
				return false;
			}

		}

		
		private boolean onConnectionTimeout() {
			if (!connectionTimeoutOccured) {
				connectionTimeoutOccured = true;
				try {
					return ((IConnectionTimeoutHandler) handler).onConnectionTimeout(this);
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("Error occured by perform onConnectTimeout callback on " + handler + " " + ioe.toString());
					}
					return false;
				}

			} else {
				setConnectionTimeoutMillis(IConnection.MAX_TIMEOUT_MILLIS);
				return true;
			}
		}

		
		private boolean onIdleTimeout() {
			if (!idleTimeoutOccured) {
				idleTimeoutOccured = true;
				try {
					return ((IIdleTimeoutHandler) handler).onIdleTimeout(this);
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("Error occured by perform onIdleTimeout callback on " + handler + " " + ioe.toString());
					}
					return false;
				}
			} else {
				setIdleTimeoutMillis(IConnection.MAX_TIMEOUT_MILLIS);
				return true;
			}
		}
		
		
		public Executor getWorkerpool() {
			return pooledConnectionHolder.getConnection().getWorkerpool();
		}


		void setReusable(boolean isReusable) {
			pooledConnectionHolder.setReusable(isReusable);
		}


		public void setAttachment(Object obj) {
			pooledConnectionHolder.getConnection().setAttachment(obj);
		}

		public Object getAttachment() {
			return pooledConnectionHolder.getConnection().getAttachment();
		}

		public void setAutoflush(boolean autoflush) {
			pooledConnectionHolder.getConnection().setAutoflush(autoflush);
		}

		public boolean isAutoflush() {
			return pooledConnectionHolder.getConnection().isAutoflush();
		}

		public void setEncoding(String defaultEncoding) {
			pooledConnectionHolder.getConnection().setEncoding(defaultEncoding);
		}

		public String getEncoding() {
			return pooledConnectionHolder.getConnection().getEncoding();
		}

		public void setFlushmode(FlushMode flushMode) {
			pooledConnectionHolder.getConnection().setFlushmode(flushMode);
		}

		public FlushMode getFlushmode() {
			return pooledConnectionHolder.getConnection().getFlushmode();
		}

		public void setOption(String name, Object value) throws IOException {
			if (isProxyOpen) {
				pooledConnectionHolder.getConnection().setOption(name, value);
			} else {
				throw new ClosedException("connection (proxy) " + getId() + " is closed");
			}
		}

		public Object getOption(String name) throws IOException {
			return pooledConnectionHolder.getConnection().getOption(name);
		}

		@SuppressWarnings("unchecked")
		public Map<String, Class> getOptions() {
			return pooledConnectionHolder.getConnection().getOptions();
		}

		public void setWriteTransferRate(int bytesPerSecond) throws ClosedException, IOException {
			if (isOpen()) {
				pooledConnectionHolder.getConnection().setWriteTransferRate(bytesPerSecond);
			} else {
				throw new ClosedException("connection (proxy) " + getId() + " is closed");
			}
		}

		public void setConnectionTimeoutMillis(long timeoutMillis) {
			if (isProxyOpen) {
				pooledConnectionHolder.getConnection().setConnectionTimeoutMillis(timeoutMillis);
				connectionTimeoutOccured = false;
			} else {
				throw new RuntimeException("connection (proxy) " + getId() + " is closed");
			}
		}

		public long getConnectionTimeoutMillis() {
			return pooledConnectionHolder.getConnection().getConnectionTimeoutMillis();
		}

		public void setIdleTimeoutMillis(long timeoutInMillis) {
			if (isProxyOpen) {
				pooledConnectionHolder.getConnection().setIdleTimeoutMillis(timeoutInMillis);
				idleTimeoutOccured = false;
			} else {
				throw new RuntimeException("connection (proxy) " + getId() + " is closed");
			}
		}

		public long getIdleTimeoutMillis() {
			return pooledConnectionHolder.getConnection().getIdleTimeoutMillis();
		}

		public long getRemainingMillisToConnectionTimeout() {
			return pooledConnectionHolder.getConnection().getConnectionTimeoutMillis();
		}

		public long getRemainingMillisToIdleTimeout() {
			return pooledConnectionHolder.getConnection().getRemainingMillisToIdleTimeout();
		}

		public boolean isSecure() {
			return pooledConnectionHolder.getConnection().isSecure();
		}

		public void activateSecuredMode() throws IOException {
			if (isProxyOpen) {
				pooledConnectionHolder.getConnection().activateSecuredMode();
				setReusable(false);
			} else {
				throw new ClosedException("connection (proxy) " + getId() + " is closed");
			}
		}

		public void suspendRead() throws IOException {
			if (isProxyOpen) {
				pooledConnectionHolder.getConnection().suspendRead();
			} else {
				throw new ClosedException("connection (proxy) " + getId() + " is closed");
			}
		}

		public void resumeRead() throws IOException {
			if (isProxyOpen) {
				pooledConnectionHolder.getConnection().resumeRead();
			} else {
				throw new ClosedException("connection (proxy) " + getId() + " is closed");
			}
		}

		public InetAddress getLocalAddress() {
			return pooledConnectionHolder.getConnection().getLocalAddress();
		}

		public int getLocalPort() {
			return pooledConnectionHolder.getConnection().getLocalPort();
		}

		public InetAddress getRemoteAddress() {
			return pooledConnectionHolder.getConnection().getRemoteAddress();
		}

		public int getRemotePort() {
			return pooledConnectionHolder.getConnection().getRemotePort();
		}


		public int write(byte b) throws IOException, BufferOverflowException {
			if (isProxyOpen) {
				return pooledConnectionHolder.getConnection().write(b);
			} else {
				throw new ClosedException("connection (proxy) " + getId() + " is closed");
			}
		}

		public int write(byte... bytes) throws IOException, BufferOverflowException {
			if (isProxyOpen) {
				return pooledConnectionHolder.getConnection().write(bytes);
			} else {
				throw new ClosedException("connection (proxy) " + getId() + " is closed");
			}
		}

		public int write(byte[] bytes, int offset, int length) throws IOException, BufferOverflowException {
			if (isProxyOpen) {
				return pooledConnectionHolder.getConnection().write(bytes, offset, length);
			} else {
				throw new ClosedException("connection (proxy) " + getId() + " is closed");
			}
		}

		public int write(ByteBuffer buffer) throws IOException, BufferOverflowException {
			if (isProxyOpen) {
				return pooledConnectionHolder.getConnection().write(buffer);
			} else {
				throw new ClosedException("connection (proxy) " + getId() + " is closed");
			}
		}

		public long write(ByteBuffer[] buffers) throws IOException, BufferOverflowException {
			if (isProxyOpen) {
				return pooledConnectionHolder.getConnection().write(buffers);
			} else {
				throw new ClosedException("connection (proxy) " + getId() + " is closed");
			}
		}

		public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
			if (isProxyOpen) {
				return pooledConnectionHolder.getConnection().write(srcs, offset, length);
			} else {
				throw new ClosedException("connection (proxy) " + getId() + " is closed");
			}
		}

		public int write(double d) throws IOException, BufferOverflowException {
			if (isProxyOpen) {
				return pooledConnectionHolder.getConnection().write(d);
			} else {
				throw new ClosedException("connection (proxy) " + getId() + " is closed");
			}
		}

		public int write(int i) throws IOException, BufferOverflowException {
			if (isProxyOpen) {
				return pooledConnectionHolder.getConnection().write(i);
			} else {
				throw new ClosedException("connection (proxy) " + getId() + " is closed");
			}
		}

		public long write(List<ByteBuffer> buffers) throws IOException, BufferOverflowException {
			if (isProxyOpen) {
				return pooledConnectionHolder.getConnection().write(buffers);
			} else {
				throw new ClosedException("connection (proxy) " + getId() + " is closed");
			}
		}

		public int write(long l) throws IOException, BufferOverflowException {
			if (isProxyOpen) {
				return pooledConnectionHolder.getConnection().write(l);
			} else {
				throw new ClosedException("connection (proxy) " + getId() + " is closed");
			}
		}

		public int write(short s) throws IOException, BufferOverflowException {
			if (isProxyOpen) {
				return pooledConnectionHolder.getConnection().write(s);
			} else {
				throw new ClosedException("connection (proxy) " + getId() + " is closed");
			}
		}

		public int write(String message) throws IOException, BufferOverflowException {
			if (isProxyOpen) {
				return pooledConnectionHolder.getConnection().write(message);
			} else {
				throw new ClosedException("connection (proxy) " + getId() + " is closed");
			}
		}

		public int write(String message, String encoding) throws IOException, BufferOverflowException {
			if (isProxyOpen) {
				return pooledConnectionHolder.getConnection().write(message, encoding);
			} else {
				throw new ClosedException("connection (proxy) " + getId() + " is closed");
			}
		}

		public void flush() throws ClosedException, IOException, SocketTimeoutException {
			if (isProxyOpen) {
				pooledConnectionHolder.getConnection().flush();
			} else {
				throw new ClosedException("connection (proxy) " + getId() + " is closed");
			}
		}

		public long transferFrom(FileChannel source) throws IOException, BufferOverflowException {
			if (isProxyOpen) {
				return pooledConnectionHolder.getConnection().transferFrom(source);
			} else {
				throw new ClosedException("connection (proxy) " + getId() + " is closed");
			}
		}

		public long transferFrom(ReadableByteChannel source) throws IOException, BufferOverflowException {
			if (isProxyOpen) {
				return pooledConnectionHolder.getConnection().transferFrom(source);
			} else {
				throw new ClosedException("connection (proxy) " + getId() + " is closed");
			}
		}

		public long transferFrom(ReadableByteChannel source, int chunkSize) throws IOException, BufferOverflowException {
			if (isProxyOpen) {
				return pooledConnectionHolder.getConnection().transferFrom(source, chunkSize);
			} else {
				throw new ClosedException("connection (proxy) " + getId() + " is closed");
			}
		}

		public int getPendingWriteDataSize() {
			return pooledConnectionHolder.getConnection().getPendingWriteDataSize();
		}

		public void markWritePosition() {
			if (isProxyOpen) {
				pooledConnectionHolder.getConnection().markWritePosition();
			} else {
				throw new RuntimeException("connection (proxy) " + getId() + " is closed");
			}
		}

		public void removeWriteMark() {
			if (isProxyOpen) {
				pooledConnectionHolder.getConnection().removeWriteMark();
			} else {
				throw new RuntimeException("connection (proxy) " + getId() + " is closed");
			}
		}

		public boolean resetToWriteMark() {
			if (isProxyOpen) {
				return pooledConnectionHolder.getConnection().resetToWriteMark();
			} else {
				throw new RuntimeException("connection (proxy) " + getId() + " is closed");
			}
		}

		public void markReadPosition() {
			pooledConnectionHolder.getConnection().markReadPosition();
		}

		public void removeReadMark() {
			pooledConnectionHolder.getConnection().removeReadMark();
		}

		public boolean resetToReadMark() {
			return pooledConnectionHolder.getConnection().resetToReadMark();
		}
		
	
		public int available() throws IOException {
			return pooledConnectionHolder.getConnection().available();
		}

				
		public int getReadBufferVersion() throws IOException {
			return pooledConnectionHolder.getConnection().getReadBufferVersion();
		}

		public int indexOf(String str) throws IOException {
			return pooledConnectionHolder.getConnection().indexOf(str);
		}

		public int indexOf(String str, String encoding) throws IOException {
			return pooledConnectionHolder.getConnection().indexOf(str, encoding);
		}

		public int read(ByteBuffer buffer) throws IOException {
			return pooledConnectionHolder.getConnection().read(buffer);
		}

		public byte readByte() throws IOException, BufferUnderflowException {
			return pooledConnectionHolder.getConnection().readByte();
		}

		public ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException, BufferUnderflowException {
			return pooledConnectionHolder.getConnection().readByteBufferByDelimiter(delimiter);
		}

		public ByteBuffer[] readByteBufferByDelimiter(String delimiter, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			return pooledConnectionHolder.getConnection().readByteBufferByDelimiter(delimiter, maxLength);
		}

		public ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException {
			return pooledConnectionHolder.getConnection().readByteBufferByDelimiter(delimiter, encoding);
		}

		public ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			return pooledConnectionHolder.getConnection().readByteBufferByDelimiter(delimiter, encoding, maxLength);
		}

		public ByteBuffer[] readByteBufferByLength(int length) throws IOException, BufferUnderflowException {
			return pooledConnectionHolder.getConnection().readByteBufferByLength(length);
		}

		public byte[] readBytesByDelimiter(String delimiter) throws IOException, BufferUnderflowException {
			return pooledConnectionHolder.getConnection().readBytesByDelimiter(delimiter);
		}

		public byte[] readBytesByDelimiter(String delimiter, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			return pooledConnectionHolder.getConnection().readBytesByDelimiter(delimiter, maxLength);
		}

		public byte[] readBytesByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException {
			return pooledConnectionHolder.getConnection().readBytesByDelimiter(delimiter, encoding);
		}

		public byte[] readBytesByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			return pooledConnectionHolder.getConnection().readBytesByDelimiter(delimiter, encoding, maxLength);
		}

		public byte[] readBytesByLength(int length) throws IOException, BufferUnderflowException {
			return pooledConnectionHolder.getConnection().readBytesByLength(length);
		}

		public double readDouble() throws IOException, BufferUnderflowException {
			return pooledConnectionHolder.getConnection().readDouble();
		}

		public int readInt() throws IOException, BufferUnderflowException {
			return pooledConnectionHolder.getConnection().readInt();
		}

		public long readLong() throws IOException, BufferUnderflowException {
			return pooledConnectionHolder.getConnection().readLong();
		}

		public short readShort() throws IOException, BufferUnderflowException {
			return pooledConnectionHolder.getConnection().readShort();
		}

		public String readStringByDelimiter(String delimiter) throws IOException, BufferUnderflowException, UnsupportedEncodingException {
			return pooledConnectionHolder.getConnection().readStringByDelimiter(delimiter);
		}

		public String readStringByDelimiter(String delimiter, int maxLength) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException {
			return pooledConnectionHolder.getConnection().readStringByDelimiter(delimiter, maxLength);
		}

		public String readStringByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException {
			return pooledConnectionHolder.getConnection().readStringByDelimiter(delimiter, encoding);
		}

		public String readStringByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException {
			return pooledConnectionHolder.getConnection().readStringByDelimiter(delimiter, encoding, maxLength);
		}

		public String readStringByLength(int length) throws IOException, BufferUnderflowException, UnsupportedEncodingException {
			return pooledConnectionHolder.getConnection().readStringByLength(length);
		}

		public String readStringByLength(int length, String encoding) throws IOException, BufferUnderflowException, UnsupportedEncodingException {
			return pooledConnectionHolder.getConnection().readStringByLength(length, encoding);
		}

		public long transferTo(WritableByteChannel target, int length) throws IOException, BufferUnderflowException {
			return pooledConnectionHolder.getConnection().transferTo(target, length);
		}
	}
}
