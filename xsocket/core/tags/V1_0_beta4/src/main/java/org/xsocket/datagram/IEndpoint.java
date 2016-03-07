// $Id: IEndpoint.java 910 2007-02-12 16:56:19Z grro $
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
package org.xsocket.datagram;


import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;



import org.xsocket.ClosedConnectionException;



/**
 * a endpoint 
 * 
 * 
 * @author grro
 */
public interface IEndpoint  {
	
	public static int GLOBAL_WORKER_POOL_SIZE = 2;
	
	
	/**
	 * returns, if the connection is open 
	 * 
	 * 
	 * @return true if the connection is open
	 */
	public boolean isOpen();

	
	
	/**
	 * closes the endpoint
	 *
	 */
	public void close();
	
	
	/**
	 * returns the socket address of the local endpoint
	 * 
	 * @return the local socket address
	 */
	public SocketAddress getLocalSocketAddress();
	
	
	/**
	 * returns the address of the local endpoint
	 * 
	 * @return the local address
	 */
	public InetAddress getLocalAddress();	
	
	
	/**
	 * returns the port of the local endpoint
	 * 
	 * @return the local port
	 */
	public int getLocalPort();

	
	/**
	 * sets the default encoding used by this connection
	 * 
	 * @param encoding the default encoding
	 */
	public void setDefaultEncoding(String encoding);
	
	
	/**
	 * gets the default encoding used by this connection
	 *  
	 * @return the default encoding
	 */
	public String getDefaultEncoding();
	
	
    /**
	 * sends a packet to the assigned remote endpoint
	 * 
	 * @param packet the packet to send 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public void send(Packet packet) throws IOException;
}
