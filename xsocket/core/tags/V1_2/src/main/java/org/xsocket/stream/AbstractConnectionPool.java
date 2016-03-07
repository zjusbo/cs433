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
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.ClosedConnectionException;
import org.xsocket.DataConverter;
import org.xsocket.ILifeCycle;
import org.xsocket.MaxReadSizeExceededException;


/**
 * implementation base of a connection pool
 *
 * @author grro@xsocket.org
 */
abstract class AbstractConnectionPool implements IConnectionPool {

	private static final Logger LOG = Logger.getLogger(AbstractConnectionPool.class.getName());

	static final long MIN_CHECKPERIOD_MILLIS = 60L * 1000L;
	static final long NULL = -1;
	static final int MAX_SIZE = Integer.MAX_VALUE;
	static final long MAX_TIMEOUT = Long.MAX_VALUE;

	static final int CREATE_CONNECTION_TIMEOUT = 250;

	private final Timer timer = new Timer("ConPoolWatchdog", true);

	private int maxActive = Integer.MAX_VALUE;
	private int maxIdle = 3;
	private long maxWaitMillis = 0;
	private long idleTimeoutMillis = MAX_TIMEOUT;
	private long lifeTimeoutMillis = MAX_TIMEOUT;

	private boolean isOpen = true;
	private final Map<InetSocketAddress, List<PoolableConnection>> idlePool = new HashMap<InetSocketAddress, List<PoolableConnection>>();
	private final Set<PoolableConnection> activePool = new HashSet<PoolableConnection>();

	private long checkPeriod = 0;
	private TimerTask watchDogTask = null;

	// listeners
	private final List<ILifeCycle> listeners = new ArrayList<ILifeCycle>();


	/**
	 * constructor
	 *
	 * @param timeToIdleMillis  the max idle time in the pool. After this time the free connection will be closed
	 * @param timeToLiveMillis  the max living time of the connection. If a free connection exeeded this time, the connection will be closed (if it is in pool)
	 * @param maxWaitMillis     the max wait time by acquiring a connection from the pool
	 * @param maxIdle           the max number of free connection in the pool
	 */
	AbstractConnectionPool(long idleTimeoutMillis, long lifeTimeoutMillis, int maxActive, long maxWaitMillis, int maxIdle) {
		this.idleTimeoutMillis = idleTimeoutMillis;
		this.lifeTimeoutMillis = lifeTimeoutMillis;
		this.maxActive = maxActive;
		this.maxWaitMillis = maxWaitMillis;
		this.maxIdle = maxIdle;

		resetCheckPeriod();
	}


	/**
	 * {@inheritDoc} <br> <br>
	 *
	 * This method is thread safe
	 */
	public synchronized final void destroyConnection(IConnection connection) throws IOException {
		if (connection == null) {
			return;
		}

		if (connection instanceof PoolableConnection) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("destroying connection " + connection.getId());
			}
			activePool.remove(connection);
			((PoolableConnection) connection).reallyClose();
		} else {
			connection.close();
		}
	}


	/**
	 * adds a listener
	 * @param listener gthe listener to add
	 */
	public void addListener(ILifeCycle listener) {
		listeners.add(listener);
	}

	/**
	 * removes a listener
	 * @param listener the listener to remove
	 * @return true, is the listener has been removed
	 */
	public boolean removeListener(ILifeCycle listener) {
		boolean result = listeners.remove(listener);

		return result;
	}


	private void resetCheckPeriod() {

		if (watchDogTask != null) {
			watchDogTask.cancel();
		}

		watchDogTask = new TimerTask() {
			@Override
			public void run() {
				Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
				List<PoolableConnection> idleConnections = idleConnectionList();
				checkTimeout(idleConnections);
				checkSize(idleConnections);
			}
		};


		long time = MIN_CHECKPERIOD_MILLIS;
		if ((lifeTimeoutMillis / 5) < time) {
			time = (lifeTimeoutMillis / 5);
		}
		if ((idleTimeoutMillis / 5) < time) {
			time = (idleTimeoutMillis / 5);
		}

		checkPeriod = time;
		timer.schedule(watchDogTask, checkPeriod, checkPeriod);
	}


	final long getCheckPeriodMillis() {
		return checkPeriod;
	}

	/**
	 * {@inheritDoc}
	 */
	public final synchronized int getMaxActive() {
        return maxActive;
    }

	/**
	 * {@inheritDoc}
	 */
    public final synchronized void setMaxActive(int maxActive) {
        this.maxActive = maxActive;
        notifyAll();
    }

	/**
	 * {@inheritDoc}
	 */
    public final synchronized long getMaxWaitMillis() {
        return maxWaitMillis;
    }


	/**
	 * {@inheritDoc}
	 */
    public final synchronized void setMaxWaitMillis(long maxWaitMillis) {
        this.maxWaitMillis = maxWaitMillis;
        notifyAll();
    }


	/**
	 * {@inheritDoc}
	 */
    public final synchronized int getMaxIdle() {
        return maxIdle;
    }


	/**
	 * {@inheritDoc}
	 */
    public final synchronized void setMaxIdle(int maxIdle) {
        this.maxIdle = maxIdle;
        notifyAll();
    }


	/**
	 * {@inheritDoc}
	 */
    public final synchronized int getNumActive() {
        return activePool.size();
    }


	/**
	 * {@inheritDoc}
	 */
    public final synchronized int getNumIdle() {
		int size = 0;
		for (List<PoolableConnection> connectionList : idlePool.values()) {
			size += connectionList.size();
		}

		return size;
	}


	/**
	 * {@inheritDoc}
	 */
	public final long getIdleTimeoutMillis() {
		return idleTimeoutMillis;
	}


	/**
	 * {@inheritDoc}
	 */
	public final void setIdleTimeoutMillis(long idleTimeoutMillis) {
		this.idleTimeoutMillis = idleTimeoutMillis;
		resetCheckPeriod();
	}


	/**
	 * {@inheritDoc}
	 */
	public final long getLifeTimeoutMillis() {
		return lifeTimeoutMillis;
	}


	/**
	 * {@inheritDoc}
	 */
	public final void setLifeTimeoutMillis(long lifeTimeoutMillis) {
		this.lifeTimeoutMillis = lifeTimeoutMillis;
		resetCheckPeriod();
	}


	synchronized List<String> getConnectionInfo() {
		List<String> info = new ArrayList<String>();

		for (PoolableConnection activeConnection : activePool) {
			info.add(activeConnection.toString());
		}

		for (PoolableConnection idleConnection : idleConnectionList()) {
			info.add(idleConnection.toString());
		}

		return info;
	}

	/**
	 * get a connection <br> <br>
	 *
	 * This method is thread safe
	 *
	 * @param host       the server address
	 * @param port       the server port
	 * @param workerPool the workerpool or <code>null</code>
	 * @return the connection
	 * @throws WaitTimeoutException if the wait timeout has been reached
	 * @throws IOException if an exception occurs
	 */
	synchronized final PoolableConnection getConnection(InetSocketAddress address, Executor workerPool) throws IOException, WaitTimeoutException {

		// pool is open
		if (isOpen) {
			PoolableConnection poolableConnection = getConnectionFromPool(address);

			if (poolableConnection == null) {

				if (maxWaitMillis == NULL) {
					poolableConnection = newConnection(address, workerPool);
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("new connection to " + poolableConnection.getId() + " has been established (idling=" + getNumIdle() + ", active=" + getNumActive() + ", maxActive=" + getMaxActive() + ", idleTimeoutMillis=" + getIdleTimeoutMillis() + ", lifeTimeout=" + getLifeTimeoutMillis() + ")");
					}
				} else {
					if (activePool.size() < maxActive) {
						poolableConnection = newConnection(address, workerPool);
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("new connection to " + poolableConnection.getId() + " has been established (idling=" + getNumIdle() + ", active=" + getNumActive() + ", maxActive=" + getMaxActive() + ", idleTimeoutMillis=" + getIdleTimeoutMillis() + ", lifeTimeout=" + getLifeTimeoutMillis() + ")");
						}
					} else {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("no free connection available waiting for a free connection (idling=" + getNumIdle() + ", active=" + getNumActive() + ", maxActive=" + getMaxActive() + ", idleTimeoutMillis=" + getIdleTimeoutMillis() + ", lifeTimeout=" + getLifeTimeoutMillis() + ")");
						}

						long start = System.currentTimeMillis();
						while ((System.currentTimeMillis() - start) < maxWaitMillis) {
							try {
								wait(maxWaitMillis - (System.currentTimeMillis() - start));
							} catch (InterruptedException ignore) { }

							poolableConnection = getConnectionFromPool(address);
							if (poolableConnection != null) {
								break;
							}
						}
						if (poolableConnection == null) {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("wait timeout reached (" + DataConverter.toFormatedDuration(maxWaitMillis) + ")");
							}
							throw new WaitTimeoutException("wait timeout reached (" + DataConverter.toFormatedDuration(maxWaitMillis) + ")");
						}
					}
				}
			}

			if (poolableConnection != null) {
				activePool.add(poolableConnection);
				poolableConnection.setStateActive();
			}
			return poolableConnection;


		// pool is already closed
		} else {
			throw new RuntimeException("pool is already closed");
		}
	}


	private PoolableConnection getConnectionFromPool(InetSocketAddress address) throws IOException {

		try {
			List<PoolableConnection> connectionList = idlePool.get(address);

			// connection list found
			if (connectionList != null) {

				// .. and it contains connections
				if (!connectionList.isEmpty()) {
					PoolableConnection poolableConnection = connectionList.remove(0);

					// check if the connection is valid
					if (isConnectionValid(System.currentTimeMillis(), poolableConnection)) {
						poolableConnection.reset();

						// remove connection list if is empty
						if (connectionList.isEmpty()) {
							idlePool.remove(address);
						}

						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("got connection to " + poolableConnection.getId() + " from pool (idling=" + getNumIdle() + ", active=" + getNumActive() + ", maxActive=" + getMaxActive()  + ", idleTimeoutMillis=" + getIdleTimeoutMillis() + ", lifeTimeout=" + getLifeTimeoutMillis() + ")");
						}

						return poolableConnection;

					// .. else call recursive
					} else {
						return getConnectionFromPool(address);
					}
				}
			}
		} catch (Exception e) {
			return null;
		}

		return null;
	}

	/**
	 * get adress as string
	 * @param host  the server address
	 * @param port  the server port
	 * @return the address as string
	 */
	final String getAddressString(String host, int port) {
		return host + ":" + port;
	}


	/**
	 * recycle a connection
	 *
	 * @param connection  the connection to recycle
	 * @throws IOException if an exception occurs
	 */
	final synchronized void returnConnection(PoolableConnection poolableConnection) throws IOException {

		activePool.remove(poolableConnection);
		poolableConnection.setStateIdle();

		// pool is open
		if (isOpen) {

			// if connection is invalid close really and return
			if (!isConnectionValid(System.currentTimeMillis(), poolableConnection)) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("do not return given connection to pool, because it is not valid/reuseable");
				}
				poolableConnection.reallyClose();
				return;

			} else {
				try {
					poolableConnection.reset();
				} catch (Exception e) {
					poolableConnection.reallyClose();
					return;
				}

				addConnectionToPool(poolableConnection);
			}

		// pool is already closed
		} else {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("pool is already closed destroy returned connection to " + poolableConnection.getId());
			}
			poolableConnection.reallyClose();
			return;
		}

		notifyAll();
	}


    private void addConnectionToPool(PoolableConnection connection) throws IOException {
    	if (idlePool.size() < maxIdle) {
			List<PoolableConnection> connectionList = idlePool.get(connection.getAddress());
			if (connectionList == null) {
				connectionList = new ArrayList<PoolableConnection>();
				idlePool.put(connection.getAddress(), connectionList);
			}

			connectionList.add(connection);

			if (LOG.isLoggable(Level.FINER)) {
				LOG.finer("connection to " + connection.getId() + " has been inserted into the pool (idling=" + getNumIdle() + ", active=" + getNumActive() + ", maxActive=" + getMaxActive() + ", idleTimeoutMillis=" + getIdleTimeoutMillis() + ", lifeTimeout=" + getLifeTimeoutMillis() + ")");
			}
    	} else {
    		connection.reallyClose();
    	}
    }


	/**
	 * {@inheritDoc}<br> <br>
	 *
	 * This method is thread safe
	 */
	public final synchronized void close() {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("closing (idling=" + getNumIdle() + ", active=" + getNumActive() + ")");
		}

		timer.cancel();

		for (List<PoolableConnection> connectionList : idlePool.values()) {
			for (PoolableConnection connection : connectionList) {
				try {
					connection.reallyClose();
				} catch (Exception e) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("error occured by (really) closing of conection " + connection.getId() + ". Reason: " + e.toString());
					}
				}
			}
		}

		idlePool.clear();

		for (ILifeCycle lifeCycle : listeners) {
			lifeCycle.onDestroy();
		}
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return this.getClass().getSimpleName() + " idling=" + getNumIdle() + ", active=" + getNumActive() + ", maxActive=" + getMaxActive() + ", idleTimeoutMillis=" + getIdleTimeoutMillis() + ", lifeTimeout=" + getLifeTimeoutMillis() + ")";
	}



	private boolean isConnectionValid(long currentTime, PoolableConnection poolableConnection) {

		if (!poolableConnection.isOpen()) {
			return false;
		}

		if (!poolableConnection.isReuseable()) {
			return false;
		}

		if (idleTimeoutMillis != MAX_TIMEOUT) {
			if (currentTime > (poolableConnection.getLastUsageTime() + idleTimeoutMillis)) {
				return false;
			}
		}

		if (lifeTimeoutMillis != MAX_TIMEOUT) {
			if (currentTime > (poolableConnection.getCreationTime() + lifeTimeoutMillis)) {
				return false;
			}
		}

		return true;
	}



	private void checkTimeout(List<PoolableConnection> idleConnections) {
		long currentTime = System.currentTimeMillis();

		for (PoolableConnection poolableConnection : idleConnections) {

			if (!isConnectionValid(currentTime, poolableConnection)) {
				closeConnection(poolableConnection, "auto");
			}
		}
	}


	private void checkSize(List<PoolableConnection> idleConnections) {
		if (idleConnections.size() > maxIdle) {
			for (int i =0; i < (idleConnections.size() - maxIdle); i++ ) {
				PoolableConnection poolableConnection = idleConnections.get(i);
				closeConnection(poolableConnection, "auto");
			}
		}
	}


	private synchronized List<PoolableConnection> idleConnectionList() {
		List<PoolableConnection> idleConnections = new ArrayList<PoolableConnection>();

		for (List<PoolableConnection> connectionList : idlePool.values()) {
			idleConnections.addAll(connectionList);
		}

		return idleConnections;
	}


	private synchronized boolean closeConnection(PoolableConnection poolableConnection, String reason) {
		assert (poolableConnection != null);

		List<PoolableConnection> connectionList = idlePool.get(poolableConnection.getAddress());
		if (connectionList.contains(poolableConnection)) {
			connectionList.remove(poolableConnection);
			try {
				poolableConnection.reallyClose();
			} catch (Exception e) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by (really) closing of connection " + poolableConnection.getId() + ". Reason: " + e.toString());
				}
			}


			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine(reason + " close of connection " + poolableConnection.getId() + " (idling=" + getNumIdle() + ", active=" + getNumActive() + ", maxActive=" + getMaxActive() + ", idleTimeoutMillis=" + getIdleTimeoutMillis() + ", lifeTimeout=" + getLifeTimeoutMillis() + ")");
			}

			return true;
		} else {
			return false;
		}
	}


	private PoolableConnection newConnection(InetSocketAddress address, Executor workerPool) throws IOException {
		int trials = 0;
		int sleepTime = 3;

		IOException ex = null;

		long start = System.currentTimeMillis();
		PoolableConnection connection = null;
		do {
			trials++;
			try {

				connection = createConnection(address, workerPool);
				return connection;
			} catch (IOException ioe) {
				ex = ioe;
				sleepTime = sleepTime * 3;
			}

			try {
				Thread.sleep(sleepTime);
			} catch (InterruptedException ignore) { }
		} while (System.currentTimeMillis() < (start + CREATE_CONNECTION_TIMEOUT));

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("error occured by creating connection to " + address
				   + ". creation timeout " +  CREATE_CONNECTION_TIMEOUT + " reached. (" + trials + " trials done)");
		}

		if (ex != null) {
			throw ex;
		} else {
			throw new IOException("couldn't create a new connetion to " + address);
		}
	}


	/**
	 * create a new poolable  connection
	 *
	 * @param host        the server address
	 * @param port        the server port
	 * @param workerPool  the worker pool or <code>null</code>
	 * @return the new connection
	 * @throws IOException  if an exception occrs
	 */
	abstract PoolableConnection createConnection(InetSocketAddress address, Executor workerPool) throws IOException;


	/**
	 * implemenation base for a poolable connection
	 *
	 * @author grro
	 */
	abstract class PoolableConnection implements IConnection {


		private AbstractConnectionPool pool = null;
		private InetSocketAddress address = null;
		private Connection delegee;

		private long creationTime = System.currentTimeMillis();
		private long lastUsageTime = System.currentTimeMillis();
		private boolean isActive = false;
		private long enteredState = System.currentTimeMillis();

		private boolean isDeleted = false;
		private boolean isReuseable = true;

		public PoolableConnection(AbstractConnectionPool pool, Connection delegee, InetSocketAddress address) throws IOException {
			this.pool = pool;
			this.delegee = delegee;
			this.address = address;
		}

		final boolean isReuseable() {
			return isReuseable;
		}

		final void setReuseable(boolean isReusable) {
			this.isReuseable = isReusable;
		}

		final void setStateActive() {
			isActive = true;
			enteredState = System.currentTimeMillis();
		}

		final void setStateIdle() {
			isActive = false;
			enteredState = System.currentTimeMillis();
		}

		final boolean isActive() {
			return isActive;
		}

		final long getStateEntered() {
			return enteredState;
		}

		final Connection getDelegee() {
			return delegee;
		}

		final InetSocketAddress getAddress() {
			return address;
		}

		final long getLastUsageTime() {
			return lastUsageTime;
		}

		final long getCreationTime() {
			return creationTime;
		}


		public void close() throws IOException {
			if (delegee.isOpen()) {
				lastUsageTime = System.currentTimeMillis();
				pool.returnConnection(this);
			} else {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("connection " + delegee.getId() + " will not return to pool because it is closed");
				}
			}
		}

		void reset() throws IOException {
			delegee.reset();
		}

		public void setIdleTimeoutSec(int timeoutInSec) {
			getDelegee().setIdleTimeoutSec(timeoutInSec);
		}

		public int getIdleTimeoutSec() {
			return getDelegee().getIdleTimeoutSec();
		}

		public Object getOption(String name) throws IOException {
			return getDelegee().getOption(name);
		}

		public Map<String, Class> getOptions() {
			return getDelegee().getOptions();
		}


		public void setConnectionTimeoutSec(int timeoutSec) {
			getDelegee().setConnectionTimeoutSec(timeoutSec);
		}


		public int getConnectionTimeoutSec() {
			return getDelegee().getConnectionTimeoutSec();
		}

		public void resumeRead() throws IOException{
			getDelegee().resumeRead();
		}

		public void suspendRead() throws IOException {
			getDelegee().suspendRead();
		}

		void reallyClose() throws IOException {
			isDeleted = true;
			delegee.close();
		}

		public void flush() throws ClosedConnectionException, IOException, SocketTimeoutException {
			delegee.flush();
		}

		public boolean isOpen() {
			return !isDeleted && delegee.isOpen();
		}

		public void activateSecuredMode() throws IOException {
			throw new UnsupportedOperationException("activateSecuredMode is not supported for a pooled connection");
		}

		public final int getPendingWriteDataSize() {
			return delegee.getPendingWriteDataSize();
		}


		public boolean getAutoflush() {
			return delegee.getAutoflush();
		}

		public void setAutoflush(boolean autoflush) {
			delegee.setAutoflush(autoflush);
		}

		public void setDefaultEncoding(String encoding) {
			delegee.setDefaultEncoding(encoding);
		}


		public String getDefaultEncoding() {
			return delegee.getDefaultEncoding();
		}

		public int getIndexOf(String str) throws IOException, ClosedConnectionException {
			return delegee.getIndexOf(str);
		}

		public int getIndexOf(String str, int maxLength) throws IOException, ClosedConnectionException, MaxReadSizeExceededException {
			return delegee.getIndexOf(str, maxLength);
		}

		public int getIndexOf(String str, String encoding, int maxLength) throws IOException, ClosedConnectionException, MaxReadSizeExceededException {
			return delegee.getIndexOf(str, encoding, maxLength);
		}

		public int read(ByteBuffer buffer) throws IOException {
			return delegee.read(buffer);
		}

		public byte readByte() throws IOException, ClosedConnectionException, SocketTimeoutException {
			return delegee.readByte();
		}

		public ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException, ClosedConnectionException, SocketTimeoutException {
			return delegee.readByteBufferByDelimiter(delimiter);
		}

		public ByteBuffer[] readByteBufferByDelimiter(String delimiter, int maxLength) throws IOException, ClosedConnectionException, SocketTimeoutException, MaxReadSizeExceededException {
			return delegee.readByteBufferByDelimiter(delimiter, maxLength);
		}

		public ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, ClosedConnectionException, MaxReadSizeExceededException {
			return delegee.readByteBufferByDelimiter(delimiter, encoding, maxLength);
		}

		public ByteBuffer[] readByteBufferByLength(int length) throws IOException, ClosedConnectionException, SocketTimeoutException {
			return delegee.readByteBufferByLength(length);
		}

		public byte[] readBytesByDelimiter(String delimiter) throws IOException, ClosedConnectionException, SocketTimeoutException {
			return delegee.readBytesByDelimiter(delimiter);
		}

		public byte[] readBytesByDelimiter(String delimiter, int maxLength) throws IOException, ClosedConnectionException, SocketTimeoutException, MaxReadSizeExceededException {
			return delegee.readBytesByDelimiter(delimiter, maxLength);
		}

		public byte[] readBytesByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, ClosedConnectionException, MaxReadSizeExceededException {
			return delegee.readBytesByDelimiter(delimiter, encoding, maxLength);
		}

		public byte[] readBytesByLength(int length) throws IOException, ClosedConnectionException, SocketTimeoutException {
			return delegee.readBytesByLength(length);
		}

		public double readDouble() throws IOException, ClosedConnectionException, SocketTimeoutException {
			return delegee.readDouble();
		}

		public int readInt() throws IOException, ClosedConnectionException, SocketTimeoutException {
			return delegee.readInt();
		}

		public short readShort() throws IOException, ClosedConnectionException, SocketTimeoutException {
			return delegee.readShort();
		}

		public long readLong() throws IOException, ClosedConnectionException, SocketTimeoutException {
			return delegee.readLong();
		}

		public String readStringByDelimiter(String delimiter) throws IOException, ClosedConnectionException, UnsupportedEncodingException, SocketTimeoutException {
			return delegee.readStringByDelimiter(delimiter);
		}

		public String readStringByDelimiter(String delimiter, int maxLength) throws IOException, ClosedConnectionException, UnsupportedEncodingException, SocketTimeoutException, MaxReadSizeExceededException {
			return delegee.readStringByDelimiter(delimiter, maxLength);
		}

		public String readStringByDelimiter(String delimiter, String encoding) throws IOException, ClosedConnectionException, UnsupportedEncodingException, SocketTimeoutException {
			return delegee.readStringByDelimiter(delimiter, encoding);
		}

		public String readStringByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, ClosedConnectionException, UnsupportedEncodingException, SocketTimeoutException, MaxReadSizeExceededException {
			return delegee.readStringByDelimiter(delimiter, encoding, maxLength);
		}

		public int indexOf(String str) throws IOException, ClosedConnectionException {
			return delegee.indexOf(str);
		}

		public String readStringByLength(int length) throws IOException, ClosedConnectionException, SocketTimeoutException {
			return delegee.readStringByLength(length);
		}

		public String readStringByLength(int length, String encoding) throws IOException, ClosedConnectionException, SocketTimeoutException {
			return delegee.readStringByLength(length, encoding);
		}

		public void removeReadMark() {
			delegee.removeReadMark();
		}

		public void removeWriteMark() {
			delegee.removeWriteMark();
		}

		public void markReadPosition() {
			delegee.markReadPosition();
		}

		public void markWritePosition() {
			delegee.markWritePosition();
		}

		public boolean resetToReadMark() {
			return delegee.resetToReadMark();
		}

		public boolean resetToWriteMark() {
			return delegee.resetToWriteMark();
		}

		public String getId() {
			return delegee.getId();
		}

		public InetAddress getLocalAddress() {
			return delegee.getLocalAddress();
		}

		public int getLocalPort() {
			return delegee.getLocalPort();
		}

		public InetAddress getRemoteAddress() {
			return delegee.getRemoteAddress();
		}

		public int getRemotePort() {
			return delegee.getRemotePort();
		}

		public int write(byte b) throws ClosedConnectionException, IOException, SocketTimeoutException {
			return delegee.write(b);
		}

		public int write(byte... bytes) throws ClosedConnectionException, IOException, SocketTimeoutException {
			return delegee.write(bytes);
		}

		public int write(short s) throws ClosedConnectionException, IOException, SocketTimeoutException {
			return delegee.write(s);
		}

		public int write(byte[] bytes, int offset, int length) throws ClosedConnectionException, IOException, SocketTimeoutException {
			return delegee.write(bytes, offset, length);
		}

		public int write(ByteBuffer buffer) throws ClosedConnectionException, IOException, SocketTimeoutException {
			return delegee.write(buffer);
		}

		public long write(ByteBuffer[] arg0, int arg1, int arg2) throws IOException {
			return delegee.write(arg0, arg1, arg2);
		}

		public long write(ByteBuffer[] buffers) throws ClosedConnectionException, IOException, SocketTimeoutException {
			return delegee.write(buffers);
		}

		public int write(double d) throws ClosedConnectionException, IOException, SocketTimeoutException {
			return delegee.write(d);
		}

		public int write(int i) throws ClosedConnectionException, IOException, SocketTimeoutException {
			return delegee.write(i);
		}

		public int write(long l) throws ClosedConnectionException, IOException, SocketTimeoutException {
			return delegee.write(l);
		}

		public int write(String message) throws ClosedConnectionException, IOException, SocketTimeoutException {
			return delegee.write(message);
		}

		public int write(String message, String encoding) throws ClosedConnectionException, IOException, SocketTimeoutException {
			return delegee.write(message, encoding);
		}

		public Object attach(Object obj) {
			return delegee.attach(obj);
		}

		public Object attachment() {
			return delegee.attachment();
		}


		@Override
		public String toString() {
			String state = "idle";
			if (isActive()) {
				state = "active";
			}
			return "[" + state + ", since " + DataConverter.toFormatedDuration(System.currentTimeMillis() - getStateEntered()) + "] " + delegee.toString();
		}
	}
}
