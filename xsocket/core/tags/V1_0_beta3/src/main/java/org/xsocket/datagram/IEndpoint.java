// $Id: IEndpoint.java 778 2007-01-16 07:13:20Z grro $
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


import java.net.InetAddress;



/**
 * a endpoint 
 * 
 * 
 * @author grro
 */
public interface IEndpoint {
	
	
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
	 * returns the port of the locale endpoint
	 * 
	 * @return the locale port number
	 */
	public int getLocalPort();
	
	

	/**
	 * returns the locale address
	 * 
	 * @return the locale IP address or InetAddress.anyLocalAddress() if the socket is not bound yet.
	 */
	public InetAddress getLocalAddress();
	

	
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
	 * set the receive data size 
	 * @param size the receive data size
	 */
    public void setReceivePacketSize(int size);
    
    
	/**
	 * return receive data size
	 * @return receive data size
	 */
    public int getReceivePacketSize();
    

	/**
	 * returns a compact string representation of the object.
	 *  
	 * @return compact string representation of the object.
	 */
	public String toCompactString();
}
