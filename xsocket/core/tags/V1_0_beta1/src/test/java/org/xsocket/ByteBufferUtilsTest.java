// $Id$
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
package org.xsocket;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.junit.Test;
import org.xsocket.AbstractConnection.ByteBufferArrayChannel;
import org.xsocket.util.TextUtils;


import junit.framework.Assert;
import junit.framework.JUnit4TestAdapter;
import junit.textui.TestRunner;



public class ByteBufferUtilsTest {

	private ByteBufferParser parser =new ByteBufferParser();
	
	@Test public void test1() throws Exception {
		byte[] terminator = "\n\r".getBytes();
		ByteBuffer input = wrap(new char[] {'t', '\n', '\r'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		
		ByteBufferParser.Index index = parser.find(queue, terminator);

		ByteBufferArrayChannel channel = new ByteBufferArrayChannel();
		parser.extract(queue, index, channel);
		
		Assert.assertTrue(isEquals(channel, 't'));
		Assert.assertTrue(isEquals(queue, null));
	}

	
	@Test public void test2() throws Exception {
		byte[] terminator = "\n\r".getBytes();
		ByteBuffer input = wrap(new char[] {'t'});
		ByteBuffer input2 = wrap(new char[] {'\n', '\r'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		queue.offer(input2);

		ByteBufferParser.Index index = parser.find(queue, terminator);
		
		ByteBufferArrayChannel channel = new ByteBufferArrayChannel();
		parser.extract(queue, index, channel);
		
		Assert.assertTrue(isEquals(channel, 't'));
		Assert.assertTrue(isEquals(queue, null));
	}	
	
	@Test public void test3() throws Exception {
		byte[] terminator = "\n\r".getBytes();
		ByteBuffer input = wrap(new char[] {'t'});
		ByteBuffer input2 = wrap(new char[] {'\n', '\r'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		queue.offer(input2);
		
		ByteBufferParser.Index index = parser.find(queue, terminator);
		
		ByteBufferArrayChannel channel = new ByteBufferArrayChannel();
		parser.extract(queue, index, channel);
		
		Assert.assertTrue(isEquals(channel, 't'));
		Assert.assertTrue(isEquals(queue, null));
	}


	@Test public void test4() throws Exception {
		byte[] terminator = "\n\r".getBytes();
		ByteBuffer input = wrap(new char[] {'t'});
		ByteBuffer input2 = wrap(new char[] {'\n'});
		ByteBuffer input3 = wrap(new char[] {'\r'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		queue.offer(input2);
		queue.offer(input3);
		
		ByteBufferParser.Index index = parser.find(queue, terminator);
		
		ByteBufferArrayChannel channel = new ByteBufferArrayChannel();
		parser.extract(queue, index, channel);
		
		Assert.assertTrue(isEquals(channel, 't'));
		Assert.assertTrue(isEquals(queue, null));
	}

	
	@Test public void test5() throws Exception {
		byte[] terminator = "\n\r".getBytes();
		ByteBuffer input = wrap(new char[] {'t', '\n', 'e', '\n', '\r', 'z', 'w', '\n', '\r'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		
		ByteBufferParser.Index index = parser.find(queue, terminator);

		ByteBufferArrayChannel channel = new ByteBufferArrayChannel();
		parser.extract(queue, index, channel);

		Assert.assertTrue(isEquals(channel, 't', '\n', 'e'));
		Assert.assertTrue(isEquals(queue, 'z', 'w', '\n', '\r'));
	}

	@Test public void test6() throws Exception {
		byte[] terminator = "\n\r".getBytes();
		ByteBuffer input = wrap(new char[] {'t', '\n', 'e', '\n'});
		ByteBuffer input2 = wrap(new char[] {'\r', 'z', 'w', '\n', '\r'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		queue.offer(input2);
		
		ByteBufferParser.Index index = parser.find(queue, terminator);

		ByteBufferArrayChannel channel = new ByteBufferArrayChannel();
		parser.extract(queue, index, channel);

		Assert.assertTrue(isEquals(channel, 't', '\n', 'e'));
		Assert.assertTrue(isEquals(queue, 'z', 'w', '\n', '\r'));
	}

	@Test public void test7() throws Exception {
		byte[] terminator = "\n\r".getBytes();
		ByteBuffer input = wrap(new char[] {'t', '\n', 'e', '\n'});
		ByteBuffer input2 = wrap(new char[] {'z', 'w', '\n'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		queue.offer(input2);
		
		ByteBufferParser.Index index = parser.find(queue, terminator);
		Assert.assertFalse(index.isDelimiterFound());
	}

	@Test public void test8() throws Exception {
		byte[] terminator = "\n\r".getBytes();
		ByteBuffer input = wrap(new char[] {'t', '\n', 'e', '\n'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		
		ByteBufferParser.Index index = parser.find(queue, terminator);
		Assert.assertFalse(index.isDelimiterFound());
		
		ByteBuffer input2 = wrap(new char[] {'\r', 'z', 'w', '\n', '\r'});
		queue.offer(input2);

		index = parser.find(queue, index);
		Assert.assertTrue(index.isDelimiterFound());
		
		ByteBufferArrayChannel channel = new ByteBufferArrayChannel();
		parser.extract(queue, index, channel);


		Assert.assertTrue(isEquals(channel, 't', '\n', 'e'));
		Assert.assertTrue(isEquals(queue, 'z', 'w', '\n', '\r'));
	}

	

	@Test public void test9() throws Exception {
		byte[] terminator = "\n\r.\n\r".getBytes();
		ByteBuffer input = wrap(new char[] {'t', 'z', 'T', '\n'});
		ByteBuffer input2 = wrap(new char[] {'\r', '.', '\n'});
		ByteBuffer input3 = wrap(new char[] {'\r', 'o', 'p'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		queue.offer(input2);
		queue.offer(input3);
		
		ByteBufferParser.Index index = parser.find(queue, terminator);

		ByteBufferArrayChannel channel = new ByteBufferArrayChannel();
		parser.extract(queue, index, channel);

		
		Assert.assertTrue(isEquals(channel, 't', 'z', 'T'));
		Assert.assertTrue(isEquals(queue, 'o', 'p'));
	}

	
	@Test public void test10() throws Exception {
		byte[] terminator = "\n\r.\n\r".getBytes();
		ByteBuffer input = wrap(new char[] {'t', 'z', 'T', '\n'});
		ByteBuffer input2 = wrap(new char[] {'\r', '.', '\n'});
		ByteBuffer input3 = wrap(new char[] {'\r', 'o', 'p'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		queue.offer(input2);
		
		ByteBufferParser.Index index = parser.find(queue, terminator);
		
		ByteBufferArrayChannel channel = new ByteBufferArrayChannel();
		parser.extractAvailable(queue, index, channel);

		Assert.assertTrue(isEquals(channel, 't', 'z', 'T'));
		
		queue.offer(input3);
		index = parser.find(queue, terminator);

		channel = new ByteBufferArrayChannel();
		parser.extractAvailable(queue, index, channel);

		Assert.assertTrue(isEquals(channel, null));
		Assert.assertTrue(isEquals(queue, 'o', 'p'));
	}
	
	
	
	@Test public void test11() throws Exception {
		byte[] terminator = "\n\r.\n\r".getBytes();
		ByteBuffer input = wrap(new char[] {'t', 'z', 'T', '\n'});
		ByteBuffer input2 = wrap(new char[] {'\r', '.', '\n'});
		ByteBuffer input3 = wrap(new char[] {'\r', 'o', 'p'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		queue.offer(input2);
		
		ByteBufferParser.Index index = parser.find(queue, terminator);

		ByteBufferArrayChannel channel = new ByteBufferArrayChannel();
		parser.extractAvailable(queue, index, channel);

		Assert.assertTrue(isEquals(channel, 't', 'z', 'T'));
		
		queue.offer(input3);
		index = parser.find(queue, terminator);
		
		channel = new ByteBufferArrayChannel();
		parser.extract(queue, index, channel);

		Assert.assertTrue(isEquals(channel, null));
		Assert.assertTrue(isEquals(queue, 'o', 'p'));
	}
	
	@Test public void test12() throws Exception {
		byte[] terminator = "\n\r.\n\r".getBytes();
		ByteBuffer input = wrap(new char[] {'t', 'z', 'T', '\n'});
		ByteBuffer input2 = wrap(new char[] {'\r', '.', '\n'});
		ByteBuffer input3 = wrap(new char[] {'\r', 'o', 'p'});
		LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();
		queue.offer(input);
		queue.offer(input2);
		
		ByteBufferParser.Index index = parser.find(queue, terminator);
		if (index.isDataAvailable()) {
			ByteBufferArrayChannel channel = new ByteBufferArrayChannel();
			parser.extractAvailable(queue, index, channel);

			Assert.assertTrue(isEquals(channel, 't', 'z', 'T'));
		}
		
		index = parser.find(queue, terminator);
		if (index.isDataAvailable()) {
			Assert.fail("no data is available");
		}

		queue.offer(input3);
		index = parser.find(queue, terminator);
		ByteBufferArrayChannel channel = new ByteBufferArrayChannel();
		parser.extract(queue, index, channel);

		Assert.assertTrue(isEquals(channel, null));
		
		Assert.assertTrue(isEquals(queue, 'o', 'p'));
	}
	
	
	private ByteBuffer wrap(char... chars) throws CharacterCodingException {
		Charset charset = Charset.forName("UTF-8");
		CharsetEncoder encoder = charset.newEncoder();
		return  encoder.encode(CharBuffer.wrap(chars));
	}
	

	private boolean isEquals(ByteBufferArrayChannel channel, char... c) throws CharacterCodingException, UnsupportedEncodingException  {
		return isEquals(Arrays.asList(channel.getContent()), c);
	}
	
	private boolean isEquals(List<ByteBuffer> buffers, char... c) throws CharacterCodingException, UnsupportedEncodingException  {
		if (c != null) {
			String ref = new String(c);
			String s = TextUtils.toString(buffers, "UTF-8");
		
			return isEquals(s, ref);
		} else {
			return (TextUtils.toString(buffers, "UTF-8").equals(""));
		}
	}
	
	private boolean isEquals(String s, String ref) {
		return (s.equals(ref));
	}

	
	public static junit.framework.Test suite() {
		Logger logger = Logger.getLogger("org.xsocket");
		logger.setLevel(Level.FINEST);
		ConsoleHandler hdl = new ConsoleHandler();
		hdl.setLevel(Level.FINEST);
		hdl.setFormatter(new LogFormatter());
		logger.addHandler(hdl);

		return new JUnit4TestAdapter(ByteBufferUtilsTest.class);
	}
		
	
	public static void main (String... args) {
		TestRunner.run(suite());
	}
	
	
	
}
