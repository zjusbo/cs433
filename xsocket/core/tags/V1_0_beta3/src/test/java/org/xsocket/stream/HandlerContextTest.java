// $Id: HandlerContextTest.java 765 2007-01-15 07:13:48Z grro $
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

import org.xsocket.Resource;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IConnectHandler;
import org.xsocket.stream.IConnectionScoped;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;




/**
*
* @author grro@xsocket.org
*/
public final class HandlerContextTest {
	
	
	private IHandlerServerContext hdlCtx = null;
	
	

	@Test public void testSimple() throws Exception {
		IMultithreadedServer server = new MultithreadedServer(new ServerHandler());
		new Thread(server).start();


		IBlockingConnection connection = new BlockingConnection("127.0.0.1", server.getLocalPort());
		connection.receiveStringByDelimiter("o");

		Assert.assertNotNull(hdlCtx);

		connection.close();
		server.shutdown();
	}

	
	private final class ServerHandler implements IConnectHandler, IConnectionScoped {

		@Resource
		private IHandlerServerContext ctx;
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.write("werwrwrer o");
			hdlCtx = ctx;
			return false;
		}

		@Override
		public Object clone() throws CloneNotSupportedException {
			return super.clone();
		}
	}
}
