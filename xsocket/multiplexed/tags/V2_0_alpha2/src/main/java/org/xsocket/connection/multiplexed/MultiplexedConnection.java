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
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
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


import org.xsocket.ClosedException;
import org.xsocket.DataConverter;
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
	
	
	// pipeline handler proxy
	private PipelineHandlerProxy handlerProxyPrototype = null;
	
	
	
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
		this(connection, PipelineHandlerProxy.newPrototype(null, null), multiplexer);
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
		this(connection, PipelineHandlerProxy.newPrototype(pipelineHandler, null), multiplexer);
	}
	
	
	/**
	 * constructor. The {@link DefaultMultiplexer} class will be used to (de)multiplex the data
	 *  
	 * @param connection       the underlying connection
	 * @param pipelineHandler  the pipeline handler
	 * @throws IOException  if an exception occurs
	 */
	public MultiplexedConnection(INonBlockingConnection connection, IHandler pipelineHandler) throws IOException {
		this(connection, PipelineHandlerProxy.newPrototype(pipelineHandler, null), new DefaultMultiplexer());
	}
	
	
	/**
	 * internal constructor  
	 * 
	 */
	MultiplexedConnection(INonBlockingConnection connection, PipelineHandlerProxy handlerProxy, IMultiplexer multiplexer) throws IOException {
		this.connection = connection;
		this.multiplexer = multiplexer;		
		this.handlerProxyPrototype = handlerProxy;
	
		connection.setAutoflush(false);
		connection.setFlushmode(FlushMode.ASYNC);
	
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
	public void setConnectionTimeoutSec(int timeoutSec) {
		connection.setConnectionTimeoutSec(timeoutSec);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getConnectionTimeoutSec() {
		return connection.getConnectionTimeoutSec();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void setIdleTimeoutSec(int timeoutInSec) {
		connection.setIdleTimeoutSec(timeoutInSec);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getIdleTimeoutSec() {
		return connection.getIdleTimeoutSec();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getRemainingSecToConnectionTimeout() {
		return connection.getRemainingSecToConnectionTimeout();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getRemainingSecToIdleTimeout() {
		return connection.getRemainingSecToIdleTimeout();
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

		NonBlockingPipeline pipeline = new NonBlockingPipeline(pipelineId, handlerProxyPrototype);
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
	public String[] listOpenPipelines() throws ClosedException {
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
		synchronized (pipelines) {
			pipelines.remove(pipeline.getId());
		}
		
		pipeline.onClose();

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + getId() + "] pipeline " + pipeline.getId() + " destroyed");
		}
	}
	
	

	/**
	 * {@inheritDoc}
	 */	
	public INonBlockingPipeline getNonBlockingPipeline(String pipelineId) throws ClosedException {
		if (connection.isOpen()) {
			synchronized (pipelines) {
				return pipelines.get(pipelineId);
			}
		} else {
			throw new ClosedException("connection " + connection.getId() + " is already closed");
		}
	}

	
	/**
	 * {@inheritDoc}
	 */
	public IBlockingPipeline getBlockingPipeline(String pipelineId) throws ClosedException {
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

		NonBlockingPipeline pipeline = new NonBlockingPipeline(pipelineId, handlerProxyPrototype);
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
	
	
	
	private String registerNewPipeline() throws ClosedException, IOException {
		synchronized (multiplexerWriteGuard) {
			return multiplexer.openPipeline(connection);
		}
	}

	
	
	private void deregisterPipeline(String pipelineId) throws ClosedException, IOException {
		synchronized (multiplexerWriteGuard) {
			multiplexer.closePipeline(connection, pipelineId);
		}
	}

	private void sendPipelineData(String pipelineId, ByteBuffer[] dataToWrite, FlushMode flushMode) throws ClosedException, IOException {
		if (dataToWrite == null) {
			return;
		}
		
		if (dataToWrite.length == 0) {
			return;
		}
		
		synchronized (multiplexerWriteGuard) {
			connection.setFlushmode(flushMode);
			multiplexer.multiplex(connection, pipelineId, dataToWrite);
			connection.setFlushmode(FlushMode.ASYNC);
		}
	}
	

	
	private static final class BlockingPipeline extends BlockingConnection implements IBlockingPipeline {
		private INonBlockingPipeline delegee = null;
		
		BlockingPipeline(INonBlockingPipeline delegee) {
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

	
	
	
	
	@Execution(Execution.Mode.NONTHREADED)
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

		
		
		// suspend support
		private boolean isSuspendRead = false;
		private final ArrayList<ByteBuffer> suspendBuffer = new ArrayList<ByteBuffer>();


		
		// timeouts 
		private int connectionTimeoutSec = IConnection.DEFAULT_CONNECTION_TIMEOUT_SEC;
		private int idleTimeoutSec = IConnection.DEFAULT_IDLE_TIMEOUT_SEC;
		private long idleTimeoutDateMillis = Long.MAX_VALUE;
		private long connectionTimeoutDateMillis = Long.MAX_VALUE;
		private long lastReceivedMillis = System.currentTimeMillis();
		
		
		private boolean idleTimeoutOccured = false;
		private boolean connectionTimeoutOccured = false;
		private TimerTask watchDogTask = null;
		private long watchDogPeriod = Long.MAX_VALUE;
		
	
		// handler
		private IHandler handler = null;
		
		
		NonBlockingPipeline(String pipelineId, PipelineHandlerProxy handlerProxyPrototype) {
			this.pipelineId = pipelineId;
			
			handler = handlerProxyPrototype.newProxy(this);
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


		
		public boolean isOpen() {
			try {
				return (getNonBlockingPipeline(pipelineId) != null);
			} catch (ClosedException ce) {
				return false;
			}
		}
		
		
		public void close() throws IOException {
			if (isOpen()) {
				closePipeline(this);
			}
		}

		
		private void onClose() {
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
				setConnectionTimeoutSec(IConnection.MAX_TIMEOUT_SEC);
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
				setIdleTimeoutSec(IConnection.MAX_TIMEOUT_SEC);
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
		
		
		public int getConnectionTimeoutSec() {
			return connectionTimeoutSec;
		}
		
		
		public void setConnectionTimeoutSec(int timeoutSec) {
			connectionTimeoutSec = timeoutSec;
			connectionTimeoutDateMillis = System.currentTimeMillis() + DataConverter.unsignedIntToLong(connectionTimeoutSec);
			
			updateTimer(connectionTimeoutSec * 100L);
			connectionTimeoutOccured = false;
		}
		
		public int getIdleTimeoutSec() {
			return idleTimeoutSec;
		}
		
		
		public void setIdleTimeoutSec(int timeoutInSec) {
			idleTimeoutSec = timeoutInSec;
			idleTimeoutDateMillis = System.currentTimeMillis() + DataConverter.unsignedIntToLong(idleTimeoutSec);
			
			updateTimer(idleTimeoutSec * 100L);		
			idleTimeoutOccured = false;
		}

		
		private synchronized void updateTimer(long requiredMinPeriod) {

			// if not watch dog already exists and required period is smaller than current one return
	        if ((watchDogTask != null) && (watchDogPeriod <= requiredMinPeriod)) {
	            return;
	        }

	        // set watch dog period
	        watchDogPeriod = requiredMinPeriod;
			
	        if (LOG.isLoggable(Level.FINE)) {
	            LOG.fine("update  watchdog task " + DataConverter.toFormatedDuration(watchDogPeriod));
	        }

	        // if watchdog task task already exits -> terminate it
	        if (watchDogTask != null) {
	            watchDogTask.cancel();
	        }


	        // create and run new watchdog task
	        watchDogTask = new TimerTask() {
	            @Override
	            public void run() {
	                checkTimeouts();
	            }
	        };
	        
	        TIMER.schedule(watchDogTask, watchDogPeriod, watchDogPeriod);
		}
		

		private void checkTimeouts() {
			long currentMillis = System.currentTimeMillis();
					
			if (getRemainingSecToConnectionTimeout(currentMillis) <= 0) {
				onConnectionTimeout();
			}
					
			if (getRemainingSecToIdleTimeout(currentMillis) <= 0) {
				onIdleTimeout();
			}
		}

		
		/**
		 * {@inheritDoc}
		 */
		public int getRemainingSecToConnectionTimeout() {
			return getRemainingSecToConnectionTimeout(System.currentTimeMillis());
		}

		
		private int getRemainingSecToConnectionTimeout(long currentMillis) {
			long remaining = connectionTimeoutDateMillis - currentMillis;
			return DataConverter.unsignedLongToInt(remaining);	
		}

		
		/**
		 * {@inheritDoc}
		 */
		public int getRemainingSecToIdleTimeout() {
			return getRemainingSecToIdleTimeout(System.currentTimeMillis());
		}
		
		
		private int getRemainingSecToIdleTimeout(long currentMillis) {
			long remaining = idleTimeoutDateMillis - currentMillis;
			
			// time out received
			if (remaining > 0) {
				return DataConverter.unsignedLongToInt(remaining);	
				
			// ... yes 
			} else {
				
				// ... but check if meantime data has been received! 
				remaining = (lastReceivedMillis +  DataConverter.unsignedIntToLong(idleTimeoutSec)) - currentMillis;
				return DataConverter.unsignedLongToInt(remaining);	
			}	
		}
		
		
		
		public void setWriteTransferRate(int bytesPerSecond) throws ClosedException, IOException {
			throw new UnsupportedOperationException("setWriteTransferRate is not supported for a pipeline. perform this operation on the MultiplexedConnection");			
		}
		
		public void setHandler(IHandler hdl) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + getId() + "] set handler " + hdl);
			}
			
			handler = PipelineHandlerProxy.newPrototype(hdl, null).newProxy(this);
			
			
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
	
		
		
		public long transferFrom(ReadableByteChannel sourceChannel) throws ClosedException, IOException, SocketTimeoutException {
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
		protected void onWriteDataInserted() throws IOException, ClosedException {
			if (isAutoflush()) {
				flush();
			}
		}

		
		
		public void flush() throws ClosedException, IOException {
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
}
