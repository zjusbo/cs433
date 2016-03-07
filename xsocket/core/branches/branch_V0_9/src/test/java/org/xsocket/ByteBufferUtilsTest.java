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


import org.junit.Test;
import org.xsocket.util.TextUtils;


import junit.framework.Assert;
import junit.framework.JUnit4TestAdapter;
import junit.textui.TestRunner;



public class ByteBufferUtilsTest {

	
	
	@Test public void test1() throws Exception {
		byte[] terminator = "\n\r".getBytes();
		ByteBuffer input = wrap(new char[] {'t', '\n', '\r'});
		ByteBufferQueue queue = new ByteBufferQueue();
		queue.offer(input);
		
		Index index = ByteBufferUtils.find(queue, terminator);
		ByteBuffer[] buffers = ByteBufferUtils.extract(queue, index);
		
		Assert.assertTrue(isEquals(buffers, 't'));
		Assert.assertTrue(isEquals(queue.drain(), null));
	}

	
	@Test public void test2() throws Exception {
		byte[] terminator = "\n\r".getBytes();
		ByteBuffer input = wrap(new char[] {'t'});
		ByteBuffer input2 = wrap(new char[] {'\n', '\r'});
		ByteBufferQueue queue = new ByteBufferQueue();
		queue.offer(input);
		queue.offer(input2);

		Index index = ByteBufferUtils.find(queue, terminator);
		ByteBuffer[] buffers = ByteBufferUtils.extract(queue, index);
		
		Assert.assertTrue(isEquals(buffers, 't'));
		Assert.assertTrue(isEquals(queue.drain(), null));
	}	
	
	@Test public void test3() throws Exception {
		byte[] terminator = "\n\r".getBytes();
		ByteBuffer input = wrap(new char[] {'t'});
		ByteBuffer input2 = wrap(new char[] {'\n', '\r'});
		ByteBufferQueue queue = new ByteBufferQueue();
		queue.offer(input);
		queue.offer(input2);
		
		Index index = ByteBufferUtils.find(queue, terminator);
		ByteBuffer[] buffers = ByteBufferUtils.extract(queue, index);
		
		Assert.assertTrue(isEquals(buffers, 't'));
		Assert.assertTrue(isEquals(queue.drain(), null));
	}


	@Test public void test4() throws Exception {
		byte[] terminator = "\n\r".getBytes();
		ByteBuffer input = wrap(new char[] {'t'});
		ByteBuffer input2 = wrap(new char[] {'\n'});
		ByteBuffer input3 = wrap(new char[] {'\r'});
		ByteBufferQueue queue = new ByteBufferQueue();
		queue.offer(input);
		queue.offer(input2);
		queue.offer(input3);
		
		Index index = ByteBufferUtils.find(queue, terminator);
		ByteBuffer[] buffers = ByteBufferUtils.extract(queue, index);
		
		Assert.assertTrue(isEquals(buffers, 't'));
		Assert.assertTrue(isEquals(queue.drain(), null));
	}

	
	@Test public void test5() throws Exception {
		byte[] terminator = "\n\r".getBytes();
		ByteBuffer input = wrap(new char[] {'t', '\n', 'e', '\n', '\r', 'z', 'w', '\n', '\r'});
		ByteBufferQueue queue = new ByteBufferQueue();
		queue.offer(input);
		
		Index index = ByteBufferUtils.find(queue, terminator);
		ByteBuffer[] buffers = ByteBufferUtils.extract(queue, index);

		Assert.assertTrue(isEquals(buffers, 't', '\n', 'e'));
		Assert.assertTrue(isEquals(queue.drain(), 'z', 'w', '\n', '\r'));
	}

	@Test public void test6() throws Exception {
		byte[] terminator = "\n\r".getBytes();
		ByteBuffer input = wrap(new char[] {'t', '\n', 'e', '\n'});
		ByteBuffer input2 = wrap(new char[] {'\r', 'z', 'w', '\n', '\r'});
		ByteBufferQueue queue = new ByteBufferQueue();
		queue.offer(input);
		queue.offer(input2);
		
		Index index = ByteBufferUtils.find(queue, terminator);
		ByteBuffer[] buffers = ByteBufferUtils.extract(queue, index);

		Assert.assertTrue(isEquals(buffers, 't', '\n', 'e'));
		Assert.assertTrue(isEquals(queue.drain(), 'z', 'w', '\n', '\r'));
	}

	@Test public void test7() throws Exception {
		byte[] terminator = "\n\r".getBytes();
		ByteBuffer input = wrap(new char[] {'t', '\n', 'e', '\n'});
		ByteBuffer input2 = wrap(new char[] {'z', 'w', '\n'});
		ByteBufferQueue queue = new ByteBufferQueue();
		queue.offer(input);
		queue.offer(input2);
		
		Index index = ByteBufferUtils.find(queue, terminator);
		Assert.assertTrue(index == null);
	}
	
	
	private ByteBuffer wrap(char... chars) throws CharacterCodingException {
		Charset charset = Charset.forName("UTF-8");
		CharsetEncoder encoder = charset.newEncoder();
		return  encoder.encode(CharBuffer.wrap(chars));
	}
	

	
	private boolean isEquals(ByteBuffer[] buffers, char... c) throws CharacterCodingException, UnsupportedEncodingException  {
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
		return new JUnit4TestAdapter(ByteBufferUtilsTest.class);
	}
		
	
	public static void main (String... args) {
		TestRunner.run(suite());
	}
	
}
