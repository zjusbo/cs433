// $Id: ConcurrentHandlerTest.java 764 2007-01-15 06:26:17Z grro $
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
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.TestUtil;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IConnectionScoped;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;




/**
*
* @author grro@xsocket.org
*/
public final class ConcurrentHandlerTest {

	private static final long MAXTIME = 2 * 1000; 

	private int running = 0;


	@Test public void testMixed() throws Exception {
//		TestUtil.setLogLevel(Level.FINE);
		
		final IMultithreadedServer server = new MultithreadedServer(new TestHandler());
		new Thread(server).start();
		server.setReceiveBufferPreallocationSize(3);

		List<Thread> threads = new ArrayList<Thread>();
		for (int j = 0; j < 10; j++) {
			Thread t = new Thread() {
				@Override
				public void run() {
					running++;
					try {
						IBlockingConnection connection = new BlockingConnection("127.0.0.1", server.getLocalPort());
					
						for (int i = 0; i < 20; i++) {
							connection.write((int) 4);
							int response = connection.receiveInt();
							Assert.assertTrue(response == 4);
						}
						connection.close();
					} catch (Exception e) {
						// do nothing
					}
					
					running--;
				}	
			};
			
			threads.add(t);
		}
		
		for (Thread thread : threads) {
			thread.start();
		}		
		
		long start = System.currentTimeMillis();
		TestUtil.sleep(500);
		
		do {
			TestUtil.sleep(100);
		} while ((running > 0) && ((System.currentTimeMillis() - MAXTIME) > start));
		
		if ((System.currentTimeMillis() - MAXTIME) > start) {
			Assert.fail("max time exceeded");
		}
		server.shutdown();
	}
	
	
	
	String generateData(int size) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < size; i++) {
			sb.append(i);
		}
		return sb.toString();
	}



	private static class TestHandler implements IDataHandler, IConnectionScoped {

		private int concurrent = 0;
		

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			try {
				concurrent++;
				Assert.assertTrue(concurrent == 1);
				int i = connection.readInt();
				connection.write(i);

			} finally {
				concurrent--;
			}
			
			return true;
		}

	
		@Override
		public Object clone() throws CloneNotSupportedException {
			return super.clone();
		}
	}
}
