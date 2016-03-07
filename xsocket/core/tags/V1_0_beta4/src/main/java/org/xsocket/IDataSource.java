// $Id: IDataSource.java 856 2007-02-03 11:01:28Z grro $
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
import java.io.UnsupportedEncodingException;


/**
 * data source definition 
 * 
 * @author grro@xsocket.org
 */
public interface IDataSource {

	
	
	
	/**
	 * read a byte
	 * 
	 * @return the byte value
	 * @throws IOException If some other I/O error occurs
	 */
	public byte readByte() throws IOException;
	
	
	/**
	 * read an int
	 * 
	 * @return the int value
	 * @throws IOException If some other I/O error occurs
	 */
	public int readInt() throws IOException;

	
	/**
	 * read a long
	 * 
	 * @return the long value
	 * @throws IOException If some other I/O error occurs
	 */
	public long readLong() throws IOException;

	
	/**
	 * read a double
	 * 
	 * @return the double value
	 * @throws IOException If some other I/O error occurs
	 */
	public double readDouble() throws IOException;
	
	

	/**
	 * read bytes by using a length defintion 
	 *  
	 * @param length the amount of bytes to read  
	 * @return the read bytes
	 * @throws IOException If some other I/O error occurs
	 */		
	public byte[] readBytesByLength(int length) throws IOException;

	

	
	
	/**
	 * read a string by using a length definition and the connection default encoding
	 * 
	 * @param length the amount of bytes to read  
	 * @return the string
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the given encoding is not supported 
	 */
	public String readStringByLength(int length) throws IOException, UnsupportedEncodingException;



	/**
	 * read a string by using a length definition 
	 * 
	 * @param length the amount of bytes to read  
	 * @param encoding the encodin to use
	 * @return the string
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the given encoding is not supported 
	 */
	public String readStringByLength(int length, String encoding) throws IOException, UnsupportedEncodingException;
}
