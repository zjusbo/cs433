/*
 *  Copyright (c) xsocket.org, 2006-2008. All rights reserved.
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
package org.xsocket.connection;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.logging.Level;

import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;




/**
*
* @author grro@xsocket.org
*/
public final class ThrottledReadTest {


	@Test
	public void testSimple() throws Exception {
/*		
		QAUtil.setLogLevel("org.xsocket.connection.spi.IoThrottledReadHandler", Level.FINE);

		Handler hdl = new Handler();
		IServer server = new Server(hdl);
		ConnectionUtils.start(server);
		server.setReadTransferRate(50);

		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());

		con.write(QAUtil.generateDirectByteBuffer(200));
		QAUtil.sleep(100);
		System.out.println("received " + hdl.getCountReceived());

		
		con.write(QAUtil.generateDirectByteBuffer(200));
		QAUtil.sleep(2000);
		System.out.println("received " + hdl.getCountReceived());
		
		con.write(QAUtil.generateDirectByteBuffer(200));
		QAUtil.sleep(2000);
		System.out.println("received " + hdl.getCountReceived());
		
		con.write(QAUtil.generateDirectByteBuffer(200));
		QAUtil.sleep(2000);
		System.out.println("received " + hdl.getCountReceived());
		

		
		con.close();
		server.close();*/
	}

	
	private static final class Handler implements IDataHandler {
		
		private int countReceived = 0;
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			byte[] data = connection.readBytesByLength(connection.available());
			countReceived += data.length;
			return true;
		}
		
		
		int getCountReceived() {
			return countReceived;
		}
	}
}
