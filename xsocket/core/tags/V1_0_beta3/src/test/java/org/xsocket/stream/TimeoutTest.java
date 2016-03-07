// $Id: TimeoutTest.java 775 2007-01-15 16:44:47Z grro $
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

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.ITimeoutHandler;
import org.xsocket.stream.MultithreadedServer;



/**
*
* @author grro@xsocket.org
*/
public final class TimeoutTest {

	private static boolean connectionTimeoutOccured = false;
	private static boolean idleTimeoutOccured = false;

	
	

	@Test public void testIdleTimeout() throws Exception {
		//TestUtil.setLogLevel(Level.FINE);
		
		IMultithreadedServer server = new MultithreadedServer(new TimeoutTestServerHandler());
		new Thread(server).start();

		connectionTimeoutOccured = false;
		idleTimeoutOccured = false;

		System.out.println("set idle timeout 2 sec");
		server.setIdleTimeoutSec(2);
		server.setConnectionTimeoutSec(15);

		IBlockingConnection con = new BlockingConnection("127.0.0.1", server.getLocalPort());
		con.write((int) 4);

		try {
			Thread.sleep(3 * 1000);
		} catch (InterruptedException ignore) {  }
		con.close();

		Assert.assertTrue(idleTimeoutOccured);
		Assert.assertFalse(connectionTimeoutOccured);
		
		server.shutdown();
	}


	@Test public void testConnectionTimeout() throws Exception {
		IMultithreadedServer server = new MultithreadedServer(new TimeoutTestServerHandler());
		new Thread(server).start();

		connectionTimeoutOccured = false;
		idleTimeoutOccured = false;

		System.out.println("set con timeout 2 sec");
		server.setIdleTimeoutSec(15);
		server.setConnectionTimeoutSec(2);

		IBlockingConnection con = new BlockingConnection("127.0.0.1", server.getLocalPort());
		con.write((int) 4);

		try {
			Thread.sleep(3 * 1000);
		} catch (InterruptedException ignore) {  }

		con.close();

		Assert.assertFalse(idleTimeoutOccured);
		Assert.assertTrue(connectionTimeoutOccured);
		server.shutdown();
	}

	
	@Test public void testIdleTimeoutWithoutSending() throws Exception {
		IMultithreadedServer server = new MultithreadedServer(new TimeoutTestServerHandler());
		new Thread(server).start();

		connectionTimeoutOccured = false;
		idleTimeoutOccured = false;

		System.out.println("set idle timeout 1 sec");
		server.setIdleTimeoutSec(1);
		server.setConnectionTimeoutSec(20);

		IBlockingConnection con = new BlockingConnection("127.0.0.1",server.getLocalPort());

		try {
			Thread.sleep(3 * 1000);
		} catch (InterruptedException ignore) {  }

		con.close();

		Assert.assertTrue(idleTimeoutOccured);
		Assert.assertFalse(connectionTimeoutOccured);
		server.shutdown();
	}



	private static class TimeoutTestServerHandler implements ITimeoutHandler {


		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
			System.out.println("con time out");
			connectionTimeoutOccured = true;
			connection.close();
			return true;
		}


		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
			System.out.println("idle time out");
			idleTimeoutOccured = true;
			connection.close();
			return true;
		}
	}
}
