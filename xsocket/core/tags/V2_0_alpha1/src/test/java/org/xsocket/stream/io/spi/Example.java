// $Id: FlushOnCloseTest.java 1017 2007-03-15 08:03:05Z grro $
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
package org.xsocket.stream.io.spi;


import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.concurrent.Executors;

import org.junit.Assert;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IConnectHandler;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IDisconnectHandler;
import org.xsocket.stream.IServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.Server;
import org.xsocket.stream.StreamUtils;
import org.xsocket.stream.io.spi.IServerIoProvider;




/**
*
* @author grro@xsocket.org
*/
public final class Example {
	
	private static final String DELIMITER = "\r\n";
	private static final String KILL = "KILL";
	
	public static void main(String... args) throws Exception {
		new Example().testSimple();
		new Example().testSingleThreaded();
		
		System.out.println("it works!");
	}
	
	public void testSimple() throws Exception {
		
		// set example nio provider
		System.setProperty(IServerIoProvider.PROVIDER_CLASSNAME_KEY, ExampleServerIoProvider.class.getName());
		
		
		// the remaing stay the same 
		Handler handler = new Handler();
		IServer server = new Server(0, handler, Executors.newCachedThreadPool());
		StreamUtils.start(server);
		 
		IBlockingConnection connection = new BlockingConnection("127.0.0.1", server.getLocalPort());
		String greeting = connection.readStringByDelimiter(DELIMITER);
		Assert.assertEquals("Helo", greeting);
		
		connection.write("it`s me, the client" + DELIMITER);
		String echo = connection.readStringByDelimiter(DELIMITER);
		
		Assert.assertEquals("it`s me, the client", echo);
		
		connection.write(KILL + DELIMITER);
		QAUtil.sleep(200);
		
		Assert.assertTrue(handler.isDisconnected);

		connection.close();
		server.close();
	}

	
	public void testSingleThreaded() throws Exception {
		
		// set example nio provider
		System.setProperty(IServerIoProvider.PROVIDER_CLASSNAME_KEY, ExampleServerIoProvider.class.getName());

		
		Handler handler = new Handler();
		IServer server = new Server(0, handler, null);
		StreamUtils.start(server);
		
		QAUtil.sleep(200);
		
		IBlockingConnection connection = new BlockingConnection("127.0.0.1", server.getLocalPort());
		String greeting = connection.readStringByDelimiter(DELIMITER);
		Assert.assertEquals("Helo", greeting);
		
		connection.write("it`s me, the client" + DELIMITER);
		String echo = connection.readStringByDelimiter(DELIMITER);
		
		Assert.assertEquals("it`s me, the client", echo);
		
		connection.write(KILL + DELIMITER);
		QAUtil.sleep(200);
		
		Assert.assertTrue(handler.isDisconnected);

		connection.close();
		server.close();
	}

	
	
	private static final class Handler implements IConnectHandler, IDataHandler, IDisconnectHandler {
		
		private boolean isDisconnected = false;
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.write("Helo" + DELIMITER);
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			String message = connection.readStringByDelimiter(DELIMITER);
			
			if (message.equals(KILL)) {
				connection.close();
			} else {
				connection.write(message + DELIMITER);
			}
			
			return true;
		}
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			isDisconnected = true;
			return true;
		}
		
	}
}
