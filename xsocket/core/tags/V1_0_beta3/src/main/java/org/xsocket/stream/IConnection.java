// $Id: IConnection.java 778 2007-01-16 07:13:20Z grro $
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
import java.nio.channels.GatheringByteChannel;

import org.xsocket.ClosedConnectionException;



/**
 * A connection (session) between two endpoints. It encapsulates the underlying socket channel. <br><br>
 * 
 * @author grro@xsocket.org
 */
public interface IConnection extends GatheringByteChannel {
	
	public static final String INITIAL_DEFAULT_ENCODING = "UTF-8";
	public static final boolean INITIAL_AUTOFLUSH = true;
	

	/**
	 * returns the id
	 * 
	 * @return id
	 */
	public String getId();
	
	
	
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
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public void close() throws ClosedConnectionException, IOException;
	
	
	/**
	 * flush the send buffer
	 *
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public void flush() throws ClosedConnectionException, IOException;
	

	
	/**
	 * returns the port of the local endpoint
	 * 
	 * @return the locale port number
	 */
	public int getLocalPort();
	
	

	/**
	 * returns the local address
	 * 
	 * @return the local IP address or InetAddress.anyLocalAddress() if the socket is not bound yet.
	 */
	public InetAddress getLocalAddress();
	

	
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
	 * returns a compact string representation of the object.
	 *  
	 * @return compact string representation of the object.
	 */
	public String toCompactString();
	
	
	
	/**
	 * set autoflush. If atoflush is activated, each write call 
	 * will cause a flush. <br><br>
	 * 
	 * By default the autoflush is activated
	 *   
	 * @param autoflush true if autoflush should be activated 
	 */
	public void setAutoflush(boolean autoflush);
	
	
	/**
	 * get autoflush
	 * @return true, if autoflush is activated
	 */
	public boolean getAutoflush();
	
	
	/**
	 * returns the address of the remote endpoint
	 * 
	 * @return the remote IP address, or null if the remote endpoint is not connected.
	 */
	public InetAddress getRemoteAddress();
	


	/**
	 * returns the port of the remote endpoint
	 * 
	 * @return the remote port number, or 0 if the remote endpoint is not connected yet.
	 */
	public int getRemotePort();
	
	
	


	/**
	 * ad hoc activation of ssl. By perfoming of this 
	 * method all remaining data to send will be flushed. 
	 * After this all data will be sent and received by 
	 * using ssl
	 * 
	 * @throws IOException If some other I/O error occurs
	 */
	public void startSSL() throws IOException;
	
	
	/**
	 * sends a message to the remote endpoint
	 * 
	 * @param message    the message to send 
	 * @param encoding   the encoding which should be used th encode the chars into byte (e.g. 'US-ASCII' or 'UTF-8')
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public int write(String message, String encoding) throws ClosedConnectionException, IOException;

	
	/**
	 * sends a message to the remote endpoint by using the connection default encoding 
	 * 
	 * @param message  the message to send 
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public int write(String message) throws ClosedConnectionException, IOException;

	
	/**
	 * sends a byte to the remote endpoint
	 *  
	 * @param b   the byte to send
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public int write(byte b) throws ClosedConnectionException, IOException;

	
	/**
	 * sends bytes to the remote endpoint
	 *  
	 * @param bytes   the bytes to send
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public int write(byte... bytes) throws ClosedConnectionException, IOException;
	

	/**
	 * sends bytes to the remote endpoint
	 *  
	 * @param bytes    the bytes to send
	 * @param offset   The offset of the subarray to be used; must be non-negative and no larger than array.length. The new buffer's position will be set to this value.
	 * @param length   The length of the subarray to be used; must be non-negative and no larger than array.length - offset. The new buffer's limit will be set to offset + length.
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public int write(byte[] bytes, int offset, int length) throws ClosedConnectionException, IOException;


	/**
	 * sends a byte buffer to the remote endpoint
	 *  
	 * @param buffer   the bytes to send
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public int write(ByteBuffer buffer) throws ClosedConnectionException, IOException;
	
	
	/**
	 * sends an array of byte buffer to the remote endpoint
	 *  
	 * @param buffers   the bytes to send
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public long write(ByteBuffer[] buffers) throws ClosedConnectionException, IOException;


	/**
	 * sends an int to the remote endpoint
	 *  
	 * @param i   the int value to send
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public int write(int i) throws ClosedConnectionException, IOException;


	/**
	 * sends a long to the remote endpoint
	 *  
	 * @param l   the int value to send
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public int write(long l) throws ClosedConnectionException, IOException;

	
	/**
	 * sends a double to the remote endpoint
	 *  
	 * @param d   the int value to send
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedConnectionException if the underlying channel is closed  
	 */
	public int write(double d) throws ClosedConnectionException, IOException;

}
