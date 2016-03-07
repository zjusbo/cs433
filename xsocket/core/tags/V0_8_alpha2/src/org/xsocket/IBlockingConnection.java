// $Id$
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
import java.nio.ByteBuffer;


/**
 * A connection which uses the underlying channel in a blocking manner. Every I/O operation
 * will block until it completes. 
 * 
 * @author grro@xsocket.org
 */
public interface IBlockingConnection extends IConnection {


	/**
	 * receive a word. the method will block, unit the delimiter has been read.
	 * For the encoding the default encoding of the connection will be used  
	 * 
	 * @param delimiter the delimiter  
	 * @return the received word
	 * @throws IOException If some other I/O error occurs
	 */	
	public String receiveWord(String delimiter) throws IOException;
	
	
	/**
	 * receive a word. the method will block, unit the delimiter has been read  
	 * 
	 * @param delimiter the delimiter
	 * @param encoding the encoding   
	 * @return the received word
	 * @throws IOException If some other I/O error occurs
	 */
	public String receiveWord(String delimiter, String encoding) throws IOException;
	
	
	/**
	 * receive a record. the method will block, unit the delimiter has been read.
	 * 
	 * @param delimiter the delimiter  
	 * @return the received record
	 * @throws IOException If some other I/O error occurs
	 */		
	public ByteBuffer[] receiveRecord(String delimiter) throws IOException;
}
