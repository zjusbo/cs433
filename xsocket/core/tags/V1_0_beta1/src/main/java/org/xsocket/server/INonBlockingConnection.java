// $Id: INonBlockingConnection.java 45 2006-06-22 16:21:07Z grro $
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
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import org.xsocket.IConnection;


/**
 * A connection which uses the underlying channel in a non-blocking manner.  
 * 
 * @author grro@xsocket.org
 */
public interface INonBlockingConnection extends IConnection {

	/**
	 * stops the handling of received data 
	 *
	 */
	public void stopReceiving();

	
	/**
	 * gets the number of bytes which are available to read
	 * 
	 * @return number of available bytes
	 */
	public int getNumberOfAvailableBytes();
	
	
	/**
	 * read all received bytes
	 * 
	 * @return the received bytes 
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readAvailable() throws IOException;

	
	
	/**
	 * read all received bytes. The metod returns if no bytes are available, or if 
	 * the delimiter has been reached
	 * 
 	 * @param delimiter the delimiter
	 * @param outputChannel the output channel to write the available bytes
	 * @return true, if the delimiter has been found
	 * @throws IOException If some other I/O error occurs
	 */
	public boolean readAvailable(String delimiter, WritableByteChannel outputChannel) throws IOException;
	
	
	/**
	 * read a record by using a delimiter 
	 * 
	 * @param delimiter the delimiter
	 * @return the record
 	 * @throws BufferUnderflowException if the delimiter has not been found 
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readRecord(String delimiter) throws IOException, BufferUnderflowException;

	/**
	 * read a string by using a delimiter and the connection default encoding
	 * 
	 * @param delimiter the delimiter
	 * @return the string
 	 * @throws BufferUnderflowException if the delimiter has not been found 
	 * @throws IOException If some other I/O error occurs
	 */
	public String readWord(String delimiter) throws IOException, BufferUnderflowException;


	/**
	 * read a string by using a delimiter
	 * 
	 * @param delimiter the delimiter
	 * @param encoding the encodin to use
	 * @return the string
 	 * @throws BufferUnderflowException if the delimiter has not been found 
	 * @throws IOException If some other I/O error occurs
	 */
	public String readWord(String delimiter, String encoding) throws IOException, BufferUnderflowException;
	
	
	/**
	 * read an int
	 * 
	 * @return the int value
 	 * @throws BufferUnderflowException If there are fewer than four bytes remaining in this buffer
	 * @throws IOException If some other I/O error occurs
	 */
	public int readInt() throws IOException, BufferUnderflowException;

	
	/**
	 * read a long
	 * 
	 * @return the long value
 	 * @throws BufferUnderflowException If there are fewer than four bytes remaining in this buffer
	 * @throws IOException If some other I/O error occurs
	 */
	public long readLong() throws IOException, BufferUnderflowException;

	
	/**
	 * read a double
	 * 
	 * @return the double value
 	 * @throws BufferUnderflowException If there are fewer than four bytes remaining in this buffer
	 * @throws IOException If some other I/O error occurs
	 */
	public double readDouble() throws IOException, BufferUnderflowException;
	
	
	/**
	 * read a byte
	 * 
	 * @return the byte value
 	 * @throws BufferUnderflowException If there are fewer than four bytes remaining in this buffer
	 * @throws IOException If some other I/O error occurs
	 */
	public byte readByte() throws IOException, BufferUnderflowException;
	
	
	
	/**
	 * get the idle timeout  
	 * 
	 * @return idle timeout
	 */
	public long getIdleTimeout();
	
	
	/**
	 * gets the connection timeout
	 * 
	 * @return connection timeout
	 */
	public long getConnectionTimeout();
}
