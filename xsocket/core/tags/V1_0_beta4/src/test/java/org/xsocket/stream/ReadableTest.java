// $Id: ReadableTest.java 899 2007-02-11 13:49:26Z grro $
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
import java.nio.ByteBuffer;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.DataConverter;
import org.xsocket.TestUtil;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;



/**
*
* @author grro@xsocket.org
*/
public final class ReadableTest {

	private static final String DELIMITER = "\r\n";


	@Test public void testLengthField() throws Exception {
		IMultithreadedServer server = new MultithreadedServer(new ServerHandler());
		new Thread(server).start();
	
		
		IBlockingConnection bc = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
		bc.setAutoflush(false);
		
		byte[] request = TestUtil.generatedByteArray(20);
		bc.write(request);
		bc.write(DELIMITER);
		bc.flush();
		
		ByteBuffer readBuffer = ByteBuffer.allocate(request.length);
		
		bc.read(readBuffer);
		byte[] response = DataConverter.toBytes(readBuffer);
		
		Assert.assertTrue(TestUtil.isEquals(request, response));
	
		
		bc.close();
		server.shutdown();
	}




	
	
	private static final class ServerHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException {
			connection.write(connection.readByteBufferByDelimiter(DELIMITER));			
			return true;
		}
	}
}
