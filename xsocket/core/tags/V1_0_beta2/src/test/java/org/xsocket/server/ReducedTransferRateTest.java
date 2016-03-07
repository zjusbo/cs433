// $Id: DelayTest.java 412 2006-11-20 18:02:50Z grro $
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
public final class ReducedTransferRateTest extends AbstractServerTest {

	
	
	private IMultithreadedServer server = null;
	
	@Before public void setUp() {
		server = createServer(8272, new TestHandler()); 
	}
	

	@After public void tearDown() {
		server.shutdown();
	}





	@Test public void testData() throws Exception {
		setUp(); // maven bug work around
/*
		IBlockingConnection connection = new BlockingConnection("127.0.0.1", server.getPort());
		
		send(connection, 3, 2000, 3500);  // 8 byte / 3 byteSec -> 2.6 sec 
		send(connection, 10, 200, 1400);  // 8 byte / 10 byteSec -> 0,8 sec
		send(connection, 5, 1500, 2500);  // 8 byte / 5 byteSec -> 1,6 sec
		 
		connection.close();
	*/		
		tearDown(); // maven bug work around
	}

	
	private void send(IBlockingConnection connection, int i, long min, long max) throws IOException {	
		long start = System.currentTimeMillis();
		// repeat the above operation, but delay is already set
		connection.write((long) i);
		connection.receiveLong();
		
		long elapsed = System.currentTimeMillis() - start;
		
		System.out.println("elapsed time " + elapsed + " (min=" + min + ", max=" + max + ")");
		Assert.assertTrue("elapsed time " + elapsed + " out of range (min=" + min + ", max=" + max + ")"
				          , (elapsed > min) && (elapsed < max));
	}
	


	private static class TestHandler implements IDataHandler {

		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			long delay = connection.readLong();
			connection.setWriteTransferRate((int) delay);
			connection.write((long) delay);
					
			return true;
		}
	}
}
