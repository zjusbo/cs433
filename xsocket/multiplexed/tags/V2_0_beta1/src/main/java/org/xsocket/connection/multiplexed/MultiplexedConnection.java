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
package org.xsocket.connection.multiplexed;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.AbstractNonBlockingStream;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IConnection;
import org.xsocket.connection.IConnectionTimeoutHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.IDisconnectHandler;
import org.xsocket.connection.IHandler;
import org.xsocket.connection.IIdleTimeoutHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.multiplexed.multiplexer.DefaultMultiplexer;
import org.xsocket.connection.multiplexed.multiplexer.IMultiplexer;
import org.xsocket.connection.multiplexed.multiplexer.IMultiplexer.IDemultiplexResultHandler;




/**
 * Implementation of the {@link IMultiplexedConnection} 
 * 
 * @author grro@xsocket.org
 */
public final class MultiplexedConnection implements IMultiplexedConnection {

	private static final Logger LOG = Logger.getLogger(MultiplexedConnection.class.getName());

	// timer
	private static final Timer TIMER = new Timer("xPipelineTimer", true); 

	private static final long MIN_WATCHDOG_PERIOD_MILLIS = 30 * 1000;
	
	// pipelines
	private final HashMap<String, NonBlockingPipeline> pipelines = new HashMap<String, NonBlockingPipeline>();

	
	// underlying connection
	private INonBlockingConnection connection = null;

	
	// multiplexer
	private IMultiplexer multiplexer = null;
	private final Object multiplexerWriteGuard = new Object();
	private final Object multiplexerReadGuard = new Object();

	// demultiplex result handler
	private final DemultiplexResultHandler demultiplexResultHandler = new DemultiplexResultHandler();
	
	
	// handler adapter
	private PipelineHandlerAdapter handlerAdapter = PipelineHandlerAdapter.newInstance(null);
	
	
	
	/**
	 * constructor. The {@link DefaultMultiplexer} class will be used to (de)multiplex the data  
	 * 
	 * @param connection   the underlying connection
	 * @throws IOException  if an exception occurs
	 */
	public MultiplexedConnection(INonBlockingConnection connection) throws IOException {
		this(connection, new DefaultMultiplexer());
	}

	
	/**
	 * constructor.
	 * 
	 * @param connection   the underlying connection
	 * @param multiplexer  the multiplexer to use
	 * @throws IOException  if an exception occurs
	 */
	public MultiplexedConnection(INonBlockingConnection connection, IMultiplexer multiplexer) throws IOException {
		this(connection, PipelineHandlerAdapter.newInstance(null), multiplexer);
	}


	/**
	 * constructor. 
	 * 
	 * @param connection       the underlying connection
	 * @param pipelineHandler  the pipeline handler
	 * @param multiplexer  the multiplexer to use
	 * @throws IOException  if an exception occurs
	 */
	public MultiplexedConnection(INonBlockingConnection connection, IHandler pipelineHandler, IMultiplexer multiplexer) throws IOException {
		this(connection, PipelineHandlerAdapter.newInstance(pipelineHandler), multiplexer);
	}
	
	
	/**
	 * constructor. The {@link DefaultMultiplexer} class will be used to (de)multiplex the data
	 *  
	 * @param connection       the underlying connection
	 * @param pipelineHandler  the pipeline handler
	 * @throws IOException  if an exception occurs
	 */
	public MultiplexedConnection(INonBlockingConnection connection, IHandler pipelineHandler) throws IOException {
		this(connection, PipelineHandlerAdapter.newInstance(pipelineHandler), new DefaultMultiplexer());
	}
	
	
	/**
	 * internal constructor  
	 * 
	 */
	MultiplexedConnection(INonBlockingConnection connection, PipelineHandlerAdapter handlerAdapter, IMultiplexer multiplexer) throws IOException {
		this.connection = connection;
		this.multiplexer = multiplexer;		
		this.handlerAdapter = handlerAdapter;
	
		connection.setAutoflush(false);
		connection.setFlushmode(FlushMode.SYNC);
	
		connection.setHandler(new MultiplexedConnectionHandler(this));
	}


	
	/**
	 * {@inheritDoc}
	 */
	public boolean isOpen() {
		return connection.isOpen();
	}
	

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public void close() throws IOException {
		
		HashMap<String, NonBlockingPipeline> copy = null;
		synchronized (pipelines) {
			copy = (HashMap<String, NonBlockingPipeline>) pipelines.clone();
		}
		
		for (NonBlockingPipeline pipeline : copy.values()) {
			pipeline.closeSilence();
		}

		try {
			connection.close();
		} catch (Exception e) { 
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + getId() + "] error occured by closing connection " +e.toString());
			}
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public String getId() {
		return connection.getId();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void activateSecuredMode() throws IOException {
		synchronized (pipelines) {
			for (NonBlockingPipeline pipline : pipelines.values()) {
				pipline.flush();
			}
		}
		
		connection.activateSecuredMode();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public boolean isSecure() {
		return connection.isSecure();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public String getDefaultEncoding() {
		return connection.getEncoding();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void setDefaultEncoding(String encoding) {
		connection.setEncoding(encoding);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public InetAddress getLocalAddress() {
		return connection.getLocalAddress();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getLocalPort() {
		return connection.getLocalPort();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public InetAddress getRemoteAddress() {
		return connection.getRemoteAddress();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getRemotePort() {
		return connection.getRemotePort();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public Object getOption(String name) throws IOException {
		return connection.getOption(name);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Class> getOptions() {
		return connection.getOptions();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void setOption(String name, Object value) throws IOException {
		connection.setOption(name, value);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void setConnectionTimeoutMillis(long timeoutMillis) {
		connection.setConnectionTimeoutMillis(timeoutMillis);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public long getConnectionTimeoutMillis() {
		return connection.getConnectionTimeoutMillis();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void setIdleTimeoutMillis(long timeoutMillis) {
		connection.setIdleTimeoutMillis(timeoutMillis);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public long getIdleTimeoutMillis() {
		return connection.getIdleTimeoutMillis();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public long getRemainingMillisToConnectionTimeout() {
		return connection.getRemainingMillisToConnectionTimeout();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public long getRemainingMillisToIdleTimeout() {
		return connection.getRemainingMillisToIdleTimeout();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void setAttachment(Object obj) {
		connection.setAttachment(obj);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public Object getAttachment() {
		return connection.getAttachment();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public String createPipeline() throws IOException {
		String pipelineId = registerNewPipeline();

		NonBlockingPipeline pipeline = new NonBlockingPipeline(pipelineId, handlerAdapter);
		synchronized (pipelines) {
			pipelines.put(pipelineId, pipeline);
		}
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + getId() + "] pipeline " + pipelineId + " created");
		}
		
		return pipelineId;
	}
	

	/**
	 * {@inheritDoc}
	 */
	public String[] listOpenPipelines() throws ClosedChannelException {
		String[] result = null;
		
		synchronized (pipelines) {
			Set<String> ids = pipelines.keySet();
			result = ids.toArray(new String[ids.size()]);
		}
		
		return result;
	}
	
	
	private void closePipeline(NonBlockingPipeline pipeline) throws IOException {
		
		if (pipeline == null) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("warning try to close a <null> pipeline");
			}
			return;
		}

		deregisterPipeline(pipeline.getId());
		removePipeline(pipeline);
	}

	
	private void removePipeline(NonBlockingPipeline pipeline) {
		
		NonBlockingPipeline pipe = null;
		synchronized (pipelines) {
			pipe = pipelines.remove(pipeline.getId());
		}
		
		if (pipe != null) {
			pipeline.onClose();
		
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + getId() + "] pipeline " + pipeline.getId() + " destroyed");
			}
		}
	}
	
	

	/**
	 * {@inheritDoc}
	 */	
	public INonBlockingPipeline getNonBlockingPipeline(String pipelineId) throws ClosedChannelException {
		if (connection.isOpen()) {
			synchronized (pipelines) {
				return pipelines.get(pipelineId);
			}
		} else {
			throw new ClosedChannelException();
		}
	}

	
	/**
	 * {@inheritDoc}
	 */
	public IBlockingPipeline getBlockingPipeline(String pipelineId) throws ClosedChannelException, IOException {
		return new BlockingPipeline(getNonBlockingPipeline(pipelineId));
	}
	
	
	
	private void onData() throws IOException {
		synchronized (multiplexerReadGuard) {
			multiplexer.demultiplex(connection, demultiplexResultHandler);
		}
	}
	
	
	
	private void onPipelineOpened(final String pipelineId) {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + getId() + "] pipeline " + pipelineId + " opened by peer");
		}

		NonBlockingPipeline pipeline = new NonBlockingPipeline(pipelineId, handlerAdapter);
		synchronized (pipelines) {
			pipelines.put(pipelineId, pipeline);
		}	
	}
	
	
	
	
	private void onPipelineClosed(final String pipelineId) {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + getId() + "] pipeline " + pipelineId + " closed by peer");
		}

		NonBlockingPipeline pipeline = null;
		synchronized (pipelines) {
			pipeline = pipelines.get(pipelineId);
		}

		if (pipeline != null) {
			removePipeline(pipeline);
		}
	}
	

	
	
	private void onPipelineData(final String pipelineId, ByteBuffer[] data) {
		NonBlockingPipeline pipeline = null;
		synchronized (pipelines) {
			pipeline = pipelines.get(pipelineId);
		}

		if (pipeline != null) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("notifying pipeline data handler");
			}
			pipeline.onData(data);
	
		} else {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("data received for non existing pipeline " + pipelineId);
			}
		}
	}
	
	
	
	private String registerNewPipeline() throws ClosedChannelException, IOException {
		synchronized (multiplexerWriteGuard) {
			return multiplexer.openPipeline(connection);
		}
	}

	
	
	private void deregisterPipeline(String pipelineId) throws ClosedChannelException, IOException {
		synchronized (multiplexerWriteGuard) {
			multiplexer.closePipeline(connection, pipelineId);
		}
	}

	private void sendPipelineData(String pipelineId, ByteBuffer[] dataToWrite, FlushMode flushMode) throws ClosedChannelException, IOException {
		if (dataToWrite == null) {
			return;
		}
		
		if (dataToWrite.length == 0) {
			return;
		}
		
		synchronized (multiplexerWriteGuard) {
			connection.setFlushmode(flushMode);
			multiplexer.multiplex(connection, pipelineId, dataToWrite);
			connection.setFlushmode(FlushMode.SYNC);
		}
	}
	

	
	private static final class BlockingPipeline extends BlockingConnection implements IBlockingPipeline {
		private INonBlockingPipeline delegee = null;
		
		BlockingPipeline(INonBlockingPipeline delegee) throws IOException {
			super(delegee);
			this.delegee = delegee;
		}
		

		public IMultiplexedConnection getMultiplexedConnection() {
			return delegee.getMultiplexedConnection();
		}
	}		
	
	
	private final class DemultiplexResultHandler implements IDemultiplexResultHandler {
		public void onPipelineOpend(String pipelineId) {
			MultiplexedConnection.this.onPipelineOpened(pipelineId);
		}
		
		public void onPipelineClosed(String pipelineId) {
			MultiplexedConnection.this.onPipelineClosed(pipelineId);
		}
		
		public void onPipelineData(String pipelineId, ByteBuffer[] data) {
			MultiplexedConnection.this.onPipelineData(pipelineId, data);			
		}
	}

	
	
	
	
	@Execution(Execution.NONTHREADED)
	private static final class MultiplexedConnectionHandler implements IConnectHandler, IDataHandler, IDisconnectHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler {
					
		private MultiplexedConnection multiplexedConnection = null;
		
		private MultiplexedConnectionHandler(MultiplexedConnection multiplexedConnection) {
			this.multiplexedConnection = multiplexedConnection;
		}
				

		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			if (connection.available() > 0) {
				onData(connection);
			}
			
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			multiplexedConnection.onData();
			return true;
		}
		
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			multiplexedConnection.close();
			return true;
		}
		
		
		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
			multiplexedConnection.close();
			return true;
		}
		
		
		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
			multiplexedConnection.close();
			return true;
		}
	}
	
	
	
	
	
	
	
	
	private final class NonBlockingPipeline extends AbstractNonBlockingStream implements INonBlockingPipeline {
		
		
		// the pipeline id
		private String pipelineId = null;

		// close flag
		private boolean isOpen = true;
		
		// suspend support
		private boolean isSuspendRead = false;
		private final ArrayList<ByteBuffer> suspendBuffer = new ArrayList<ByteBuffer>();


		
		// timeouts 
		private long connectionTimeoutMillis = IConnection.DEFAULT_CONNECTION_TIMEOUT_MILLIS;
		private long idleTimeoutMillis = IConnection.DEFAULT_IDLE_TIMEOUT_MILLIS;
		private long idleTimeoutDateMillis = Long.MAX_VALUE;
		private long connectionTimeoutDateMillis = Long.MAX_VALUE;
		private long lastReceivedMillis = System.currentTimeMillis();
		
		
		private boolean idleTimeoutOccured = false;
		private boolean connectionTimeoutOccured = false;
	
		
		private WatchDogTask watchDogTask = null;
		
	
		// handler
		private IHandler handler = null;
		
		
		NonBlockingPipeline(String pipelineId, PipelineHandlerAdapter handlerAdapter) {
			this.pipelineId = pipelineId;
			
			handler = handlerAdapter.getConnectionInstance();
			onOpen();
		}

		
		private void onOpen() {
			try {
				((IConnectHandler) handler).onConnect(this);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) 
					LOG.fine("error occured by performing onConnect call back on " + handler + " " + ioe.toString());
			}
		}

		

		@Override
		protected boolean isDataWriteable() {
			try {
				return (getNonBlockingPipeline(pipelineId) != null);
			} catch (ClosedChannelException ce) {
				return false;
			}
		}
		
		
		@Override
		protected boolean isMoreInputDataExpected() {
			try {
				return (getNonBlockingPipeline(pipelineId) != null);
			} catch (ClosedChannelException ce) {
				return false;
			}
		}
		
		
		public boolean isOpen() {
			if (!isOpen) {
				return false;
				
			} else {
				if (!isReadBufferEmpty()) {
					return true;
				}
				
				try {
					return (getNonBlockingPipeline(pipelineId) != null);
				} catch (ClosedChannelException ce) {
					return false;
				}
			}
		}
		
		
		public void close() throws IOException {
			super.close();

			if (isOpen) {
				closePipeline(this);
			}
			
			isOpen = false;			
		}

		
		private void onClose() {
			terminateWatchDog();
			
			try {
				((IDisconnectHandler) handler).onDisconnect(this);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) 
					LOG.fine("error occured by performing onDisconnect call back on " + handler + " " + ioe.toString());
			}

		}

		
		public void onData(ByteBuffer[] data) {
			
			if (isSuspendRead) {
				for (ByteBuffer byteBuffer : data) {
					suspendBuffer.add(byteBuffer);	
				}
				return;
			}
			
			appendDataToReadBuffer(data);
			try {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("notifying handler " + handler);
				}
				((IDataHandler) handler).onData(this);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by calling onData for pipeline " + getId() + " " + ioe.toString());
				}
			}
		}
		
		
		
		
		private void onConnectionTimeout() {
			if (!connectionTimeoutOccured) {
				connectionTimeoutOccured = true;
				try {
					((IConnectionTimeoutHandler) handler).onConnectionTimeout(this);
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) 
						LOG.fine("error occured by performing onConnectionTimeout call back on " + handler + " " + ioe.toString());
				}
				
			} else {
				setConnectionTimeoutMillis(IConnection.MAX_TIMEOUT_MILLIS);
			}

		}
				
		
		private void onIdleTimeout() {
			if (!idleTimeoutOccured) {
				idleTimeoutOccured = true;
				try {
					((IIdleTimeoutHandler) handler).onIdleTimeout(this);
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) 
						LOG.fine("error occured by performing onIdleTimeout call back on " + handler + " " + ioe.toString());
				}
				
			} else {
				setIdleTimeoutMillis(IConnection.MAX_TIMEOUT_MILLIS);
			}
		}
		
	

		
		void closeSilence() {
			try {
				close();
			} catch (Exception ignore) { }
		}
		
		
		public String getId() {
			return pipelineId;
		}

		
		public Executor getWorkerpool() {
			return connection.getWorkerpool();
		}
		
		public IMultiplexedConnection getMultiplexedConnection() {
			return MultiplexedConnection.this;
		}
		
		public void setMaxReadBufferThreshold(int size) {
			throw new UnsupportedOperationException("setMaxReadBufferThreshold is not supported for a pipeline. perform this operation on the MultiplexedConnection");			
		}
		
		public int getMaxReadBufferThreshold() {
			return connection.getMaxReadBufferThreshold();
		}
		
		
		public long getConnectionTimeoutMillis() {
			return connectionTimeoutMillis;
		}
		
		
		public void setConnectionTimeoutMillis(long timeoutMillis) {
			connectionTimeoutDateMillis = System.currentTimeMillis() + timeoutMillis;
			
			if (connectionTimeoutMillis != timeoutMillis) {
				connectionTimeoutMillis = timeoutMillis;
				updateWatchdog(connectionTimeoutMillis, idleTimeoutMillis);
			}
			
			connectionTimeoutOccured = false;
		}
		
		
		public long getIdleTimeoutMillis() {
			return idleTimeoutMillis;
		}
		
		
		public void setIdleTimeoutMillis(long timeoutMillis) {
			idleTimeoutDateMillis = System.currentTimeMillis() + timeoutMillis;
			
			if (idleTimeoutMillis != timeoutMillis) {
				idleTimeoutMillis = timeoutMillis;
				updateWatchdog(connectionTimeoutMillis, idleTimeoutMillis);
			}
			
			idleTimeoutOccured = false;
		}
		
		
		

		
		private synchronized void updateWatchdog(long connectionTimeoutMillis, long idleTimeoutMillis) {
			
			long watchdogPeriod = connectionTimeoutMillis;
			if (idleTimeoutMillis < watchdogPeriod) {
				watchdogPeriod = idleTimeoutMillis; 
			}

			if (watchdogPeriod > 500) {
				watchdogPeriod = watchdogPeriod / 5;
			}
			
			if (watchdogPeriod > MIN_WATCHDOG_PERIOD_MILLIS) {
				watchdogPeriod = MIN_WATCHDOG_PERIOD_MILLIS;
			}
			
			terminateWatchDog();
			
	        watchDogTask = new WatchDogTask(this);
	        TIMER.schedule(watchDogTask, watchdogPeriod, watchdogPeriod);
		}
		
		
		private synchronized void terminateWatchDog() {
	        if (watchDogTask != null) {
	            watchDogTask.cancel();
	        }			
		}

		

		private void checkTimeouts() {
			long currentMillis = System.currentTimeMillis();
					
			if (getRemainingMillisToConnectionTimeout(currentMillis) <= 0) {
				onConnectionTimeout();
			}
					
			if (getRemainingMillisToIdleTimeout(currentMillis) <= 0) {
				onIdleTimeout();
			}
		}

		
		/**
		 * {@inheritDoc}
		 */
		public long getRemainingMillisToConnectionTimeout() {
			return getRemainingMillisToConnectionTimeout(System.currentTimeMillis());
		}

		
		private long getRemainingMillisToConnectionTimeout(long currentMillis) {
			return connectionTimeoutDateMillis - currentMillis;
		}

		
		/**
		 * {@inheritDoc}
		 */
		public long getRemainingMillisToIdleTimeout() {
			return getRemainingMillisToIdleTimeout(System.currentTimeMillis());
		}
		
		
		private long getRemainingMillisToIdleTimeout(long currentMillis) {
			long remaining = idleTimeoutDateMillis - currentMillis;
			
			// time out received
			if (remaining > 0) {
				return remaining;	
				
			// ... yes 
			} else {
				
				// ... but check if meantime data has been received! 
				return (lastReceivedMillis + idleTimeoutMillis) - currentMillis;
			}	
		}
		
		
		
		public void setWriteTransferRate(int bytesPerSecond) throws ClosedChannelException, IOException {
			throw new UnsupportedOperationException("setWriteTransferRate is not supported for a pipeline. perform this operation on the MultiplexedConnection");			
		}
		
		public void setHandler(IHandler hdl) throws IOException {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + getId() + "] set handler " + hdl);
			}
			
			handler = PipelineHandlerAdapter.newInstance(hdl);
			
			if (available() > 0) {
				onData(null);
			}
		}
		
		
		public void setOption(String name, Object value) throws IOException {
			LOG.warning("set option is vaild for all pipelines. Better use <MultiplexedcCnnection>.serOptions(String, Object)");
			connection.setOption(name, value);
		}
		
		public Object getOption(String name) throws IOException {
			return connection.getOption(name);
		}
		
		@SuppressWarnings("unchecked")
		public Map<String, Class> getOptions() {
			return connection.getOptions();
		}
		
		
		public InetAddress getLocalAddress() {
			return connection.getLocalAddress();
		}
		
		public int getLocalPort() {
			return connection.getLocalPort();
		}
		
		public InetAddress getRemoteAddress() {
			return connection.getRemoteAddress();
		}
		
		public int getRemotePort() {
			return connection.getRemotePort();
		}
		
		public void suspendRead() throws IOException {
			isSuspendRead = true;
		}
		
		@SuppressWarnings("unchecked")
		public void resumeRead() throws IOException {
			isSuspendRead = false;
			ArrayList<ByteBuffer> data = (ArrayList<ByteBuffer>) suspendBuffer.clone();
			suspendBuffer.clear();
			onData(data.toArray(new ByteBuffer[data.size()]));
		}
		
		
		public int getPendingWriteDataSize() {
			return getWriteBufferSize();
		}
	
	
		
		public void activateSecuredMode() throws IOException {
			throw new UnsupportedOperationException("activateSecuredMode is not supported for a pipeline. perform this operation on the MultiplexedConnection");			
		}

		public boolean isSecure() {
			return connection.isSecure();
		}
	
		
		
		public long transferFrom(ReadableByteChannel sourceChannel) throws ClosedChannelException, IOException, SocketTimeoutException {
			int chunkSize = (Integer) getOption(SO_SNDBUF);
			long transfered = 0;

			int read = 0;
			do {
				ByteBuffer transferBuffer = ByteBuffer.allocate(chunkSize);
				read = sourceChannel.read(transferBuffer);
					
				if (read > 0) { 
					if (transferBuffer.remaining() == 0) {
						transferBuffer.flip();
						write(transferBuffer);
							
					} else {
						transferBuffer.flip();
						write(transferBuffer.slice());
					}
						
					transfered += read;
				}
			} while (read > 0);
				
			return transfered;
		}

	
	
		@Override
		protected void onWriteDataInserted() throws IOException, ClosedChannelException {
			if (isAutoflush()) {
				flush();
			}
		}

		
		
		public void flush() throws ClosedChannelException, IOException {
			removeWriteMark();
			
			ByteBuffer[] dataToWrite = drainWriteQueue();
			sendPipelineData(getId(), dataToWrite, getFlushmode());
		}

		
		/**
		 * {@inheritDoc}
		 */
		@Override
		public String toString() {
			try {
				if (isOpen()) {
					return "id=" + getId() + ", remote=" + getRemoteAddress().getCanonicalHostName() + "(" + getRemoteAddress() + ":" + getRemotePort() + ")";
				} else {
					return "id=" + getId() + " (closed)";
				}
			} catch (Exception e) {
				return super.toString();
			}
		}
	}
	
	
	private static final class WatchDogTask extends TimerTask {
		
		private WeakReference<NonBlockingPipeline> nonBlockingPipelineRef = null;
		
		public WatchDogTask(NonBlockingPipeline nonBlockingPipeline) {
			nonBlockingPipelineRef = new WeakReference<NonBlockingPipeline>(nonBlockingPipeline);
		}
	
		
		@Override
		public void run() {
			NonBlockingPipeline nonBlockingPipeline = nonBlockingPipelineRef.get();
			
			if (nonBlockingPipeline == null)  {
				this.cancel();
				
			} else {
				nonBlockingPipeline.checkTimeouts();
			}
		}		
	}
}
