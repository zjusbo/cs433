// $Id: BlockingConnectionTest.java 1386 2007-06-28 11:47:15Z grro $
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
package org.xsocket.stream.io.grizzly;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;


import org.junit.Assert;
import org.junit.Test;

import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IConnectHandler;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.IServer;
import org.xsocket.stream.Server;
import org.xsocket.stream.StreamUtils;
import org.xsocket.stream.IConnection.FlushMode;





/**
*
* @author grro@xsocket.org
*/
public final class BlockingConnectionTest {

	private static final Logger LOG = Logger.getLogger(BlockingConnectionTest.class.getName());
	
	private static final String DELIMITER = "x";
	private static final int LOOPS = 20;
	
	private int running = 0;
	
	
	
	@Test 
	public void testAsyncServerSide() throws Exception {
		System.setProperty("org.xsocket.stream.io.spi.ServerIoProviderClass", org.xsocket.stream.io.grizzly.GrizzlyIoProvider.class.getName());
		
		perform(new Server(new AsyncHandler()));
	}

	
	
	@Test 
	public void testSyncServerSide() throws Exception {
		System.setProperty("org.xsocket.stream.io.spi.ServerIoProviderClass", org.xsocket.stream.io.grizzly.GrizzlyIoProvider.class.getName());

		perform(new Server(new SyncHandler()));
	}

	
	
	private void perform(final IServer server) throws Exception {
//		QAUtil.setLogLevel("org.xsocket.stream.io.grizzly", Level.FINE);
		
		StreamUtils.start(server);

		
		for (int i = 0; i < LOOPS; i++) {
			
			final int num = i;
			Thread t = new Thread() {
				@Override
				public void run() {
					running++;

					try {
						IBlockingConnection connection = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
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
			LOG.fine("returning data");
			
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
			LOG.fine("returning data");
			
			connection.flush();
			return true;
		}
	}

}
