// $Id: SSLTest.java 1023 2007-03-16 16:27:41Z grro $
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



/**
*
* @author grro@xsocket.org
*/
public final class StreamUtilsTest {



	@Test 
	public void testSimple() throws Exception {
		IServer server = new Server(new Handler());
		StreamUtils.start(server);
		
		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);
		String request = "hallo";
		
		connection.markWritePosition();
		connection.write((int) 0);
		
		int written = connection.write(request);
		connection.resetToWriteMark();
		connection.write(written);
		connection.flush();

		String response = connection.readStringByLength(written);
		
		Assert.assertEquals(request, response);
		
		connection.close();
		server.close();
	}
	
	private static final class Handler implements IConnectHandler, IDataHandler {
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			int length = StreamUtils.validateSufficientDatasizeByIntLengthField(connection);
			String text = connection.readStringByLength(length);
		       
			connection.write(text);
			connection.flush();
			return false;
		}
	}
}
