// $Id: IConnection.java 46 2006-06-22 16:22:10Z grro $
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

package org.xsocket;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;



/**
 * A connection (session) between two endpoints. It encapsulates the underlying socket channel. 
 * 
 * @author grro@xsocket.org
 */
public interface IConnection {
	
	/**
	 * sets the dafault encoding used by this connection
	 * 
	 * @param encoding the default encoding
	 */
	public void setDefaultEncoding(String encoding);
	
	
	/**
	 * gets the dafault encoding used by this connection
	 *  
	 * @return the default encoding
	 */
	public String getDefaultEncoding();
	
	
	/**
	 * sets the assigend connection id
	 *  
	 * @param id the connection id
	 */
	public void setId(String id);
	
	
	/**
	 * the connection id
	 * 
	 * @return connection id
	 */
	public String getId();
	
	
	
	/**
	 * Returns the address of the remote endpoint
	 * 
	 * @return the remote IP address, or null if the remote endpoint is not connected.
	 */
	public InetAddress getRemoteAddress();
	
	

	/**
	 * Returns the port of the remote endpoint
	 * 
	 * @return the remote port number, or 0 if the remote endpoint is not connected yet.
	 */
	public int getRemotePort();
	
	
	
	/**
	 * sends a message to the remote endpoint
	 * 
	 * @param message the message to send 
	 * @param encoding the encoding which should be used th encode the chars into byte (e.g. 'US-ASCII' or 'UTF-8')
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ConnectionClosedException if the underlying channel is closed  
	 */
	public int writeWord(String s, String encoding) throws ClosedConnectionException,IOException;

	/**
	 * sends a message to the remote endpoint by using the connection default encoding 
	 * 
	 * @param message the message to send 
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ConnectionClosedException if the underlying channel is closed  
	 */
	public int writeWord(String s) throws ClosedConnectionException,IOException;

	
	/**
	 * sends a byteto the remote endpoint
	 *  
	 * @param b the byte to send
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ConnectionClosedException if the underlying channel is closed  
	 */
	public int writeByte(byte b) throws ClosedConnectionException,IOException;
	

	/**
	 * sends a byte buffer to the remote endpoint
	 *  
	 * @param buffer the bytes to send
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ConnectionClosedException if the underlying channel is closed  
	 */
	public int write(ByteBuffer buffer) throws ClosedConnectionException,IOException;
	
	
	/**
	 * sends an array of byte buffer to the remote endpoint
	 *  
	 * @param buffer the bytes to send
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ConnectionClosedException if the underlying channel is closed  
	 */
	public long write(ByteBuffer[] buffers) throws ClosedConnectionException,IOException;


	/**
	 * sends an int to the remote endpoint
	 *  
	 * @param i the int value to send
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ConnectionClosedException if the underlying channel is closed  
	 */
	public int writeInt(int i) throws ClosedConnectionException,IOException;


	/**
	 * sends a long to the remote endpoint
	 *  
	 * @param l the int value to send
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ConnectionClosedException if the underlying channel is closed  
	 */
	public int writeLong(long l) throws ClosedConnectionException,IOException;

	
	/**
	 * sends a double to the remote endpoint
	 *  
	 * @param d the int value to send
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ConnectionClosedException if the underlying channel is closed  
	 */
	public int writeDouble(double d) throws ClosedConnectionException,IOException;

		
	/**
	 * returns the connection opening time 
	 * 
	 * @return connection opening time
	 */
	public long getConnectionOpenedTime();
	
	
	/**
	 * returns the last receiving time
	 *  
	 * @return last receiving time
	 */
	public long getLastReceivingTime();
	
	
	
	/**
	 * close the connection
	 *
	 */
	public void close();
	
	
	/**
	 * return if the connection is open 
	 * @return true if the connection is open
	 */
	public boolean isOpen();

	
	/**
	 * Returns the port of the locale endpoint
	 * 
	 * @return the locale port number
	 */
	public int getLocalePort();
	
	

	
	/**
	 * Returns a compact string representation of the object.
	 *  
	 * @return compact string representation of the object.
	 */
	public String toCompactString();
}
