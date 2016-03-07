// $Id: DataConverter.java 1546 2007-07-23 06:07:56Z grro $

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


import java.io.UnsupportedEncodingException;
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
 * a data converter utilities class 
 * 
 * @author grro@xsocket.org
 */
public final class DataConverter {

	private static final Map<String, CharsetEncoder> encoders = new HashMap<String, CharsetEncoder>();
	private static final Map<String, CharsetDecoder> decoders = new HashMap<String, CharsetDecoder>();
	
	
	/**
	 * converts the given byte size in a textual representation
	 * 
	 * @param bytes the bytes to convert
	 * @return the formated String representation of the bytes 
	 */
	public static String toFormatedBytesSize(long bytes) {
		if (bytes > (5 * 1000 * 1000)) {
			return (bytes/1000000) + " mb";
			
		} else if (bytes > (10 * 1000)) {
			return (bytes/1000) + " kb";
			
		} else {
			return bytes +  " bytes";
		}
	}
	
	
	/**
	 * converts the given time in a textual representation
	 * 
	 * @param time the time to convert
	 * @return the formated String representation of the date 
	 */	
	public static String toFormatedDate(long time) {
		return new SimpleDateFormat("MMM.dd HH:mm").format(new Date(time));		
	}

	/**
	 * converts the given duration in a textual representation
	 * 
	 * @param duration the duration to convert
	 * @return the formated String representation of the duration 
	 */		
	public static String toFormatedDuration(long duration) {

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
	public static String toString(ByteBuffer buffer) throws UnsupportedEncodingException {
		return toString(buffer, "UTF-8");
	}

	
	/**
	 * converts the given ByteBuffer array into String by using 
	 * UTF-8 encoding
	 * 
	 * @param buffer the ByteBuffer arrayto convert
	 * @return the ByteByuffer as String
	 */
	public static String toString(ByteBuffer[] buffer) throws UnsupportedEncodingException {
		return toString(buffer, "UTF-8");
	}


	/**
	 * converts the given ByteBuffer into String repesentation
	 * 
	 * @param buffer the ByteBuffer to convert
	 * @param encoding the encoding to use
	 * @return the ByteByuffer as String
	 */
	public static String toString(ByteBuffer buffer, String encoding) throws UnsupportedEncodingException {
		try {
			CharsetDecoder decoder = decoders.get(encoding);
			if (decoder == null) {
				Charset charset = Charset.forName(encoding);
				if (charset != null) {
					decoder = charset.newDecoder();
				    decoders.put(encoding, decoder);
				    encoders.put(encoding, charset.newEncoder());
				} else {
					throw new UnsupportedEncodingException("charset '" + encoding + "' has not been found");
				}
			}
			
			return decoder.decode(buffer).toString();
			
		} catch (CharacterCodingException cce) {
			RuntimeException re = new RuntimeException("coding exception for '" + encoding + "' occured: " + cce.toString(), cce);
			throw re;
		}
	}
	
	
	
	/**
	 * converts the given ByteBuffer into a hex string
	 * 
	 * @param buffer             the ByteBuffer to convert
	 * @return the hex string 
	 */ 
	public static String toByteString(ByteBuffer buffer) {
		StringBuilder sb = new StringBuilder();
		
		while (buffer.hasRemaining()) {
			String hex = Integer.toHexString(0x0100 + (buffer.get() & 0x00FF)).substring(1);
			sb.append((hex.length() < 2 ? "0" : "") + hex + " ");
		}
		
		return sb.toString();
	}


	
	/**
	 * converts the given list of ByteBuffers into a String
	 * 
	 * @param buffers the list of ByteBuffer to convert
	 * @param encoding the encoding to use
	 * @return the ByteByuffer as String
	 */
	public static String toString(List<ByteBuffer> buffers, String encoding) throws UnsupportedEncodingException {
		return toString(buffers.toArray(new ByteBuffer[buffers.size()]), encoding);
	}
	

	/**
	 * converts the given array of ByteBuffers into String
	 * 
	 * @param buffers the array of ByteBuffer to convert
	 * @param encoding the encoding to use
	 * @return the ByteByuffer as String
	 */
	public static String toString(ByteBuffer[] buffers, String encoding) throws UnsupportedEncodingException {
		byte[] bytes = toBytes(buffers);
		if (bytes != null) {
			return new String(bytes, encoding);
		} else {
			return "";
		}
	}
	


	/**
	 * print the bytebuffer as limited string
	 * 
	 * @param buffers the buffers to print
	 * @param encoding the encoding to use  
	 * @param maxOutSize the max size to print
	 * 
	 * @return the ByteBuffers as string representation
	 */
	public static String toString(ByteBuffer[] buffers, String encoding, int maxOutSize) throws UnsupportedEncodingException {
		String s = toString(buffers, encoding);
		if (s.length() > maxOutSize) {
			s = s.substring(0, maxOutSize) + " [output has been cut]";
		}
		return s;
	}

	
	/**
	 * merges a ByteBuffer array into a (direct) ByteBuffer
	 * 
	 * @param buffers the ByteBuffer array to merge
	 * @return the single ByteBuffer
	 */
	public static ByteBuffer toByteBuffer(ByteBuffer[] buffers) {
		byte[] bytes = toBytes(buffers);
		return ByteBuffer.wrap(bytes);
	}

	
	/**
	 * converts a list of ByteBuffer to a byte array
	 * 
	 * @param buffers the ByteBuffer list to convert
	 * @return the byte array
	 */
	public static byte[] toBytes(List<ByteBuffer> buffers) {
		return toBytes(buffers.toArray(new ByteBuffer[buffers.size()]));
	}

	
	
	/**
	 * converts a ByteBuffer array to a byte array
	 * 
	 * @param buffers the ByteBuffer array to convert
	 * @return the byte array
	 */
	public static byte[] toBytes(ByteBuffer[] buffers) {
		byte[] result = null;

		if (buffers == null) {
			return null;
		}

		int size = 0;
		for (ByteBuffer buffer : buffers) {
			size += buffer.remaining();
			if (result == null) {
				byte[] bytes = toBytes(buffer);
				if (bytes.length > 0) {
					result = bytes;
				}
			} else {
				byte[] additionalBytes = toBytes(buffer);
				byte[] newResult = new byte[result.length + additionalBytes.length];
				System.arraycopy(result, 0, newResult, 0, result.length);
				System.arraycopy(additionalBytes, 0, newResult, result.length, additionalBytes.length);
				result = newResult;
			}
		}
				
		return result;
	}

	
	/**
	 * converts a ByteBuffer into a byte array
	 * 
	 * @param buffer the ByteBuffer to convert
	 * @return the byte array
	 */
	public static byte[] toBytes(ByteBuffer buffer) {
		
		int savedPos = buffer.position();
		int savedLimit = buffer.limit();

		try {
			
			byte[] array = new byte[buffer.limit() - buffer.position()];
	
			if (buffer.hasArray()) {
				int offset = buffer.arrayOffset();
				byte[] bufferArray = buffer.array();
				System.arraycopy(bufferArray, offset, array, 0, array.length);
	
				return array;
			} else {
				buffer.get(array);
				return array;
			}
			
		} finally {
			buffer.position(savedPos);
			buffer.limit(savedLimit);
		}
	}
	
	

	/**
	 * print the byte array as a hex string
	 * 
	 * @param buffers the buffers to print 
	 * @param maxOutSize the max size to print
	 * 
	 * @return the ByteBuffers as hex representation
	 */
	public static String toHexString(byte[] buffers, int maxOutSize) {
		return toHexString(new ByteBuffer[] { ByteBuffer.wrap(buffers) }, maxOutSize);
	}



	/**
	 * print the bytebuffer as a hex string
	 * 
	 * @param buffers            the buffers to print
	 * @param maxOutSize         the max size to print
	 * 
	 * @return the ByteBuffers as hex representation
	 */
	public static String toHexString(ByteBuffer[] buffers, int maxOutSize) {

		// first cut output if longer than max limit
		String postfix = "";
		int size = 0;
		List<ByteBuffer> copies = new ArrayList<ByteBuffer>();
		for (ByteBuffer buffer : buffers) {
			ByteBuffer copy = buffer.duplicate();
			if ((size + copy.limit()) > maxOutSize) {
				copy.limit(maxOutSize - size);
				copies.add(copy);
				postfix = " [...output has been cut]";
				break;
			} else {
				copies.add(copy);
			}
		}
	
		StringBuilder result = new StringBuilder();
	
	
		for (ByteBuffer buffer : copies) {
			result.append(toByteString(buffer));
		}
	
		result.append(postfix);
	
		return result.toString();
	}


	/**
	 * convert the ByteBuffer into a hex or text string (deping on content)
	 * 
	 * @param buffer      the buffers to print 
	 * @param maxOutSize  the max size to print
	 * @param encoding    the encoding to use
	 * @return the converted ByteBuffer
	 */
	public static String toTextOrHexString(ByteBuffer buffer, String encoding, int maxOutSize) {
		return toTextOrHexString(new ByteBuffer[] { buffer }, encoding, maxOutSize);
	}
	
	
	/**
	 * convert the ByteBuffer array into a hex or text string (deping on content)
 	 *
	 * @param buffers     the buffers to print 
	 * @param maxOutSize  the max size to print
	 * @param encoding    the encoding to use
	 * @return the converted ByteBuffer
	 */
	public static String toTextOrHexString(ByteBuffer[] buffers, String encoding, int maxOutSize)  {
		boolean hasNonPrintableChars = false;
		
		for (ByteBuffer buffer : buffers) {
			ByteBuffer copy = buffer.duplicate();
			while (copy.hasRemaining()) {
				int i = copy.get();
				if (i < 10) {
					hasNonPrintableChars = true;
				}
			}
		}
		
		if (hasNonPrintableChars) {
			return toHexString(buffers, maxOutSize);
		} else {
			try {
				return toString(buffers, encoding, maxOutSize);
			} catch (UnsupportedEncodingException use) {
				return toHexString(buffers, maxOutSize);
			}
		}
	}
	
	
	
	public static String toTextAndHexString(ByteBuffer[] buffers, String encoding, int maxOutSize) {
		StringBuilder sb = new StringBuilder();
		sb.append(DataConverter.toHexString(buffers, 500));
		sb.append("\n");
		try {
			sb.append("[txt:] " + toString(buffers, "US-ASCII", 500));
		} catch (Exception ignore) { 
			sb.append("[txt:] ... content not printable ...");
		}
		return sb.toString();
	}
}
