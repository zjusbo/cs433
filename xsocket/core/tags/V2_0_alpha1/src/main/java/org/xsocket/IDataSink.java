// $Id: IDataSink.java 1691 2007-08-20 05:42:14Z grro $
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
package org.xsocket;

import java.io.IOException;
import java.nio.ByteBuffer;




/**
 * A data sink is an I/O resource capable of receiving data.
 * 
 * @author grro@xsocket.org
 */
public interface IDataSink {


	/**
	 * writes a byte to the data sink
	 *  
	 * @param b   the byte to write
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 */
	public int write(byte b) throws IOException;

	
	/**
	 * writes bytes to the data sink
	 *  
	 * @param bytes   the bytes to write
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 */
	public int write(byte... bytes) throws IOException;
	

	/**
	 * writes bytes to the data sink
	 *  
	 * @param bytes    the bytes to write
	 * @param offset   The offset of the subarray to be used; must be non-negative and no larger than array.length. The new buffer`s position will be set to this value.
	 * @param length   The length of the subarray to be used; must be non-negative and no larger than array.length - offset. The new buffer`s limit will be set to offset + length.
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 */
	public int write(byte[] bytes, int offset, int length) throws IOException;


	/**
	 * writes a byte buffer to the data sink
	 *  
	 * @param buffer   the bytes to write
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 */
	public int write(ByteBuffer buffer) throws IOException;
	
	
	/**
	 * writes a byte array to the data sink
	 *  
	 * @param buffers   the bytes to write
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 */
	public long write(ByteBuffer[] buffers) throws IOException;


	/**
	 * writes a int to the data sink
	 *  
	 * @param i   the int value to write
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 */
	public int write(int i) throws IOException;

	
	/**
	 * writes a short to the data sink
	 *  
	 * @param s   the short value to write
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 */
	public int write(short s) throws IOException;

	/**
	 * writes a long to the data sink
	 *  
	 * @param l   the int value to write
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 */
	public int write(long l) throws IOException;

	
	/**
	 * writes a double to the data sink
	 *  
	 * @param d   the int value to write
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 */
	public int write(double d) throws IOException;
	
	
	/**
	 * write a message
	 * 
	 * @param message    the message to write
	 * @param encoding   the encoding which should be used th encode the chars into byte (e.g. `US-ASCII` or `UTF-8`)
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 */
	public int write(String message, String encoding) throws IOException;

	
	/**
	 * writes a message
	 * 
	 * @param message  the message to write
	 * @return the number of send bytes 
	 * @throws IOException If some other I/O error occurs
	 */
	public int write(String message) throws IOException;

}
