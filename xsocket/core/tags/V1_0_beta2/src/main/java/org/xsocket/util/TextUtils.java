// $Id: TextUtils.java 413 2006-11-21 17:02:36Z grro $

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
package org.xsocket.util;


import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * a Text utilities class 
 * 
 * @author grro@xsocket.org
 */
public final class TextUtils {

	private static final SimpleDateFormat DATE = new SimpleDateFormat("MMM.dd HH:mm");
		
	private static final Map<String, CharsetEncoder> encoders = new HashMap<String, CharsetEncoder>();
	private static final Map<String, CharsetDecoder> decoders = new HashMap<String, CharsetDecoder>();
	
	
	/**
	 * format the given bytes
	 * 
	 * @param bytes the bytes to format
	 * @return the formated String representation of the bytes 
	 */
	public static String printFormatedBytesSize(long bytes) {
		if (bytes > (5 * 1000 * 1000)) {
			return (bytes/1000000) + " mb";
			
		} else if (bytes > (10 * 1000)) {
			return (bytes/1000) + " kb";
			
		} else {
			return bytes +  " bytes";
		}
	}
	
	
	/**
	 * prints the given date
	 * 
	 * @param time the time to print
	 * @return the formated String representation of the date 
	 */	
	public static String printFormatedDate(long time) {
		return DATE.format(new Date(time));		
	}

	/**
	 * prints the given duration
	 * 
	 * @param duration the duration to print
	 * @return the formated String representation of the duration 
	 */		
	public static String printFormatedDuration(long duration) {

		if (duration < 5 * 1000) {
			return duration + " millis";
			
		} else if (duration < (60 * 1000)) {
			return ((int) (duration / 1000)) + " sec";

		} else if (duration < (60 * 60 * 1000)) {
			return ((int) (duration / (60* 1000)))  + " min";
		} else {
			return ((int) (duration / (60 * 60* 1000)))  + " h";
		}
	}
	
	
	/**
	 * converts the given String into a ByteBuffer
	 * 
	 * @param s the String to convert
	 * @param encoding the encoding to use
	 * @return the String as ByteBuffer
	 */
	public static ByteBuffer toByteBuffer(String s, String encoding) {
		CharsetEncoder encoder = encoders.get(encoding);
		if (encoder == null) {
			Charset charset = Charset.forName(encoding);
			if (charset != null) {
			    encoder = charset.newEncoder(); 
			    encoders.put(encoding, encoder);
			    decoders.put(encoding, charset.newDecoder());
			} else {
				return null;
			}
		}
		
		try {
			return encoder.encode(CharBuffer.wrap(s));
		} catch (CharacterCodingException cce) {
			throw new RuntimeException(cce);
		}
	}
	
	
	/**
	 * converts the given ByteBuffer into String by using 
	 * UTF-8 encoding
	 * 
	 * @param buffer the ByteBuffer to convert
	 * @return the ByteByuffer as String
	 */
	public static String toString(ByteBuffer buffer) {
		return toString(buffer, "UTF-8");
	}


	/**
	 * converts the given ByteBuffer into String
	 * 
	 * @param buffer the ByteBuffer to convert
	 * @param encoding the encoding to use
	 * @return the ByteByuffer as String
	 */
	public static String toString(ByteBuffer buffer, String encoding) {
		try {
			CharsetDecoder decoder = decoders.get(encoding);
			if (decoder == null) {
				Charset charset = Charset.forName(encoding);
				if (charset != null) {
					decoder = charset.newDecoder();
				    decoders.put(encoding, decoder);
				    encoders.put(encoding, charset.newEncoder());
				} else {
					throw new RuntimeException("charset '" + encoding + "' has not been found");
				}
			}
			
			return decoder.decode(buffer).toString();
			
		} catch (CharacterCodingException cce) {
			RuntimeException re = new RuntimeException("coding exception for '" + encoding + "' occured: " + cce.toString(), cce);
			throw re;
		}
	}
	 
	public static String toByteString(byte[] bytes) {
		return toByteString(ByteBuffer.wrap(bytes));
	}
	
	public static String toByteString(ByteBuffer buffer) {
		StringBuilder sb = new StringBuilder();
		
		while (buffer.hasRemaining()) {
			String hex = Integer.toHexString(0x0100 + (buffer.get() & 0x00FF)).substring(1);
			sb.append((hex.length() < 2 ? "0" : "") + hex + " ");
		}
		
		return sb.toString();
	}
	
	/**
	 * converts the given list of ByteBuffers into String
	 * 
	 * @param buffers the list of ByteBuffer to convert
	 * @param encoding the encoding to use
	 * @return the ByteByuffer as String
	 */
	public static String toString(List<ByteBuffer> buffers, String encoding) {
		return toString(buffers.toArray(new ByteBuffer[buffers.size()]), encoding);
	}
	

	/**
	 * converts the given array of ByteBuffers into String
	 * 
	 * @param buffers the array of ByteBuffer to convert
	 * @param encoding the encoding to use
	 * @return the ByteByuffer as String
	 */
	public static String toString(ByteBuffer[] buffers, String encoding) {
		StringBuilder sb = new StringBuilder();
		
		for (ByteBuffer buffer : buffers) {
			if (buffer != null) {
				sb.append(toString(buffer, encoding));
			}
		}
		
		return sb.toString();
	}
	


		

	

	/**
	 * print the bytebuffer as limited string
	 * 
	 * @param buffers the buffers to print
	 * @param encoding the encoding to use  
	 * @param maxOutSize the max size to print
	 * @return the ByteBuffers as hex representation
	 */
	public static String toString(ByteBuffer[] buffers, String encoding, int maxOutSize) {

		// first cut output if longer than max limit
		String postfix = "";
		int size = 0;
		List<ByteBuffer> copies = new ArrayList<ByteBuffer>();
		for (ByteBuffer buffer : buffers) {
			ByteBuffer copy = buffer.duplicate();
			if ((size + copy.limit()) > maxOutSize) {
				copy.limit(maxOutSize - size);
				copies.add(copy);
				postfix = " [output has been cut]";
				break;
			} else {
				copies.add(copy);
			}
		}

		StringBuilder result = new StringBuilder();

		// create text out put
		try {
			for (ByteBuffer buffer : copies) {
				result.append(toString(buffer, encoding));
				buffer.flip();
			}
		} catch (Exception ignore) { }

		result.append(postfix);

		return result.toString();
	}


	/**
	 * print the bytebuffer as hex string
	 * 
	 * @param buffers the buffers to print 
	 * @param maxOutSize the max size to print
	 * @return the ByteBuffers as hex representation
	 */
	private static String toHexString(ByteBuffer[] buffers, int maxOutSize) {
	
		// first cut output if longer than max limit
		String postfix = "";
		int size = 0;
		List<ByteBuffer> copies = new ArrayList<ByteBuffer>();
		for (ByteBuffer buffer : buffers) {
			ByteBuffer copy = buffer.duplicate();
			if ((size + copy.limit()) > maxOutSize) {
				copy.limit(maxOutSize - size);
				copies.add(copy);
				postfix = " [output has been cut]";
				break;
			} else {
				copies.add(copy);
			}
		}
	
		StringBuilder result = new StringBuilder();
	
	
		result.append("[hex:]");
		for (ByteBuffer buffer : copies) {
			result.append(toByteString(buffer));
		}
	
		result.append(postfix);
	
		return result.toString();
	}


	public static String toTextOrHexString(ByteBuffer[] buffers, String encoding, int maxOutSize) {

		String result = toString(buffers, encoding, maxOutSize);
		if (result.length() > 0) {
			return result;
		} else {
			return toHexString(buffers, maxOutSize); 
		}
	}
}
