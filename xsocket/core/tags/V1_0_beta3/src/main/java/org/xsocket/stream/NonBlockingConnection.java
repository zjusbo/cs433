// $Id: NonBlockingConnection.java 778 2007-01-16 07:13:20Z grro $
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
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.xsocket.ClosedConnectionException;
import org.xsocket.DataConverter;
import org.xsocket.WorkerPool;
import org.xsocket.stream.IoHandler.IIOEventHandler;



/**
 * Implementation of the <code>INonBlockingConnection</code> interface. <br><br>
 * 
 * The methods of this class are not thread-safe. 
 * 
 * @author grro@xsocket.org
 */
public final class NonBlockingConnection extends Connection implements INonBlockingConnection {

	private static final Logger LOG = Logger.getLogger(BlockingConnection.class.getName());
		
	private static WorkerPool defaultWorkerPool = null;

	private IHandler appHandler = null;
	private boolean isConnectHandler = false;
    private boolean isDisconnectHandler = false;
	private boolean isDataHandler = false;
	private boolean isTimeoutHandler = false;

	
	/**
	 * constructor. <br><br>
	 *
     * @param hostname  the remote host
	 * @param port		the port of the remote host to connect
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port) throws IOException {
		this(hostname, port, null, false);
	}

	
	/**
	 * constructor
	 * 
	 * @param hostname         the remote host
	 * @param port             the remote port
	 * @param appHandler       the application handler 
	 * @param workerPoolSize   the worker pool to use
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port, IDataHandler appHandler, int workerPoolSize) throws IOException {
		this(new IoSocketHandler(SocketChannel.open(new InetSocketAddress(hostname, port)), "c.", null, null, new WorkerPool(workerPoolSize)), null, false, null, true, appHandler, false, false, true, false, true);
	}
	
	
	/**
	 * constructor
	 * 
	 * @param hostname         the remote host
	 * @param port             the remote port
	 * @param sslContext       the ssl context to use
	 * @param startSSL         true, if SSL should be activated
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port, SSLContext sslContext, boolean startSSL) throws IOException {
		this(new IoSocketHandler(SocketChannel.open(new InetSocketAddress(hostname, port)), "c.", null, null, getDefaultWorkerPool()), sslContext, startSSL);
	}
	
	private NonBlockingConnection(IoSocketHandler socketHandler, SSLContext sslContext, boolean startSSL) throws IOException {
		this(socketHandler, sslContext, startSSL, socketHandler.getMemoryManager(), true, null, false, false, true, false, true);
	}
	
	
	/**
	 * constructor 
	 * 
	 * @param socketHandler        the sockdet io handler
	 * @param sslContext           the ssl context to use
	 * @param startSSL             true, if SSL should be activated
	 * @param sslMemoryManager     the ssl memory manager 
	 * @param isClient             true, is is in cleint mode
	 * @param appHandler           the assigned application handler
	 * @param isConnectHandler     true, is is connect handler
	 * @param isDisconnectHandler  true, if is disconnect handler
	 * @param isDataHandler        true, if is data handler
	 * @param isTimeoutHandler     true, if is timeout handler
	 * @param autoflush            true, if autoflush should be activated
	 * @throws IOException If some other I/O error occurs
	 */
	NonBlockingConnection(IoSocketHandler socketHandler, SSLContext sslContext, boolean startSSL, IMemoryManager sslMemoryManager, boolean isClient, IHandler appHandler, boolean isConnectHandler, boolean isDisconnectHandler, boolean isDataHandler, boolean isTimeoutHandler, boolean autoflush) throws IOException {
		super(autoflush);
		
		this.appHandler = appHandler;
		this.isConnectHandler = isConnectHandler;
		this.isDataHandler = isDataHandler;
		this.isTimeoutHandler = isTimeoutHandler;
		this.isDisconnectHandler = isDisconnectHandler;

		socketHandler.setIOEventHandler(new IOEventHandler());
		
		if (sslContext != null) {
			IoSSLHandler sslHandler = new IoSSLHandler(socketHandler, sslContext, startSSL, isClient, sslMemoryManager);
			setIOHandler(sslHandler);
			open();

		} else {
			setIOHandler(socketHandler);
			open();
		}
	}
		
	
	private static WorkerPool getDefaultWorkerPool() {
		if (defaultWorkerPool == null) {
			defaultWorkerPool = new WorkerPool(1);
		}
		return defaultWorkerPool;
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
		LinkedList<ByteBuffer> queue = extractAvailableFromReadQueue();
		if (queue != null) {
			return queue.toArray(new ByteBuffer[queue.size()]);
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
	public byte readByte() throws IOException, ClosedConnectionException, BufferUnderflowException {
		return extractByteFromReadQueue();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException, ClosedConnectionException, BufferUnderflowException {
		ByteBufferOutputChannel channel = new ByteBufferOutputChannel();
		extractBytesByDelimiterFromReadQueue(delimiter, channel);
	
		return channel.toByteBufferArray();
	}
	
	
	/**
	 * {@inheritDoc}
	 */	
	public ByteBuffer[] readByteBufferByLength(int length) throws IOException, ClosedConnectionException, BufferUnderflowException {
		ByteBufferOutputChannel channel = new ByteBufferOutputChannel();
		extractBytesByLength(length, channel);
	
		return channel.toByteBufferArray();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public byte[] readBytesByDelimiter(String delimiter) throws IOException, ClosedConnectionException, BufferUnderflowException {
		return DataConverter.toArray(readByteBufferByDelimiter(delimiter));
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public byte[] readBytesByLength(int length) throws IOException, ClosedConnectionException, BufferUnderflowException {
		return DataConverter.toArray(readByteBufferByLength(length));
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
	public String readStringByDelimiter(String delimiter) throws IOException, ClosedConnectionException, BufferUnderflowException, UnsupportedEncodingException {
		return readStringByDelimiter(delimiter, getDefaultEncoding());
	}
	

	/**
	 * {@inheritDoc}
	 */
	public String readStringByDelimiter(String delimiter, String encoding) throws IOException, ClosedConnectionException, BufferUnderflowException, UnsupportedEncodingException {
		ByteBufferOutputChannel channel = new ByteBufferOutputChannel();
		extractBytesByDelimiterFromReadQueue(delimiter, channel);

		return DataConverter.toString(channel.toByteBufferArray(), encoding);
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
		ByteBufferOutputChannel channel = new ByteBufferOutputChannel();
		extractBytesByLength(length, channel);

		return DataConverter.toString(channel.toByteBufferArray(), encoding);
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
				flush();
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
				flush();
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
	
	
	
	
	private final class IOEventHandler implements IIOEventHandler {
		
		public boolean listenForData() {
			return isDataHandler; 
		}
		
		public void onDataEvent() {
			receive();
			
			if (isDataHandler) {
				try {
					
					((IDataHandler) appHandler).onData(NonBlockingConnection.this);
				
				} catch (BufferUnderflowException bue) {
					// ignore
					
				} catch (Exception e) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + getId() + "] error occured by handling data. Reason: " + e.toString());
					}
				} finally {
					try {
						flush();
					} catch (Exception e) { 
						// ignore
					}
				}
			}
		}

		public boolean listenForConnect() {
			return isConnectHandler;
		}
		
		public void onConnectEvent() {
			if (isConnectHandler) {
				try {
					((IConnectHandler) appHandler).onConnect(NonBlockingConnection.this);
				} catch (Exception e) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + getId() + "] error occured by handling connect. Reason: " + e.toString());
					}
				} finally {
					try {
						flush();
					} catch (Exception e) { 
						// ignore
					}
				}
			}
		}
		
		public boolean listenForDisconnect() {
			return isDisconnectHandler;
		}
		
		public void onDisconnectEvent() {
			if (isDisconnectHandler) {
				try {
					((IDisconnectHandler) appHandler).onDisconnect(NonBlockingConnection.this.getId());
				} catch (Exception e) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + getId() + "] error occured by handling connect. Reason: " + e.toString());
					}
				}
			}
		}			
		
		public void onConnectionTimeout() {
			if (isTimeoutHandler) {
				try {
					((ITimeoutHandler) appHandler).onConnectionTimeout(NonBlockingConnection.this);
				} catch (Exception e) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + getId() + "] error occured by handling onConnectionTimeout. Reason: " + e.toString());
					}
				}
			}			
		}
		
		public void onIdleTimeout() {
			if (isTimeoutHandler) {
				try {
					((ITimeoutHandler) appHandler).onIdleTimeout(NonBlockingConnection.this);
				} catch (Exception e) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + getId() + "] error occured by handling onIdleTimeout. Reason: " + e.toString());
					}
				}
			}			
		}
	}
}
