// $Id: NonBlockingConnectionImpl.java 41 2006-06-22 06:30:23Z grro $
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

package org.xsocket.server;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.AbstractConnection;
import org.xsocket.ClosedConnectionException;
import org.xsocket.IConnection;
import org.xsocket.util.TextUtils;


/**
 * A non blocking specialization of the <code>ConnectionImpl</code> 
 * 
 * @author grro@xsocket.org
 */
class NonBlockingConnection extends AbstractConnection implements INonBlockingConnection {
	
	private static final Logger LOG = Logger.getLogger(NonBlockingConnection.class.getName());
	
	private SocketChannel channel = null;
	
	private SelectionKey key = null;
	private InternalHandler handler = null;

	private long idleTimeout = 0;
	private long connectionTimeout = 0;
	
	private final Queue sendQueue = new Queue();
	
	
	/**
	 * constructor 
	 *  
	 * @param channel  the underlying socket channel
	 * @param id the connection id
	 * @throws IOException if the channel can not be configured ain a non-blocking mode
	 */
	public NonBlockingConnection(SocketChannel channel, String id) throws IOException {
		super();
		
		this.channel = channel;
		setId(id);
	}
	
	
	/**
	 * @see AbstractConnection
	 */
	@Override
	protected final SocketChannel getAssignedSocketChannel() {
		return channel;
	}

	
	/**
	 * reads the underlying channel and let the assigned handler handle the input data 
	 * 
	 * @throws IOException If some other I/O error occurs
	 * @throws ConnectionClosedException if the underlying channel is closed  
	 */
	public void handleNonBlockingRead() throws ClosedConnectionException, IOException {
		int bytesAdded = getReceiveQueue().append(readPhysical());
		if (bytesAdded > 0) {
			getAssignedHandler().onData(this);
		}
	}
	
	/**
	 * writes the bytes to send by usign the underlying channel 
	 * 
	 * @throws IOException If some other I/O error occurs
	 * @throws ConnectionClosedException if the underlying channel is closed  
	 */
	public void handleNonBlockingWrite() throws ClosedConnectionException, IOException {
		List<ByteBuffer> queue = sendQueue.drain();
		writePhysical(queue.toArray(new ByteBuffer[queue.size()]));
	}


	/**
	 * signals that the idle timeout has been occured
	 * 
	 * @throws IOException If some other I/O error occurs
	 */
	public void handleIdleTimeout() throws IOException {
		handler.onIdleTimeout(this);
	}

	
	/**
	 * signals that the connection timeout has been occured
	 * 
	 * @throws IOException If some other I/O error occurs
	 */
	public void handleConnectionTimeout() throws IOException {
		handler.onConnectionTimeout(this);
	}

	
	
	/**
	 * @see IConnection
	 */	
	public long write(ByteBuffer[] buffers) throws ClosedConnectionException, IOException {
		long written = 0;
		
		for (ByteBuffer buffer : buffers) {
			written += sendQueue.append(buffer);
		}
		
		if (written > 0)  {
			try {
				key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
				key.selector().wakeup();
			} catch (CancelledKeyException cke) {
				ClosedConnectionException cce = new ClosedConnectionException("connection " + this.getId() + " is already closed");
				LOG.throwing(this.getClass().getName(), "write(ByteBuffer[]", cce);
				throw cce;
			}
		}		
		return written;
	}
	

	/**
	 * @see INonBlockingConnection
	 */
	public final ByteBuffer[] readRecord(String delimiter) throws IOException {

		ByteBufferArrayChannel channel = new ByteBufferArrayChannel();
		getReceiveQueue().readRecord(delimiter, channel);
	
		return channel.getContent();
	}

	
	/**
	 * @see INonBlockingConnection
	 */
	public final String readWord(String delimiter) throws IOException {
		return readWord(delimiter, getDefaultEncoding());
	}

	
	/**
	 * @see INonBlockingConnection
	 */
	public int readInt() throws IOException, BufferUnderflowException {
		return getReceiveQueue().readInt();
	}


	
	/**
	 * @see INonBlockingConnection
	 */
	public long readLong() throws IOException, BufferUnderflowException {
		return getReceiveQueue().readLong();
	}


	/**
	 * @see INonBlockingConnection
	 */
	public double readDouble() throws IOException, BufferUnderflowException {
		return getReceiveQueue().readDouble();
	}
	
	
	/**
	 * @see INonBlockingConnection
	 */
	public byte readByte() throws IOException, BufferUnderflowException {
		return getReceiveQueue().readByte();
	}
	
	
	/**
	 * @see INonBlockingConnection
	 */
	public final String readWord(String delimiter, String encoding) throws IOException, BufferUnderflowException {
		ByteBufferArrayChannel channel = new ByteBufferArrayChannel();
		getReceiveQueue().readRecord(delimiter, channel);

		return TextUtils.toString(channel.getContent(), encoding);
	}
	
	
	/**
	 * @see INonBlockingConnection
	 */
	public final ByteBuffer[] readAvailable() throws ClosedConnectionException, IOException {
		LinkedList<ByteBuffer> queue = getReceiveQueue().drain();
		return queue.toArray(new ByteBuffer[queue.size()]);
	}
	

	/**
	 * @see INonBlockingConnection
	 */
	public boolean readAvailable(String delimiter, WritableByteChannel outputChannel) throws IOException {
		return getReceiveQueue().readAvailable(delimiter, outputChannel);
	}
	
	
	/**
	 * @see INonBlockingConnection
	 */	
	public int getNumberOfAvailableBytes() {
		return getReceiveQueue().getSize();
	}

	/**
	 * @see INonBlockingConnection
	 */
	public final void stopReceiving() {
		super.stopReading();
	}

	
	/**
	 * @see IInternalNonBlockingConnection
	 */
	public boolean hasDataToSend() {
		return (sendQueue.getSize() > 0);
	}
		

	
	/**
	 * @see IInternalNonBlockingConnection
	 */
	public void init(final InternalHandler handler) throws IOException {
		setHandler(handler);		
		handler.onConnect(this);
	}
	
	
	/**
	 * set the assigned handler 
	 * 
	 * @param handler the handler to be assigned
	 * @throws IOException If some other I/O error occurs
	 */
	protected final void setHandler(final InternalHandler handler) throws IOException {
		this.handler = handler;
	}

		
		
	/**
	 * @see IInternalNonBlockingConnection
	 */
	public final void registerSelector(Selector selector, int ops) throws IOException {
		if (isOpen()) {
			channel.configureBlocking(false);
			key = channel.register(selector, ops, this);
		}
	}
		
	
	/**
	 * gets the connection timeout
	 * 
	 * @return connection timeout
	 */
	public long getConnectionTimeout() {
		return connectionTimeout;
	}
	
	
	/**
	 * sets the connection timeout
	 * 
	 * @param timeout the connection timeout
	 */
	void setConnectionTimeout(long timeout) {
		this.connectionTimeout = timeout;
		
	}
	
	
	/**
	 * returns the idle timeout  
	 * 
	 * @return idle timeout
	 */
	public long getIdleTimeout() {
		return idleTimeout;
	}
	
	
	
	/**
	 * sets the idle timeout  
	 * 
	 * @param timeout idle timeout
	 */	
	void setIdleTimeout(long timeout) {
		this.idleTimeout = timeout;
	}
	
	
	/**
	 * @see AbstractConnection
	 */
	@Override
	final protected ByteBuffer acquireMemory() {
		return Dispatcher.getMemoryManager().acquireMemory();
	}
	
	/**
	 * @see IConnection
	 */
	public synchronized void close() {
		
		// cancel selection key
		try {
			key.cancel();
		} catch (Exception ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + getId() + "] error occured while closing key: " + ioe.toString());
			}
		}		

		// notify handler, that connection will be closed
		if (handler != null) {
			handler.onClose(this);
		}
		
		super.close();
	}

	
	/**
	 * @see AbstractConnection
	 */
	@Override
	final protected void recycleMemory(ByteBuffer buf) {
		Dispatcher.getMemoryManager().recycleMemory(buf);
	}	
	
	
	/**
	 * update the selection key ops for this connection
	 * 
	 * @param ops the new ops
	 */
	void updateSelectionKeyOps(int ops) {
		key.interestOps(ops);
	}
	
	/**
	 * get the assigned handler 
	 * 
	 * @return the assigned handler
	 */
	protected final InternalHandler getAssignedHandler() {
		return handler;
	}
	
	protected final Queue getSendQueue() {
		return sendQueue;
	}
	
	@Override
	public String toString() {
		return super.toString() + ", sendQueueSize=" + sendQueue.getSize();
	}
}