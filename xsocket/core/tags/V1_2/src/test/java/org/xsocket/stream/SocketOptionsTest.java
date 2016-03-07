// $Id: DataPackagesTest.java 1017 2007-03-15 08:03:05Z grro $
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
import java.net.SocketOptions;
import java.nio.BufferUnderflowException;
import java.util.HashMap;
import java.util.Map;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;




/**
*
* @author grro@xsocket.org
*/
public final class SocketOptionsTest {

	private static final String DELIMITER = "\r\n\r\n\r";
	
	private String error = null;

	
	@Test 
	public void testOption() throws Exception {
		IMultithreadedServer server = new MultithreadedServer(new TestHandler());
		StreamUtils.start(server);	
		
		BlockingConnection connection = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
		connection.setAutoflush(false);
		
		
		boolean noDelay = (Boolean) connection.getOption(IConnection.TCP_NODELAY);
		
		
		connection.setOption(IConnection.TCP_NODELAY, !noDelay);
		Assert.assertEquals(!noDelay, (Boolean) connection.getOption(IConnection.TCP_NODELAY));

		
		connection.write("Hello" + DELIMITER);
		connection.flush();

		connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		
		connection.close();
		server.close();
		
		Assert.assertNull(error, error);
	}


	@Test 
	public void testBlockingConnectionOption() throws Exception {
		IMultithreadedServer server = new MultithreadedServer(new TestHandler());
		StreamUtils.start(server);	
		
		
		Map<String, Object> opts = new HashMap<String, Object>();
		opts.put(IConnection.SO_LINGER, 1000);
		opts.put(IConnection.SO_SNDBUF, 8899);
		opts.put(IConnection.SO_RCVBUF, 9988);
		opts.put(IConnection.TCP_NODELAY, true);
		opts.put(IConnection.SO_KEEPALIVE, true);
		
		BlockingConnection connection = new BlockingConnection(server.getLocalAddress().getHostName(), server.getLocalPort(), opts);
		connection.setAutoflush(false);
		
		Assert.assertTrue(connection.getOption(IConnection.SO_LINGER).equals(1000));
		Assert.assertTrue(connection.getOption(IConnection.SO_SNDBUF).equals(8899));
		Assert.assertTrue(connection.getOption(IConnection.SO_RCVBUF).equals(9988));
		Assert.assertTrue(connection.getOption(IConnection.TCP_NODELAY).equals(true));
		Assert.assertTrue(connection.getOption(IConnection.SO_KEEPALIVE).equals(true));
		
		connection.write("Hello" + DELIMITER);
		connection.flush();

		connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		
		connection.close();
		server.close();
		
		Assert.assertNull(error, error);
	}


	



	
	@Test 
	public void testServerSideOptionNoReuseAdress() throws Exception {
		TestHandler hdl = new TestHandler();
		
		Map<String, Object> options = new HashMap<String, Object>();
		options.put(IMultithreadedServer.SO_REUSEADDR, false);
		
		IMultithreadedServer server = new MultithreadedServer(options, hdl);
		StreamUtils.start(server);	
		
		BlockingConnection connection = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
		connection.setAutoflush(false);
		
		
		QAUtil.sleep(300);
		SocketOptions serverSideOptions = hdl.options;
		Assert.assertTrue(serverSideOptions.getOption(SocketOptions.SO_REUSEADDR).equals(false));
				
		
		connection.write("Hello" + DELIMITER);
		connection.flush();

		connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		
		connection.close();
		server.close();
		
		Assert.assertNull(error, error);
	}

	

	private static class TestHandler implements IDataHandler, IConnectHandler {
		
		private SocketOptions options = null;

		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			options = ((NonBlockingConnection) connection).getSocketOptions();
			connection.setAutoflush(false);
			
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			byte[] buffer = connection.readBytesByDelimiter(DELIMITER, Integer.MAX_VALUE);

			connection.write(buffer);
			connection.write(DELIMITER);
			
			connection.flush();
			return true;
		}
	}
}
