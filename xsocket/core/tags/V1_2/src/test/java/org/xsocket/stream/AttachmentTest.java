// $Id: DisconnectTest.java 1156 2007-04-15 08:10:39Z grro $
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



import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IConnectHandler;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;




/**
*
* @author grro@xsocket.org
*/
public final class AttachmentTest {


	@Test 
	public void testSimple() throws Exception {
		Handler hdl = new Handler();
		final IMultithreadedServer server = new MultithreadedServer(hdl);
		StreamUtils.start(server);

		IBlockingConnection bc = new BlockingConnection(server.getLocalAddress(),server.getLocalPort());
		bc.setAutoflush(false);
		
		bc.setDefaultEncoding("US-ASCII");
		
		bc.write("24524232432");
		bc.flush();
		QAUtil.sleep(100);
		
		bc.write("22432");
		bc.flush();
		QAUtil.sleep(100);
		
		bc.write("224wwrwerwrrw32");
		bc.flush();
		QAUtil.sleep(100);
		
		
		bc.write("224w3253242");
		bc.flush();

		QAUtil.sleep(300);
		
		
		Assert.assertTrue(hdl.read == 42);
		
		bc.close();
		server.close();
	}

	
	private static final class Handler implements IDataHandler, IConnectHandler {
		
		private int read = 0;
		
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.attach(Integer.valueOf(0));
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			connection.setAutoflush(false);
			
			Integer i = (Integer) connection.attachment();
			int av = connection.getNumberOfAvailableBytes();
			
			connection.readBytesByLength(av);
			
			i += av;
			connection.attach(i);
			read = i;
			return true;
		}
	}
}
