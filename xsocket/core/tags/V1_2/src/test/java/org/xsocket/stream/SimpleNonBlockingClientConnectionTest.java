// $Id: ConcurrentHandlerTest.java 974 2007-03-02 17:38:15Z grro $
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

import org.junit.Test;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;




/**
*
* @author grro@xsocket.org
*/
public final class SimpleNonBlockingClientConnectionTest {

	private static String DELIMITER ="\r\n";


	@Test 
	public void testByUsingHandler() throws Exception {
		
		// start the server
		IMultithreadedServer server = new MultithreadedServer(new EchoHandler());
		StreamUtils.start(server);
		
		
		// define the client side handler
		IDataHandler clientSideHandler = new IDataHandler() {
			public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
				connection.setAutoflush(false);
				String response = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
				System.out.println("response: " + response);
				return true;
			}
		};

		
		INonBlockingConnection con = new NonBlockingConnection(server.getLocalAddress(), server.getLocalPort(), clientSideHandler);
		con.setAutoflush(false);
		con.setDefaultEncoding("ISO-8859-1");

		// send the request 
		System.out.println("sending the request");
		con.write("NICK maneh" + DELIMITER);
		con.write("USER asdasd"+ DELIMITER);
		con.flush();

		// the request will be handled by cleint event handler, just wait some millis
		try {
			Thread.sleep(500);
		} catch (InterruptedException ignore) { }

		con.close();
		server.close();
	}
	
	
	@Test 
	public void testByUsingPooling() throws Exception {
		
		// start the server
		IMultithreadedServer server = new MultithreadedServer(new EchoHandler());
		StreamUtils.start(server);
		
		INonBlockingConnection con = new NonBlockingConnection(server.getLocalAddress(), server.getLocalPort());
		con.setAutoflush(false);
		con.setDefaultEncoding("ISO-8859-1");

		// send the request 
		System.out.println("sending the request");
		con.write("NICK maneh" + DELIMITER);
		con.write("USER asdasd"+ DELIMITER);
		con.flush();

		// pooling, while getting the request
		// (the event driven approach is often a better choice that this pooling-driven approach!)
		boolean received = false;
		do {
			try {
				String response = con.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
				System.out.println("response: " + response);
				received = true;
			} catch (BufferUnderflowException bue) {
				// not enough data received -> wait some millis and try it again
				try {
					Thread.sleep(50);
				} catch (InterruptedException ignore) { }
			}
		} while (!received);

		con.close();
		server.close();
	}
	
	


	private static class EchoHandler implements IDataHandler {

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			connection.setAutoflush(false);
			connection.write(connection.readByteBufferByDelimiter(DELIMITER, Integer.MAX_VALUE));
			connection.write(DELIMITER);
			
			connection.flush();
			return true;
		}
	}
}
