// $Id: BlockingConnection.java 1281 2007-05-29 19:48:07Z grro $
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
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

import javax.net.ssl.SSLContext;

import org.xsocket.ClosedConnectionException;
import org.xsocket.DataConverter;
import org.xsocket.MaxReadSizeExceededException;



/**
 * Implementation of the <code>IBlockingConnection</code> interface. <br><br>
 * 
 * The methods of this class are not thread-safe. 
 *
 * @author grro@xsocket.org
 */
public final class BlockingConnection extends Connection implements IBlockingConnection {
		
	
	// read thread handling
	private Object readGuard = new Object();
	private long receiveTimeout = 0; 

		

	/**
	 * constructor. <br><br>
	 *
     * @param hostname  the remote host
	 * @param port		the port of the remote host to connect
	 * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(String hostname, int port) throws IOException {
		this(new InetSocketAddress(hostname, port), null,  null, false);
	}
	
	
	/**
	 * constructor. <br><br>
	 *
     * @param hostname             the remote host
	 * @param port		           the port of the remote host to connect
	 * @param socketConfiguration  the socket configuration
	 * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(String hostname, int port, StreamSocketConfiguration socketConfiguration) throws IOException {
		this(new InetSocketAddress(hostname, port), socketConfiguration,  null, false);
	}
	
	
	
	/**
	 * constructor 
	 * 
	 * @param address  the remote host address
	 * @param port     the remote host port
	 * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(InetAddress address, int port) throws IOException {
		this(new InetSocketAddress(address, port), null, null, false);
	}

	/**
	 * constructor 
	 * 
	 * @param address     the remote host address
	 * @param port        the remote host port
	 * @param socketConf  the socket configuration 
	 * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(InetAddress address, int port, StreamSocketConfiguration socketConf) throws IOException {
		this(new InetSocketAddress(address, port), socketConf, null, false);
	}

		
	
	
	
	/**
	 * constructor 
	 * 
	 * @param address              the remote host name
	 * @param port                 the remote host port
	 * @param sslContext           the sslContext to use
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IConnection#activateSecuredMode()})
	 * @throws IOException If some other I/O error occurs 
	 */
	public BlockingConnection(InetAddress address, int port, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(address, port), null, sslContext, sslOn);
	}

	
	
	/**
	 * constructor 
	 * 
	 * @param address              the remote host name
	 * @param port                 the remote host port
	 * @param socketConf           the socket configuration 
	 * @param sslContext           the sslContext to use
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IConnection#activateSecuredMode()})
     * @throws IOException If some other I/O error occurs 
	 */
	public BlockingConnection(InetAddress address, int port, StreamSocketConfiguration socketConf, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(address, port), socketConf, sslContext, sslOn);
	}
	
	/**
	 * constructor 
	 * 
	 * @param hostname             the remote host name
	 * @param port                 the remote host port
	 * @param sslContext           the sslContext to use
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IConnection#activateSecuredMode()})
*    * @throws IOException If some other I/O error occurs 
	 */
	public BlockingConnection(String hostname, int port, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(hostname, port), null, sslContext, sslOn);
	}

	
	/**
	 * constructor 
	 * 
	 * @param hostname             the remote host name
	 * @param port                 the remote host port
	 * @param socketConf           the socket configuration 
	 * @param sslContext           the sslContext to use
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IConnection#activateSecuredMode()})
     * @throws IOException If some other I/O error occurs 
	 */
	public BlockingConnection(String hostname, int port, StreamSocketConfiguration socketConf, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(hostname, port), socketConf, sslContext, sslOn);
	}
	
	
	/**
	 * intermediate constructor, which uses the global dispatcher
	 */
	private BlockingConnection(InetSocketAddress remoteAddress, StreamSocketConfiguration socketConf, SSLContext sslContext, boolean sslOn) throws IOException {
		this(createClientIoSocketHandler(remoteAddress, getGlobalMemoryManager(), getGlobalDispatcher(), socketConf), sslContext, sslOn, getGlobalMemoryManager());
	}

	
	private BlockingConnection(IoSocketHandler socketHandler, SSLContext sslContext, boolean sslOn, IMemoryManager sslMemoryManager) throws IOException {
		setReceiveTimeoutMillis(INITIAL_RECEIVE_TIMEOUT);
		
		setFlushmode(FlushMode.SYNC);
		init(socketHandler, sslContext, sslOn, true, sslMemoryManager);
	}

	
	@Override
	void reset() {
		readGuard = new Object();

		super.reset();		

		setReceiveTimeoutMillis(IBlockingConnection.INITIAL_RECEIVE_TIMEOUT); 
		setFlushmode(FlushMode.SYNC);
	}
	 
	
	
	/**
	 * {@inheritDoc}
	 * 
	 **/	
	public byte readByte() throws IOException ,ClosedConnectionException, SocketTimeoutException {
	
		long start = System.currentTimeMillis();
		long remainingTime = receiveTimeout;
		
		synchronized (readGuard) {
			do {
				try {
					return extractByteFromReadQueue();
				} catch (BufferUnderflowException bue) {
					try {
						readGuard.wait(remainingTime);
					} catch (InterruptedException ignore) { }					
				}
				remainingTime = (start + receiveTimeout) - System.currentTimeMillis();
			} while (remainingTime > 0);
		}
		
		throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(receiveTimeout) + " reached"); 
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final void setReceiveTimeoutMillis(long timeout) {
		this.receiveTimeout = timeout;
	}
	

	/**
	 * {@inheritDoc}
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException, ClosedConnectionException, SocketTimeoutException {
		return readByteBufferByDelimiter(delimiter, Integer.MAX_VALUE);
	}


	/**
	 * {@inheritDoc}
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter, int maxLength) throws IOException, ClosedConnectionException, SocketTimeoutException, MaxReadSizeExceededException {

		long start = System.currentTimeMillis();
		long remainingTime = receiveTimeout;
		
		synchronized (readGuard) {
			do {
				try {
					LinkedList<ByteBuffer> result = extractBytesByDelimiterFromReadQueue(delimiter, maxLength);
					return result.toArray(new ByteBuffer[result.size()]);
				} catch (MaxReadSizeExceededException mee) {
					throw mee;
					
				} catch (BufferUnderflowException bue) {
					try {
						readGuard.wait(remainingTime);
					} catch (InterruptedException ignore) { }					
				}
				remainingTime = (start + receiveTimeout) - System.currentTimeMillis();
			} while (remainingTime > 0);
		}
		
		throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(receiveTimeout) + " reached"); 
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public ByteBuffer[] readByteBufferByLength(int length) throws IOException, ClosedConnectionException, SocketTimeoutException {

		if (length <= 0) {
			return null;
		}
		
		long start = System.currentTimeMillis();
		long remainingTime = receiveTimeout;
		
		synchronized (readGuard) {
			do {
				try {
					LinkedList<ByteBuffer> result = extractBytesByLength(length);
					return result.toArray(new ByteBuffer[result.size()]);
				} catch (BufferUnderflowException bue) {
					try {
						readGuard.wait(remainingTime);
					} catch (InterruptedException ignore) { }					
				}
				remainingTime = (start + receiveTimeout) - System.currentTimeMillis();
			} while (remainingTime > 0);
		}
		
		throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(receiveTimeout) + " reached"); 
	}

	
	/**
	 * {@inheritDoc}
	 */
	public byte[] readBytesByDelimiter(String delimiter) throws IOException ,ClosedConnectionException ,SocketTimeoutException {
		return readBytesByDelimiter(delimiter, Integer.MAX_VALUE);
	}
	

	/**
	 * {@inheritDoc}
	 */
	public byte[] readBytesByDelimiter(String delimiter, int maxLength) throws IOException, ClosedConnectionException, SocketTimeoutException, MaxReadSizeExceededException {
		return DataConverter.toBytes(readByteBufferByDelimiter(delimiter, maxLength));
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
		long remainingTime = receiveTimeout;
		
		synchronized (readGuard) {
			do {
				try {
					return extractDoubleFromReadQueue();
				} catch (BufferUnderflowException bue) {
					try {
						readGuard.wait(remainingTime);
					} catch (InterruptedException ignore) { }					
				}
				remainingTime = (start + receiveTimeout) - System.currentTimeMillis();
			} while (remainingTime> 0);
		}
		
		throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(receiveTimeout) + " reached"); 

	}
	
	/**
	 * {@inheritDoc}
	 */
	public int readInt() throws IOException, ClosedConnectionException, SocketTimeoutException {
		long start = System.currentTimeMillis();
		long remainingTime = receiveTimeout;
		
		synchronized (readGuard) {
			do {
				try {
					return extractIntFromReadQueue();
				} catch (BufferUnderflowException bue) {
					try {
						readGuard.wait(remainingTime);
					} catch (InterruptedException ignore) { }					
				}
				remainingTime = (start + receiveTimeout) - System.currentTimeMillis();
			} while (remainingTime > 0 );
		}
		
		throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(receiveTimeout) + " reached"); 

	}
	
	
	/**
	 * {@inheritDoc}. 
	 */
	public final int read(ByteBuffer buffer) throws IOException {
		int size = buffer.remaining();
		if (size < 1) {
			return 0;
		}
		
		long start = System.currentTimeMillis();
		long remainingTime = receiveTimeout;
		
		synchronized (readGuard) {
			do {
				int availableSize =  getReadQueue().getSize();
				
				// if at least one byte is available -> read and return
				if (availableSize > 0) {
					if (size > availableSize) {
						size = availableSize;
					}
					ByteBuffer[] bufs = readByteBufferByLength(size);
					
					for (ByteBuffer buf : bufs) {
						buffer.put(buf);
					}			
					
					return size;
					
				// ... or wait for at least one byte 
				}else {
					try {
						readGuard.wait(remainingTime);
					} catch (InterruptedException ignore) { }					
				}
				remainingTime = (start + receiveTimeout) - System.currentTimeMillis();
			} while (remainingTime > 0);
		}
				
		throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(receiveTimeout) + " reached"); 
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public long readLong() throws IOException, ClosedConnectionException, SocketTimeoutException {
		long start = System.currentTimeMillis();
		long remainingTime = receiveTimeout;
		
		synchronized (readGuard) {
			do {
				try {
					return extractLongFromReadQueue();
				} catch (BufferUnderflowException bue) {
					try {
						readGuard.wait(remainingTime);
					} catch (InterruptedException ignore) { }					
				}
				remainingTime = (start + receiveTimeout) - System.currentTimeMillis();
			} while (remainingTime > 0);
		}
				
		throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(receiveTimeout) + " reached"); 
	}

	
	
	/**
	 * {@inheritDoc}
	 */
	public String readStringByDelimiter(String delimiter) throws IOException, ClosedConnectionException, UnsupportedEncodingException, SocketTimeoutException {
		return readStringByDelimiter(delimiter, Integer.MAX_VALUE);
	}


	/**
	 * {@inheritDoc}
	 */
	public String readStringByDelimiter(String delimiter, int maxLength) throws IOException ,ClosedConnectionException ,java.io.UnsupportedEncodingException ,SocketTimeoutException ,MaxReadSizeExceededException {
		return readStringByDelimiter(delimiter, getDefaultEncoding(), maxLength);		
	};


	/**
	 * {@inheritDoc}
	 */
	public String readStringByDelimiter(String delimiter, String encoding) throws IOException, ClosedConnectionException, UnsupportedEncodingException, SocketTimeoutException {
		return readStringByDelimiter(delimiter, encoding, Integer.MAX_VALUE);
	}

	/**
	 * {@inheritDoc}
	 */
	public String readStringByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, ClosedConnectionException, UnsupportedEncodingException, SocketTimeoutException, MaxReadSizeExceededException {
		long start = System.currentTimeMillis();
		long remainingTime = receiveTimeout;
		
		synchronized (readGuard) {
			do {
				try {
					LinkedList<ByteBuffer> extracted = extractBytesByDelimiterFromReadQueue(delimiter, maxLength);
					return DataConverter.toString(extracted, encoding);
				} catch (MaxReadSizeExceededException mle) {
					throw mle;
					
				} catch (BufferUnderflowException bue) {
					try {
						readGuard.wait(remainingTime);
					} catch (InterruptedException ignore) { }					
				}
				remainingTime = (start + receiveTimeout) - System.currentTimeMillis();
			} while (remainingTime > 0);
		}
		
		throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(receiveTimeout) + " reached"); 
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getIndexOf(String str) throws IOException ,ClosedConnectionException, SocketTimeoutException {
		return getIndexOf(str, Integer.MAX_VALUE);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public int getIndexOf(String str, int maxLength) throws IOException, ClosedConnectionException, MaxReadSizeExceededException, SocketTimeoutException {
		long start = System.currentTimeMillis();
		long remainingTime = receiveTimeout;
		
		synchronized (readGuard) {
			do {
				try {
					return readIndexOf(str, maxLength);
				} catch (MaxReadSizeExceededException mle) {
					throw mle;
					
				} catch (BufferUnderflowException bue) {
					try {
						readGuard.wait(remainingTime);
					} catch (InterruptedException ignore) { }					
				}
				remainingTime = (start + receiveTimeout) - System.currentTimeMillis();
			} while (remainingTime > 0);
		}
		
		throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(receiveTimeout) + " reached"); 
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
		long remainingTime = receiveTimeout;
		
		synchronized (readGuard) {
			do {
				try {
					LinkedList<ByteBuffer> extracted = extractBytesByLength(length);
					return DataConverter.toString(extracted, encoding);
				} catch (BufferUnderflowException bue) {
					try {
						readGuard.wait(remainingTime);
					} catch (InterruptedException ignore) { }					
				}
				remainingTime = (start + receiveTimeout) - System.currentTimeMillis();
			} while (remainingTime > 0);
		}
		
		throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(receiveTimeout) + " reached"); 
	}
	
	

	@Override
	protected void onDataEvent() {
		synchronized (readGuard) {
			receive();
			readGuard.notify();
		}			
	}
}
