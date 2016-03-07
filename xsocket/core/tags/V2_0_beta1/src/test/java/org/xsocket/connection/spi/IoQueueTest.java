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
package org.xsocket.connection.spi;

import java.nio.ByteBuffer;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.DataConverter;
import org.xsocket.connection.spi.IoQueue;


/**
*
* @author grro@xsocket.org
*/
public final class IoQueueTest {
	
	private static final String TEXT_1 = "Yes, "; 
	private static final String TEXT_2 = "it ";
	private static final String TEXT_3 = "works ";
	private static final String TEXT_4 = "fine";

	
	@Test 
	public void append1() throws Exception {
		IoQueue ioQueue = new IoQueue();
		ioQueue.append(DataConverter.toByteBuffer(TEXT_1, "UTF-8"));
		String data = DataConverter.toString(ioQueue.drain(), "UTF-8");
		
		Assert.assertEquals(data, TEXT_1);
	}

	
	@Test 
	public void append2() throws Exception {
		IoQueue ioQueue = new IoQueue();
		
		ByteBuffer[] buffers = new ByteBuffer[2];
		buffers[0] = DataConverter.toByteBuffer(TEXT_1, "UTF-8");
		buffers[1] = DataConverter.toByteBuffer(TEXT_2, "UTF-8");
		ioQueue.append(buffers);
		
		String data = DataConverter.toString(ioQueue.drain(), "UTF-8");
		Assert.assertEquals(data, TEXT_1 + TEXT_2);
	}

	@Test 
	public void append3() throws Exception {
		IoQueue ioQueue = new IoQueue();
		
		ioQueue.append(DataConverter.toByteBuffer(TEXT_1, "UTF-8"));
		
		ByteBuffer[] buffers = new ByteBuffer[2];
		buffers[0] = DataConverter.toByteBuffer(TEXT_2, "UTF-8");
		buffers[1] = DataConverter.toByteBuffer(TEXT_3, "UTF-8");
		ioQueue.append(buffers);
		
		String data = DataConverter.toString(ioQueue.drain(), "UTF-8");
		Assert.assertEquals(data, TEXT_1 + TEXT_2 + TEXT_3);
	}

	
	@Test 
	public void append4() throws Exception {
		IoQueue ioQueue = new IoQueue();
		
		ByteBuffer[] buffers = new ByteBuffer[2];
		buffers[0] = DataConverter.toByteBuffer(TEXT_1, "UTF-8");
		buffers[1] = DataConverter.toByteBuffer(TEXT_2, "UTF-8");
		ioQueue.append(buffers);

		ByteBuffer[] buffers2 = new ByteBuffer[2];
		buffers2[0] = DataConverter.toByteBuffer(TEXT_3, "UTF-8");
		buffers2[1] = DataConverter.toByteBuffer(TEXT_4, "UTF-8");
		ioQueue.append(buffers2);
		
		String data = DataConverter.toString(ioQueue.drain(), "UTF-8");
		Assert.assertEquals(data, TEXT_1 + TEXT_2 + TEXT_3 + TEXT_4);
	}

	
	@Test 
	public void append5() throws Exception {
		IoQueue ioQueue = new IoQueue();
		
		ByteBuffer[] buffers = new ByteBuffer[2];
		buffers[0] = DataConverter.toByteBuffer(TEXT_1, "UTF-8");
		buffers[1] = DataConverter.toByteBuffer(TEXT_2, "UTF-8");
		ioQueue.append(buffers);

		ioQueue.append(DataConverter.toByteBuffer(TEXT_3, "UTF-8"));

		
		String data = DataConverter.toString(ioQueue.drain(), "UTF-8");		
		Assert.assertEquals(data, TEXT_1 + TEXT_2 + TEXT_3);
	}

	
	@Test 
	public void add1() throws Exception {
		IoQueue ioQueue = new IoQueue();
		
		ByteBuffer[] buffers = new ByteBuffer[2];
		buffers[0] = DataConverter.toByteBuffer(TEXT_1, "UTF-8");
		buffers[1] = DataConverter.toByteBuffer(TEXT_2, "UTF-8");
		ioQueue.addFirst(buffers);
		
		String data = DataConverter.toString(ioQueue.drain(), "UTF-8");
		Assert.assertEquals(data, TEXT_1 + TEXT_2);
	}


	
	@Test 
	public void add2() throws Exception {
		IoQueue ioQueue = new IoQueue();
		
		ByteBuffer[] buffers = new ByteBuffer[2];
		buffers[0] = DataConverter.toByteBuffer(TEXT_3, "UTF-8");
		buffers[1] = DataConverter.toByteBuffer(TEXT_4, "UTF-8");
		ioQueue.addFirst(buffers);

		ByteBuffer[] buffers2 = new ByteBuffer[2];
		buffers2[0] = DataConverter.toByteBuffer(TEXT_1, "UTF-8");
		buffers2[1] = DataConverter.toByteBuffer(TEXT_2, "UTF-8");
		ioQueue.addFirst(buffers2);
		
		String data = DataConverter.toString(ioQueue.drain(), "UTF-8");
		Assert.assertEquals(data, TEXT_1 + TEXT_2 + TEXT_3 + TEXT_4);
	}

	
	
	@Test 
	public void add3() throws Exception {
		IoQueue ioQueue = new IoQueue();
		
		ioQueue.append(DataConverter.toByteBuffer(TEXT_3, "UTF-8"));
		
		ByteBuffer[] buffers = new ByteBuffer[2];
		buffers[0] = DataConverter.toByteBuffer(TEXT_1, "UTF-8");
		buffers[1] = DataConverter.toByteBuffer(TEXT_2, "UTF-8");
		ioQueue.addFirst(buffers);

		String data = DataConverter.toString(ioQueue.drain(), "UTF-8");
		Assert.assertEquals(data, TEXT_1 + TEXT_2 + TEXT_3);
	}

}
