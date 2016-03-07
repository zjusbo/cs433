// $Id: IndexOfTest.java 1748 2007-09-17 08:19:23Z grro $
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


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
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
public final class IndexOfTest {

	private static final String DELIMITER = "\r\n\r\n\r";
	
	private static final String TEST_MESSAGE_1 = "123456789";
	private static final String TEST_MESSAGE_2 = "1";
	

	
	@Test 
	public void testBlocking() throws Exception {
		IMultithreadedServer server = new MultithreadedServer(new EchoHandler());
		StreamUtils.start(server);	


		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);
		connection.setReceiveTimeoutMillis(300);
		
		int length = 233;
		byte[] testData = QAUtil.generateByteArray(length);
		connection.write((int) 45);
		connection.write(testData);
		connection.write(EchoHandler.DELIMITER);
		connection.flush();

		int index = connection.getIndexOf(EchoHandler.DELIMITER);
		Assert.assertTrue(index == (233 + 4));
		
		connection.readInt();
		
		index = connection.getIndexOf(EchoHandler.DELIMITER);
		Assert.assertTrue(index == 233);
		
		connection.readBytesByDelimiter(EchoHandler.DELIMITER);
		
		try {
			index = connection.getIndexOf(EchoHandler.DELIMITER);
			Assert.fail("a socket time should have been thrown (no delimiter)");
		} catch (SocketTimeoutException expected) { }
		
		
		
		connection.close();
		server.close();
	}

	

	@Test 
	public void testBlocking2() throws Exception {
		IMultithreadedServer server = new MultithreadedServer(new EchoHandler());
		StreamUtils.start(server);	


		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);
		connection.setReceiveTimeoutMillis(300);
		
		int length = 233;
		byte[] testData = QAUtil.generateByteArray(length);
		connection.write(testData);
		connection.write(EchoHandler.DELIMITER);
		connection.flush();

		int index = connection.getIndexOf(EchoHandler.DELIMITER);
		Assert.assertTrue(index == 233);

		byte[] response = connection.readBytesByDelimiter(EchoHandler.DELIMITER);
		Assert.assertArrayEquals(testData, response);
		
		connection.close();
		server.close();
	}

	
	@Test 
	public void testMaxSizeExceeded() throws Exception {
		IMultithreadedServer server = new MultithreadedServer(new EchoHandler());
		StreamUtils.start(server);	


		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);
		connection.setReceiveTimeoutMillis(300);
		
		int length = 233;
		byte[] testData = QAUtil.generateByteArray(length);
		connection.write(testData);
		connection.write(EchoHandler.DELIMITER);
		connection.flush();

		try {
			int index = connection.getIndexOf(EchoHandler.DELIMITER, 232);
			Assert.fail("a MaxReadSizeExceededException should have been thrown");
		} catch (MaxReadSizeExceededException ecpected) { }; 
		
		connection.close();
		server.close();
	}
	
	


	


	@Test 
	public void testNonblocking() throws Exception {
		TestHandler hdl = new TestHandler();
		IMultithreadedServer server = new MultithreadedServer(hdl);
		StreamUtils.start(server);	


		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);
		
		connection.write(TEST_MESSAGE_1 + DELIMITER);
		QAUtil.sleep(250);
		Assert.assertTrue(hdl.errorOccured == false);

		
		connection.write(TEST_MESSAGE_2 + DELIMITER);
		QAUtil.sleep(250);
		Assert.assertTrue(hdl.errorOccured == false);

		connection.write(DELIMITER);
		QAUtil.sleep(250);
		Assert.assertTrue(hdl.errorOccured == false);

		
		connection.close();
		server.close();
	}



	private static class TestHandler implements IConnectHandler, IDataHandler {
		
		private int state = 0; 
		
		private boolean errorOccured = false;

		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			
			switch (state) {
			case 0:
				int i = connection.getIndexOf(DELIMITER);
				if (i != 9) {
					errorOccured = true;
				}
				i = connection.getIndexOf(DELIMITER);
				if (i != 9) {
					errorOccured = true;
				}
				
				String request = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
				if (!request.equals(TEST_MESSAGE_1)) {
					errorOccured = true;
				}
				state = 1;
				break;

			case 1:
				i = connection.getIndexOf(DELIMITER);
				if (i != 1) {
					errorOccured = true;
				}
				
				request = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
				if (!request.equals(TEST_MESSAGE_2)) {
					errorOccured = true;
				}
				state = 2;
				break;
				
			case 2:
				i = connection.getIndexOf(DELIMITER);
				if (i != 0) {
					errorOccured = true;
				}
				
				state = 3;
				break;
				
			default:
				break;
			}
			
			return true;
		}
	}
}
