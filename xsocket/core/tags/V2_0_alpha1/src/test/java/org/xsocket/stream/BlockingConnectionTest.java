// $Id: BlockingConnectionTest.java 1738 2007-09-13 07:02:26Z grro $
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
import org.xsocket.stream.IServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.Server;
import org.xsocket.stream.IConnection.FlushMode;





/**
*
* @author grro@xsocket.org
*/
public final class BlockingConnectionTest {

	private static final Logger LOG = Logger.getLogger(BlockingConnectionTest.class.getName());

	private static final String DELIMITER = "x";

	private static byte[] byteArray = new byte[] { 1, 2, 3, 4, 5 };

	private int running = 0;


	@Test
	public void testLive() throws Exception {
		IBlockingConnection connection = new BlockingConnection("www.web.de", 80);
		connection.setAutoflush(true);

		connection.write("GET /\r\n");
		String response = connection.readStringByDelimiter("\r\n\r\n");
		Assert.assertTrue(response.indexOf("WEB.DE") != -1);

		connection.close();
	}




	@Test
	public void testAsyncServerSide() throws Exception {
		perform(new Server(new AsyncHandler()));
	}



	@Test
	public void testSyncServerSide() throws Exception {
		perform(new Server(new SyncHandler()));
	}



	private void perform(final IServer server) throws Exception {
		StreamUtils.start(server);


		for (int i = 0; i < 50; i++) {

			final int num = i;
			Thread t = new Thread() {
				@Override
				public void run() {
					running++;

					try {
						IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
						connection.setAutoflush(true);

						String request = "helo" + num;
						connection.write(request);
						connection.write(DELIMITER);
						String response = connection.readStringByDelimiter(DELIMITER);
						Assert.assertEquals(request, response);

						LOG.fine("server returned helo. send next request");

						byte[] requestArray = QAUtil.generateByteArray(10 + num);
						connection.write(requestArray);
						connection.write(DELIMITER);

						LOG.fine("waiting for response..");
						byte[] responseArray = connection.readBytesByDelimiter(DELIMITER, Integer.MAX_VALUE);
						Assert.assertTrue(QAUtil.isEquals(requestArray, responseArray));
						LOG.fine("server returned second request");

						connection.close();

					} catch (Exception e) {
						e.printStackTrace();
					}

					running--;
				}
			};

			t.start();
		}

		do {
			QAUtil.sleep(100);
		} while(running > 0);

		server.close();
	}


	@Test
	public void testNonAutoflush() throws Exception {
 		IServer responsingServer = new Server(new ResponsingHandler());
 		StreamUtils.start(responsingServer);

		IBlockingConnection connection = new BlockingConnection("localhost", responsingServer.getLocalPort());
		connection.setAutoflush(false);

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


	@Test
	public void testReceiveTimeoutVeryHigh() throws Exception {
 		IServer responsingServer = new Server(new ResponsingHandler());
 		StreamUtils.start(responsingServer);

		IBlockingConnection connection = new BlockingConnection("localhost", responsingServer.getLocalPort());
		connection.setAutoflush(false);
		connection.setReceiveTimeoutMillis(1000000);


		connection.write(byteArray);
		connection.write(DELIMITER);
		connection.flush();
		byte[] response = connection.readBytesByDelimiter(DELIMITER);
		Assert.assertTrue(QAUtil.isEquals(byteArray, response));

		connection.close();
		responsingServer.close();
	}




	@Test
	public void testReceiveNormal() throws Exception {
 		IServer responsingServer = new Server(new ResponsingHandler());
 		StreamUtils.start(responsingServer);

		IBlockingConnection connection = new BlockingConnection("localhost", responsingServer.getLocalPort());
		connection.setAutoflush(false);
		connection.setReceiveTimeoutMillis(634);


		connection.write(byteArray);
		connection.write(DELIMITER);
		connection.flush();
		byte[] response = connection.readBytesByDelimiter(DELIMITER, Integer.MAX_VALUE);
		Assert.assertTrue(QAUtil.isEquals(byteArray, response));
		connection.close();
		responsingServer.close();
	}


	@Test
	public void testNonresponsive() throws Exception {
		IServer nonResponsingServer = new Server(new NonResponsingHandler());
		StreamUtils.start(nonResponsingServer);


		IBlockingConnection connection = new BlockingConnection("localhost", nonResponsingServer.getLocalPort());
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
			connection.setAutoflush(false);

			ByteBuffer[] buffers = connection.readByteBufferByDelimiter(DELIMITER);
			connection.write(buffers);
			connection.write(DELIMITER);
			LOG.fine("return data");

			connection.flush();
			return true;
		}
	}


	private static final class AsyncHandler implements IConnectHandler, IDataHandler {

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			connection.setFlushmode(FlushMode.ASYNC);

			return true;
		}

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			ByteBuffer[] buffers = connection.readByteBufferByDelimiter(DELIMITER);
			connection.write(buffers);
			connection.write(DELIMITER);
			LOG.fine("return data");

			connection.flush();
			return true;
		}
	}


	private static final class SyncHandler implements IConnectHandler, IDataHandler {

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			connection.setFlushmode(FlushMode.SYNC);

			return true;
		}

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			ByteBuffer[] buffers = connection.readByteBufferByDelimiter(DELIMITER);
			connection.write(buffers);
			connection.write(DELIMITER);
			LOG.fine("return data");

			connection.flush();
			return true;
		}
	}

}
