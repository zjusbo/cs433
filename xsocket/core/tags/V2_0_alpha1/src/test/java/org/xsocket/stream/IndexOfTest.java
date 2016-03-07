// $Id: IndexOfTest.java 1738 2007-09-13 07:02:26Z grro $
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
import org.xsocket.stream.IServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.Server;



/**
*
* @author grro@xsocket.org
*/
public final class IndexOfTest {

	private static final String DELIMITER = "\r\n\r\n\r";
	
	private static final String TEST_MESSAGE_1 = "123456789";
	private static final String TEST_MESSAGE_2 = "1";
	

	
	@Test 
	public void testNonBlocking() throws Exception {
		IServer server = new Server(new EchoHandler());
		StreamUtils.start(server);	


		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);
		
		int length = 233;
		byte[] testData = QAUtil.generateByteArray(length);
		connection.write((int) 45);
		connection.write(testData);
		connection.write(EchoHandler.DELIMITER);
		connection.flush();
		QAUtil.sleep(150);

		int index = connection.indexOf(EchoHandler.DELIMITER);
		Assert.assertTrue(index == (233 + 4));
		
		connection.readInt();
		
		index = connection.indexOf(EchoHandler.DELIMITER);
		Assert.assertTrue(index == 233);
		
		connection.readBytesByDelimiter(EchoHandler.DELIMITER);
		
		index = connection.indexOf(EchoHandler.DELIMITER);
		Assert.assertEquals(-1, index);
		
		connection.close();
		server.close();
	}

	
	
	@Test 
	public void testNonBlocking2() throws Exception {
		IServer server = new Server(new EchoHandler());
		StreamUtils.start(server);	


		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);
		
		int length = 233;
		byte[] testData = QAUtil.generateByteArray(length);
		connection.write(testData);
		connection.write(EchoHandler.DELIMITER);
		connection.flush();
		
		QAUtil.sleep(150);

		int index = connection.indexOf(EchoHandler.DELIMITER);
		Assert.assertTrue(index == 233);

		byte[] response = connection.readBytesByDelimiter(EchoHandler.DELIMITER);
		Assert.assertArrayEquals(testData, response);
		
		connection.close();
		server.close();
	}

	
	
	@Test 
	public void testMaxSizeExceeded() throws Exception {
		IServer server = new Server(new EchoHandler());
		StreamUtils.start(server);	


		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);
		
		int dataLength = 233;
		byte[] testData = QAUtil.generateByteArray(dataLength);
		connection.write(testData);
		connection.write(EchoHandler.DELIMITER);
		connection.flush();
		QAUtil.sleep(150);

		try {
			int index = connection.indexOf(EchoHandler.DELIMITER, dataLength);  // delimiter starts on position 234
			Assert.fail("a MaxReadSizeExceededException should have been thrown because delimiter hasn't been found within the valid range");
		} catch (MaxReadSizeExceededException ecpected) { }; 

		
		int index = connection.indexOf(EchoHandler.DELIMITER, dataLength + EchoHandler.DELIMITER.length());
		Assert.assertEquals(index, dataLength);

		
		connection.close();
		server.close();
	}
}
