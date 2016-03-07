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
import java.nio.channels.ClosedChannelException;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;




/**
*
* @author grro@xsocket.org
*/
public final class CompatibilityWriteableByteChannelTest {


	@Test
	public void testNonBlockingWriteClientClose() throws Exception {

		ServerHandler srvHdl = new ServerHandler();
		IServer server = new Server(srvHdl);
		ConnectionUtils.start(server);


		INonBlockingConnection clientCon = new NonBlockingConnection("localhost", server.getLocalPort());

		QAUtil.sleep(200);
		INonBlockingConnection serverCon = srvHdl.getConection();


		ByteBuffer buffer = QAUtil.generateByteBuffer(40);
		int written = clientCon.write(buffer);
		Assert.assertEquals(40, written);

		QAUtil.sleep(200);
		Assert.assertEquals(40, serverCon.available());

		clientCon.close();

		buffer = QAUtil.generateByteBuffer(40);
		try {
			written = clientCon.write(buffer);
			Assert.fail("ClosedChannelException expected");
		} catch (ClosedChannelException expected) { }

		server.close();
	}


	@Test
	public void testNonBlockingWriteServerConnectionClose() throws Exception {

		ServerHandler srvHdl = new ServerHandler();
		IServer server = new Server(srvHdl);
		ConnectionUtils.start(server);


		INonBlockingConnection clientCon = new NonBlockingConnection("localhost", server.getLocalPort());

		QAUtil.sleep(200);
		INonBlockingConnection serverCon = srvHdl.getConection();


		ByteBuffer buffer = QAUtil.generateByteBuffer(40);
		int written = clientCon.write(buffer);
		Assert.assertEquals(40, written);

		QAUtil.sleep(200);
		Assert.assertEquals(40, serverCon.available());

		serverCon.close();

		QAUtil.sleep(200);
		buffer = QAUtil.generateByteBuffer(40);
		try {
			written = clientCon.write(buffer);
			Assert.fail("ClosedChannelException expected");
		} catch (ClosedChannelException expected) { }

		clientCon.close();
		server.close();
	}


	@Test
	public void testNonBlockingWriteServerConClose() throws Exception {

		ServerHandler srvHdl = new ServerHandler();
		IServer server = new Server(srvHdl);
		ConnectionUtils.start(server);


		INonBlockingConnection clientCon = new NonBlockingConnection("localhost", server.getLocalPort());

		QAUtil.sleep(200);
		INonBlockingConnection serverCon = srvHdl.getConection();


		ByteBuffer buffer = QAUtil.generateByteBuffer(40);
		int written = clientCon.write(buffer);
		Assert.assertEquals(40, written);

		QAUtil.sleep(200);
		Assert.assertEquals(40, serverCon.available());

		server.close();

		buffer = QAUtil.generateByteBuffer(40);
		try {
			written = clientCon.write(buffer);
			Assert.fail("ClosedChannelException expected");
		} catch (ClosedChannelException expected) { }

		clientCon.close();
	}



	private static final class ServerHandler implements IConnectHandler {

		private INonBlockingConnection connection = null;

		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			this.connection = connection;
			return true;
		}

		INonBlockingConnection getConection() {
			return connection;
		}
	}
}
