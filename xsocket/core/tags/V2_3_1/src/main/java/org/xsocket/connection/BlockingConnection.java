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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.xsocket.DataConverter;
import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;




/**
 * Implementation of the <code>IBlockingConnection</code> interface. Internally a {@link INonBlockingConnection}
 * will be used. A <code>BlockingConnection</code> wraps a <code>INonBlockingConnection</code>. There are two ways to 
 * create a <code>BlockingConnection</code>: 
 * <ul>
 *   <li>by passing over the remote address (e.g. host name & port), or</li>
 *   <li>by passing over a <code>INonBlockingConnection</code>, which will be wrapped</li>
 * </ul>
 * <br><br>
 * 
 * A newly created connection is in the open state. Write or read methods can be called immediately <br><br>
 *
 * <b>The methods of this class are not thread-safe.</b>
 *
 * @author grro@xsocket.org
 */
public class BlockingConnection implements IBlockingConnection {
	
	private static final Logger LOG = Logger.getLogger(BlockingConnection.class.getName());
	
 
	private final ReadNotificationHandler handler = new ReadNotificationHandler();
	private final Object readGuard = new Object();
	
	private final INonBlockingConnection delegee;
	private int readTimeout = DEFAULT_READ_TIMEOUT;

	
	
	/**
	 * constructor. <br><br>
	 *
     * @param hostname  the remote host
	 * @param port		the port of the remote host to connect
	 * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(String hostname, int port) throws IOException {
		this(new InetSocketAddress(hostname, port), null, true, Integer.MAX_VALUE, new HashMap<String, Object>(), null, false);
	}




	/**
	 * constructor. <br><br>
	 *
     * @param hostname             the remote host
	 * @param port		           the port of the remote host to connect
	 * @param options              the socket options
	 * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(String hostname, int port, Map<String, Object> options) throws IOException {
		this(new InetSocketAddress(hostname, port), null, true, Integer.MAX_VALUE, options,  null, false);
	}


	/**
	 * constructor
	 *
	 * @param address  the remote host address
	 * @param port     the remote host port
	 * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(InetAddress address, int port) throws IOException {
		this(address, port, Integer.MAX_VALUE, new HashMap<String, Object>(), null, false);
	}



	/**
	 * constructor
	 *
	 * @param address                the remote host address
	 * @param port                   the remote host port
	 * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(InetAddress address, int port, int connectTimeoutMillis) throws IOException{
		this(new InetSocketAddress(address, port), null, true, connectTimeoutMillis, new HashMap<String, Object>(), null, false);
	}


	/**
	 * constructor
	 *
	 * @param address              the remote host name
	 * @param port                 the remote host port
	 * @param sslContext           the sslContext to use
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IReadWriteableConnection#activateSecuredMode()})
	 * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(InetAddress address, int port, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(address, port), null, true, Integer.MAX_VALUE, new HashMap<String, Object>(), sslContext, sslOn);
	}


	/**
	 * constructor
	 *
	 * @param address                the remote host name
	 * @param port                   the remote host port
     * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @param sslContext             the sslContext to use
	 * @param sslOn                  true, activate SSL mode. false, ssl can be activated by user (see {@link IReadWriteableConnection#activateSecuredMode()})
	 * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(InetAddress address, int port, int connectTimeoutMillis, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(address, port), null, true, connectTimeoutMillis, new HashMap<String, Object>(), sslContext, sslOn);
	}




	/**
	 * constructor
	 *
	 * @param address              the remote host name
	 * @param port                 the remote host port
	 * @param options              the socket options
	 * @param sslContext           the sslContext to use
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IReadWriteableConnection#activateSecuredMode()})
     * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(InetAddress address, int port, Map<String, Object> options, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(address, port), null, true, Integer.MAX_VALUE, options, sslContext, sslOn);
	}


	/**
	 * constructor
	 *
	 * @param address                the remote host name
	 * @param port                   the remote host port
     * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @param options                the socket options
	 * @param sslContext             the sslContext to use
	 * @param sslOn                  true, activate SSL mode. false, ssl can be activated by user (see {@link IReadWriteableConnection#activateSecuredMode()})
     * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(InetAddress address, int port, int connectTimeoutMillis, Map<String, Object> options, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(address, port), null, true, connectTimeoutMillis, options, sslContext, sslOn);
	}



	/**
	 * constructor
	 *
	 * @param hostname             the remote host name
	 * @param port                 the remote host port
	 * @param sslContext           the sslContext to use or <null>
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IReadWriteableConnection#activateSecuredMode()})
     * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(String hostname, int port, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(hostname, port), null, true, Integer.MAX_VALUE, new HashMap<String, Object>(), sslContext, sslOn);
	}



	/**
	 * constructor
	 * 
	 * @param remoteAddress           the remote address
	 * @param localAddress            the local address
     * @param waitForConnect          true, if the constructor should block until the connection is established. 
	 * @param connectTimeoutMillis    the connection timeout
	 * @param options                 the socket options
	 * @param sslContext              the ssl context or <null>
	 * @param sslOn                   true, if ssl mode 
     * @throws IOException If some other I/O error occurs            
	 */
	public BlockingConnection(InetSocketAddress remoteAddress, InetSocketAddress localAddress, boolean waitForConnect, int connectTimeoutMillis, Map<String, Object> options, SSLContext sslContext, boolean sslOn) throws IOException {
		this.delegee= new NonBlockingConnection(remoteAddress, localAddress, handler, waitForConnect, connectTimeoutMillis, options, sslContext, sslOn);
	}


	/**
	 * Constructor. <br><br>
	 * 
	 * By creating a BlokingConnection based on the given NonBlockingConnection the 
	 * assigned handler of the NonBlockingConnection will be removed (if exists). Take
	 * care by using this on the server-side (see tutorial for more informations). 
	 * 
	 * @param delegee    the underlying non blocking connection
     * @throws IOException If some other I/O error occurs  
	 */
	public BlockingConnection(INonBlockingConnection delegee) throws IOException {
		this.delegee = delegee;
		delegee.setHandler(handler);
	}
	
	
	final INonBlockingConnection getDelegee() {
		return delegee;
	}


	
	/**
	 * {@inheritDoc}
	 */
	public void setReadTimeoutMillis(int timeout) throws IOException {
		this.readTimeout = timeout;

		int soTimeout = (Integer) delegee.getOption(SO_TIMEOUT);
		if (timeout > soTimeout) {
			delegee.setOption(SO_TIMEOUT, timeout);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void setReceiveTimeoutMillis(int timeout) throws IOException {
		setReadTimeoutMillis(timeout);
	}


	/**
	 * {@inheritDoc}
	 */
	public final int getReceiveTimeoutMillis() throws IOException {
		return getReadTimeoutMillis();
	}	
	

	

	/**
	 * {@inheritDoc}
	 */
	public int getReadTimeoutMillis() throws IOException {
		return readTimeout;
	}	
	
	/**
	 * {@inheritDoc}
	 */	
	public int getMaxReadBufferThreshold() {
		return delegee.getMaxReadBufferThreshold();
	}
	
	/**
	 * {@inheritDoc}
	 */		
	public void setMaxReadBufferThreshold(int size) {
		delegee.setMaxReadBufferThreshold(size);
	}

	/**
	 * {@inheritDoc}
	 */
	void setMaxWriteBufferThreshold(int size) {
//		delegee.setMaxWriteBufferThreshold(size);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void setEncoding(String defaultEncoding) {
		delegee.setEncoding(defaultEncoding);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final String getEncoding() {
		return delegee.getEncoding();
	}
	
	
	/**
	 * return if the data source is open. Default is true
	 * @return true, if the data source is open
	 */
	public final boolean isOpen() {
		return delegee.isOpen();
	}
		 
	/**
     * {@inheritDoc}
     */
    public boolean isServerSide() {
        return delegee.isServerSide();
    }
    
	
	/**
	 * {@inheritDoc}
	 */
	public final void close() throws IOException {
		delegee.close();
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final void flush() throws ClosedChannelException, IOException, SocketTimeoutException {
		delegee.flush();
	}
	
	
	/**
	 * {@inheritDoc}
	 */

	public String getId() {
		return delegee.getId();
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final InetAddress getRemoteAddress() {
		return delegee.getRemoteAddress();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int getRemotePort() {
		return delegee.getRemotePort();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final InetAddress getLocalAddress() {
		return delegee.getLocalAddress();
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int getLocalPort() {
		return delegee.getLocalPort();
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final int getPendingWriteDataSize() {
		return delegee.getPendingWriteDataSize();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void suspendRead() throws IOException {
		delegee.suspendReceiving();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public boolean isReadSuspended() {
		return delegee.isReceivingSuspended();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final void resumeRead() throws IOException {
		delegee.resumeReceiving();
	}
	
	/**
	 * {@inheritDoc}
	 */	
	public boolean isReceivingSuspended() {
		return delegee.isReceivingSuspended();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void resumeReceiving() throws IOException {
		delegee.resumeReceiving();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void suspendReceiving() throws IOException {
		delegee.suspendReceiving();
	}
	
		
	/**
	 * {@inheritDoc}
	 */	
	public void setFlushmode(FlushMode flushMode) {
		delegee.setFlushmode(flushMode);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public FlushMode getFlushmode() {
		return delegee.getFlushmode();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void setOption(String name, Object value) throws IOException {
		delegee.setOption(name, value);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final Object getOption(String name) throws IOException {
		return delegee.getOption(name);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public final Map<String, Class> getOptions() {
		return delegee.getOptions();
	}



	/**
	 * {@inheritDoc}
	 */
	public final void setIdleTimeoutMillis(long timeoutInMillis) {
		delegee.setIdleTimeoutMillis(timeoutInMillis);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final long getIdleTimeoutMillis() {
		return delegee.getIdleTimeoutMillis();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final void setConnectionTimeoutMillis(long timeoutMillis) {
		delegee.setConnectionTimeoutMillis(timeoutMillis);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final long getConnectionTimeoutMillis() {
		return delegee.getConnectionTimeoutMillis();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public long getRemainingMillisToConnectionTimeout() {
		return delegee.getRemainingMillisToConnectionTimeout();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public long getRemainingMillisToIdleTimeout() {
		return delegee.getRemainingMillisToIdleTimeout();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void setAttachment(Object obj) {
		delegee.setAttachment(obj);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final Object getAttachment() {
		return delegee.getAttachment();
	}
	

	/**
	 * {@inheritDoc}
	 */
	public final void setAutoflush(boolean autoflush) {
		delegee.setAutoflush(autoflush);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final boolean isAutoflush() {
		return delegee.isAutoflush();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void activateSecuredMode() throws IOException {
		delegee.activateSecuredMode();
	}
	
	

	/**
	 * {@inheritDoc}
	 */
	public boolean isSecure() {
		return delegee.isSecure();
	}


	
	
	/**
	 * {@inheritDoc}
	 */
	public final void markReadPosition() {
		delegee.markReadPosition();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final void markWritePosition() {
		delegee.markWritePosition();
	}
	
	
	/**
     * {@inheritDoc}.
     */
	public void unread(ByteBuffer[] buffers) throws IOException {
	    delegee.unread(buffers);
	}
	
	
	/**
     * {@inheritDoc}.
     */
	public void unread(byte[] bytes) throws IOException {
	    delegee.unread(bytes);
	}
	
	
	/**
     * {@inheritDoc}.
     */
	public void unread(ByteBuffer buffer) throws IOException {
	    delegee.unread(buffer);
	}
	
	
	/**
     * {@inheritDoc}.
     */
	public void unread(String text) throws IOException {
	    delegee.unread(text);
	    
	}
	
	
	/**
	 * {@inheritDoc}.
	 */
	public final int read(ByteBuffer buffer) throws IOException, ClosedChannelException {
		int size = buffer.remaining();
		if (size < 1) {
			return 0;
		}

		long start = System.currentTimeMillis();
		long remainingTime = readTimeout;

		synchronized (readGuard) {
			do {
				int availableSize = delegee.available();
				
				// if at least one byte is available -> read and return
				if (availableSize > 0) {
					int read = delegee.read(buffer);
					if (read > 0) {
						return read;
					}
				}				
				
				
				if (availableSize == -1) {
					// check if channel is closed by reading with length 0
					// is closed a ClosedChannelException will be thrown
					delegee.read(ByteBuffer.allocate(0));
				}
				
				// no data available
				if (isOpen()) {
					waitForData(readGuard, remainingTime);
					
				} else {
					return -1;
				}
				
				remainingTime = (start + readTimeout) - System.currentTimeMillis();
			} while (remainingTime > 0);
		}

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("receive timeout " + DataConverter.toFormatedDuration(readTimeout) + " reached. throwing timeout exception");
		}

		throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(readTimeout) + " reached");
	}
	
	private void waitForData(Object readGuard, long maxWaittime) {
		try {
			readGuard.wait(maxWaittime);
		} catch (InterruptedException ignore) {  }
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final byte readByte() throws IOException, SocketTimeoutException {
		long start = System.currentTimeMillis();
		long remainingTime = readTimeout;

		do {
			synchronized (readGuard) {
				try {
					return delegee.readByte();
				} catch (BufferUnderflowException bue) {
					if (isOpen()) {
						waitForData(readGuard, remainingTime);
						
					} else {
						throw new ClosedChannelException();
					}
				}
			} 

			remainingTime = (start + readTimeout) - System.currentTimeMillis();
		} while (remainingTime > 0);

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("receive timeout " + DataConverter.toFormatedDuration(readTimeout) + " reached. throwing timeout exception");
		}

		throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(readTimeout) + " reached");
	}
	
	

	/**
	 * {@inheritDoc}
	 */
	public final short readShort() throws IOException, SocketTimeoutException {
		long start = System.currentTimeMillis();
		long remainingTime = readTimeout;

		do {
			synchronized (readGuard) {
				try {
					return delegee.readShort();
				} catch (BufferUnderflowException bue) {
					if (isOpen()) {
						waitForData(readGuard, remainingTime);								
					} else {
						throw new ClosedChannelException();
					}
				}
			} 

			remainingTime = (start + readTimeout) - System.currentTimeMillis();
		} while (remainingTime > 0);
		

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("receive timeout " + DataConverter.toFormatedDuration(readTimeout) + " reached. throwing timeout exception");
		}

		throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(readTimeout) + " reached");
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int readInt() throws IOException, SocketTimeoutException {
		long start = System.currentTimeMillis();
		long remainingTime = readTimeout;
	
		do {
			synchronized (readGuard) {
				try {
					return delegee.readInt();
				} catch (BufferUnderflowException bue) {
					if (isOpen()) {
						waitForData(readGuard, remainingTime);								
					} else {
						throw new ClosedChannelException();
					}
				}
			} 

			remainingTime = (start + readTimeout) - System.currentTimeMillis();
		} while (remainingTime > 0);


		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("receive timeout " + DataConverter.toFormatedDuration(readTimeout) + " reached. throwing timeout exception");
		}

		throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(readTimeout) + " reached");
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final long readLong() throws IOException, SocketTimeoutException {
		long start = System.currentTimeMillis();
		long remainingTime = readTimeout;

		do {
			synchronized (readGuard) {
				try {
					return delegee.readLong();
				} catch (BufferUnderflowException bue) {
					if (isOpen()) {
						waitForData(readGuard, remainingTime);								
					} else {
						throw new ClosedChannelException();
					}
				}
			} 

			remainingTime = (start + readTimeout) - System.currentTimeMillis();
		} while (remainingTime > 0);


		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("receive timeout " + DataConverter.toFormatedDuration(readTimeout) + " reached. throwing timeout exception");
		}

		throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(readTimeout) + " reached");
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final double readDouble() throws IOException, SocketTimeoutException {
		long start = System.currentTimeMillis();
		long remainingTime = readTimeout;

		do {
			synchronized (readGuard) {
				try {
					return delegee.readDouble();
				} catch (BufferUnderflowException bue) {
					if (isOpen()) {
						waitForData(readGuard, remainingTime);		 						
					} else { 
						throw new ClosedChannelException();
					}
				}
			} 
			remainingTime = (start + readTimeout) - System.currentTimeMillis();
		} while (remainingTime > 0);

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("receive timeout " + DataConverter.toFormatedDuration(readTimeout) + " reached. throwing timeout exception");
		}
		throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(readTimeout) + " reached");
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException, SocketTimeoutException {
		return readByteBufferByDelimiter(delimiter, getEncoding());
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final ByteBuffer[] readByteBufferByDelimiter(String delimiter, int maxLength) throws IOException, MaxReadSizeExceededException, SocketTimeoutException {
		return readByteBufferByDelimiter(delimiter, getEncoding(), maxLength);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding) throws IOException, SocketTimeoutException {
		return readByteBufferByDelimiter(delimiter, encoding, Integer.MAX_VALUE);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, MaxReadSizeExceededException, SocketTimeoutException {

		long start = System.currentTimeMillis();
		long remainingTime = readTimeout;

		
		do {
			synchronized (readGuard) {
				try {
					return delegee.readByteBufferByDelimiter(delimiter, encoding, maxLength);
						
				} catch (MaxReadSizeExceededException mre) {
					throw mre;

				} catch (BufferUnderflowException bue) {
					if (isOpen()) {
						waitForData(readGuard, remainingTime);								
					} else {
						throw new ClosedChannelException();
					}
				}
			} 

			remainingTime = (start + readTimeout) - System.currentTimeMillis();
		} while (remainingTime > 0);
	

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("receive timeout " + DataConverter.toFormatedDuration(readTimeout) + " reached. throwing timeout exception");
		}

		throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(readTimeout) + " reached");
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final ByteBuffer[] readByteBufferByLength(int length) throws IOException, SocketTimeoutException {
		if (length <= 0) {
			return new ByteBuffer[0];
		}

		long start = System.currentTimeMillis();
		long remainingTime = readTimeout;

		do {
			synchronized (readGuard) {
				try {
					return delegee.readByteBufferByLength(length);
				} catch (BufferUnderflowException bue) {
					
					if (isOpen()) {
						waitForData(readGuard, remainingTime);
						
					} else {
						throw new ClosedChannelException();
					}
				}
			} 
		
			remainingTime = (start + readTimeout) - System.currentTimeMillis();
		} while (remainingTime > 0);
		
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("receive timeout " + DataConverter.toFormatedDuration(readTimeout) + " reached. throwsing timeout exception");
		}

		throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(readTimeout) + " reached");
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final byte[] readBytesByDelimiter(String delimiter) throws IOException, SocketTimeoutException {
		return readBytesByDelimiter(delimiter, getEncoding());
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final byte[] readBytesByDelimiter(String delimiter, int maxLength) throws IOException, MaxReadSizeExceededException, SocketTimeoutException {
		return readBytesByDelimiter(delimiter, getEncoding(), maxLength);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final byte[] readBytesByDelimiter(String delimiter, String encoding) throws IOException, SocketTimeoutException {
		return readBytesByDelimiter(delimiter, encoding, Integer.MAX_VALUE);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final byte[] readBytesByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, MaxReadSizeExceededException, SocketTimeoutException {
		return DataConverter.toBytes(readByteBufferByDelimiter(delimiter, encoding, maxLength));
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final byte[] readBytesByLength(int length) throws IOException,  SocketTimeoutException {
		return DataConverter.toBytes(readByteBufferByLength(length));
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final String readStringByDelimiter(String delimiter) throws IOException, UnsupportedEncodingException, SocketTimeoutException {
		return readStringByDelimiter(delimiter, Integer.MAX_VALUE);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final String readStringByDelimiter(String delimiter, int maxLength) throws IOException, UnsupportedEncodingException, MaxReadSizeExceededException, SocketTimeoutException {
		return readStringByDelimiter(delimiter, getEncoding(), maxLength);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final String readStringByDelimiter(String delimiter, String encoding) throws IOException, UnsupportedEncodingException, MaxReadSizeExceededException, SocketTimeoutException {
		return readStringByDelimiter(delimiter, encoding, Integer.MAX_VALUE);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final String readStringByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, UnsupportedEncodingException, MaxReadSizeExceededException, SocketTimeoutException {
		return DataConverter.toString(readByteBufferByDelimiter(delimiter, encoding, maxLength), encoding);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final String readStringByLength(int length) throws IOException, UnsupportedEncodingException, SocketTimeoutException {
		return readStringByLength(length, getEncoding());
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final String readStringByLength(int length, String encoding) throws IOException, UnsupportedEncodingException, SocketTimeoutException {
		return DataConverter.toString(readByteBufferByLength(length), encoding);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final long transferTo(WritableByteChannel target, int length) throws IOException, SocketTimeoutException {
		long written = 0;
		
		ByteBuffer[] buffers = readByteBufferByLength(length);
		for (ByteBuffer buffer : buffers) {
			written += target.write(buffer);
		}
		
		return written;
	}
	

	/**
	 * {@inheritDoc}
	 */
	public final boolean resetToWriteMark() {
		return delegee.resetToWriteMark();
	}



	/**
	 * {@inheritDoc}
	 */
	public final boolean resetToReadMark() {
		return delegee.resetToReadMark();
	}


	/**
	 * {@inheritDoc}
	 */
	public final void removeReadMark() {
		delegee.removeReadMark();
	}


	/**
	 * {@inheritDoc}
	 */
	public final void removeWriteMark() {
		delegee.removeWriteMark();
	}

	

	/**
	 * {@inheritDoc}
	 */
	public final int write(byte b) throws IOException, BufferOverflowException {
		return delegee.write(b);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int write(byte... bytes) throws IOException {
		return delegee.write(bytes);
	}
	
	
	/**
     * {@inheritDoc}
     */
	public void write(byte[] bytes, IWriteCompletionHandler writeCompletionHandler) throws IOException {
	    delegee.write(bytes, writeCompletionHandler);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int write(byte[] bytes, int offset, int length) throws IOException {
		return delegee.write(bytes, offset, length);
	}

	
	
	/**
     * {@inheritDoc}
     */
	public void write(byte[] bytes, int offset, int length, IWriteCompletionHandler writeCompletionHandler) throws IOException {
        delegee.write(bytes, offset, length, writeCompletionHandler);	    
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int write(short s) throws IOException {
		return delegee.write(s);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int write(int i) throws IOException {
		return delegee.write(i);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int write(long l) throws IOException {
		return delegee.write(l);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int write(double d) throws IOException {
		return delegee.write(d);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int write(String message) throws IOException {
		return delegee.write(message);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int write(String message, String encoding) throws IOException {
		return delegee.write(message, encoding);
	}
	
	
	/**
     * {@inheritDoc}
     */
	public void write(String message, String encoding, IWriteCompletionHandler writeCompletionHandler) throws IOException {
	    delegee.write(message, encoding, writeCompletionHandler);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final long write(ArrayList<ByteBuffer> buffers) throws IOException {
		return delegee.write(buffers);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final long write(List<ByteBuffer> buffers) throws IOException {
		return delegee.write(buffers);
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public final long write(ByteBuffer[] buffers) throws IOException {
		return delegee.write(buffers);
	}

	
	/**
	 * {@inheritDoc}
	 */
	public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
		return delegee.write(srcs, offset, length);
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int write(ByteBuffer buffer) throws IOException {
		return delegee.write(buffer);
	}
	

	/**
     * {@inheritDoc}
     */
	public void write(ByteBuffer buffer, IWriteCompletionHandler writeCompletionHandler) throws IOException {
	    delegee.write(buffer, writeCompletionHandler);
	}


	/**
     * {@inheritDoc}
     */
	public void write(ByteBuffer[] buffers, IWriteCompletionHandler writeCompletionHandler) throws IOException {
	    delegee.write(buffers, writeCompletionHandler);	    
	}
	
	
    /**
     * {@inheritDoc}
     */
	public void write(ByteBuffer[] srcs, int offset, int length, IWriteCompletionHandler writeCompletionHandler) throws IOException {
	    delegee.write(srcs, offset,length, writeCompletionHandler);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final long transferFrom(ReadableByteChannel source) throws IOException {
		return delegee.transferFrom(source);
	}
	
	/**
     * {@inheritDoc}
     */
	public void write(List<ByteBuffer> buffers, IWriteCompletionHandler writeCompletionHandler) throws IOException {
	    delegee.write(buffers, writeCompletionHandler);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final long transferFrom(ReadableByteChannel source, int chunkSize) throws IOException {
		return delegee.transferFrom(source, chunkSize);
	}
	
	

	private void onReadDataInserted() {
		synchronized (readGuard) {
			readGuard.notifyAll();
		}
	}
	
	
	public long transferFrom(FileChannel source) throws IOException {
		return delegee.transferFrom(source);
	}
	
		
	@Override
	public String toString() {
		return delegee.toString();
	}

	
	@Execution(Execution.NONTHREADED)
	private final class ReadNotificationHandler implements IDataHandler, IDisconnectHandler, IConnectionTimeoutHandler, IIdleTimeoutHandler, IUnsynchronized {
			
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			onReadDataInserted();
			return true;
		}
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			onReadDataInserted();
			return true;
		}
		
		
		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
			onReadDataInserted();
			
			connection.close();
			return true;
		}
		
		
		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
			onReadDataInserted();
			
			connection.close();
			return true;
		}		
	}
}
