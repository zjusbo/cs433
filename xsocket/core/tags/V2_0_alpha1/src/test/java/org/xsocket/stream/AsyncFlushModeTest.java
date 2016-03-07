// $Id: ReadByDelimiterAndMaxSizeTest.java 1237 2007-05-13 16:55:37Z grro $
/*
 *  Copyright (c) xsocket.org, 2006 - 2007. All rights reserved.
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
import java.nio.ByteBuffer;

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
public final class AsyncFlushModeTest {

	private static final String DELIMITER = "x"; 
	

	@Test 
	public void testSimple() throws Exception {
		IServer server = new Server(new ServerHandler());
		StreamUtils.start(server);

		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		
		String request = "dsfdsdsffds";
		connection.write(request + DELIMITER);
		
		String response = connection.readStringByDelimiter(DELIMITER);
		Assert.assertEquals(request, response);
		
		connection.close();
		server.close();
	}


	@Test 
	public void testNonBlockingClientSide() throws Exception {
		IServer server = new Server(new ServerHandler());
		StreamUtils.start(server);

		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort());
		connection.setFlushmode(FlushMode.ASYNC);
		
		String request = "dsfdsdsffds";
		connection.write(request + DELIMITER);
		
		QAUtil.sleep(250);
		
		String response = connection.readStringByDelimiter(DELIMITER);
		Assert.assertEquals(request, response);
		
		connection.close();
		server.close();
	}

	
	
	
	
	private static class ServerHandler implements IConnectHandler, IDataHandler {
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setFlushmode(FlushMode.ASYNC);
			connection.setAutoflush(false);
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException {
			ByteBuffer[] data = connection.readByteBufferByDelimiter(DELIMITER);
			connection.write(data);
			connection.write(DELIMITER);
			
			connection.flush();
			return true;
		}
	}
}
