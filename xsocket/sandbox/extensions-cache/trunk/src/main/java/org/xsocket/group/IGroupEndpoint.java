//$Id: AbstractChannelBasedEndpoint.java 1049 2007-03-21 16:42:48Z grro $
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
package org.xsocket.group;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.net.SocketTimeoutException;
import java.util.Set;



/**
 *   
 *  
 * @author grro@xsocket.org
 */
public interface IGroupEndpoint<T extends Message> extends Closeable {
	
	
	/**
	 * returns, if the connection is open 
	 * 
	 * 
	 * @return true if the connection is open
	 */
	public boolean isOpen();


	/**
	 * return the group member address of the endpoint
	 * @return the group member address
	 */
	public Address getLocalAddress();

	public Set<Address> getPeerAddresses();
	
		
	public <E extends Serializable> ObjectMessage<E> createObjectMessage(E obj) throws IOException;
	

	
	 /**
	 * sends a message to the group peers
	 * 
	 * @param message          the message to send 
	 * @throws IOException If some other I/O error occurs 
	 * @throws SocketTimeoutException if the timeout has been reached
	 */
	public void send(T message) throws IOException, SocketTimeoutException;
	
	
	
	/**
	 * receive a message (receive timeout = 0)
	 * 
	 * @return the received message
     * @throws IOException If some other I/O error occurs 
	 */
	public T receiveMessage() throws IOException;
	
	
	/**
	 * receive a message
	 * 
	 * @return the received message
     * @throws IOException If some other I/O error occurs 
     * @throws SocketTimeoutException If the receive timeout has been reached
	 */
	public T receiveMessage(long receiveTimeout) throws IOException, SocketTimeoutException;
	
	
	/**
	 * adds a listener 
	 * @param listener the listener to add
	 */
	public void addListener(IGroupNodeListener listener);
	
	
	/**
	 * removes a listener 
	 * @param listener the listener to remove 
	 */
	public void removeListener(IGroupNodeListener listener);
}
