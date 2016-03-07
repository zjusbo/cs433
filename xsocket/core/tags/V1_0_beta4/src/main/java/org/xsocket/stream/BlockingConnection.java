// $Id: BlockingConnection.java 910 2007-02-12 16:56:19Z grro $
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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import javax.net.ssl.SSLContext;

import org.xsocket.ClosedConnectionException;
import org.xsocket.DataConverter;
import org.xsocket.TimeoutException;
import org.xsocket.WorkerPool;
import org.xsocket.stream.IoHandler.IIOEventHandler;


/**
 * Implementation of the <code>IBlockingConnection</code> interface. <br><br>
 * 
 * The methods of this class are not thread-safe. 
 *
 * @author grro@xsocket.org
 */
public final class BlockingConnection extends Connection implements IBlockingConnection {

	private static final WorkerPool WORKER_POOL = new WorkerPool(1);
	
	// thread handling
	private final Set<Thread> waitingReadThreads = new HashSet<Thread>();	
	private long readTimeout = 0; 
	private long sleepDuration = 0;
		

	/**
	 * constructor. <br><br>
	 *
     * @param hostname  the remote host
	 * @param port		the port of the remote host to connect
	 * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(String hostname, int port) throws IOException {
		this(hostname, port, null, false, null, null);
	}
	
	
	/**
	 * constructor 
	 * 
	 * @param address  the remote host address
	 * @param port     the remote host port
	 * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(InetAddress address, int port) throws IOException {
		this(address.getHostAddress(), port, null, false, null, null);
	}

	


	/**
	 * constructor
	 * 
	 * @param address                  the remote address
	 * @param port                     the remote host port
	 * @param memoryPreallocationSize  the receive buffer preallocation size
	 * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(InetAddress address, int port, int memoryPreallocationSize) throws IOException {
		this(address.getHostName(), port, new MemoryManager(memoryPreallocationSize, true));
	}
	

	/**
	 * constructor
	 * 
	 * @param hostname                 the remote hostname
	 * @param port                     the remote host port
	 * @param memoryPreallocationSize  the receive buffer preallocation size
	 * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(String hostname, int port, int memoryPreallocationSize) throws IOException {
		this(hostname, port, new MemoryManager(memoryPreallocationSize, true));
	}
	
	
	

	private BlockingConnection(String hostname, int port, IMemoryManager memoryManager) throws IOException {
		this(hostname, port, null, false, memoryManager, memoryManager);
	}
	
	
	/**
	 * constructor 
	 * 
	 * @param address      the remote host name
	 * @param port         the remote host port
	 * @param sslContext   the sslContext to use
	 * @param startSSL     true, is SSL mode should be activated
	 * @throws IOException If some other I/O error occurs 
	 */
	public BlockingConnection(InetAddress address, int port, SSLContext sslContext, boolean startSSL) throws IOException {
		this(address.getHostAddress(), port, sslContext, startSSL, null, null);
	}
	

	/**
	 * constructor 
	 * 
	 * @param hostname     the remote host name
	 * @param port         the remote host port
	 * @param sslContext   the sslContext to use
	 * @param startSSL     true, is SSL mode should be activated
	 * @throws IOException If some other I/O error occurs 
	 */
	public BlockingConnection(String hostname, int port, SSLContext sslContext, boolean startSSL) throws IOException {
		this(hostname, port, sslContext, startSSL, null, null);
	}

	
	private BlockingConnection(String hostname, int port, SSLContext sslContext, boolean startSSL, IMemoryManager memoryManager, IMemoryManager sslMemoryManager) throws IOException {
		this(new InetSocketAddress(hostname, port), sslContext, startSSL, memoryManager, sslMemoryManager);		
	}

	private BlockingConnection(InetSocketAddress inetAddress, SSLContext sslContext, boolean startSSL, IMemoryManager memoryManager, IMemoryManager sslMemoryManager) throws IOException {
		this(new IoSocketHandler(SocketChannel.open(inetAddress), "c.", memoryManager, null, WORKER_POOL), sslContext, startSSL);		
	}

	
	private BlockingConnection(IoSocketHandler socketHandler, SSLContext sslContext, boolean startSSL) throws IOException {
		this(socketHandler, sslContext, startSSL, socketHandler.getMemoryManager());
	}
	
	
	private BlockingConnection(IoSocketHandler socketHandler, SSLContext sslContext, boolean startSSL, IMemoryManager sslMemoryManager) throws IOException {
		super(true);
		
		socketHandler.setIOEventHandler(new IOEventHandler());
		if (sslContext != null) {
			IoSSLHandler sslHandler = new IoSSLHandler(socketHandler, sslContext, startSSL, true, sslMemoryManager);
			setIOHandler(sslHandler);
			open();

		} else {
			setIOHandler(socketHandler);
			open();
		}
		
		setReceiveTimeout(INITIAL_RECEIVE_TIMEOUT);
	}

	 
	/**
	 * {@inheritDoc}
	 * 
	 **/	
	public byte readByte() throws IOException ,ClosedConnectionException, SocketTimeoutException {
	
		long start = System.currentTimeMillis();
		do {
			synchronized (waitingReadThreads) {
				try {
					return extractByteFromReadQueue();
				} catch (BufferUnderflowException bue) {
					waitingReadThreads.add(Thread.currentThread());
				}
			}
			sleep(sleepDuration);

		} while (System.currentTimeMillis() < (start + readTimeout));
		
		throw new TimeoutException("timeout " + DataConverter.toFormatedDuration(readTimeout) + " reached"); 
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final void setReceiveTimeout(long timeout) {
		this.readTimeout = timeout;
		sleepDuration = readTimeout / 5;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException, ClosedConnectionException, SocketTimeoutException {

		long start = System.currentTimeMillis();
		do {
			synchronized (waitingReadThreads) {
				try {
					LinkedList<ByteBuffer> result = extractBytesByDelimiterFromReadQueue(delimiter);
					return result.toArray(new ByteBuffer[result.size()]);
				} catch (BufferUnderflowException bue) {
					waitingReadThreads.add(Thread.currentThread());
				}
			}
			sleep(sleepDuration);
			
		} while (System.currentTimeMillis() < (start + readTimeout));
		
		throw new TimeoutException("timeout " + DataConverter.toFormatedDuration(readTimeout) + " reached"); 
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public ByteBuffer[] readByteBufferByLength(int length) throws IOException, ClosedConnectionException, SocketTimeoutException {

		if (length <= 0) {
			return null;
		}
		
		long start = System.currentTimeMillis();
		do {
			synchronized (waitingReadThreads) {
				try {
					LinkedList<ByteBuffer> result = extractBytesByLength(length);
					return result.toArray(new ByteBuffer[result.size()]);
				} catch (BufferUnderflowException bue) {
					waitingReadThreads.add(Thread.currentThread());
				}
			}
			sleep(sleepDuration);

		} while (System.currentTimeMillis() < (start + readTimeout));
		
		throw new TimeoutException("timeout " + DataConverter.toFormatedDuration(readTimeout) + " reached"); 
	}

	
	/**
	 * {@inheritDoc}
	 */
	public byte[] readBytesByDelimiter(String delimiter) throws IOException, ClosedConnectionException, SocketTimeoutException {
		return DataConverter.toBytes(readByteBufferByDelimiter(delimiter));
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public byte[] readBytesByLength(int length) throws IOException, ClosedConnectionException, SocketTimeoutException {
		return DataConverter.toBytes(readByteBufferByLength(length));
	}
	
	/**
	 * {@inheritDoc}
	 */
	public double readDouble() throws IOException, ClosedConnectionException, SocketTimeoutException {
		long start = System.currentTimeMillis();
		do {
			synchronized (waitingReadThreads) {
				try {
					return extractDoubleFromReadQueue();
				} catch (BufferUnderflowException bue) {
					waitingReadThreads.add(Thread.currentThread());
				}
			}
			sleep(sleepDuration);

		} while (System.currentTimeMillis() < (start + readTimeout));
		
		throw new TimeoutException("timeout " + DataConverter.toFormatedDuration(readTimeout) + " reached"); 

	}
	
	/**
	 * {@inheritDoc}
	 */
	public int readInt() throws IOException, ClosedConnectionException, SocketTimeoutException {
		long start = System.currentTimeMillis();
		do {
			synchronized (waitingReadThreads) {
				try {
					return extractIntFromReadQueue();
				} catch (BufferUnderflowException bue) {
					waitingReadThreads.add(Thread.currentThread());
				}
			}
			sleep(sleepDuration);

		} while (System.currentTimeMillis() < (start + readTimeout));
		
		throw new TimeoutException("timeout " + DataConverter.toFormatedDuration(readTimeout) + " reached"); 

	}
	
	
	/**
	 * {@inheritDoc}. 
	 */
	public final int read(ByteBuffer buffer) throws IOException {
		int savedPos = buffer.position();
		int savedLimit = buffer.limit();
		
		int size = buffer.remaining();
		
		
		ByteBuffer[] bufs = readByteBufferByLength(size);
		
		for (ByteBuffer buf : bufs) {
			buffer.put(buf);
		}
		
		buffer.position(savedPos);
		buffer.limit(savedLimit);
		return size;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public long readLong() throws IOException, ClosedConnectionException, SocketTimeoutException {
		long start = System.currentTimeMillis();
		do {
			synchronized (waitingReadThreads) {
				try {
					return extractLongFromReadQueue();
				} catch (BufferUnderflowException bue) {
					waitingReadThreads.add(Thread.currentThread());
				}
			}
			sleep(sleepDuration);
			
		} while (System.currentTimeMillis() < (start + readTimeout));
		
		throw new TimeoutException("timeout " + DataConverter.toFormatedDuration(readTimeout) + " reached"); 
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public String readStringByDelimiter(String delimiter) throws IOException, ClosedConnectionException, SocketTimeoutException {
		return readStringByDelimiter(delimiter, getDefaultEncoding());
	}

	/**
	 * {@inheritDoc}
	 */
	public String readStringByDelimiter(String delimiter, String encoding) throws IOException, ClosedConnectionException, SocketTimeoutException {
		long start = System.currentTimeMillis();
		do {
			synchronized (waitingReadThreads) {
				try {
					LinkedList<ByteBuffer> extracted = extractBytesByDelimiterFromReadQueue(delimiter);
					return DataConverter.toString(extracted, encoding);
				} catch (BufferUnderflowException bue) {
					waitingReadThreads.add(Thread.currentThread());
				}
			}
			sleep(sleepDuration);
			
		} while (System.currentTimeMillis() < (start + readTimeout));
		
		throw new TimeoutException("timeout " + DataConverter.toFormatedDuration(readTimeout) + " reached"); 
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public String readStringByLength(int length) throws IOException, ClosedConnectionException, SocketTimeoutException {
		return readStringByLength(length, getDefaultEncoding());
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public String readStringByLength(int length, String encoding) throws IOException, ClosedConnectionException, SocketTimeoutException {
		
		if (length <= 0) {
			return null;
		}
		
		long start = System.currentTimeMillis();
		do {
			synchronized (waitingReadThreads) {
				try {
					LinkedList<ByteBuffer> extracted = extractBytesByLength(length);
					return DataConverter.toString(extracted, encoding);
				} catch (BufferUnderflowException bue) {
					waitingReadThreads.add(Thread.currentThread());
				}
			}
			sleep(sleepDuration);
			
		} while (System.currentTimeMillis() < (start + readTimeout));
		
		throw new TimeoutException("timeout " + DataConverter.toFormatedDuration(readTimeout) + " reached"); 
	}
	


	
	private void sleep(long duration) throws ClosedConnectionException, IOException {
		try {
			Thread.sleep(duration);
		} catch (InterruptedException ignore) { }
	}


	
	private final class IOEventHandler implements IIOEventHandler {
	
		public boolean listenForData() {
			return true;
		}
		
		public void onDataEvent() {
			receive();
				
			synchronized (waitingReadThreads) {
				if (!waitingReadThreads.isEmpty()) {
					for (Thread waitingThread : waitingReadThreads) {
						waitingThread.interrupt();
					}
					waitingReadThreads.clear();
				}
			}			
		}

		public boolean listenForConnect() {
			return false;
		}
		
		public void onConnectEvent() {
			// ignore
		}

		public boolean listenForDisconnect() {
			return false;
		}
		
		public void onDisconnectEvent() {
			// ignore
		}
		
		public void onConnectionTimeout() {
			// ignore
		}
		
		public void onIdleTimeout() {
			// ignore
		}
	}
}
