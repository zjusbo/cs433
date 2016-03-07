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
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.Map;




/**
 * internal IoHandler
 *
 *
 * @author grro@xsocket.org
 */
abstract class IoChainableHandler  {

	private IoChainableHandler successor = null;
	private IoChainableHandler previous = null;

	private IIoHandlerCallback callback = null;


	/**
	 * constructor
	 *
	 * @param successor  the sucessor
	 */
	public IoChainableHandler(IoChainableHandler successor) {
		setSuccessor(successor);
	}
	
	
	/**
	 * "starts" the handler. Callback methods will not be called before
	 * this method has been performed.
	 *
	 *
	 * @param callbackHandler  the callback handler
	 */
	public abstract void init(IIoHandlerCallback callbackHandler) throws IOException;




	/**
	 * non-blocking close of the handler. <br><br>
	 *
	 * The implementation has to be threadsafe
	 *
	 * @param immediate if true, close the connection immediate. If false remaining
	 *                  out buffers (collected by the writOutgoing methods) has
	 *                  to written before closing
	 * @throws IOException If some other I/O error occurs
	 */
	public abstract void close(boolean immediate) throws IOException;



	/**
	 * non-blocking write.  Because the IIoHandler is free to write the given buffers
	 * within a backgound activity, the caller can`t reuse the buffers, until the
	 * {@link IIoHandlerCallback#onWritten(IOException)} call back method has been called.
	 *
	 * @param buffers the data to add into the out buffer
	 * @throws ClosedChannelException if the underlying connection is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	public abstract void write(ByteBuffer[] buffers) throws ClosedChannelException, IOException;



	/**
	 * return the successor
	 *
	 * @return the successor
	 */
	public final IoChainableHandler getSuccessor() {
		return successor;
	}


	/**
	 * set the successor
	 *
	 * @param successor the successor
	 */
	protected final void setSuccessor(IoChainableHandler successor) {
		this.successor = successor;
		if (successor != null) {
			successor.setPrevious(this);
		}
	}


	/**
	 * set the previous IoHandler
	 * @param previous the previous IoHandler
	 */
	protected final void setPrevious(IoChainableHandler previous) {
		this.previous = previous;
	}


	/**
	 * get the previous IoHandler
	 * @return the previous IoHandler
	 */
	protected final IoChainableHandler getPrevious() {
		return previous;
	}


	public boolean reset() {
		IoChainableHandler successor = getSuccessor();
		if (successor != null) {
			return successor.reset();
		}

		return true;
	}


	/**
	 * get the local address of the underlying connection
	 *
	 * @return the local address of the underlying connection
	 */
	public InetAddress getLocalAddress() {
		return getSuccessor().getLocalAddress();
	}


	public long getLastTimeReceivedMillis() {
		return getSuccessor().getLastTimeReceivedMillis();
	}

	public long getNumberOfReceivedBytes() {
		return getSuccessor().getNumberOfReceivedBytes();
	}
	
	public long getNumberOfSendBytes() {
		return getSuccessor().getNumberOfSendBytes();
	}
	
	public String getRegisteredOpsInfo() {
		return getSuccessor().getRegisteredOpsInfo();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public int getPendingWriteDataSize() {
		IoChainableHandler successor = getSuccessor();
		if (successor != null) {
			return successor.getPendingWriteDataSize();
		} else {
			return 0;
		}
	}


	/**
	 * {@inheritDoc}
	 */
	public boolean hasDataToSend() {
		IoChainableHandler successor = getSuccessor();
		if (successor != null) {
			return successor.hasDataToSend();
		} else {
			return false;
		}
	}


	public void suspendRead() throws IOException {
		IoChainableHandler successor = getSuccessor();
		if (successor != null) {
			successor.suspendRead();
		}
	}


	public void resumeRead() throws IOException {
		IoChainableHandler successor = getSuccessor();
		if (successor != null) {
			successor.resumeRead();
		}
	}

	
	public boolean isReadSuspended() {
		IoChainableHandler successor = getSuccessor();
		return successor.isReadSuspended() ;
	}

    public void setRetryRead(boolean isRetryRead) {
		IoChainableHandler successor = getSuccessor();
		successor.setRetryRead(isRetryRead);
    }


	public String getId() {
		return getSuccessor().getId();
	}


	void setPreviousCallback(IIoHandlerCallback callback) {
		this.callback = callback;
	}

	IIoHandlerCallback getPreviousCallback() {
		return callback;
	}


	/**
	 * get the local port of the underlying connection
	 *
	 * @return the local port of the underlying connection
	 */
	public int getLocalPort() {
		return getSuccessor().getLocalPort();
	}



	/**
	 * get the address of the remote host of the underlying connection
	 *
	 * @return the address of the remote host of the underlying connection
	 */
	public InetAddress getRemoteAddress() {
		return getSuccessor().getRemoteAddress();
	}


	/**
	 * get the port of the remote host of the underlying connection
	 *
	 * @return the port of the remote host of the underlying connection
	 */
	public int getRemotePort() {
		return getSuccessor().getRemotePort();
	}


	/**
	 * check, if handler is open
	 *
	 * @return true, if the handler is open
	 */
	public boolean isOpen() {
		return getSuccessor().isOpen();
	}


	/**
	 * {@inheritDoc}
	 */
	public void setIdleTimeoutMillis(long timeout) {
		getSuccessor().setIdleTimeoutMillis(timeout);
	}



	/**
	 * sets the connection timout
	 *
	 * @param timeout the connection timeout
	 */
	public void setConnectionTimeoutMillis(long timeout) {
		getSuccessor().setConnectionTimeoutMillis(timeout);
	}


	/**
	 * gets the connection timeout
	 *
	 * @return the connection timeout
	 */
	public long getConnectionTimeoutMillis() {
		return getSuccessor().getConnectionTimeoutMillis();
	}



	/**
	 * {@inheritDoc}
	 */
	public long getIdleTimeoutMillis() {
		return getSuccessor().getIdleTimeoutMillis();
	}


	/**
	 * {@inheritDoc}
	 */
	public long getRemainingMillisToConnectionTimeout() {
		return getSuccessor().getRemainingMillisToConnectionTimeout();
	}


	/**
	 * {@inheritDoc}
	 */
	public long getRemainingMillisToIdleTimeout() {
		return getSuccessor().getRemainingMillisToIdleTimeout();
	}

	/**
	 * {@inheritDoc}
	 */
	public Object getOption(String name) throws IOException {
		return getSuccessor().getOption(name);
	}


	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Class> getOptions() {
		return getSuccessor().getOptions();
	}


	/**
	 * {@inheritDoc}
	 */
	public void setOption(String name, Object value) throws IOException {
		getSuccessor().setOption(name, value);
	}


	/**
	 * flush the outgoing buffers
	 *
	 * @throws IOException If some other I/O error occurs
	 */
	public abstract void flushOutgoing() throws IOException;



	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(this.getClass().getSimpleName() + "#" + hashCode());


		IoChainableHandler ioHandler = this;
		while (ioHandler.getPrevious() != null) {
			ioHandler = ioHandler.getPrevious();
		}

		String forwardChain = "";
		while (ioHandler != null) {
			forwardChain = forwardChain + ioHandler.getClass().getSimpleName() + " > ";
			ioHandler = ioHandler.getSuccessor();
		}
		forwardChain = forwardChain.trim();
		forwardChain = forwardChain.substring(0, forwardChain.length() - 1);
		sb.append(" (forward chain: " + forwardChain.trim() + ")");

		return sb.toString();
	}
}
