// $Id: BlockingConnection.java 448 2006-12-08 14:55:13Z grro $
/*
 *  Copyright (c) xsocket.org, 2006. All rights reserved.
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

package org.xsocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import javax.net.ssl.SSLContext;


import org.xsocket.util.TextUtils;



/**
 * Implementation of the <code>IBlockingConnection</code> interface. <br><br>
 * 
 * The methods of this class are not thread-safe. 
 *
 * @author grro@xsocket.org
 */
public final class BlockingConnection extends Connection implements IBlockingConnection {
	
	
	/**
	 * constructor
	 *
     * @param hostname  the remote host
	 * @param port		the port of the remote host to connect
	 * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(String hostname, int port) throws IOException {
		this(hostname, port, null, false);
	}


	/**
	 * Constructor
	 *
     * @param hostname    the remote host
	 * @param port		  the port of the remote host to connect
	 * @param sslContext  the sslContext to use
	 * @param sslOn       true, if data should be send and received by using ssl
	 * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(String hostname, int port, SSLContext sslContext, boolean sslOn) throws IOException {
		this(SocketChannel.open(new InetSocketAddress(hostname, port)), sslContext, sslOn);
	}

	/**
	 * Constructor
	 *
	 * @param channel  the underlying socket channel
	 * @param sslContext  the sslContext to use
	 * @param sslOn       true, if data should be send and received by using ssl
	 * @throws IOException If some other I/O error occurs
	 */
	private BlockingConnection(SocketChannel channel, SSLContext sslContext, boolean sslOn) throws IOException {
		super(channel, null, true, sslContext, sslOn);
		channel.configureBlocking(false);
		
		init();
	}

	
	
	/**
	 * {@inheritDoc}
	 */
	public ByteBuffer[] receiveByteBufferByDelimiter(String delimiter) throws IOException, ClosedConnectionException, SocketTimeoutException {

		ByteBufferOutputChannel channel = new ByteBufferOutputChannel();

		while (true) {
			try {
				extractRecordByDelimiterFromReadQueue(delimiter, channel);
				return channel.toByteBufferArray();
			} catch (BufferUnderflowException bue) {
				readIncoming();
			}
		}
	}

	
	/**
	 * {@inheritDoc}
	 */
	public byte[] receiveBytesByDelimiter(String delimiter) throws IOException, ClosedConnectionException, SocketTimeoutException {
		return toArray(receiveByteBufferByDelimiter(delimiter));
	}


	/**
	 * {@inheritDoc}
	 */
	public ByteBuffer[] receiveByteBufferByLength(int length) throws IOException, ClosedConnectionException, SocketTimeoutException {
		ByteBufferOutputChannel channel = new ByteBufferOutputChannel();

		while (true) {
			try {
				extractRecordByLength(length, channel);
				return channel.toByteBufferArray();
			} catch (BufferUnderflowException bue) {
				readIncoming();
			}
		}
	}

	
	/**
	 * {@inheritDoc}
	 */
	public byte[] receiveBytesByLength(int length) throws IOException, ClosedConnectionException, SocketTimeoutException {
		return toArray(receiveByteBufferByLength(length));
	}
	
	/**
	 * {@inheritDoc}
	 */
	public String receiveStringByLength(int length) throws IOException, ClosedConnectionException, SocketTimeoutException {
		return receiveStringByLength(length, getDefaultEncoding());
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public String receiveStringByLength(int length, String encoding) throws IOException, ClosedConnectionException, SocketTimeoutException {

		while (true) {
			try {
				ByteBufferOutputChannel channel = new ByteBufferOutputChannel();
				extractRecordByLength(length, channel);
				return TextUtils.toString(channel.toByteBufferArray(), encoding);
			} catch (BufferUnderflowException bue) {
				readIncoming();
			}
		}
	}
	

	/**
	 * {@inheritDoc}
	 */
	public String receiveStringByDelimiter(String delimiter, String encoding) throws IOException, ClosedConnectionException, SocketTimeoutException {

		while (true) {
			try {
				ByteBufferOutputChannel channel = new ByteBufferOutputChannel();
				extractRecordByDelimiterFromReadQueue(delimiter, channel);
				return TextUtils.toString(channel.toByteBufferArray(), encoding);
			} catch (BufferUnderflowException bue) {				
				readIncoming();
			}
		}
	}


	/**
	 * {@inheritDoc}
	 */
	public String receiveStringByDelimiter(String delimiter) throws IOException, ClosedConnectionException, SocketTimeoutException {
		return receiveStringByDelimiter(delimiter, getDefaultEncoding());
	}


	/**
	 * {@inheritDoc}
	 */
	public byte receiveByte() throws IOException, ClosedConnectionException, SocketTimeoutException {
		byte b = 0;
		boolean found = false;
		do {
			try {
				b = extractByteFromReadQueue();
				found = true;
			} catch (BufferUnderflowException bue) {
				readIncoming();
			}
		} while (!found);
		return b;
	}



	/**
	 * {@inheritDoc}
	 */
	public double receiveDouble() throws IOException, ClosedConnectionException, SocketTimeoutException {
		double d = 0;
		boolean found = false;
		do {
			try {
				d = extractDoubleFromReadQueue();
				found = true;
			} catch (BufferUnderflowException bue) {
				readIncoming();
			}
		} while (!found);
		return d;
	}


	/**
	 * {@inheritDoc}
	 */
	public int receiveInt() throws IOException, ClosedConnectionException, SocketTimeoutException {
		int i = 0;
		boolean found = false;
		do {
			try {
				i = extractIntFromReadQueue();
				found = true;
			} catch (BufferUnderflowException bue) {
				readIncoming();
			}
		} while (!found);
		return i;
	}


	/**
	 * {@inheritDoc}
	 */
	public long receiveLong() throws IOException, ClosedConnectionException, SocketTimeoutException {
		long l = 0;
		boolean found = false;
		do {
			try {
				l = extractLongFromReadQueue();
				found = true;
			} catch (BufferUnderflowException bue) {
				readIncoming();
			}
		} while (!found);
		return l;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void flushOutgoing() {
		
	}
}
