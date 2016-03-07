// $Id: IndexOfTest.java 1190 2007-04-29 11:30:53Z grro $
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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.ClosedConnectionException;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;
import org.xsocket.stream.IConnection.FlushMode;



/**
*
* @author grro@xsocket.org
*/
public final class ReadOnServerCloseTest {

	private static final int CHUNK_SIZE = 4000;
	private static final int CHUNK_COUNT = 9;
	
	@Test 
	public void testReadOnClose() throws Exception {
		//QAUtil.setLogLevel(Level.FINE);
		
		IMultithreadedServer server = new MultithreadedServer(new Handler());
		StreamUtils.start(server);

		IBlockingConnection connection = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
		connection.setAutoflush(false);
		
		connection.write(CHUNK_SIZE);
		connection.write(CHUNK_COUNT);
		connection.flush();

		byte[] data = connection.readBytesByLength(CHUNK_SIZE * CHUNK_COUNT);
		Assert.assertEquals(data.length, CHUNK_SIZE * CHUNK_COUNT);
		
		try {
			connection.close();
		} catch (ClosedConnectionException ignore) { }
		server.close();
	}

	
	

	
	private static final class Handler implements IConnectHandler, IDataHandler {
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			connection.setFlushmode(FlushMode.ASYNC);
			
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			int length = connection.readInt();
			int chunks = connection.readInt();

			for (int i = 0; i < chunks; i++) {
				byte[] data = QAUtil.generateByteArray(length);
				connection.write(data);
				connection.flush();
			}
			connection.close();
			
			return true;
		}
	}
}
