// $Id: ByteBufferParserTest.java 764 2007-01-15 06:26:17Z grro $
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
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;


import org.junit.Test;
import org.xsocket.DataConverter;
import org.xsocket.stream.ByteBufferOutputChannel;
import org.xsocket.stream.ByteBufferParser;



import junit.framework.Assert;



public class ByteBufferParserTest {

	private ByteBufferParser parser =new ByteBufferParser();
	
	@Test public void test1() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t', '\n', '\r'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		
		ByteBufferParser.Index index = parser.find(queue, terminator);

		ByteBufferOutputChannel channel = new ByteBufferOutputChannel();
		parser.extract(queue, index, channel);
		
		Assert.assertTrue(isEquals(channel, 't'));
		Assert.assertTrue(isEquals(queue, null));
	}

	
	@Test public void test2() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t'});
		ByteBuffer input2 = wrap(new char[] {'\n', '\r'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		queue.offer(input2);

		ByteBufferParser.Index index = parser.find(queue, terminator);
		
		ByteBufferOutputChannel channel = new ByteBufferOutputChannel();
		parser.extract(queue, index, channel);
		
		Assert.assertTrue(isEquals(channel, 't'));
		Assert.assertTrue(isEquals(queue, null));
	}	
	
	@Test public void test3() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t'});
		ByteBuffer input2 = wrap(new char[] {'\n', '\r'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		queue.offer(input2);
		
		ByteBufferParser.Index index = parser.find(queue, terminator);
		
		ByteBufferOutputChannel channel = new ByteBufferOutputChannel();
		parser.extract(queue, index, channel);
		
		Assert.assertTrue(isEquals(channel, 't'));
		Assert.assertTrue(isEquals(queue, null));
	}


	@Test public void test4() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t'});
		ByteBuffer input2 = wrap(new char[] {'\n'});
		ByteBuffer input3 = wrap(new char[] {'\r'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		queue.offer(input2);
		queue.offer(input3);
		
		ByteBufferParser.Index index = parser.find(queue, terminator);
		
		ByteBufferOutputChannel channel = new ByteBufferOutputChannel();
		parser.extract(queue, index, channel);
		
		Assert.assertTrue(isEquals(channel, 't'));
		Assert.assertTrue(isEquals(queue, null));
	}

	
	@Test public void test5() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t', '\n', 'e', '\n', '\r', 'z', 'w', '\n', '\r'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		
		ByteBufferParser.Index index = parser.find(queue, terminator);

		ByteBufferOutputChannel channel = new ByteBufferOutputChannel();
		parser.extract(queue, index, channel);

		Assert.assertTrue(isEquals(channel, 't', '\n', 'e'));
		Assert.assertTrue(isEquals(queue, 'z', 'w', '\n', '\r'));
	}

	@Test public void test6() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t', '\n', 'e', '\n'});
		ByteBuffer input2 = wrap(new char[] {'\r', 'z', 'w', '\n', '\r'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		queue.offer(input2);
		
		ByteBufferParser.Index index = parser.find(queue, terminator);

		ByteBufferOutputChannel channel = new ByteBufferOutputChannel();
		parser.extract(queue, index, channel);

		Assert.assertTrue(isEquals(channel, 't', '\n', 'e'));
		Assert.assertTrue(isEquals(queue, 'z', 'w', '\n', '\r'));
	}

	@Test public void test7() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t', '\n', 'e', '\n'});
		ByteBuffer input2 = wrap(new char[] {'z', 'w', '\n'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		queue.offer(input2);
		
		ByteBufferParser.Index index = parser.find(queue, terminator);
		Assert.assertFalse(index.hasDelimiterFound());
	}

	@Test public void test8() throws Exception {
		String terminator = "\n\r";
		ByteBuffer input = wrap(new char[] {'t', '\n', 'e', '\n'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		
		ByteBufferParser.Index index = parser.find(queue, terminator);
		Assert.assertFalse(index.hasDelimiterFound());
		
		ByteBuffer input2 = wrap(new char[] {'\r', 'z', 'w', '\n', '\r'});
		queue.offer(input2);

		index = parser.find(queue, index);
		Assert.assertTrue(index.hasDelimiterFound());
		
		ByteBufferOutputChannel channel = new ByteBufferOutputChannel();
		parser.extract(queue, index, channel);


		Assert.assertTrue(isEquals(channel, 't', '\n', 'e'));
		Assert.assertTrue(isEquals(queue, 'z', 'w', '\n', '\r'));
	}

	

	@Test public void test9() throws Exception {
		String terminator = "\n\r.\n\r";
		ByteBuffer input = wrap(new char[] {'t', 'z', 'T', '\n'});
		ByteBuffer input2 = wrap(new char[] {'\r', '.', '\n'});
		ByteBuffer input3 = wrap(new char[] {'\r', 'o', 'p'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		queue.offer(input2);
		queue.offer(input3);
		
		ByteBufferParser.Index index = parser.find(queue, terminator);

		ByteBufferOutputChannel channel = new ByteBufferOutputChannel();
		parser.extract(queue, index, channel);

		
		Assert.assertTrue(isEquals(channel, 't', 'z', 'T'));
		Assert.assertTrue(isEquals(queue, 'o', 'p'));
	}

	
	private ByteBuffer wrap(char... chars) throws CharacterCodingException {
		Charset charset = Charset.forName("UTF-8");
		CharsetEncoder encoder = charset.newEncoder();
		return  encoder.encode(CharBuffer.wrap(chars));
	}
	

	private boolean isEquals(ByteBufferOutputChannel channel, char... c) throws CharacterCodingException, UnsupportedEncodingException  {
		return isEquals(Arrays.asList(channel.toByteBufferArray()), c);
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
	
	private boolean isEquals(String s, String ref) {
		return (s.equals(ref));
	}
}