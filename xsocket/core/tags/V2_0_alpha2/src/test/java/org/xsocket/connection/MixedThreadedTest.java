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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.DataConverter;
import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.IConnection.FlushMode;



/**
*
* @author grro@xsocket.org
*/
public final class MixedThreadedTest {

	private static final int ON_CONNECT_PAUSE = 300;
	private static final String DELIMITER = "\r";

	
	@Test 
	public void testMixThreaded() throws Exception {

		Handler serverHandler = new Handler(); 
		IServer server = new Server(serverHandler);
		server.setFlushMode(FlushMode.ASYNC);
		ConnectionUtils.start(server);

		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(true);
		
		QAUtil.sleep(100);
		Assert.assertEquals(1, serverHandler.countOnConnect);
		Assert.assertEquals(0, serverHandler.countOnData);
		Assert.assertFalse(serverHandler.threadName.startsWith("xDispatcher"));
		
		QAUtil.sleep(ON_CONNECT_PAUSE * 2);
		connection.write("test" + DELIMITER);
		
		QAUtil.sleep(100);

		Assert.assertEquals(1, serverHandler.countOnConnect);
		Assert.assertEquals(1, serverHandler.countOnData);
		Assert.assertTrue(serverHandler.threadName.startsWith("xDispatcher"));
		
		connection.close();
		server.close();
	}
	
	

	@Test 
	public void testMixThreaded2() throws Exception {

		Handler serverHandler = new Handler(); 
		IServer server = new Server(serverHandler);
		server.setFlushMode(FlushMode.ASYNC);
		ConnectionUtils.start(server);

		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(true);
		
		QAUtil.sleep(100);
		Assert.assertEquals(1, serverHandler.countOnConnect);
		Assert.assertEquals(0, serverHandler.countOnData);
		Assert.assertFalse(serverHandler.threadName.startsWith("xDispatcher"));
		
		
		QAUtil.sleep(ON_CONNECT_PAUSE / 2);
		connection.write("test" + DELIMITER);
		
		QAUtil.sleep(ON_CONNECT_PAUSE);
		Assert.assertEquals(1, serverHandler.countOnConnect);
		Assert.assertEquals(1, serverHandler.countOnData);
		Assert.assertFalse(serverHandler.threadName.startsWith("xDispatcher"));
		
		connection.close();
		server.close();
	}
	

	

	@Execution(Execution.Mode.NONTHREADED)
	private static final class Handler implements IConnectHandler, IDataHandler {
		
		private String threadName = null;
		
		private int countOnConnect = 0;
		private int countOnData = 0;

		@Execution(Execution.Mode.MULTITHREADED)
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			threadName = Thread.currentThread().getName();
			countOnConnect++;
			
			QAUtil.sleep(ON_CONNECT_PAUSE);
			return true;
		}
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			threadName = Thread.currentThread().getName();
			countOnData++;
			
			connection.write(connection.readStringByDelimiter(DELIMITER) + DELIMITER);
			return true;
		}
	}
}
