// $Id: BlockingConnectionTest.java 1108 2007-03-29 16:44:02Z grro $
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
import java.util.logging.Logger;


import org.junit.Assert;
import org.junit.Test;

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
public final class BlockingConnectionTest {

	private static final Logger LOG = Logger.getLogger(BlockingConnectionTest.class.getName());
	
	private static final String DELIMITER = "x";
	
	private static byte[] byteArray = new byte[] { 1, 2, 3, 4, 5 };
	

	
	
	@Test public void testAutoflush() throws Exception {
		IMultithreadedServer responsingServer = new MultithreadedServer(new ResponsingHandler());
		new Thread(responsingServer).start();

		
		IBlockingConnection connection = new BlockingConnection(responsingServer.getLocalAddress(), responsingServer.getLocalPort());
		connection.setAutoflush(true);
		
		connection.write(byteArray);
		connection.write(DELIMITER);
		byte[] response = connection.readBytesByDelimiter(DELIMITER, Integer.MAX_VALUE);
		Assert.assertTrue(QAUtil.isEquals(byteArray, response));
		
		connection.write(byteArray);
		connection.write(DELIMITER);
		byte[] response2 = connection.readBytesByDelimiter(DELIMITER, Integer.MAX_VALUE);
		Assert.assertTrue(QAUtil.isEquals(byteArray, response2));
		
		connection.close();
		
		responsingServer.close();
	}


	@Test public void testNonAutoflush() throws Exception {
 		IMultithreadedServer responsingServer = new MultithreadedServer(new ResponsingHandler());
		new Thread(responsingServer).start();
		
		IBlockingConnection connection = new BlockingConnection(responsingServer.getLocalAddress(), responsingServer.getLocalPort());
		// by default autoflush is false
		
		connection.write(byteArray);
		connection.write(DELIMITER);
		connection.flush();
		byte[] response = connection.readBytesByDelimiter(DELIMITER, Integer.MAX_VALUE);
		Assert.assertTrue(QAUtil.isEquals(byteArray, response));
		
		connection.write(byteArray);
		connection.write(DELIMITER);
		connection.flush();
		byte[] response2 = connection.readBytesByDelimiter(DELIMITER, Integer.MAX_VALUE);
		Assert.assertTrue(QAUtil.isEquals(byteArray, response2));
		
		connection.close();
		
		responsingServer.close();
	}

	
	@Test public void testReceiveTimeoutVeryHigh() throws Exception {
 		IMultithreadedServer responsingServer = new MultithreadedServer(new ResponsingHandler());
		new Thread(responsingServer).start();
		
		IBlockingConnection connection = new BlockingConnection(responsingServer.getLocalAddress(), responsingServer.getLocalPort());
		// by default autoflush is false
		connection.setReceiveTimeoutMillis(100000000L);
		
		
		connection.write(byteArray);
		connection.write(DELIMITER);
		connection.flush();
		byte[] response = connection.readBytesByDelimiter(DELIMITER, Integer.MAX_VALUE);
		Assert.assertTrue(QAUtil.isEquals(byteArray, response));
		
		connection.close();
		responsingServer.close();
	}
	
	

	
	@Test public void testReceiveNormal() throws Exception {
 		IMultithreadedServer responsingServer = new MultithreadedServer(new ResponsingHandler());
		new Thread(responsingServer).start();
		
		IBlockingConnection connection = new BlockingConnection(responsingServer.getLocalAddress(), responsingServer.getLocalPort());
		// by default autoflush is false
		connection.setReceiveTimeoutMillis(634);
		
		
		connection.write(byteArray);
		connection.write(DELIMITER);
		connection.flush();
		byte[] response = connection.readBytesByDelimiter(DELIMITER, Integer.MAX_VALUE);
		Assert.assertTrue(QAUtil.isEquals(byteArray, response));
		connection.close();
		responsingServer.close();
	}
	
	
	@Test public void testNonresponsive() throws Exception {
		IMultithreadedServer nonResponsingServer = new MultithreadedServer(new NonResponsingHandler());
		new Thread(nonResponsingServer).start();

		
		IBlockingConnection connection = new BlockingConnection(nonResponsingServer.getLocalAddress(), nonResponsingServer.getLocalPort());
		connection.setReceiveTimeoutMillis(1000);
		connection.setAutoflush(true);
		
		long start = System.currentTimeMillis();
		try {
			connection.readInt();
			Assert.fail("Timeout Exception should have been occured");
		} catch (SocketTimeoutException te) {
			QAUtil.assertTimeout(System.currentTimeMillis() - start, 1000, 2000);
		}

		
		
		connection.setReceiveTimeoutMillis(3000);
		
		start = System.currentTimeMillis();
		try {
			connection.readInt();
			Assert.fail("Timeout Exception should have been occured");
		} catch (SocketTimeoutException te) {
			QAUtil.assertTimeout(System.currentTimeMillis() - start, 3000, 3500);
		}

		
		connection.close();
		
		nonResponsingServer.close();
	}


	private static final class NonResponsingHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			// do nothing
			return true;
		}
	}

	
	private static final class ResponsingHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			ByteBuffer[] buffers = connection.readByteBufferByDelimiter(DELIMITER, Integer.MAX_VALUE);
			connection.write(buffers);
			connection.write(DELIMITER);
			LOG.fine("return data");
			return true;
		}
	}

}
