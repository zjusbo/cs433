// $Id: ByteBufferParserTest.java 1448 2007-07-04 09:28:48Z grro $
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
package org.xsocket.stream;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.LinkedList;
import java.util.List;


import org.junit.Test;
import org.xsocket.DataConverter;
import org.xsocket.QAUtil;



import junit.framework.Assert;



public class ByteBufferParserTest {

	private ByteBufferParser parser =new ByteBufferParser();

	@Test
	public void test1() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t', '\n', '\r'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);

		ByteBufferParser.Index index = parser.find(queue, terminator.getBytes());

		LinkedList<ByteBuffer> extracted = parser.extract(queue, index);

		Assert.assertTrue(isEquals(extracted, 't'));
		Assert.assertTrue(isEquals(queue, (char[]) null));
	}


	@Test
	public void test2() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t'});
		ByteBuffer input2 = wrap(new char[] {'\n', '\r'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		queue.offer(input2);

		ByteBufferParser.Index index = parser.find(queue, terminator.getBytes());

		LinkedList<ByteBuffer> extracted = parser.extract(queue, index);

		Assert.assertTrue(isEquals(extracted, 't'));
		Assert.assertTrue(isEquals(queue,(char[]) null));
	}

	@Test
	public void test3() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t'});
		ByteBuffer input2 = wrap(new char[] {'\n', '\r'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		queue.offer(input2);

		ByteBufferParser.Index index = parser.find(queue, terminator.getBytes());

		LinkedList<ByteBuffer> extracted = parser.extract(queue, index);

		Assert.assertTrue(isEquals(extracted, 't'));
		Assert.assertTrue(isEquals(queue, (char[]) null));
	}


	@Test
	public void test4() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t'});
		ByteBuffer input2 = wrap(new char[] {'\n'});
		ByteBuffer input3 = wrap(new char[] {'\r'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		queue.offer(input2);
		queue.offer(input3);

		ByteBufferParser.Index index = parser.find(queue, terminator.getBytes());

		LinkedList<ByteBuffer> extracted = parser.extract(queue, index);

		Assert.assertTrue(isEquals(extracted, 't'));
		Assert.assertTrue(isEquals(queue, (char[]) null));
	}


	@Test
	public void test5() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t', '\n', 'e', '\n', '\r', 'z', 'w', '\n', '\r'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);

		ByteBufferParser.Index index = parser.find(queue, terminator.getBytes());

		LinkedList<ByteBuffer> extracted = parser.extract(queue, index);

		Assert.assertTrue(isEquals(extracted, 't', '\n', 'e'));
		Assert.assertTrue(isEquals(queue, 'z', 'w', '\n', '\r'));
	}

	@Test
	public void test6() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t', '\n', 'e', '\n'});
		ByteBuffer input2 = wrap(new char[] {'\r', 'z', 'w', '\n', '\r'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		queue.offer(input2);

		ByteBufferParser.Index index = parser.find(queue, terminator.getBytes());

		LinkedList<ByteBuffer> extracted = parser.extract(queue, index);

		Assert.assertTrue(isEquals(extracted, 't', '\n', 'e'));
		Assert.assertTrue(isEquals(queue, 'z', 'w', '\n', '\r'));
	}

	@Test
	public void test7() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t', '\n', 'e', '\n'});
		ByteBuffer input2 = wrap(new char[] {'z', 'w', '\n'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		queue.offer(input2);

		ByteBufferParser.Index index = parser.find(queue, terminator.getBytes());
		Assert.assertFalse(index.hasDelimiterFound());
	}

	@Test
	public void test8() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t', '\n', 'e', '\n'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);

		ByteBufferParser.Index index = parser.find(queue, terminator.getBytes());
		Assert.assertFalse(index.hasDelimiterFound());

		ByteBuffer input2 = wrap(new char[] {'\r', 'z', 'w', '\n', '\r'});
		queue.offer(input2);

		index = parser.find(queue, index);
		Assert.assertTrue(index.hasDelimiterFound());

		LinkedList<ByteBuffer> extracted = parser.extract(queue, index);


		Assert.assertTrue(isEquals(extracted, 't', '\n', 'e'));
		Assert.assertTrue(isEquals(queue, 'z', 'w', '\n', '\r'));
	}



	@Test
	public void test9() throws Exception {
		String terminator = "\n\r.\n\r";
		ByteBuffer input = wrap(new char[] {'t', 'z', 'T', '\n'});
		ByteBuffer input2 = wrap(new char[] {'\r', '.', '\n'});
		ByteBuffer input3 = wrap(new char[] {'\r', 'o', 'p'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		queue.offer(input2);
		queue.offer(input3);

		ByteBufferParser.Index index = parser.find(queue, terminator.getBytes());

		LinkedList<ByteBuffer> extracted = parser.extract(queue, index);


		Assert.assertTrue(isEquals(extracted, 't', 'z', 'T'));
		Assert.assertTrue(isEquals(queue, 'o', 'p'));
	}



	@Test
	public void test10() throws Exception {
		String terminator = "\r\n.\r\n";

		ByteBuffer input = wrap(new char[] {'4', '2', '\r', '\n', '\r', '\n', '.', '\r', '\n'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);

		ByteBufferParser.Index index = parser.find(queue, terminator.getBytes());

		LinkedList<ByteBuffer> extracted = parser.extract(queue, index);


		Assert.assertTrue(isEquals(extracted, '4', '2', '\r', '\n'));
	}


	@Test
	public void test11() throws Exception {
		String terminator = "\r\n";

		ByteBuffer input2 = wrap(new char[] {'\r', '\n'});
		ByteBuffer input = QAUtil.generateByteBuffer(700000);
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		queue.offer(input2);

		ByteBufferParser.Index index = parser.find(queue, terminator.getBytes());

		LinkedList<ByteBuffer> extracted = parser.extract(queue, index);

		input.flip();
		Assert.assertTrue(isEquals(extracted, input));
	}



	private ByteBuffer wrap(char... chars) throws CharacterCodingException {
		Charset charset = Charset.forName("UTF-8");
		CharsetEncoder encoder = charset.newEncoder();
		return  encoder.encode(CharBuffer.wrap(chars));
	}



	private boolean isEquals(List<ByteBuffer> buffers, char... c) throws CharacterCodingException, UnsupportedEncodingException  {
		if (c != null) {
			String ref = new String(c);
			String s = DataConverter.toString(buffers, "UTF-8");

			return isEquals(s, ref);
		} else {
			return (DataConverter.toString(buffers, "UTF-8").equals(""));
		}
	}


	private boolean isEquals(List<ByteBuffer> buffers, ByteBuffer buffer) throws CharacterCodingException, UnsupportedEncodingException  {
		return DataConverter.toString(buffer).equals(DataConverter.toString(buffer));
	}


	private boolean isEquals(String s, String ref) {
		return (s.equals(ref));
	}
}