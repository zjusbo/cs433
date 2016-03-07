// $Id: ConcurrentHandlerTest.java 439 2006-12-06 06:43:30Z grro $
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

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import org.xsocket.IBlockingConnection;
import org.xsocket.BlockingConnection;
import org.xsocket.INonBlockingConnection;




/**
*
* @author grro@xsocket.org
*/
public final class ConcurrentHandlerTest extends AbstractServerTest {

	private static IMultithreadedServer server = null;
	
	private static int concurrent = 0;
	private static boolean finished = false;
	
	
	@BeforeClass public static void setUp() throws Exception {
		server = createServer(7372, new TestHandler()); 
	}
	

	@AfterClass public static void tearDown() {
		server.shutdown();
	}


	@Test public void testMixed() throws Exception {
		setUp(); // maven bug work around

		final IBlockingConnection connection = new BlockingConnection("127.0.0.1", server.getPort());
		
		Thread t = new Thread() {
			@Override
			public void run() {
				
				try {
					Thread.sleep(500);
				} catch (InterruptedException ignore) { }
				
				for (int i = 0; i < 20; i++) {
					try {
						connection.write((int) 4);
					} catch (Exception e) {
						e.printStackTrace();
					}
				
				}
				try {
					connection.write((int) 0);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}	
		};
		
		
	
		t.start();
		do {
			if (concurrent> 1) {
				Assert.fail("multiple concurrent handler call occured");
			}
			
		} while (!finished);
		
		
		connection.close();
		tearDown(); // maven bug work around
	}
	
	
	
	String generateData(int size) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < size; i++) {
			sb.append(i);
		}
		return sb.toString();
	}



	private static class TestHandler implements IDataHandler, IConnectionScoped {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			concurrent++;
			
			
			try {
				boolean b = false;
				
				do {
					try {
						int i = connection.readInt();
						if (i == 0) {
							b = true;
						}
					} catch (BufferUnderflowException bue) {
						// simulate blocking
						try {
							Thread.sleep(500);
						} catch (InterruptedException ignore) { }
						return true;
					}	
					
					if (b) {
						finished = true;
					}
				} while (!finished);
				
				return true;

			} finally {
				concurrent--;
			}
		}

	
		@Override
		public Object clone() throws CloneNotSupportedException {
			return super.clone();
		}
	}
}
