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
import java.nio.ByteBuffer;

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
	 * read all received bytes
	 * 
	 * @return the received bytes 
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readAvailable() throws IOException;
	
	
	/**
	 * read a record by using a delimiter 
	 * 
	 * @param delimiter the delimiter
	 * @return the record, or null if the delimiter has not been found
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readRecord(String delimiter) throws IOException;

	/**
	 * read a string by using a delimiter and the connection default encoding
	 * @param delimiter the delimiter
	 * @return the string, or null if the delimiter has not been found
	 * @throws IOException If some other I/O error occurs
	 * @return the string
	 */
	public String readWord(String delimiter) throws IOException;
	
	
	/**
	 * read a string by using a delimiter
	 * @param delimiter the delimiter
	 * @param encoding the encodin to use
	 * @return the string, or null if the delimiter has not been found
	 * @throws IOException If some other I/O error occurs
	 * @return the string
	 */
	public String readWord(String delimiter, String encoding) throws IOException;
}
