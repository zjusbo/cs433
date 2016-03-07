// $Id: DecoupledReceiveAndConsumeTest.java 439 2006-12-06 06:43:30Z grro $
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
import java.nio.BufferUnderflowException;


import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.xsocket.IBlockingConnection;
import org.xsocket.BlockingConnection;
import org.xsocket.INonBlockingConnection;





/**
*
* @author grro@xsocket.org
*/
public final class DecoupledReceiveAndConsumeTest extends AbstractServerTest {

	
	private static int sleeptime = 500;
	private static INonBlockingConnection serverSideConnection = null;

	
	private IMultithreadedServer server = null;
	
	
	@Before public void setUp() {
		server = createServer(7959, new TestHandler()); 
	}
	

	@After public void tearDown() {
		server.shutdown();
	}




	@Test public void testSimple() throws Exception {
		setUp(); // maven bug work around

		IBlockingConnection bc = new BlockingConnection("127.0.0.1", server.getPort());
		
		// send first package & wait 
		bc.write(generateData(1200));
		try {
			Thread.sleep(sleeptime);
		} catch (InterruptedException ignore) { }
		
		
		// send second one
		bc.write(generateData(1200));
		
		try {
			Thread.sleep(sleeptime);
		} catch (InterruptedException ignore) { }
		
		
		// no, all packages sholud be received by the server 
		Assert.assertTrue(serverSideConnection.getNumberOfAvailableBytes() > 1500);
		
		bc.close();

		tearDown(); // maven bug work around
	}
	
	
	private byte[] generateData(int size) {
		byte[] data = new byte[size];
		
		for (byte b : data) {
			b = 4;
		}
		
		return data;
	}



	private static final class TestHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			serverSideConnection = connection;
			
			// simlulate blocking
			try {
				Thread.sleep(sleeptime * 3);
			} catch (InterruptedException ignore) {  }
		
			return true;
		}
	}
}
