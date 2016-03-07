// $Id: ByteBufferParserTest.java 834 2007-01-29 08:16:42Z grro $
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

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;




public class ReadByteBufferTest {

	private static final String DELIMITER = "\r"; 
	
	@Test 
	public void testBlockingConnection() throws Exception {
		IMultithreadedServer server = new MultithreadedServer(new Handler());
		StreamUtils.start(server);
		
		
		IBlockingConnection connection = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
		connection.setAutoflush(false);
		
		String s = "test123";
		ByteBuffer request = ByteBuffer.wrap(s.getBytes());
		connection.write(request);
		connection.write(DELIMITER);
		connection.flush();
		
		
		ByteBuffer response = ByteBuffer.allocate(request.capacity() + 30);
		connection.read(response);
		Assert.assertTrue(response.position() > 0);
		Assert.assertTrue(response.get(0) == request.get(0));
		
		
		connection.close();
		server.close();
	}
	
	
	@Test 
	public void testBlockingNoBytesConnection() throws Exception {
		IMultithreadedServer server = new MultithreadedServer(new DelayHandler(500));
		StreamUtils.start(server);
		
		
		IBlockingConnection connection = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
		connection.setAutoflush(false);
		connection.setReceiveTimeoutMillis(200);
		
		String s = "test123";
		ByteBuffer request = ByteBuffer.wrap(s.getBytes());
		connection.write(request);
		connection.write(DELIMITER);
		connection.flush();
		
		
		ByteBuffer response = ByteBuffer.allocate(request.capacity());
		try {
			connection.read(response);
			Assert.fail("timeout exception should haven been thrown");
		} catch (SocketTimeoutException expected) {	};

		QAUtil.sleep(500);
		
		connection.read(response);
		Assert.assertTrue(QAUtil.isEquals(request, response));
		
		connection.close();
		server.close();
	}

	
	@Test 
	public void testNonBlockingConnection() throws Exception {
		IMultithreadedServer server = new MultithreadedServer(new Handler());
		StreamUtils.start(server);
		
		
		INonBlockingConnection connection = new NonBlockingConnection(server.getLocalAddress(), server.getLocalPort());
		connection.setAutoflush(false);
		
		String s = "test123";
		ByteBuffer request = ByteBuffer.wrap(s.getBytes());
		connection.write(request);
		connection.write(DELIMITER);
		connection.flush();
		
		QAUtil.sleep(200);
		
		ByteBuffer response = ByteBuffer.allocate(request.capacity());
		connection.read(response);
		Assert.assertTrue(response.position() == request.position());
		Assert.assertTrue(QAUtil.isEquals(response, request));
		
		
		connection.close();
		server.close();
	}

	
	
	private static final class Handler implements IDataHandler {
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			connection.setAutoflush(false);
			
			connection.write(connection.readByteBufferByDelimiter(DELIMITER));
			connection.write(DELIMITER);
			connection.flush();
			return true;
		}
	}
	
	
	private static final class DelayHandler implements IDataHandler {
		
		private int delay = 0;
		
		DelayHandler(int delay) {
			this.delay = delay;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			connection.setAutoflush(false);
			
			connection.write(connection.readByteBufferByDelimiter(DELIMITER));
			connection.write(DELIMITER);
			
			QAUtil.sleep(delay);
			
			connection.flush();
			return true;
		}
	}
}