// $Id: IndexOfTest.java 1017 2007-03-15 08:03:05Z grro $
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

import java.util.Random;


import org.junit.Assert;
import org.junit.Test;
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
public final class IndexOfTest {

	private static final String DELIMITER = "\r\n\r\n\r";
	
	private static final String TEST_MESSAGE_1 = "123456789";
	private static final String TEST_MESSAGE_2 = "1";
	

	
	

	@Test public void testNonblocking() throws Exception {
		TestHandler hdl = new TestHandler();
		IMultithreadedServer server = new MultithreadedServer(hdl);
		new Thread(server).start();	


		IBlockingConnection connection = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());

		connection.write(TEST_MESSAGE_1 + DELIMITER);
		TestUtil.sleep(250);
		Assert.assertTrue(hdl.errorOccured == false);

		
		connection.write(TEST_MESSAGE_2 + DELIMITER);
		TestUtil.sleep(250);
		Assert.assertTrue(hdl.errorOccured == false);

		connection.write(DELIMITER);
		TestUtil.sleep(250);
		Assert.assertTrue(hdl.errorOccured == false);

		
		connection.close();
		server.close();
	}



	private static class TestHandler implements IDataHandler {
		
		private int state = 0; 
		
		private boolean errorOccured = false;

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			
			switch (state) {
			case 0:
				int i = connection.indexOf(DELIMITER);
				if (i != 9) {
					errorOccured = true;
				}
				i = connection.indexOf(DELIMITER);
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
				i = connection.indexOf(DELIMITER);
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
				i = connection.indexOf(DELIMITER);
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
