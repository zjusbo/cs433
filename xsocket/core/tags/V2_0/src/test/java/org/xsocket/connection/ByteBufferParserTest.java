/*
 *  Copyright (c) xsocket.org, 2006-2008. All rights reserved.
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
package org.xsocket.connection;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;


import org.junit.Test;
import org.xsocket.DataConverter;
import org.xsocket.QAUtil;
import org.xsocket.connection.ByteBufferUtil;



import junit.framework.Assert;



public class ByteBufferParserTest {

	private ByteBufferUtil parser = new ByteBufferUtil();
	
	
	public static void main(String[] args) throws Exception {
		ByteBufferUtil parser = new ByteBufferUtil();
		
		ByteBuffer input = wrap(new char[] {'t', '\n', 'e', 't', '\n', 'e', 't', '\n', 'e', 't', '\n', 'e', 't', '\n', 'e', 't', '\n', 'e', '\r', '\n', '\r', '\n'});
		ByteBuffer[] queue = new ByteBuffer[1];
		queue[0] = input;

		byte[] terminator = "\n\r\n\r".getBytes();
		
		while (true) {
			parser.find(queue, terminator);
		}
	}
	

	@Test
	public void test1() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t', '\n', '\r'});
		ByteBuffer[] queue = new ByteBuffer[1];
		queue[0] = input;

		ByteBufferUtil.Index index = parser.find(queue, terminator.getBytes());

		ByteBuffer[] extracted = parser.extract(queue, index.getContentLength());

		Assert.assertTrue(isEquals(extracted, 't'));
		Assert.assertTrue(isEquals(queue, '\n', '\r'));
	}


	@Test
	public void test2() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t'});
		ByteBuffer input2 = wrap(new char[] {'\n', '\r'});
		ByteBuffer[] queue = new ByteBuffer[2];
		queue[0] = input;
		queue[1] = input2;

		ByteBufferUtil.Index index = parser.find(queue, terminator.getBytes());

		ByteBuffer[] extracted = parser.extract(queue, index.getContentLength());

		Assert.assertTrue(isEquals(extracted, 't'));
		Assert.assertTrue(isEquals(queue, '\n', '\r'));
	}

	@Test
	public void test3() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t'});
		ByteBuffer input2 = wrap(new char[] {'\n', '\r'});
		ByteBuffer[] queue = new ByteBuffer[2];
		queue[0] = input;
		queue[1] = input2;

		ByteBufferUtil.Index index = parser.find(queue, terminator.getBytes());

		ByteBuffer[] extracted = parser.extract(queue, index.getContentLength());

		Assert.assertTrue(isEquals(extracted, 't'));
		Assert.assertTrue(isEquals(queue, '\n', '\r'));
	}


	@Test
	public void test4() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t'});
		ByteBuffer input2 = wrap(new char[] {'\n'});
		ByteBuffer input3 = wrap(new char[] {'\r'});
		ByteBuffer[] queue = new ByteBuffer[3];
		queue[0] = input;
		queue[1] = input2;
		queue[2] = input3;

		ByteBufferUtil.Index index = parser.find(queue, terminator.getBytes());

		ByteBuffer[] extracted = parser.extract(queue, index.getContentLength());

		Assert.assertTrue(isEquals(extracted, 't'));
		Assert.assertTrue(isEquals(queue, '\n', '\r'));
	}


	@Test
	public void test5() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t', '\n', 'e', '\n', '\r', 'z', 'w', '\n', '\r'});
		ByteBuffer[] queue = new ByteBuffer[1];
		queue[0] = input;

		ByteBufferUtil.Index index = parser.find(queue, terminator.getBytes());

		ByteBuffer[] extracted = parser.extract(queue, index.getContentLength());

		Assert.assertTrue(isEquals(extracted, 't', '\n', 'e'));
		Assert.assertTrue(isEquals(queue, '\n', '\r', 'z', 'w', '\n', '\r'));
	}

	@Test
	public void test6() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t', '\n', 'e', '\n'});
		ByteBuffer input2 = wrap(new char[] {'\r', 'z', 'w', '\n', '\r'});
		ByteBuffer[] queue = new ByteBuffer[2];
		queue[0] = input;
		queue[1] = input2;

		ByteBufferUtil.Index index = parser.find(queue, terminator.getBytes());

		ByteBuffer[] extracted = parser.extract(queue, index.getContentLength());

		Assert.assertTrue(isEquals(extracted, 't', '\n', 'e'));
		Assert.assertTrue(isEquals(queue, '\n', '\r', 'z', 'w', '\n', '\r'));
	}

	@Test
	public void test7() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t', '\n', 'e', '\n'});
		ByteBuffer input2 = wrap(new char[] {'z', 'w', '\n'});
		ByteBuffer[] queue = new ByteBuffer[2];
		queue[0] = input;
		queue[1] = input2;

		ByteBufferUtil.Index index = parser.find(queue, terminator.getBytes());
		Assert.assertFalse(index.hasDelimiterFound());
	}

	@Test
	public void test8() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t', '\n', 'e', '\n'});
		ByteBuffer[] queue = new ByteBuffer[2];
		queue[0] = input;

		ByteBufferUtil.Index index = parser.find(queue, terminator.getBytes());
		Assert.assertFalse(index.hasDelimiterFound());

		
		index = parser.find(queue, index);
		Assert.assertFalse(index.hasDelimiterFound());

		
		ByteBuffer input2 = wrap(new char[] {'\r', 'z', 'w', '\n', '\r'});
		queue[1] = input2;

		index = parser.find(queue, index);
		Assert.assertTrue(index.hasDelimiterFound());

		ByteBuffer[] extracted = parser.extract(queue, index.getContentLength());


		Assert.assertTrue(isEquals(extracted, 't', '\n', 'e'));
		Assert.assertTrue(isEquals(queue, '\n', '\r', 'z', 'w', '\n', '\r'));
	}


	@Test
	public void test8b() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t', '\n', 'e', '\n'});
		ByteBuffer[] queue = new ByteBuffer[1];
		queue[0] = input;

		ByteBufferUtil.Index index = parser.find(queue, terminator.getBytes());
		Assert.assertFalse(index.hasDelimiterFound());

		
		index = parser.find(queue, index);
		Assert.assertFalse(index.hasDelimiterFound());

		
		ByteBuffer input2 = wrap(new char[] {'\r', 'z', 'w', '\n', '\r'});
		ByteBuffer[] queue2 = new ByteBuffer[2];
		queue2[0] = input;
		queue2[1] = input2;

		index = parser.find(queue2, index);
		Assert.assertTrue(index.hasDelimiterFound());

		ByteBuffer[] extracted = parser.extract(queue2, index.getContentLength());


		Assert.assertTrue(isEquals(extracted, 't', '\n', 'e'));
		Assert.assertTrue(isEquals(queue2, '\n', '\r', 'z', 'w', '\n', '\r'));
	}
	

	@Test
	public void test9() throws Exception {
		String terminator = "\n\r.\n\r";
		ByteBuffer input = wrap(new char[] {'t', 'z', 'T', '\n'});
		ByteBuffer input2 = wrap(new char[] {'\r', '.', '\n'});
		ByteBuffer input3 = wrap(new char[] {'\r', 'o', 'p'});
		ByteBuffer[] queue = new ByteBuffer[3];
		queue[0] = input;
		queue[1] = input2;
		queue[2] = input3;

		ByteBufferUtil.Index index = parser.find(queue, terminator.getBytes());

		ByteBuffer[] extracted = parser.extract(queue, index.getContentLength());


		Assert.assertTrue(isEquals(extracted, 't', 'z', 'T'));
		Assert.assertTrue(isEquals(queue, '\n', '\r', '.', '\n', '\r', 'o', 'p'));
	}



	@Test
	public void test10() throws Exception {
		String terminator = "\r\n.\r\n";

		ByteBuffer input = wrap(new char[] {'4', '2', '\r', '\n', '\r', '\n', '.', '\r', '\n'});
		ByteBuffer[] queue = new ByteBuffer[1];
		queue[0] = input;

		ByteBufferUtil.Index index = parser.find(queue, terminator.getBytes());

		ByteBuffer[] extracted = parser.extract(queue, index.getContentLength());


		Assert.assertTrue(isEquals(extracted, '4', '2', '\r', '\n'));
	}


	@Test
	public void test11() throws Exception {
		String terminator = "\r\n";

		ByteBuffer input2 = wrap(new char[] {'\r', '\n'});
		ByteBuffer input = QAUtil.generateByteBuffer(700000);
		ByteBuffer[] queue = new ByteBuffer[2];
		queue[0] = input;
		queue[1] = input2;

		ByteBufferUtil.Index index = parser.find(queue, terminator.getBytes());

		ByteBuffer[] extracted = parser.extract(queue, index.getContentLength());

		input.flip();
		Assert.assertTrue(isEquals(extracted, input));
	}


	@Test
	public void test12() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t', '\n', 'e', '\n'});
		ByteBuffer input2 = wrap(new char[] {'\r', 'z', 'w', '\n', '\r'});
		ByteBuffer[] queue = new ByteBuffer[6];
		queue[1] = input;
		queue[4] = input2;

		ByteBufferUtil.Index index = parser.find(queue, terminator.getBytes());

		ByteBuffer[] extracted = parser.extract(queue, index.getContentLength());

		Assert.assertTrue(isEquals(extracted, 't', '\n', 'e'));
		Assert.assertTrue(isEquals(queue, '\n', '\r', 'z', 'w', '\n', '\r'));
	}

	
	

	@Test
	public void test13() throws Exception {
		String terminator = ";";
		ByteBuffer input = DataConverter.toByteBuffer("REQUEST", "UTF-8");
		ByteBuffer input2 = DataConverter.toByteBuffer(";", "UTF-8");
		ByteBuffer[] queue = new ByteBuffer[2];
		queue[0] = input;
		queue[1] = input2;

		ByteBufferUtil.Index index = parser.find(queue, terminator.getBytes());

		ByteBuffer[] extracted = parser.extract(queue, index.getContentLength());

		Assert.assertEquals(DataConverter.toString(extracted), "REQUEST");
	}
	
	


	@Test
	public void test14() throws Exception {
		String terminator = ";";
		ByteBuffer input = DataConverter.toByteBuffer("REQUEST", "UTF-8");
		ByteBuffer input2 = DataConverter.toByteBuffer(";", "UTF-8");
		ByteBuffer[] queue = new ByteBuffer[7];
		queue[5] = input;
		queue[6] = input2;

		ByteBufferUtil.Index index = parser.find(queue, terminator.getBytes());

		ByteBuffer[] extracted = parser.extract(queue, index.getContentLength());

		Assert.assertEquals(DataConverter.toString(extracted), "REQUEST");
	}

	


	@Test
	public void test15() throws Exception {
		byte[] terminator = new byte[] { 0x0d, 0x0a, 0x0d, 0x0a };
		ByteBuffer input = ByteBuffer.wrap(new byte[] { 0x36, 0x2e, 0x31, 0x2e, 0x78, 0x29 });
		ByteBuffer input2 = ByteBuffer.wrap(new byte[] { 0x0d, 0x0a, 0x0d, 0x0a, 0x74, 0x72, 0x75, 0x65 });
		ByteBuffer[] queue = new ByteBuffer[2];
		queue[0] = input;
		queue[1] = input2;

		ByteBufferUtil.Index index = parser.find(queue, terminator);

		ByteBuffer[] extracted = parser.extract(queue, index.getContentLength());

		Assert.assertTrue(isEquals(extracted, new byte[] { 0x36, 0x2e, 0x31, 0x2e, 0x78, 0x29 }));
	}
	
	
	
	private static ByteBuffer wrap(char... chars) throws CharacterCodingException {
		Charset charset = Charset.forName("UTF-8");
		CharsetEncoder encoder = charset.newEncoder();
		return  encoder.encode(CharBuffer.wrap(chars));
	}



	private boolean isEquals(ByteBuffer[] buffers, char... c) throws CharacterCodingException, UnsupportedEncodingException  {
		if (c != null) {
			String ref = new String(c);
			String s = DataConverter.toString(buffers, "UTF-8");

			return isEquals(s, ref);
		} else {
			return (DataConverter.toString(buffers, "UTF-8").equals(""));
		}
	}

	private boolean isEquals(ByteBuffer[] buffers, byte... bytes) throws CharacterCodingException, UnsupportedEncodingException  {
		if (bytes != null) {
			return isEquals(buffers, ByteBuffer.wrap(bytes));
		} else {
			return (DataConverter.toString(buffers, "UTF-8").equals(""));
		}
	}
	
	
	private boolean isEquals(ByteBuffer[] buffers, ByteBuffer buffer) throws CharacterCodingException, UnsupportedEncodingException  {
		String s1 = DataConverter.toString(buffers);
		String s2 = DataConverter.toString(buffer);
		return s1.equals(s2);
	}


	private boolean isEquals(String s, String ref) {
		return (s.equals(ref));
	}
}