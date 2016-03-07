// $Id: NonBlockingConnection.java 1281 2007-05-29 19:48:07Z grro $
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
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.xsocket.ClosedConnectionException;
import org.xsocket.DataConverter;
import org.xsocket.IDispatcher;
import org.xsocket.ILifeCycle;
import org.xsocket.IWorkerPool;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.Synchronized;
import org.xsocket.Synchronized.Mode;




/**
 * Implementation of the <code>INonBlockingConnection</code> interface. <br><br>
 * 
 * The methods of this class are not thread-safe. 
 * 
 * @author grro@xsocket.org
 */
public final class NonBlockingConnection extends Connection implements INonBlockingConnection {

	private static final Logger LOG = Logger.getLogger(BlockingConnection.class.getName());
	
	private static final long MIN_WATCHDOG_PERIOD_MILLIS =  15 * 60 * 1000L;  

	
	private IHandler appHandler = null;
	private boolean isConnectHandler = false;
    private boolean isDisconnectHandler = false;
	private boolean isDataHandler = false;
	private boolean isTimeoutHandler = false;

	private boolean isClient = false;
	
	
	private IWorkerPool workerPool = null;
		
	
	// event queue
	private final TaskQueue taskQueue = new TaskQueue();
	private Runnable taskProcessor = null;

	
	private TimeoutWatchdog timeoutWatchdog = null;
	private static Timer watchDogTimer = null;

	
	
	/**
	 * constructor. This constructor will be used to create a non blocking 
	 * client-side connection.
	 *
     * @param hostname  the remote host
	 * @param port		the port of the remote host to connect
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port) throws IOException {
		this(InetAddress.getByName(hostname), port);
	}

	

	/**
	 * constructor. This constructor will be used to create a non blocking 
	 * client-side connection.
	 *
     * @param hostname             the remote host
	 * @param port		           the port of the remote host to connect
	 * @param socketConfiguration  the socket configuration 
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port, StreamSocketConfiguration socketConfiguration) throws IOException {
		this(InetAddress.getByName(hostname), port, socketConfiguration);
	}

	
	
	/**
	 * constructor. This constructor will be used to create a non blocking 
	 * client-side connection.
	 * 
	 * @param address   the remote host
	 * @param port		the port of the remote host to connect
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port) throws IOException {
		this(new InetSocketAddress(address, port), null, null, false, null);
	}

	

	/**
	 * constructor. This constructor will be used to create a non blocking 
	 * client-side connection.
	 * 
	 * @param address              the remote host
	 * @param port	          	   the port of the remote host to connect
	 * @param socketConfiguration  the socket configuration  
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, StreamSocketConfiguration socketConfiguration) throws IOException {
		this(new InetSocketAddress(address, port), socketConfiguration, null, false, null);
	}
	
	/**
	 * constructor. This constructor will be used to create a non blocking 
	 * client-side connection. The default worker pool will be used
	 * 
	 * @param address          the remote address
	 * @param port             the remote port
	 * @param appHandler       the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler and ITimeoutHandler) 
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler) throws IOException {
		this(new InetSocketAddress(address, port), null, null, false, appHandler);
	}

	
	
	/**
	 * constructor. This constructor will be used to create a non blocking 
	 * client-side connection. The default worker pool will be used
	 * 
	 * @param address              the remote address
	 * @param port                 the remote port
	 * @param socketConfiguration  the socket configuration 
	 * @param appHandler           the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler and ITimeoutHandler) 
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, StreamSocketConfiguration socketConfiguration, IHandler appHandler) throws IOException {
		this(new InetSocketAddress(address, port), socketConfiguration, null, false, appHandler);
	}

	

	/**
	 * constructor <br><br>
	 * 
	 * constructor. This constructor will be used to create a non blocking 
	 * client-side connection. The default worker pool will be used
	 * 
	 * @param hostname         the remote host
	 * @param port             the remote port
	 * @param appHandler       the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler and ITimeoutHandler)  
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port, IHandler appHandler) throws IOException {
		this(new InetSocketAddress(hostname, port), null, null, false, appHandler);
	}

	

	/**
	 * constructor <br><br>
	 * 
	 * constructor. This constructor will be used to create a non blocking 
	 * client-side connection. The default worker pool will be used
	 * 
	 * @param hostname             the remote host
	 * @param port                 the remote port
	 * @param socketConfiguration  the socket configuration
	 * @param appHandler           the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler and ITimeoutHandler)  
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port, StreamSocketConfiguration socketConfiguration, IHandler appHandler) throws IOException {
		this(new InetSocketAddress(hostname, port), socketConfiguration, null, false, appHandler);
	}

	
	/**
	 * constructor. This constructor will be used to create a non blocking 
	 * client-side connection.
	 * 
	 * 
	 * @param address              the remote address
	 * @param port                 the remote port
	 * @param sslContext           the ssl context to use
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IConnection#activateSecuredMode()})
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(address, port), null, sslContext, sslOn, null);
	}

	
	/**
	 * constructor. This constructor will be used to create a non blocking 
	 * client-side connection.
	 * 
	 * 
	 * @param address              the remote address
	 * @param port                 the remote port
	 * @param socketConfiguration  the socket configuration 
	 * @param sslContext           the ssl context to use
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IConnection#activateSecuredMode()})
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, StreamSocketConfiguration socketConfiguration, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(address, port), socketConfiguration, sslContext, sslOn, null);
	}
	
	
	
	/**
	 * constructor. This constructor will be used to create a non blocking 
	 * client-side connection.
	 * 
	 * 
	 * @param hostname             the remote host
	 * @param port                 the remote port
	 * @param sslContext           the ssl context to use
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IConnection#activateSecuredMode()})
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(hostname, port), null, sslContext, sslOn, null);
	}
	
	
	/**
	 * constructor. This constructor will be used to create a non blocking 
	 * client-side connection.
	 * 
	 * 
	 * @param hostname             the remote host
	 * @param port                 the remote port
	 * @param socketConfiguration  the socket configuration 
	 * @param sslContext           the ssl context to use
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IConnection#activateSecuredMode()})
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port, StreamSocketConfiguration socketConfiguration, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(hostname, port), socketConfiguration, sslContext, sslOn, null);
	}
	
	
	/**
	 * intermediate constructor, which uses the global dispatcher
	 */
	private NonBlockingConnection(InetSocketAddress remoteAddress, StreamSocketConfiguration socketConf, SSLContext sslContext, boolean sslOn, IHandler appHandler) throws IOException {
		this(createClientIoSocketHandler(remoteAddress, getGlobalMemoryManager(), getGlobalDispatcher(), socketConf), sslContext, sslOn, getGlobalMemoryManager(), getGlobalWorkerPool(), true, appHandler, (appHandler instanceof IConnectHandler),  (appHandler instanceof IDisconnectHandler), (appHandler instanceof IDataHandler), (appHandler instanceof ITimeoutHandler), isSynchronized(appHandler));
		
		if (LOG.isLoggable(Level.FINE)) {
			if ((appHandler instanceof IConnectionScoped)) {
				LOG.fine("handler type IConnectionScoped is not supported in the client context");
			}
			
			if ((appHandler instanceof ILifeCycle)) {
				LOG.fine("ILifeCycle is not supported in the client context");
			}
		}
	}
	
	
	
	
	/**
	 * constructor <br><br>
	 * 
	 * constructor. This constructor will be used to create a non blocking 
	 * client-side connection.
	 * 
	 * @param hostname         the remote host
	 * @param port             the remote port
	 * @param appHandler       the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler and ITimeoutHandler)  
	 * @param workerPool       the worker pool to use
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port, IHandler appHandler, IWorkerPool workerPool, int preallocationMemorySize) throws IOException {
		this(new InetSocketAddress(hostname, port), null, null, false, appHandler, workerPool, new MemoryManager(preallocationMemorySize, true));
	}

	

	/**
	 * constructor <br><br>
	 * 
	 * constructor. This constructor will be used to create a non blocking 
	 * client-side connection.
	 * 
	 * @param hostname             the remote host
	 * @param port                 the remote port
	 * @param socketConf           the socket configuration 
	 * @param appHandler           the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler and ITimeoutHandler)  
	 * @param workerPool           the worker pool to use
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port, StreamSocketConfiguration socketConf, IHandler appHandler, IWorkerPool workerPool, int preallocationMemorySize) throws IOException {
		this(new InetSocketAddress(hostname, port), socketConf, null, false, appHandler, workerPool, new MemoryManager(preallocationMemorySize, true));
	}

	

	/**
	 * constructor. This constructor will be used to create a non blocking 
	 * client-side connection.
	 * 
	 * @param address          the remote address
	 * @param port             the remote port
	 * @param appHandler       the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler and ITimeoutHandler) 
	 * @param workerPool       the worker pool to use
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, IWorkerPool workerPool, int preallocationMemorySize) throws IOException {
		this(new InetSocketAddress(address, port), null, null, false, appHandler, workerPool, new MemoryManager(preallocationMemorySize, true));
	}

	
	/**
	 * constructor. This constructor will be used to create a non blocking 
	 * client-side connection.
	 * 
	 * @param address              the remote address
	 * @param port                 the remote port
	 * @param socketConf           the socket configuration  
	 * @param appHandler           the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler and ITimeoutHandler) 
	 * @param workerPool           the worker pool to use
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, StreamSocketConfiguration socketConf, IHandler appHandler, IWorkerPool workerPool, int preallocationMemorySize) throws IOException {
		this(new InetSocketAddress(address, port), socketConf, null, false, appHandler, workerPool, new MemoryManager(preallocationMemorySize, true));
	}

	
	/**
	 * intermediate constructor, which uses a specific dispatcher
	 */
	private NonBlockingConnection(InetSocketAddress remoteAddress, StreamSocketConfiguration socketConf, SSLContext sslContext, boolean startSSL, IHandler appHandler, IWorkerPool workerPool, IMemoryManager memoryManager) throws IOException {
		this(createClientIoSocketHandler(remoteAddress, memoryManager, newDispatcher("ClientDispatcher", memoryManager), socketConf), sslContext, startSSL, memoryManager, workerPool, true, appHandler, (appHandler instanceof IConnectHandler),  (appHandler instanceof IDisconnectHandler), (appHandler instanceof IDataHandler), (appHandler instanceof ITimeoutHandler), isSynchronized(appHandler));
		
		if (LOG.isLoggable(Level.FINE)) {
			if ((appHandler instanceof IConnectionScoped)) {
				LOG.fine("handler type IConnectionScoped is not supported in the client context");
			}
			
			if ((appHandler instanceof org.xsocket.ILifeCycle)) {
				LOG.fine("ILifeCycle is not supported in the client context");
			}
		}
	}

	
	
	/**
	 * server-side constructor 
	 * 
	 * @param socketHandler          the sockdet io handler
	 * @param sslContext             the ssl context to use
	 * @param sslOn                  true, activate SSL mode. false, ssl can be activated by user (see {@link IConnection#activateSecuredMode()})
	 * @param sslMemoryManager       the ssl memory manager 
	 * @param isClient               true, is is in cleint mode
	 * @param appHandler             the assigned application handler
	 * @param isConnectHandler       true, is is connect handler
	 * @param isDisconnectHandler    true, if is disconnect handler
	 * @param isDataHandler          true, if is data handler
	 * @param isTimeoutHandler       true, if is timeout handler
	 * @param isSynchronizedHandler  true, if the handler is synchromized
	 * @throws IOException If some other I/O error occurs
	 */
	NonBlockingConnection(IoSocketHandler socketHandler, SSLContext sslContext, boolean sslOn, IMemoryManager sslMemoryManager, IWorkerPool workerPool, boolean isClient, IHandler appHandler, boolean isConnectHandler, boolean isDisconnectHandler, boolean isDataHandler, boolean isTimeoutHandler, boolean isSynchronizedHandler) throws IOException {
		this.workerPool = workerPool;
		this.appHandler = appHandler;
		this.isConnectHandler = isConnectHandler;
		this.isDataHandler = isDataHandler;
		this.isDataHandler = isDataHandler;
		this.isTimeoutHandler = isTimeoutHandler;
		this.isDisconnectHandler = isDisconnectHandler;
		
		this.isClient = isClient;
		
		if (isSynchronizedHandler) {
			taskProcessor = new SynchronizedTaskProcessor();
		} else {
			taskProcessor = new NonSynchronizedTaskProcessor();
		}
		
		init(socketHandler, sslContext, sslOn, isClient, sslMemoryManager);
	}
		
	IHandler getAppHandler() {
		return appHandler;
	}
	
	
	@Override
	void reset() {
		try {
			setWriteTransferRate(UNLIMITED);
		} catch (Exception e) { 
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by reseting (setWriteTransferRate). Reason: " + e.toString());
			}
		}
		super.reset();

		setIdleTimeoutSec(Integer.MAX_VALUE);
		setConnectionTimeoutSec(Integer.MAX_VALUE);
		setFlushmode(INonBlockingConnection.INITIAL_FLUSH_MODE);
	}

	
	/**
	 * {@inheritDoc}
	 */
	public void setIdleTimeoutSec(int timeoutInSec) {
		getIoSocketHandler().setIdleTimeoutMillis(((long) timeoutInSec) * 1000);
		
		if (isClient) {
			getWatchdog().updateTimeoutCheckPeriod(getIoSocketHandler().getIdleTimeoutMillis(), getIoSocketHandler().getConnectionTimeoutMillis());
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void setConnectionTimeoutSec(int timeoutSec) {
		getIoSocketHandler().setConnectionTimeoutMillis(((long) timeoutSec) * 1000);
		
		if (isClient) {
			getWatchdog().updateTimeoutCheckPeriod(getIoSocketHandler().getIdleTimeoutMillis(), getIoSocketHandler().getConnectionTimeoutMillis());
		}
	}
	
	
	@SuppressWarnings("unchecked")
	private TimeoutWatchdog getWatchdog() {
		if (timeoutWatchdog == null) {
			timeoutWatchdog = new TimeoutWatchdog();
			timeoutWatchdog.setDispatcher(getIoSocketHandler().getDispatcher());
		}
		
		return timeoutWatchdog;
	}


	
	public int getConnectionTimeoutSec() {
		return (int) (getIoSocketHandler().getConnectionTimeoutMillis() / 1000);
	}
	
	public int getIdleTimeoutSec() {
		return (int) (getIoSocketHandler().getIdleTimeoutMillis() / 1000);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void setFlushmode(FlushMode mode) {
		super.setFlushmode(mode);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public FlushMode getFlushmode() {
		return super.getFlushmode();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void setWriteTransferRate(int bytesPerSecond) throws ClosedConnectionException, IOException {		

		IoDelayWriteHandler delayHandler = getDelayIOHandler();
		
		// unlimited -> remove Delay handler (if exists)
		if (bytesPerSecond == UNLIMITED) {
			if (delayHandler != null) {
				delayHandler.flushOutgoing();
				IoHandler ioHandler = delayHandler.getSuccessor();
				setIOHandler(ioHandler);
			}
			
		// not unlimited -> add Delay handler (if not exists) 
		} else {
			if (delayHandler == null) {
				delayHandler = new IoDelayWriteHandler(getIOHandler());
				setIOHandler(delayHandler);
			}
			
			delayHandler.setWriteRateSec(bytesPerSecond);
		}
	}
	
	
	private IoDelayWriteHandler getDelayIOHandler() {
		IoHandler ioHandler = getIOHandler();
		do {
			if (ioHandler instanceof IoDelayWriteHandler) {
				return (IoDelayWriteHandler) ioHandler;
			}
			ioHandler = ioHandler.getSuccessor();
		} while (ioHandler != null);
		
		return null;
	}
		

	
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getNumberOfAvailableBytes() {
		return getReadQueue().getSize(); 
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public ByteBuffer[] readAvailable() throws IOException, ClosedConnectionException {
		LinkedList<ByteBuffer> buffers = extractAvailableFromReadQueue();
		if (buffers != null) {
			return buffers.toArray(new ByteBuffer[buffers.size()]);
		} else {
			return new ByteBuffer[0];
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public boolean readAvailableByDelimiter(String delimiter, WritableByteChannel outputChannel) throws IOException, ClosedConnectionException {
		return extractAvailableFromReadQueue(delimiter, outputChannel);
	}
	

	/**
	 * {@inheritDoc}
	 */
	public int read(ByteBuffer buffer) throws IOException {
		int size = buffer.remaining();
		
		int available = getNumberOfAvailableBytes();
		if (available < size) {
			size = available;
		} 
		
		ByteBuffer[] bufs = readByteBufferByLength(size);
		for (ByteBuffer buf : bufs) {
			while (buf.hasRemaining()) {
				buffer.put(buf); 
			}
		}
		
		return size;
	}
	 
	
	/**
	 * {@inheritDoc}
	 */
	public byte readByte() throws IOException, ClosedConnectionException, BufferUnderflowException {
		return extractByteFromReadQueue();
	}

	
	/**
	 * {@inheritDoc}
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException, ClosedConnectionException, BufferUnderflowException {
		return readByteBufferByDelimiter(delimiter, Integer.MAX_VALUE);
	}


	/**
	 * {@inheritDoc}
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter, int maxLength) throws IOException, ClosedConnectionException, MaxReadSizeExceededException, BufferUnderflowException {
		LinkedList<ByteBuffer> result = extractBytesByDelimiterFromReadQueue(delimiter, maxLength);
		return result.toArray(new ByteBuffer[result.size()]);
	}
	
	
	/**
	 * {@inheritDoc}
	 */	
	public ByteBuffer[] readByteBufferByLength(int length) throws IOException, ClosedConnectionException, BufferUnderflowException {
		LinkedList<ByteBuffer> extracted = extractBytesByLength(length);
	
		return extracted.toArray(new ByteBuffer[extracted.size()]);
	}
	

	
	/**
	 * {@inheritDoc}
	 */
	public byte[] readBytesByDelimiter(String delimiter) throws IOException, ClosedConnectionException, BufferUnderflowException {
		return readBytesByDelimiter(delimiter, Integer.MAX_VALUE);
	}

	
	/**
	 * {@inheritDoc}
	 */
	public byte[] readBytesByDelimiter(String delimiter, int maxLength) throws IOException, ClosedConnectionException, MaxReadSizeExceededException, BufferUnderflowException {
		return DataConverter.toBytes(readByteBufferByDelimiter(delimiter, maxLength));
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public byte[] readBytesByLength(int length) throws IOException, ClosedConnectionException, BufferUnderflowException {
		return DataConverter.toBytes(readByteBufferByLength(length));
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public double readDouble() throws IOException, ClosedConnectionException, BufferUnderflowException {
		return extractDoubleFromReadQueue();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public int readInt() throws IOException, ClosedConnectionException, BufferUnderflowException {
		return extractIntFromReadQueue();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public long readLong() throws IOException, ClosedConnectionException, BufferUnderflowException {
		return extractLongFromReadQueue();
	}
	

	/**
	 * {@inheritDoc}
	 */
	public String readStringByDelimiter(String delimiter) throws IOException ,ClosedConnectionException ,BufferUnderflowException ,UnsupportedEncodingException {
		return readStringByDelimiter(delimiter, Integer.MAX_VALUE);
	};

	
	/**
	 * {@inheritDoc}
	 */
	public String readStringByDelimiter(String delimiter, int maxLength) throws IOException ,ClosedConnectionException ,BufferUnderflowException ,UnsupportedEncodingException ,MaxReadSizeExceededException {
		return readStringByDelimiter(delimiter, getDefaultEncoding(), maxLength);
	};
	

	/**
	 * {@inheritDoc}
	 */
	public String readStringByDelimiter(String delimiter, String encoding) throws IOException, ClosedConnectionException, BufferUnderflowException, UnsupportedEncodingException {
		return readStringByDelimiter(delimiter, encoding, Integer.MAX_VALUE);
	}

	
	/**
	 * {@inheritDoc}
	 */
	public String readStringByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, ClosedConnectionException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException {
		LinkedList<ByteBuffer> extracted = extractBytesByDelimiterFromReadQueue(delimiter, maxLength);

		return DataConverter.toString(extracted, encoding);
	}

	
	/**
	 * {@inheritDoc}
	 */
	public String readStringByLength(int length) throws IOException, ClosedConnectionException, BufferUnderflowException, UnsupportedEncodingException {
		return readStringByLength(length, getDefaultEncoding());
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public String readStringByLength(int length, String encoding) throws IOException, ClosedConnectionException, BufferUnderflowException, UnsupportedEncodingException {
		LinkedList<ByteBuffer> extracted = extractBytesByLength(length);
		return DataConverter.toString(extracted, encoding);
	}

	
	/**
	 * {@inheritDoc}
	 */
	public int getIndexOf(String str, int maxLength) throws IOException, ClosedConnectionException, BufferUnderflowException, MaxReadSizeExceededException {
		return readIndexOf(str, maxLength);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public int getIndexOf(String str) throws IOException, ClosedConnectionException, BufferUnderflowException {
		return getIndexOf(str, Integer.MAX_VALUE);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	void onConnectionTimeout() {
		try {
			// if is timeout handler -> notify handler			
			if (isTimeoutHandler) {
				boolean handled = ((ITimeoutHandler)appHandler).onConnectionTimeout(this);
				internalFlush();
				if (!handled) {
					close();
				}
			} else {
				close();
			}		
		} catch (Exception e) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by handling connection timeout event. Reason: " + e.toString());
			}				
		}	
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	void onIdleTimeout() {
		try {
			// if is timeout handler -> notify handler			
			if (isTimeoutHandler) {
				boolean handled = ((ITimeoutHandler)appHandler).onIdleTimeout(this);
				internalFlush();
				if (!handled) {
					close();
				}
			} else {
				close();
			}		
		} catch (Exception e) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by handling idle timeout event. Reason: " + e.toString());
			}				
		}	
	}

	private static synchronized Timer getTimer() {
		if (watchDogTimer == null) {
			watchDogTimer = new Timer("xNBCWatchdogTimer", true);
		}
		return watchDogTimer;
	}
	
	
	static boolean isSynchronized(IHandler hdl) {
		if (hdl == null) {
			return true;
		}
		
		Synchronized sync = hdl.getClass().getAnnotation(Synchronized.class);
		if (sync != null) {
			Mode scope = sync.value();
			return scope == Mode.CONNECTION; 
		} else {
			return true;
		}
	}
	
	
	@Override
	protected void onDataEvent() {
		receive();
		
		if (isDataHandler) {
			
			Runnable task = new Runnable() {
				public void run() {
					try {
						boolean remaingDataToHandle = false;
						int modifyVersion = 0;
						
						do {
							remaingDataToHandle = false;
							modifyVersion = getReadQueue().getModifyVersion();
							((IDataHandler) appHandler).onData(NonBlockingConnection.this);
							
							if (!getReadQueue().isEmpty()) {
								if (modifyVersion != getReadQueue().getModifyVersion()) {
									remaingDataToHandle = true;
								}
							}
							
						} while (remaingDataToHandle);
					
						
					} catch (MaxReadSizeExceededException mee) {
						try {
							close();
						} catch (Exception fe) { 
							// ignore
						}
						
					} catch (BufferUnderflowException bue) {
						// ignore
						
					} catch (Exception e) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("[" + getId() + "] error occured by handling data. Reason: " + e.toString());
						}
					}
				}
			};
			
			taskQueue.processTask(workerPool, task, taskProcessor);
		}
	}
	
	
	protected boolean listenForConnect() {
		return isConnectHandler;
	}
	
	

	
	@Override
	protected void onConnectEvent() {
		if (isConnectHandler) {
				
			Runnable task = new Runnable() {
				public void run() {
					try {
						((IConnectHandler) appHandler).onConnect(NonBlockingConnection.this);
					} catch (Exception e) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("[" + getId() + "] error occured by handling connect. Reason: " + e.toString());
						}
					} finally {
						try {
							internalFlush();
						} catch (Exception e) { 
							// ignore
						}
					}
				}
			};
				
			taskQueue.processTask(workerPool, task, taskProcessor);
		}	
	}

	protected boolean listenForDisconnect() {
		return isDisconnectHandler;
	}
	
	
	@Override
	protected void onDisconnectEvent() {
		if (isDisconnectHandler) {
			
			Runnable task = new Runnable() {
				public void run() {
					try {
						((IDisconnectHandler) appHandler).onDisconnect(NonBlockingConnection.this);
					} catch (Exception e) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("[" + getId() + "] error occured by handling connect. Reason: " + e.toString());
						}
					}
				}
			};
			
			taskQueue.processTask(workerPool, task, taskProcessor);
		}
	}
	
	
	@Override
	protected void onConnectionTimeoutEvent() {
		if (isTimeoutHandler) {
			
			Runnable task = new Runnable() {
				public void run() {
					try {
						boolean isHandled = ((ITimeoutHandler) appHandler).onConnectionTimeout(NonBlockingConnection.this);
						if (!isHandled) {
							close();
						}
					} catch (Exception e) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("[" + getId() + "] error occured by handling onConnectionTimeout. Reason: " + e.toString());
						}
					}
				}
			};
			
			taskQueue.processTask(workerPool, task, taskProcessor);

			
		} else {
			try {
				close();
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + getId() + "] error occured closing connection caused by connection timeout. Reason: " + ioe.toString());
				}
			}			
		}
	}
	
	@Override
	protected void onIdleTimeoutEvent() {
		if (isTimeoutHandler) {
			
			Runnable task = new Runnable() {
				public void run() {
					try {
						boolean isHandled = ((ITimeoutHandler) appHandler).onIdleTimeout(NonBlockingConnection.this);
						if (!isHandled) {
							close();
						}
					} catch (Exception e) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("[" + getId() + "] error occured by handling onIdleTimeout. Reason: " + e.toString());
						}
					}
				}
			};
			
			taskQueue.processTask(workerPool, task, taskProcessor);
			
		} else {
			try {
				close();
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + getId() + "]  error occured closing connection caused by idle timeout. Reason: " + ioe.toString());
				}
			}
		}
	}
	
	
	
	private final class TaskQueue {
		
		private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<Runnable>(); 
		
		public void processTask(IWorkerPool workerPool, Runnable newTask, Runnable taskProcessor) {
			
			// add task to task queue
			tasks.offer(newTask);
	
			// process the task
			workerPool.execute(taskProcessor);
		}
	}
	

	
	private final class SynchronizedTaskProcessor implements Runnable {
		
		public void run() {
			synchronized (NonBlockingConnection.this) {
				Runnable task = taskQueue.tasks.poll();
				if (task != null) {
					try {
						task.run();
					} catch (Exception e) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("error occured by proccesing task " + task);
						}
					}
				}
			}
		}
	}
	
	
	private final class NonSynchronizedTaskProcessor implements Runnable {
		
		public void run() {
			Runnable task = taskQueue.tasks.poll();
			if (task != null) {
				try {
					task.run();
				} catch (Exception e) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("error occured by proccesing task " + task);
					}
				}
			}
		}
	}
	
	
	private static final class TimeoutWatchdog {
		private TimerTask watchdogTimerTask = null;
		
		private IDispatcher<IoSocketHandler> dispatcher = null;
	
	
		synchronized void setDispatcher(IDispatcher<IoSocketHandler> dispatcher) {
			this.dispatcher = dispatcher;
		}
			
		
		void updateTimeoutCheckPeriod(long idleTimeoutMillis, long connectionTimeoutMillis) {
			long period = idleTimeoutMillis;
			if (connectionTimeoutMillis < idleTimeoutMillis) {
				period = connectionTimeoutMillis;
			}

			
			if (period > (MIN_WATCHDOG_PERIOD_MILLIS * 5)) {
				period = MIN_WATCHDOG_PERIOD_MILLIS * 5;
			}
			
			setTimeoutCheckPeriod((int) (((double) period) / 5));
		}	
		
		
		private void setTimeoutCheckPeriod(long period) {
			if (watchdogTimerTask != null) {
				watchdogTimerTask.cancel();
			}
			
			watchdogTimerTask = new TimerTask() {
				@Override
				public void run() {
					checkDispatcherTimeout();
				}
			};
			
			getTimer().schedule(watchdogTimerTask, period, period);
		}	
		
		
		void shutdown() {
			if (watchdogTimerTask != null) {
				watchdogTimerTask.cancel();
			}
		}

		
		private synchronized void checkDispatcherTimeout() {
			try {
				long current = System.currentTimeMillis();
				Set<IoSocketHandler> socketHandlers = dispatcher.getRegistered();
				for (IoSocketHandler socketHandler : socketHandlers) {
						
					checkTimeout(socketHandler, current);	
				}
			} catch (Exception e) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured: " + e.toString());
				}
			}			
		}
		
		
		
		private void checkTimeout(IoSocketHandler ioSocketHandler, long current) {
			ioSocketHandler.checkConnection();
			ioSocketHandler.checkIdleTimeout(current);
			ioSocketHandler.checkConnectionTimeout(current);
		}
	}
}
