// $Id: IoHandler.java 1276 2007-05-28 15:38:57Z grro $
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
import java.nio.ByteBuffer;
import java.util.LinkedList;

import org.xsocket.ClosedConnectionException;


/**
 * internal IoHandler 
 * 
 * 
 * @author grro@xsocket.org
 */
abstract class IoHandler {
	
	private IoHandler successor = null;
	private IoHandler previous = null;
	
	
	/**
	 * contructore 
	 * @param successor  the sucessor
	 */
	IoHandler(IoHandler successor) {
		setSuccessor(successor);
	}
	
	
	/**
	 * return the successor
	 * 
	 * @return the successor
	 */
	final IoHandler getSuccessor() {
		return successor;
	}

	
	/**
	 * set the successor 
	 *
	 * @param successor the successor
	 */
	final void setSuccessor(IoHandler successor) {
		this.successor = successor;
		if (successor != null) {
			successor.setPrevious(this);
		}
	}

	
	/**
	 * set the previous IoHandler 
	 * @param previous the previous IoHandler 
	 */
	final void setPrevious(IoHandler previous) {
		this.previous = previous;
	}

	/**
	 * get the previous IoHandler 
	 * @return the previous IoHandler 
	 */
	final IoHandler getPrevious() {
		return previous;
	}

	
	/**
	 * flush the outgoing buffers
	 *
	 * @throws IOException If some other I/O error occurs
	 */
	abstract void flushOutgoing() throws IOException;
	
	/**
	 * return the id 
	 * @return id
	 */
	String getId() {
		return getSuccessor().getId();
	}
	
	
	
	/**
	 * opens the handler 
	 * 
	 * @throws IOException If some other I/O error occurs
	 */
	abstract void open() throws IOException;
		
	/**
	 * closes the handler 
	 * 
	 * @param immediate  close the connection immediate without flushing
	 * @throws IOException If some other I/O error occurs
	 */
	abstract void close(boolean immediate) throws IOException;
	

	/**
	 * write into the handler's write buffer
	 * 
	 * @param data the data to add into the out buffer
	 * @throws ClosedConnectionException if the underlying connection is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	abstract void writeOutgoing(ByteBuffer data) throws ClosedConnectionException, IOException;
	
	
	abstract boolean isChainSendBufferEmpty();
	
	
	/**
	 * write into the handler's write buffer
	 * 
	 * @param datas the datas to add into the out buffer
	 * @throws ClosedConnectionException if the underlying connection is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	abstract void writeOutgoing(LinkedList<ByteBuffer> datas) throws ClosedConnectionException, IOException;
		
	/**
	 * drain the handler's read buffer
	 * 
	 * @return the content of the handler's read buffer
	 */
	abstract LinkedList<ByteBuffer> drainIncoming();
	
	
	
	/**
	 * get the local address of the underlying connection
	 * 
	 * @return the local address of the underlying connection
	 */
	InetAddress getLocalAddress() {
		return getSuccessor().getLocalAddress();
	}
	
	/**
	 * get the local port of the underlying connection 
	 * 
	 * @return the local port of the underlying connection 
	 */
	int getLocalPort() {
		return getSuccessor().getLocalPort();
	}
	
	
	/**
	 * get the address of the remote host of the underlying connection
	 * 
	 * @return the address of the remote host of the underlying connection
	 */
	InetAddress getRemoteAddress() {
		return getSuccessor().getRemoteAddress();
	}
	
	/**
	 * get the port of the remote host of the underlying connection
	 * 
	 * @return the port of the remote host of the underlying connection
	 */
	int getRemotePort() {
		return getSuccessor().getRemotePort();
	}
	
	/**
	 * check, if handler is open
	 * 
	 * @return true, if the handler is open
	 */
	boolean isOpen() {
		return getSuccessor().isOpen();
	}
	
	
	
	/**
	 * set the event handler
	 *  
	 * @param ioEventHandler the event handler to set
	 */
	abstract void setIOEventHandler(IIOEventHandler ioEventHandler);
	
	
	/**
	 * get the assigned event handler
	 * 
	 * @return the assigned event handler
	 */
	abstract IIOEventHandler getIOEventHandler();

	
	/**
	 * event handler
	 *
	 * @author grro
	 */
	interface IIOEventHandler {
		
		public void onWrittenEvent();
		
		public void onWriteExceptionEvent(IOException ioe);
		
		public void onDataEvent();
		
		public void onConnectEvent();
		
		public void onDisconnectEvent();
		
		public void onIdleTimeout();
		
		public void onConnectionTimeout();
		
		public void initiateClose();
	}
}
