// $Id: DelayedSSLTest.java 1236 2007-05-13 13:01:23Z grro $
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


import org.junit.Assert;
import org.junit.Test;

import org.xsocket.SSLTestContextFactory;
import org.xsocket.QAUtil;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IConnectHandler;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;



/**
*
* @author grro@xsocket.org
*/
public final class SSLdelayTest {

	
	private static final int WRITE_RATE = 3;
	private static final String DELIMITER = System.getProperty("line.separator");
	


	@Test public void testXSocket() throws Exception {
		//TestUtil.setLogLevel(Level.FINE);
		
		IMultithreadedServer sslTestServer = new MultithreadedServer(0, new SSLHandler(),  true, new SSLTestContextFactory().getSSLContext());
		StreamUtils.start(sslTestServer);

		
		IBlockingConnection connection = new BlockingConnection(sslTestServer.getLocalAddress(), sslTestServer.getLocalPort(), new SSLTestContextFactory().getSSLContext(), true);
		connection.setAutoflush(true);
		
		
		connection.write("test" + DELIMITER);
		long start = System.currentTimeMillis(); 
		String response = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		QAUtil.assertTimeout(System.currentTimeMillis() - start, 1800, 3700);  // 6 bytes / 3 bytesSec -> 2 sec 
		Assert.assertEquals("test", response);
		
		connection.write("test2" + DELIMITER);
		start = System.currentTimeMillis(); 
		response = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		QAUtil.assertTimeout(System.currentTimeMillis() - start, 2000, 3800);  // 7 bytes / 3 bytesSec -> 2,3 sec
		Assert.assertEquals("test2", response);
		
		connection.close();
		
		sslTestServer.close();
	}
	

	
	private static final class SSLHandler implements IDataHandler, IConnectHandler {
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setWriteTransferRate(WRITE_RATE);
			return false;
		}
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			String word = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
			connection.write(word + DELIMITER);
			return true;
		}
	}
}
