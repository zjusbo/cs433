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

	
	@Test public void testOption() throws Exception {
		IMultithreadedServer server = new MultithreadedServer(new TestHandler());
		StreamUtils.start(server);	
		
		BlockingConnection connection = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
		connection.setAutoflush(false);
		
		SocketOptions options = connection.getSocketOptions();
		Assert.assertNotNull(options);
		
		boolean noDelay = (Boolean) options.getOption(SocketOptions.TCP_NODELAY);
		
		boolean newNoDelay = !noDelay;
		options.setOption(SocketOptions.TCP_NODELAY, newNoDelay);
		Assert.assertTrue(options.getOption(SocketOptions.TCP_NODELAY).equals(newNoDelay));
		
		connection.write("Hello" + DELIMITER);
		connection.flush();

		connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		
		connection.close();
		server.close();
		
		Assert.assertNull(error, error);
	}


	@Test public void testBlockingConnectionOption() throws Exception {
		IMultithreadedServer server = new MultithreadedServer(new TestHandler());
		StreamUtils.start(server);	
		
		StreamSocketConfiguration socketConf = new StreamSocketConfiguration();
		socketConf.setSO_LINGER(1000);
		socketConf.setSO_SNDBUF(8899);
		socketConf.setSO_RCVBUF(9988);
		socketConf.setTCP_NODELAY(true);
		socketConf.setSO_KEEPALIVE(true);
		
		BlockingConnection connection = new BlockingConnection(server.getLocalAddress().getHostName(), server.getLocalPort(), socketConf);
		connection.setAutoflush(false);
		
		SocketOptions options = connection.getSocketOptions();
		
		Assert.assertNotNull(options);
		Assert.assertTrue(options.getOption(SocketOptions.SO_LINGER).equals(1000));
		Assert.assertTrue(options.getOption(SocketOptions.SO_SNDBUF).equals(8899));
		Assert.assertTrue(options.getOption(SocketOptions.SO_RCVBUF).equals(9988));
		Assert.assertTrue(options.getOption(SocketOptions.TCP_NODELAY).equals(true));
		Assert.assertTrue(options.getOption(SocketOptions.SO_KEEPALIVE).equals(true));
		
		connection.write("Hello" + DELIMITER);
		connection.flush();

		connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		
		connection.close();
		server.close();
		
		Assert.assertNull(error, error);
	}


	
	@Test public void testServerSideOption() throws Exception {
		TestHandler hdl = new TestHandler();
		
		StreamSocketConfiguration socketConf = new StreamSocketConfiguration();
		socketConf.setSO_RCVBUF(5555);
		socketConf.setTCP_NODELAY(true);
		socketConf.setSO_KEEPALIVE(true);
		
		IMultithreadedServer server = new MultithreadedServer(socketConf, hdl);
		StreamUtils.start(server);	
		
		BlockingConnection connection = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
		connection.setAutoflush(false);
		
		
		QAUtil.sleep(300);
		SocketOptions serverSideOptions = hdl.options;
		Assert.assertTrue(serverSideOptions.getOption(SocketOptions.TCP_NODELAY).equals(true));
		Assert.assertTrue(serverSideOptions.getOption(SocketOptions.SO_KEEPALIVE).equals(true));
		Assert.assertTrue(serverSideOptions.getOption(SocketOptions.SO_REUSEADDR).equals(true));
				
		
		connection.write("Hello" + DELIMITER);
		connection.flush();

		connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		
		connection.close();
		server.close();
		
		Assert.assertNull(error, error);
	}


	
	@Test public void testServerSideOptionNoReuseAdress() throws Exception {
		TestHandler hdl = new TestHandler();
		
		StreamSocketConfiguration socketConf = new StreamSocketConfiguration();
		socketConf.setSO_REUSEADDR(false);
		
		IMultithreadedServer server = new MultithreadedServer(socketConf, hdl);
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
