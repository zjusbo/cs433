// $Id: BlockingConnection.java 1020 2007-03-16 16:25:53Z grro $
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
import org.xsocket.stream.IoHandler.IIOEventHandler;



/**
 * Implementation of the <code>IBlockingConnection</code> interface. <br><br>
 * 
 * The methods of this class are not thread-safe. 
 *
 * @author grro@xsocket.org
 */
public final class BlockingConnection extends Connection implements IBlockingConnection {
		
	private static final long SEND_TIMEOUT = 60 * 1000;
	
	
	// read thread handling
	private final Object readGuard = new Object();
	private long receiveTimeout = 0; 

	// write thread handling
	private final Object writeGuard = new Object();
	private IOException writeException = null;
		

	/**
	 * constructor. <br><br>
	 *
     * @param hostname  the remote host
	 * @param port		the port of the remote host to connect
	 * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(String hostname, int port) throws IOException {
		this(new InetSocketAddress(hostname, port), null, false);
	}
	
	
	/**
	 * constructor 
	 * 
	 * @param address  the remote host address
	 * @param port     the remote host port
	 * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(InetAddress address, int port) throws IOException {
		this(new InetSocketAddress(address, port), null, false);
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
		this(new InetSocketAddress(address, port), sslContext, startSSL);
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
		this(new InetSocketAddress(hostname, port), sslContext, startSSL);
	}
	
	
	/**
	 * intermediate constructor, which uses the global dispatcher
	 */
	private BlockingConnection(InetSocketAddress inetAddress, SSLContext sslContext, boolean startSSL) throws IOException {
		this(createcClientIoSocketHandler(inetAddress, getGlobalMemoryManager(), getGlobalDispatcher()), sslContext, startSSL, getGlobalMemoryManager());
	}

	
	private BlockingConnection(IoSocketHandler socketHandler, SSLContext sslContext, boolean startSSL, IMemoryManager sslMemoryManager) throws IOException {
		socketHandler.setIOEventHandler(new IOEventHandler());
		if (sslContext != null) {
			IoSSLHandler sslHandler = new IoSSLHandler(socketHandler, sslContext, startSSL, true, sslMemoryManager);
			setIOHandler(sslHandler);
			open();

		} else {
			setIOHandler(socketHandler);
			open();
		}
		
		setReceiveTimeoutMillis(INITIAL_RECEIVE_TIMEOUT);
	}

	 
	/**
	 * {@inheritDoc}
	 * 
	 **/	
	public byte readByte() throws IOException ,ClosedConnectionException, SocketTimeoutException {
	
		long start = System.currentTimeMillis();
		
		synchronized (readGuard) {
			do {
				try {
					return extractByteFromReadQueue();
				} catch (BufferUnderflowException bue) {
					try {
						readGuard.wait(receiveTimeout / 10);
					} catch (InterruptedException ignore) { }					
				}
			} while (System.currentTimeMillis() < (start + receiveTimeout));
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
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter, int maxLength) throws IOException, ClosedConnectionException, SocketTimeoutException, MaxReadSizeExceededException {

		long start = System.currentTimeMillis();
		synchronized (readGuard) {
			do {
				try {
					LinkedList<ByteBuffer> result = extractBytesByDelimiterFromReadQueue(delimiter, maxLength);
					return result.toArray(new ByteBuffer[result.size()]);
				} catch (MaxReadSizeExceededException mee) {
					throw mee;
					
				} catch (BufferUnderflowException bue) {
					try {
						readGuard.wait(receiveTimeout / 10);
					} catch (InterruptedException ignore) { }					
				}
			} while (System.currentTimeMillis() < (start + receiveTimeout));
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
		synchronized (readGuard) {
			do {
				try {
					LinkedList<ByteBuffer> result = extractBytesByLength(length);
					return result.toArray(new ByteBuffer[result.size()]);
				} catch (BufferUnderflowException bue) {
					try {
						readGuard.wait(receiveTimeout / 10);
					} catch (InterruptedException ignore) { }					
				}
			} while (System.currentTimeMillis() < (start + receiveTimeout));
		}
		
		throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(receiveTimeout) + " reached"); 
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
		synchronized (readGuard) {
			do {
				try {
					return extractDoubleFromReadQueue();
				} catch (BufferUnderflowException bue) {
					try {
						readGuard.wait(receiveTimeout / 10);
					} catch (InterruptedException ignore) { }					
				}
			} while (System.currentTimeMillis() < (start + receiveTimeout));
		}
		
		throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(receiveTimeout) + " reached"); 

	}
	
	/**
	 * {@inheritDoc}
	 */
	public int readInt() throws IOException, ClosedConnectionException, SocketTimeoutException {
		long start = System.currentTimeMillis();
		synchronized (readGuard) {
			do {
				try {
					return extractIntFromReadQueue();
				} catch (BufferUnderflowException bue) {
					try {
						readGuard.wait(receiveTimeout / 10);
					} catch (InterruptedException ignore) { }					
				}
			} while (System.currentTimeMillis() < (start + receiveTimeout));
		}
		
		throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(receiveTimeout) + " reached"); 

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
		synchronized (readGuard) {
			do {
				try {
					return extractLongFromReadQueue();
				} catch (BufferUnderflowException bue) {
					try {
						readGuard.wait(receiveTimeout / 10);
					} catch (InterruptedException ignore) { }					
				}
			} while (System.currentTimeMillis() < (start + receiveTimeout));
		}
				
		throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(receiveTimeout) + " reached"); 
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
	public String readStringByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, ClosedConnectionException, UnsupportedEncodingException, SocketTimeoutException, MaxReadSizeExceededException {
		long start = System.currentTimeMillis();
		synchronized (readGuard) {
			do {
				try {
					LinkedList<ByteBuffer> extracted = extractBytesByDelimiterFromReadQueue(delimiter, maxLength);
					return DataConverter.toString(extracted, encoding);
				} catch (MaxReadSizeExceededException mle) {
					throw mle;
					
				} catch (BufferUnderflowException bue) {
					try {
						readGuard.wait(receiveTimeout / 10);
					} catch (InterruptedException ignore) { }					
				}
			} while (System.currentTimeMillis() < (start + receiveTimeout));
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
		synchronized (readGuard) {
			do {
				try {
					LinkedList<ByteBuffer> extracted = extractBytesByLength(length);
					return DataConverter.toString(extracted, encoding);
				} catch (BufferUnderflowException bue) {
					try {
						readGuard.wait(receiveTimeout / 10);
					} catch (InterruptedException ignore) { }					
				}
			} while (System.currentTimeMillis() < (start + receiveTimeout));
		}
		
		throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(receiveTimeout) + " reached"); 
	}
	
	
	/**
	 * {@inheritDoc}
	 */ 
	public void flush() throws ClosedConnectionException, IOException, SocketTimeoutException {
		
		long start = System.currentTimeMillis();
		synchronized (writeGuard) {
			super.flush();
			do {
				// all buffers empty?
				if (getIOHandler().isChainSendBufferEmpty()) {
					return;
				
				// write exception occured?	
				} else if(writeException != null) {
					IOException ioe = writeException;
					writeException = null;
					throw ioe;
					
				// ... no -> wait
				} else {
					try {
						writeGuard.wait(SEND_TIMEOUT / 10);
					} catch (InterruptedException ignore) { }
				}
				
			} while (System.currentTimeMillis() < (start + SEND_TIMEOUT));
		}
	}
	


	
	private final class IOEventHandler implements IIOEventHandler {
	
		public boolean listenForWritten() {
			return true;
		}
		
		public void onWrittenEvent() {
			synchronized (writeGuard) {
				writeGuard.notify();
			}		
		}
		
		public void onWriteExceptionEvent(IOException ioe) {
			synchronized (writeGuard) {
				writeException = ioe;
				writeGuard.notify();
			}
		}
		
		public void onDataEvent() {
			synchronized (readGuard) {
				receive();
				readGuard.notify();
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
