// $Id: IoHandlerBase.java 1315 2007-06-10 08:05:00Z grro $
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
package org.xsocket.stream.io.impl;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Map;

import org.xsocket.stream.io.spi.IIoHandler;
import org.xsocket.stream.io.spi.IIoHandlerCallback;



/**
 * internal IoHandler 
 * 
 * 
 * @author grro@xsocket.org
 */
abstract class ChainableIoHandler implements IIoHandler {
	
	private ChainableIoHandler successor = null;
	private ChainableIoHandler previous = null;
	
	private IIoHandlerCallback callback = null;
	
	
	/**
	 * constructor
	 *  
	 * @param successor  the sucessor
	 */
	public ChainableIoHandler(ChainableIoHandler successor) {
		setSuccessor(successor);
	}
	
	
	/**
	 * return the successor
	 * 
	 * @return the successor
	 */
	public final ChainableIoHandler getSuccessor() {
		return successor;
	}
	
	
	/**
	 * set the successor 
	 *
	 * @param successor the successor
	 */
	protected final void setSuccessor(ChainableIoHandler successor) {
		this.successor = successor;
		if (successor != null) {
			successor.setPrevious(this);
		}
	}

	
	/**
	 * set the previous IoHandler 
	 * @param previous the previous IoHandler 
	 */
	protected final void setPrevious(ChainableIoHandler previous) {
		this.previous = previous;
	}

	
	/**
	 * get the previous IoHandler 
	 * @return the previous IoHandler 
	 */
	protected final ChainableIoHandler getPrevious() {
		return previous;
	}
	
	
	/**
	 * get the local address of the underlying connection
	 * 
	 * @return the local address of the underlying connection
	 */	
	public InetAddress getLocalAddress() {
		return getSuccessor().getLocalAddress();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getPendingWriteDataSize() {
		ChainableIoHandler successor = getSuccessor();
		if (successor != null) {
			return successor.getPendingWriteDataSize();
		} else {
			return 0;
		}
	}

	
	int getPendingReceiveDataSize() {
		ChainableIoHandler successor = getSuccessor();
		if (successor != null) {
			return successor.getPendingReceiveDataSize();
		} else {
			return 0;
		}
	}

	

	public void suspendRead() throws IOException {
		ChainableIoHandler successor = getSuccessor();
		if (successor != null) {
			successor.suspendRead();
		} 
	}
	
	public void resumeRead() throws IOException {
		ChainableIoHandler successor = getSuccessor();
		if (successor != null) {
			successor.resumeRead();
		} 		
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
	public void setIdleTimeoutSec(int timeout) {
		getSuccessor().setIdleTimeoutSec(timeout);
	}
	
	
	
	/**
	 * sets the connection timout
	 * 
	 * @param timeout the connection timeout
	 */
	public void setConnectionTimeoutSec(int timeout) {
		getSuccessor().setConnectionTimeoutSec(timeout);
	}
	
	
	/**
	 * gets the connection timeout 
	 *  
	 * @return the connection timeout
	 */
	public int getConnectionTimeoutSec() {
		return getSuccessor().getConnectionTimeoutSec();
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getIdleTimeoutSec() {
		return getSuccessor().getIdleTimeoutSec();
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
	
	
	
	/**
	 * drain the handler`s read buffer
	 * 
	 * @return the content of the handler`s read buffer
	 */
	public abstract LinkedList<ByteBuffer> drainIncoming();
	
	
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(this.getClass().getSimpleName() + "#" + hashCode());
		
		
		ChainableIoHandler ioHandler = this;
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
