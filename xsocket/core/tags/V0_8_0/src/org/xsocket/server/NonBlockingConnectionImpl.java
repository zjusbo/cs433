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
class NonBlockingConnectionImpl extends AbstractConnection implements INonBlockingConnection {
	
	private static final Logger LOG = Logger.getLogger(NonBlockingConnectionImpl.class.getName());
	
	private static ThreadLocal<Integer> receivebufferPreallocationSize = new ThreadLocal<Integer>();
	private static ThreadLocal<ByteBuffer> buffer = new ThreadLocal<ByteBuffer>();

	
	private SocketChannel channel = null;
	
	private SelectionKey key = null;
	private IAllHandler handler = null;

	
	/**
	 * constructor 
	 *  
	 * @param channel  the underlying socket channel
	 * @param id the connection id
	 * @throws IOException if the channel can not be configured ain a non-blocking mode
	 */
	public NonBlockingConnectionImpl(SocketChannel channel, String id) throws IOException {
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
	 * signals the data should be read from the channel, and be handled by the handler
	 * 
	 * @throws IOException If some other I/O error occurs
	 * @throws ConnectionClosedException if the underlying channel is closed  
	 */
	protected void handleRead() throws ClosedConnectionException, IOException {
		boolean isDataAvailable = readFromChannel();	
		if (isDataAvailable) {
			handler.onData(this);
		}
	}


	private boolean readFromChannel() throws ClosedConnectionException, IOException {
		
		boolean moreDataAvailable = true;
		ByteBuffer buf = null;
		do {
			buf = readPhysical();
			if (buf != null) {
				moreDataAvailable = buf.limit() == buf.capacity();
				addToReceiveQueue(buf);
			}
		} while ((buf != null) && moreDataAvailable);
		
		return !isReceiveQueueEmpty();
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
	public final String readWord(String delimiter, String encoding) throws IOException {
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
	public final void stopReceiving() {
		super.stopReading();
	}

	
	/**
	 * signals if there are bytes to send
	 * 
	 * @return true, if there bytes to send
	 */
	final boolean hasDataToSend() {
		return !isSendQueueEmpty();
	}
	

	/**
	 * write all bytes of the send queue on the channel
	 * @return number of written bytes
	 * @throws ClosedConnectionException
	 * @throws IOException if an io excption occurs
	 */
	protected synchronized long handleWrite() throws ClosedConnectionException, IOException {
		if (!isSendQueueEmpty()) {
			key.interestOps(SelectionKey.OP_READ);	
			return writePhysical(drainSendQueue());
		} else {
			return 0;
		}
	}
	

	
	/**
	 * init the connection
	 *  
	 * @param handler  the assigned handler
	 * @throws IOException if an io excption occurs
	 */
	final void init(final IAllHandler handler) throws IOException {
		this.handler = handler;		
		init();
	}

	
	/**
	 * init the connection
	 *  
	 * @throws IOException if an io excption occurs
	 */	
	protected void init() throws IOException {
		handler.onConnectionOpening(this);
	}
	

	 
	/**
	 * get the attached handler
	 *  
	 * @return the atteched handler or null
	 */
	final IHandler getHandler() {
		return handler;
	}
		
		
	/**
	 * register a selector to this connection 
	 * 
	 * @param selector the selector to register 
	 * @param ops the operation 
	 * @throws IOException if an io excption occurs
	 */
	final void registerSelector(Selector selector, int ops) throws IOException {
		if (isOpen()) {
			channel.configureBlocking(false);
			key = channel.register(selector, ops, this);
		}
	}
		

	
	/**
	 * set the receive buffer size in context of the current thread
	 * @param size the receive buffer size
	 */
	final static void setReceivebufferPreallocationSize(int size) {
		receivebufferPreallocationSize.set(size);
	}


	/**
	 * @see AbstractConnection
	 */
	@Override
	final protected ByteBuffer acquireMemory() {
		ByteBuffer buf = buffer.get();
		if (buf == null) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("allocate new physical memory (new size: " + receivebufferPreallocationSize.get() + ")");
			}
			buf = ByteBuffer.allocateDirect(receivebufferPreallocationSize.get());
		}
		buffer.set(null);
		
		return buf;
	}
	
	/**
	 * @see IConnection
	 */
	public final synchronized void close() {
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
		if (LOG.isLoggable(Level.FINEST)) {
			LOG.finest("free buffer " + buffer + " has been put back");
		}
		buffer.set(buf);
	}	
	
	
	/**
	 * get the assigned selection key
	 * @return the selection key
	 */
	final protected SelectionKey getSelectionKey() {
		return key;
	}
}