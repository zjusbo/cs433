// $Id: Endpoint.java 1049 2007-03-21 16:42:48Z grro $
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




/**
 * Endpoint implementation
 * 
 * @author grro@xsocket.org
 */
public final class Endpoint extends AbstractChannelBasedEndpoint implements IEndpoint {
	

	/**
  	 * Constructs a datagram socket and binds it to any 
	 * available port on the local host machine. The socket 
	 * will be bound to the wildcard address, an IP address 
	 * chosen by the kernel. 
	 *  
     * @throws IOException If some I/O error occurs
	 */
	public Endpoint() throws IOException {
		this(0);
	}
	
	

	/**
  	 * Constructs a datagram socket and binds it to any 
	 * available port on the local host machine. The socket 
	 * will be bound to the wildcard address, an IP address 
	 * chosen by the kernel. 
	 * 
     * @param receivePacketSize        the receive packet size
     * @throws IOException If some I/O error occurs
   	 */
	public Endpoint(int receivePacketSize) throws IOException {
		this(receivePacketSize, null);
	}

	

	
	/**
  	 * Constructs a datagram socket and binds it to any 
	 * available port on the local host machine. The socket 
	 * will be bound to the wildcard address, an IP address 
	 * chosen by the kernel. 
	 * 
     * @param receivePacketSize        the receive packet size
     * @param datagramHandler          the datagram handler
     * @throws IOException If some I/O error occurs
   	 */
	public Endpoint(int receivePacketSize, IDatagramHandler datagramHandler) throws IOException {
		this(receivePacketSize, datagramHandler, InetAddress.getLocalHost(), 0);
	}
	

	
	/**
  	 * Constructs a datagram socket and binds it to the given 
	 * port on the local host machine. The socket 
	 * will be bound to the wildcard address, an IP address 
	 * chosen by the kernel. 
	 * 
     * @param receivePacketSize        the receive packet size
     * @param datagramHandler          the datagram handler
     * @param address                  the local address
 	 * @param  port                    the local port which must be between 0 and 65535 inclusive.
     * @throws IOException If some I/O error occurs
   	 */
	public Endpoint(int receivePacketSize, IDatagramHandler datagramHandler, InetAddress address, int port) throws IOException {
		super(address, port, datagramHandler, receivePacketSize);
	}
}
