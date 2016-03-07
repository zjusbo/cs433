// $Id: ReadAvailableByDelimiterTest.java 899 2007-02-11 13:49:26Z grro $
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
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.DataConverter;
import org.xsocket.TestUtil;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;




/**
*
* @author grro@xsocket.org
*/
public final class ReadAvailableByDelimiterTest {

	private static final String DELIMITER = "\r\n\r\n\r";
	private static final String OK = "OK";


	@Test public void testSimple() throws Exception {
		DataSink dataSink = new DataSink();
		IMultithreadedServer server = new MultithreadedServer(new Handler(dataSink));
		new Thread(server).start();	
		
		IBlockingConnection connection = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
		connection.setAutoflush(false);
		
		byte[] request1 = TestUtil.generatedByteArray(60);
		connection.write(request1);
		connection.flush();
		TestUtil.sleep(200);
		
		byte[] received1 = DataConverter.toBytes(dataSink.getReceiveBuffers());
		Assert.assertTrue(TestUtil.isEquals(request1, received1));
	
		
		byte[] request2 = TestUtil.generatedByteArray(20);
		connection.write(request2);
		connection.flush();
		TestUtil.sleep(200);
		
		
		byte[] received2 = DataConverter.toBytes(dataSink.getReceiveBuffers());
		Assert.assertTrue(TestUtil.isEquals(TestUtil.mergeByteArrays(request1, request2), received2));
		
		
		connection.write(DELIMITER);
		connection.flush();
		
		String okResponse = connection.readStringByDelimiter(DELIMITER);
		Assert.assertTrue(okResponse.equals(OK));

		connection.close();
		server.shutdown();
	}


	private static final class DataSink implements WritableByteChannel {
		
		private final List<ByteBuffer> buffers = new ArrayList<ByteBuffer>();
		
		
		private boolean isOpen = true;
		
		public void close() throws IOException {
			isOpen = false;
		}
		
		public boolean isOpen() {
			return isOpen;
		}
		
		public int write(ByteBuffer buffer) throws IOException {
			int size = buffer.remaining();
			buffers.add(buffer);
			return size;
		}
		
		ByteBuffer[] getReceiveBuffers() {
			return buffers.toArray(new ByteBuffer[buffers.size()]);
		}
	}


	private static final class Handler implements IDataHandler {
		
		private WritableByteChannel dataSink = null;
		
		public Handler(WritableByteChannel dataSink) {
			this.dataSink = dataSink;
		}

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			boolean delimiterFound = connection.readAvailableByDelimiter(DELIMITER, dataSink);
			
			if (delimiterFound) {
				connection.write(OK);
				connection.write(DELIMITER);
			}
			
			return true;
		}

	}
}
