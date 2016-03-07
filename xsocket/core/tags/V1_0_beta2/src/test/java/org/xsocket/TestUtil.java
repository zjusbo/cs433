package org.xsocket;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

import org.xsocket.server.IHandler;
import org.xsocket.server.IMultithreadedServer;
import org.xsocket.server.MultithreadedServer;



public final class TestUtil {
	
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
	
}
