// $Id$
/**
 * Copyright 2006 xsocket.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.xsocket.util;


import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.text.SimpleDateFormat;
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
		if (bytes > (10 * 1000 * 1000)) {
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

		if (duration < 1000) {
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
}
