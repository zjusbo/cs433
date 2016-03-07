// $Id: BlockingConnectionTest.java 765 2007-01-15 07:13:48Z grro $
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
import java.util.logging.Logger;


import org.junit.Assert;
import org.junit.Test;

import org.xsocket.TestUtil;
import org.xsocket.TimeoutException;
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
public final class BlockingConnectionTest {

	private static final Logger LOG = Logger.getLogger(BlockingConnectionTest.class.getName());
	
	private static final String DELIMITER = "x";
	
	private static byte[] byteArray = new byte[] { 1, 2, 3, 4, 5 };
	

	
	
	@Test public void testAutoflush() throws Exception {
		IMultithreadedServer responsingServer = new MultithreadedServer(new ResponsingHandler());
		new Thread(responsingServer).start();

		
		IBlockingConnection connection = new BlockingConnection("127.0.0.1", responsingServer.getLocalPort());
		
		connection.write(byteArray);
		connection.write(DELIMITER);
		byte[] response = connection.receiveBytesByDelimiter(DELIMITER);
		Assert.assertTrue(TestUtil.isEquals(byteArray, response));
		
		connection.write(byteArray);
		connection.write(DELIMITER);
		byte[] response2 = connection.receiveBytesByDelimiter(DELIMITER);
		Assert.assertTrue(TestUtil.isEquals(byteArray, response2));
		
		connection.close();
		
		responsingServer.shutdown();
	}


	@Test public void testNonAutoflush() throws Exception {
 		IMultithreadedServer responsingServer = new MultithreadedServer(new ResponsingHandler());
		new Thread(responsingServer).start();
		
		IBlockingConnection connection = new BlockingConnection("127.0.0.1", responsingServer.getLocalPort());
		connection.setAutoflush(false);
		
		connection.write(byteArray);
		connection.write(DELIMITER);
		connection.flush();
		byte[] response = connection.receiveBytesByDelimiter(DELIMITER);
		Assert.assertTrue(TestUtil.isEquals(byteArray, response));
		
		connection.write(byteArray);
		connection.write(DELIMITER);
		connection.flush();
		byte[] response2 = connection.receiveBytesByDelimiter(DELIMITER);
		Assert.assertTrue(TestUtil.isEquals(byteArray, response2));
		
		connection.close();
		
		responsingServer.shutdown();
	}

	
	
	@Test public void testNonresponsive() throws Exception {
		IMultithreadedServer nonResponsingServer = new MultithreadedServer(new NonResponsingHandler());
		new Thread(nonResponsingServer).start();

		
		IBlockingConnection connection = new BlockingConnection("127.0.0.1", nonResponsingServer.getLocalPort());
		connection.setReceiveTimeout(1000);
		
		long start = System.currentTimeMillis();
		try {
			connection.receiveInt();
			Assert.fail("Timeout Exception should have been occured");
		} catch (TimeoutException te) {
			TestUtil.assertTimeout(System.currentTimeMillis() - start, 1000, 1500);
		}

		
		
		connection.setReceiveTimeout(3000);
		
		start = System.currentTimeMillis();
		try {
			connection.receiveInt();
			Assert.fail("Timeout Exception should have been occured");
		} catch (TimeoutException te) {
			TestUtil.assertTimeout(System.currentTimeMillis() - start, 3000, 3500);
		}

		
		connection.close();
		
		nonResponsingServer.shutdown();
	}


	private static final class NonResponsingHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			// do nothing
			return true;
		}
	}

	
	private static final class ResponsingHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			ByteBuffer[] buffers = connection.readByteBufferByDelimiter(DELIMITER);
			connection.write(buffers);
			connection.write(DELIMITER);
			LOG.fine("return data");
			return true;
		}
	}

}
