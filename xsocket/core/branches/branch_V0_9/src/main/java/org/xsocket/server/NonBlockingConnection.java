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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.AbstractConnection;
import org.xsocket.ClosedConnectionException;


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
	protected final synchronized SocketChannel getAssignedSocketChannel() {
		return channel;
	}

	
	/**
	 * reads the underlying channel and let the assigned handler handle the input data 
	 * 
	 * @throws IOException If some other I/O error occurs
	 * @throws ConnectionClosedException if the underlying channel is closed  
	 */
	public void handleNonBlockingRead() throws ClosedConnectionException, IOException {
		int bytesAdded = addToReceiveQueue(readPhysical());
		if (bytesAdded > 0) {
			getAssignedHandler().onDataReceived(this);
		}
	}
	
	/**
	 * writes the bytes to send by usign the underlying channel 
	 * 
	 * @throws IOException If some other I/O error occurs
	 * @throws ConnectionClosedException if the underlying channel is closed  
	 */
	public void handleNonBlockingWrite() throws ClosedConnectionException, IOException {
		writePhysical(drainSendQueue());
	}

	
	/**
	 * @see AbstractConnection
	 */
	public final long write(ByteBuffer[] buffers) throws ClosedConnectionException, IOException {
		long written = 0;
		for (ByteBuffer buffer : buffers) {
			written += addToSendQueue(buffer);
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
		return readRecordFromReceiveReceiveQueue(delimiter);
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
		return readIntFromReceiveQueue();
	}


	
	/**
	 * @see INonBlockingConnection
	 */
	public long readLong() throws IOException, BufferUnderflowException {
		return readLongFromReceiveQueue();
	}


	/**
	 * @see INonBlockingConnection
	 */
	public double readDouble() throws IOException, BufferUnderflowException {
		return readDoubleFromReceiveQueue();
	}
	
	
	/**
	 * @see INonBlockingConnection
	 */
	public byte readByte() throws IOException, BufferUnderflowException {
		return readByteFromReceiveQueue();
	}
	
	
	/**
	 * @see INonBlockingConnection
	 */
	public final String readWord(String delimiter, String encoding) throws IOException, BufferUnderflowException {
		return readWordFromReceiveQueue(delimiter, encoding);
	}
	
	
	/**
	 * @see INonBlockingConnection
	 */
	public final ByteBuffer[] readAvailable() throws ClosedConnectionException, IOException {
		return drainReceiveQueue();
	}
	
	/**
	 * @see INonBlockingConnection
	 */	
	public int getNumberOfAvailableBytes() {
		return numberOfAvailableInputBytes();
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
		return !isSendQueueEmpty();
	}
		

	
	/**
	 * @see IInternalNonBlockingConnection
	 */
	public void init(final InternalHandler handler) throws IOException {
		setHandler(handler);		
		handler.onConnectionOpened(this);
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
	 * @see AbstractConnection
	 */
	@Override
	final protected ByteBuffer acquireMemory() {
		return ThreadBoundMemoryManager.acquireMemory();
	}
	
	/**
	 * @see IConnection
	 */
	public synchronized void close() {
		
		// notify handler, that connection will be closed
		if (handler != null) {
			handler.onConnectionClose(this);
		}
		
		// cancel selection key
		try {
			key.cancel();
		} catch (Exception ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + getId() + "] error occured while closing key: " + ioe.toString());
			}
		}		

		super.close();
	}

	
	/**
	 * @see AbstractConnection
	 */
	@Override
	final protected void recycleMemory(ByteBuffer buf) {
		ThreadBoundMemoryManager.recycleMemory(buf);
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
}