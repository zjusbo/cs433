// $Id: LocalAddressTest.java 1108 2007-03-29 16:44:02Z grro $
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

import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.MultithreadedServer;





/**
*
* @author grro@xsocket.org
*/
public final class LocalAddressTest  {



	@Test public void testSimple() throws Exception {
		
		for (int i = 0; i < 30; i++)  {
			IMultithreadedServer server = new MultithreadedServer(QAUtil.getRandomLocalAddress(), 0, new Handler());
			new Thread(server).start();

			IBlockingConnection connection = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
			connection.write("trtr");
			connection.flush();
			
			server.close();
		}
	}

	
	private static final class Handler implements IDataHandler {
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			connection.write(connection.readAvailable());
			return true;
		}
	}
}
