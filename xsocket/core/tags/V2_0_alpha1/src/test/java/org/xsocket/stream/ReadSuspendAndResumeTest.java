// $Id: MarkAndResetTest.java 1379 2007-06-25 08:43:44Z grro $
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
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.Server;




/**
*
* @author grro@xsocket.org
*/
public final class ReadSuspendAndResumeTest {

	private static final String DELIMITER = "\r";

	@Test 
	public void testSimple() throws Exception {
		IServer server = new Server(new Handler());
		StreamUtils.start(server);
		
		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort());
		connection.write("helo" + DELIMITER);
		
		QAUtil.sleep(100);
		Assert.assertEquals(connection.readStringByDelimiter(DELIMITER), "helo");
		
		connection.suspendRead();
		
		connection.write("helo again" + DELIMITER);
		QAUtil.sleep(200);
		
		Assert.assertEquals(connection.getNumberOfAvailableBytes(), 0);
		
		connection.resumeRead();
		QAUtil.sleep(200);
		
		Assert.assertEquals(connection.readStringByDelimiter(DELIMITER), "helo again");
		
		connection.close();
		server.close();
	}

	
	
	
	@Test 
	public void testServerSupsend() throws Exception {
		SuspendedHandler hdl = new SuspendedHandler();
		IServer server = new Server(hdl);
		StreamUtils.start(server);
		
		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort());
		for (int i = 0; i < 100; i++) {
			connection.write("helo" + DELIMITER);
			
			QAUtil.sleep(20);
		}	

		
		QAUtil.sleep(500);
		Assert.assertTrue(hdl.con.getNumberOfAvailableBytes() < 10);
		
		hdl.con.resumeRead();
		
		QAUtil.sleep(300);
		Assert.assertTrue(hdl.con.getNumberOfAvailableBytes() > 100);
		
		connection.close();
		server.close();
	}


	
	private static final class SuspendedHandler implements IConnectHandler, IDataHandler {

		private INonBlockingConnection con = null;
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.suspendRead();
			this.con = connection;
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			
			return true;
		}
	}


	private static final class Handler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			connection.write(connection.readStringByDelimiter(DELIMITER)+ DELIMITER);
			return true;
		}
		
	}
	
}
