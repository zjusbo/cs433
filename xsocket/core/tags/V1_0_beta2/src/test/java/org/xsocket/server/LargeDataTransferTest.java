// $Id: LargeDataTransferTest.java 448 2006-12-08 14:55:13Z grro $
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
package org.xsocket.server;

import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import junit.framework.JUnit4TestAdapter;
import junit.textui.TestRunner;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.xsocket.IBlockingConnection;
import org.xsocket.BlockingConnection;
import org.xsocket.LogFormatter;
import org.xsocket.TestUtil;


/**
*
* @author grro@xsocket.org
*/
public final class LargeDataTransferTest extends AbstractServerTest {


	
	private IMultithreadedServer server = null;
	
	@Before public void setUp() {
		server = createServer(8772, new EchoHandler()); 
	}
	

	@After public void tearDown() {
		server.shutdown();
	}



	@Test public void testData() throws Exception {
		setUp(); // maven bug work around

		send("127.0.0.1", server.getPort(), 7000000);
		System.gc();
		tearDown(); // maven bug work around
	}

	
	
	private void send(String host, int port, int dataSizeInBytes) throws IOException{
		
		IBlockingConnection connection = new BlockingConnection(host, port);
		String request = new String(TestUtil.generatedByteArray(dataSizeInBytes));
		
		for (int i = 0; i < 1; i++) {
			connection.write(request);
			connection.write(EchoHandler.DELIMITER);
			String response = connection.receiveStringByDelimiter(EchoHandler.DELIMITER);
			Assert.assertEquals(request, response);
		}		
	}

		

	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(LargeDataTransferTest.class);
	}


	public static void main (String... args) {
		Logger logger = Logger.getLogger("org.xsocket");
		logger.setLevel(Level.WARNING);
		ConsoleHandler hdl = new ConsoleHandler();
		hdl.setLevel(Level.FINE);
		hdl.setFormatter(new LogFormatter());
		logger.addHandler(hdl);

		TestRunner.run(suite());
	}
}
