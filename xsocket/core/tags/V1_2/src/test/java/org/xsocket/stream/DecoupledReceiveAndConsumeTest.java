// $Id$
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
import java.lang.reflect.Method;
import java.nio.BufferUnderflowException;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;
import org.xsocket.stream.NonBlockingConnection;
import org.xsocket.stream.io.spi.IIoHandler;



 

/**
*
* @author grro@xsocket.org
*/
public final class DecoupledReceiveAndConsumeTest  {


	private static final String DELIMITER = "\n";
	private static final int SLEEPTIME = 300;
		
	


	@Test 
	public void testSimple() throws Exception {
		TestHandler hdl = new TestHandler();
		IMultithreadedServer server = new MultithreadedServer(hdl);
		StreamUtils.start(server);

		IBlockingConnection bc = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
		bc.setAutoflush(false);
		
		// send first package & wait 
		bc.write(QAUtil.generateByteArray(600));
		bc.write(DELIMITER);
		bc.flush();

		QAUtil.sleep(SLEEPTIME);
		
		
		// send second one
		bc.write(QAUtil.generateByteArray(1200));
		bc.write(DELIMITER);
		bc.flush();
		
		QAUtil.sleep(SLEEPTIME);
		
		// now, all packages should be received by the server (600 alreday procced) 
		Assert.assertTrue("consumed data size is " + hdl.readSize + " but should be 600", hdl.readSize == 600);
		
		IIoHandler ioHandler = hdl.connection.getIoHandler();
		
		Method getSuccessorMeth = ioHandler.getClass().getMethod("getSuccessor", new Class[] { });
		getSuccessorMeth.setAccessible(true);
		
		IIoHandler socketHandler = (IIoHandler) getSuccessorMeth.invoke(ioHandler, new Object[0]);
		Method getReceivedQueueSize = socketHandler.getClass().getMethod("getReceiveQueueSize", new Class[] { });
		getReceivedQueueSize.setAccessible(true);
		
		int receiveQueueSize = (Integer) getReceivedQueueSize.invoke(socketHandler, new Object[0]);
		
		Assert.assertTrue("receivedAndonConsumend data size is " + receiveQueueSize + " but should be " + (1200 + DELIMITER.length()), receiveQueueSize == (1200 + DELIMITER.length()));
		
		bc.close();

		server.close();
	}
	

	private static final class TestHandler implements IConnectHandler, IDataHandler {
		
		private int readSize = 0;
		private NonBlockingConnection connection = null;
		
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			this.connection = (NonBlockingConnection) connection;
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			byte[] bytes = connection.readBytesByDelimiter(DELIMITER);
			readSize = bytes.length;
			
			// simlulate blocking
			try {
				Thread.sleep(1000000);
			} catch (InterruptedException ignore) {  }
		

			
			return true;
		}
	}
}
