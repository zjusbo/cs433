// $Id: IDataSource.java 1638 2007-08-03 06:02:02Z grro $
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
import java.nio.ByteBuffer;

import org.xsocket.stream.IConnection;


/**
 * A data sink is an I/O resource capable of providing data.
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
	 * read a short value
	 * 
	 * @return the short value
	 * @throws IOException If some other I/O error occurs
	 */
	public short readShort() throws IOException;
	
	
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
	 * read a ByteBuffer by using a delimiter. The default encoing will be used to decode the delimiter 
	 * To avoid memory leaks the {@link IConnection#readByteBufferByDelimiter(String, int)} method is generally preferable  
	 * <br> 
     * For performance reasons, the ByteBuffer readByteBuffer method is 
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter
	 * @return the ByteBuffer
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException;
	
	
	
	/**
	 * read a ByteBuffer by using a delimiter. The default encoing will be used to decode the delimiter 
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is 
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown	 
	 * @return the ByteBuffer
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found     
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter, int maxLength) throws IOException, MaxReadSizeExceededException;



	/**
	 * read a ByteBuffer by using a delimiter 
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is 
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter
	 * @param encoding    the encoding of the delimiter
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown	 
	 * @return the ByteBuffer
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found     
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, MaxReadSizeExceededException;

	
	/**
	 * read a ByteBuffer by using a length defintion 
	 * 
	 * @param length the amount of bytes to read
	 * @return the ByteBuffer
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer[] readByteBufferByLength(int length) throws IOException;



	
	
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
	
	
	/**
	 * read a string by using a delimiter and the connection default encoding
	 * 
	 * @param delimiter   the delimiter
	 * @return the string
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the default encoding is not supported
	 */
	public String readStringByDelimiter(String delimiter) throws IOException, UnsupportedEncodingException;
	
	
	/**
	 * read a string by using a delimiter and the connection default encoding
	 * 
	 * @param delimiter   the delimiter
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown	 
	 * @return the string
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found     
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the default encoding is not supported
	 */
	public String readStringByDelimiter(String delimiter, int maxLength) throws IOException, UnsupportedEncodingException, MaxReadSizeExceededException;
	
	
	
	/**
	 * read a string by using a delimiter
	 * 
	 * @param delimiter   the delimiter
	 * @param encoding    the encodin to use
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown	 
	 * @return the string
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found   
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the given encoding is not supported 
	 */
	public String readStringByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, UnsupportedEncodingException, MaxReadSizeExceededException;

	
	
	/**
	 * read a byte array by using a delimiter
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter  
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown	  
	 * @return the read bytes
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found   
	 * @throws IOException If some other I/O error occurs
	 */		
	public byte[] readBytesByDelimiter(String delimiter, int maxLength) throws IOException, MaxReadSizeExceededException;
}
