// $Id: ConcurrentHandlerTest.java 1432 2007-07-03 17:15:47Z grro $
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
import org.xsocket.QAUtil;
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
public final class ConcurrentCallbackCallsTest {



	@Test 
	public void testSimple() throws Exception {
		
		TestHandler hdl = new TestHandler();
		final IMultithreadedServer server = new MultithreadedServer(hdl);
		StreamUtils.start(server);

		
		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort());
					
		for (int i = 0; i < 20; i++) {
			connection.write((int) 4);
		}
		QAUtil.sleep(300);

		connection.close();
		server.close();

		Assert.assertTrue(hdl.errors.isEmpty());
	}
	



	private static class TestHandler implements IDataHandler{

		private int concurrent = 0;
		private List<String> errors = new ArrayList<String>();
		

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			try {
				concurrent++;
				
				if (concurrent != 1) {
					errors.add(concurrent + " concurrent calls");
				}
				int i = connection.readInt();
				connection.write(i);
				QAUtil.sleep(100);

			} finally {
				concurrent--;
			}
			
			return true;
		}
	}
}
