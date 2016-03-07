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

import javax.net.ssl.SSLContext;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.SSLTestContextFactory;
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
public final class SSLTest {

	private static final String DELIMITER = System.getProperty("line.separator");
	


	

	@Test public void testXSocket() throws Exception {
		SSLContext sslCtx = new SSLTestContextFactory().getSSLContext();
		System.out.println("got ssl context for " + sslCtx.getProtocol());
		IMultithreadedServer sslTestServer = new MultithreadedServer(0, new SSLHandler(), true, sslCtx);
		new Thread(sslTestServer).start();

		
		IBlockingConnection connection = new BlockingConnection(sslTestServer.getLocalAddress(), sslTestServer.getLocalPort(), new SSLTestContextFactory().getSSLContext(), true);
		
		connection.write("test" + DELIMITER);
		connection.flush();
		
		String response = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		connection.close();
		
		Assert.assertEquals("test", response);

		sslTestServer.close();
	}

	
	

	
	private static final class SSLHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			String word = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
			connection.write(word + DELIMITER);
			return true;
		}
	}
}
