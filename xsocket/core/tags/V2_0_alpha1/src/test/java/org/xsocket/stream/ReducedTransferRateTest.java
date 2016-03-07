// $Id: ReducedTransferRateTest.java 1751 2007-09-18 07:08:17Z grro $
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
import java.util.logging.Level;

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
import org.xsocket.stream.IConnection.FlushMode;




/**
*
* @author grro@xsocket.org
*/
public final class ReducedTransferRateTest {


	@Test
	public void testClientSideData() throws Exception {

		IServer server = new Server(new Handler());
		StreamUtils.start(server);

		int size = 1000;
		byte[] data = QAUtil.generateByteArray(size);


		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(true);
		connection.setFlushmode(FlushMode.ASYNC);

		connection.setWriteTransferRate(550);
		connection.write(data);

		QAUtil.sleep(3000);

		Assert.assertTrue(QAUtil.isEquals(data, connection.readBytesByLength(size)));


		connection.close();
		server.close();
	}




	@Test
	public void testServerSideRate() throws Exception {
		IServer server = new Server(new TestHandler());
		StreamUtils.start(server);


		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(true);

		send(connection, 3, 2000, 3700);  // 8 byte / 3 byteSec -> 2.6 sec
		send(connection, 10, 200, 1800);  // 8 byte / 10 byteSec -> 0,8 sec
		send(connection, 5, 1500, 3000);  // 8 byte / 5 byteSec -> 1,6 sec

		connection.close();

		server.close();
	}


	private void send(IBlockingConnection connection, int i, long min, long max) throws IOException {
		long start = System.currentTimeMillis();
		// repeat the above operation, but delay is already set
		connection.write((long) i);

		connection.readLong();

		long elapsed = System.currentTimeMillis() - start;

		System.out.println("elapsed time " + elapsed + " (min=" + min + ", max=" + max + ")");
		Assert.assertTrue("elapsed time " + elapsed + " out of range (min=" + min + ", max=" + max + ")"
				          , (elapsed > min) && (elapsed < max));
	}


	private static final class Handler implements IDataHandler {
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			connection.write(connection.readAvailable());
			return true;
		}
	}


	private static final class TestHandler implements IConnectHandler, IDataHandler {

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setFlushmode(FlushMode.ASYNC);
			return true;
		}



		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			long delay = connection.readLong();
			connection.setWriteTransferRate((int) delay);
			connection.write((long) delay);

			return true;
		}
	}
}
