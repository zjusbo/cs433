// $Id: TestUtil.java 1025 2007-03-18 08:25:13Z grro $
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
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Random;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Assert;


/**
*
* @author grro@xsocket.org
*/
public final class QAUtil {
	
	private static String testMail = 
		  "Received: from localhost (localhost [127.0.0.1])\r\n" 
		+ "by Semta.de with ESMTP id 881588961.1153334034540.1900236652.1\r\n" 
		+ "for feki@semta.de; Mi, 19 Jul 2006 20:34:00 +0200\r\n" 
		+ "Message-ID: <24807938.01153334039898.JavaMail.grro@127.0.0.1>\r\n" 
		+ "Date: Wed, 19 Jul 2006 20:33:59 +0200 (CEST)\r\n" 
		+ "From: feki2 <fekete99@web.de>\r\n" 
		+ "To: Gregor Roth <feki@semta.de>\r\n" 
		+ "Subject: Test mail\r\n" 
		+ "MIME-Version: 1.0\r\n" 
		+ "Content-Type: multipart/mixed;\r\n" 
		+ "boundary=\"----=_Part_1_14867177.1153334039707\"\r\n" 
		+ "\r\n" 
		+ "This is a multi-part message in MIME format.\r\n"
		+ "------=_Part_1_14867177.1153334039707\r\n" 
		+ "Content-Type: multipart/mixed;\r\n" 
		+ "boundary=\"----=_Part_0_14158819.1153334039687\"\r\n" 
		+ "\r\n" 
		+ "------=_Part_0_14158819.1153334039687\r\n" 
		+ "Content-Type: text/plain; charset=us-ascii\r\n" 
		+ "Content-Transfer-Encoding: 7bit\r\n" 
		+ "\r\n" 
		+ "Halli Hallo\r\n" 
		+ "------=_Part_0_14158819.1153334039687\r\n" 
		+ "------=_Part_1_14867177.1153334039707--";

	

	private static final int OFFSET = 48; 
		

	
	public static ByteBuffer getAsByteBuffer() {
		try {
			Charset charset = Charset.forName("ISO-8859-1");
		    CharsetEncoder encoder = charset.newEncoder();
		    ByteBuffer buf = encoder.encode(CharBuffer.wrap(testMail.toCharArray()));
		    return buf;
		} catch (Exception e) {
			throw new RuntimeException(e.toString());
		}
	}


	public static ByteBuffer generatedByteBuffer(int length) {
		ByteBuffer buffer = ByteBuffer.wrap(generatedByteArray(length));
		return buffer;
	}
	
	public static byte[] generatedByteArray(int length) {
		
		byte[] bytes = new byte[length];
		
		int item = OFFSET;
		
		for (int i = 0; i < length; i++) {
			bytes[i] = (byte) item;
			
			item++;
			if (item > (OFFSET + 9)) {
				item = OFFSET;
			}
		}
		
		return bytes;
	}
	
	
	public static byte[] generatedByteArray(int length, String delimiter) {
		byte[] del = delimiter.getBytes();
		byte[] data = generatedByteArray(length);
		
		byte[] result = new byte[del.length + data.length];
		System.arraycopy(data, 0, result, 0, data.length);
		System.arraycopy(del, 0, result, data.length, del.length);
		return result;
	}
	
	
	public static boolean isEquals(byte[] b1, byte[] b2) {
		if (b1.length != b2.length) {
			return false;
		}
		
		for (int i = 0; i < b1.length; i++) {
			if (b1[i] != b2[i]) {
				return false;
			}
		}
		
		return true;
	}
	
	
	public static boolean isEquals(ByteBuffer[] b1, ByteBuffer[] b2) {
		return isEquals(DataConverter.toByteBuffer(b1), DataConverter.toByteBuffer(b2));
	}
	
	public static boolean isEquals(ByteBuffer b1, ByteBuffer[] b2) {
		return isEquals(b1, DataConverter.toByteBuffer(b2));
	}
	
	public static boolean isEquals(ByteBuffer b1, ByteBuffer b2) {
		if (b1.limit() != b2.limit()) {
			return false;
		}
		
		if (b1.position() != b2.position()) {
			return false;
		}
		
		if (b1.capacity() != b2.capacity()) {
			return false;
		}
		
		for (int i = 0; i < b1.limit(); i++) {
			if (b1.get(i) != b2.get(i)) {
				return false;
			}
		}
		
		return true;
	}
	
	
	
	public static void sleep(int sleepTime) {
		try {
			Thread.sleep(sleepTime);
		} catch (InterruptedException ignore) { }
	}
	
	
	public static byte[] mergeByteArrays(byte[] b1, byte[] b2) {
		byte[] result = new byte[b1.length + b2.length];
		System.arraycopy(b1, 0, result, 0, b1.length);
		System.arraycopy(b2, 0, result, b1.length, b2.length);
		
		return result;
	}
	
	
	public static byte[] toArray(ByteBuffer buffer) {

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
	}

	public static void setLogLevel(Level level) {
		Logger logger = Logger.getLogger("org.xsocket");
		logger.setLevel(Level.FINE);
		ConsoleHandler hdl = new ConsoleHandler();
		hdl.setLevel(Level.FINEST);
		hdl.setFormatter(new LogFormatter());
		logger.addHandler(hdl);
	}
	
	public static void assertTimeout(long elapsed, long min, long max) {
		System.out.println("elapsed time " + elapsed + " (min=" + min + ", max=" + max + ")");
		Assert.assertTrue("elapsed time " + elapsed + " out of range (min=" + min + ", max=" + max + ")"
				          , (elapsed >= min) && (elapsed <= max));
	}

	
	public static InetAddress getRandomLocalAddress() throws IOException {
		String hostname = InetAddress.getLocalHost().getHostName();
		InetAddress[] addresses = InetAddress.getAllByName(hostname);
	
		int i = new Random().nextInt();
		if (i < 0) {
			i = 0 - i;
		}
		
		i = i % addresses.length;
		
		return addresses[i];
	}
}
