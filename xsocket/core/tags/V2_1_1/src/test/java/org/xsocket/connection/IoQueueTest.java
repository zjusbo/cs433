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

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.DataConverter;
import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.IConnection.FlushMode;



/**
*
* @author grro@xsocket.org
*/
public final class IoQueueTest {

	
	@Test 
	public void testDrainWithMaxLength1() throws Exception {

		IoQueue ioQueue = new IoQueue();
		
		ByteBuffer buf1 = ByteBuffer.wrap(new byte[] { 0, 1, 2, 3 });
		
		ioQueue.append(buf1);
		
		ByteBuffer[] bufs = ioQueue.drain(3);
		Assert.assertEquals(1, bufs.length);
		Assert.assertArrayEquals(new byte[] { 0, 1, 2 }, QAUtil.toArray(bufs[0]));
		
		bufs = ioQueue.drain();
		Assert.assertEquals(1, bufs.length);
		Assert.assertArrayEquals(new byte[] { 3 }, QAUtil.toArray(bufs[0]));			
	}	
	
	
	@Test 
	public void testDrainWithMaxLength2() throws Exception {

		IoQueue ioQueue = new IoQueue();
		
		ByteBuffer buf1 = ByteBuffer.wrap(new byte[] { });
		
		ioQueue.append(buf1);
		
		ByteBuffer[] bufs = ioQueue.drain(3);
		Assert.assertNull(bufs);
	}	
	
	
	
	@Test 
	public void testDrainWithMaxLength3() throws Exception {

		IoQueue ioQueue = new IoQueue();
		
		ByteBuffer buf1 = ByteBuffer.wrap(new byte[] { 0, 1 });
		ByteBuffer buf2 = ByteBuffer.wrap(new byte[] { 2, 3, 4, 5, 6, 7 });
		ByteBuffer buf3 = ByteBuffer.wrap(new byte[] { 8, 9 });
		
		ioQueue.append(buf1);
		ioQueue.append(buf2);
		ioQueue.append(buf3);
		
		ByteBuffer[] bufs = ioQueue.drain(4);
		Assert.assertEquals(2, bufs.length);
		Assert.assertArrayEquals(new byte[] { 0, 1 }, QAUtil.toArray(bufs[0]));		
		Assert.assertArrayEquals(new byte[] { 2, 3 }, QAUtil.toArray(bufs[1]));
		
		bufs = ioQueue.drain();
		Assert.assertEquals(2, bufs.length);
		Assert.assertArrayEquals(new byte[] { 4, 5, 6, 7 }, QAUtil.toArray(bufs[0]));		
		Assert.assertArrayEquals(new byte[] { 8, 9 }, QAUtil.toArray(bufs[1]));
	}	
	

	@Test 
	public void testDrainWithMaxLength4() throws Exception {

		IoQueue ioQueue = new IoQueue();
		
		ByteBuffer buf1 = ByteBuffer.wrap(new byte[] { 0, 1, 2, 3 });
		ByteBuffer buf2 = ByteBuffer.wrap(new byte[] { 4, 5, 6, 7 });
		ByteBuffer buf3 = ByteBuffer.wrap(new byte[] { 8, 9 });
		
		ioQueue.append(buf1);
		ioQueue.append(buf2);
		ioQueue.append(buf3);
		
		ByteBuffer[] bufs = ioQueue.drain(4);
		Assert.assertEquals(1, bufs.length);
		Assert.assertArrayEquals(new byte[] { 0, 1, 2, 3 }, QAUtil.toArray(bufs[0]));		
	}	
	
	
	@Test 
	public void testDrainWithMaxLength5() throws Exception {

		IoQueue ioQueue = new IoQueue();
		
		ByteBuffer buf1 = ByteBuffer.wrap(new byte[] { 0, 1, 2, 3 });
		ByteBuffer buf2 = ByteBuffer.wrap(new byte[] { 4, 5, 6, 7 });
		ByteBuffer buf3 = ByteBuffer.wrap(new byte[] { 8, 9 });
		
		ioQueue.append(buf1);
		ioQueue.append(buf2);
		ioQueue.append(buf3);
		
		ByteBuffer[] bufs = ioQueue.drain(8);
		Assert.assertEquals(2, bufs.length);
		Assert.assertArrayEquals(new byte[] { 0, 1, 2, 3 }, QAUtil.toArray(bufs[0]));		
		Assert.assertArrayEquals(new byte[] { 4, 5, 6, 7 }, QAUtil.toArray(bufs[1]));
		
		bufs = ioQueue.drain();
		Assert.assertEquals(1, bufs.length);
		Assert.assertArrayEquals(new byte[] { 8, 9 }, QAUtil.toArray(bufs[0]));	
	}	
	
	
	@Test 
	public void testDrainWithMaxLength6() throws Exception {

		IoQueue ioQueue = new IoQueue();
		
		ByteBuffer buf1 = ByteBuffer.wrap(new byte[] { 0, 1, 2, 3 });
		
		
		ioQueue.append(buf1);
		
		ByteBuffer[] bufs = ioQueue.drain(4);
		Assert.assertEquals(1, bufs.length);
		Assert.assertArrayEquals(new byte[] { 0, 1, 2, 3 }, QAUtil.toArray(bufs[0]));		
		
		bufs = ioQueue.drain();
		Assert.assertNull(bufs);		
	}	
}
