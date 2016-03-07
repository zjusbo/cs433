// $Id: DecoupledReceiveAndConsumeTest.java 899 2007-02-11 13:49:26Z grro $
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
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.IoHandler;
import org.xsocket.stream.IoSocketHandler;
import org.xsocket.stream.MultithreadedServer;
import org.xsocket.stream.NonBlockingConnection;





/**
*
* @author grro@xsocket.org
*/
public final class DecoupledReceiveAndConsumeTest  {

	
	private static int sleeptime = 500;
	private static NonBlockingConnection serverSideConnection = null;

	
	


	@Test public void testSimple() throws Exception {
		IMultithreadedServer server = new MultithreadedServer(new TestHandler());
		new Thread(server).start();

		IBlockingConnection bc = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
		
		// send first package & wait 
		bc.write(generateData(600));

		try {
			Thread.sleep(sleeptime);
		} catch (InterruptedException ignore) { }
		
		
		// send second one
		bc.write(generateData(1200));
		
		try {
			Thread.sleep(sleeptime);
		} catch (InterruptedException ignore) { }
		
		
		// no, all packages sholud be received by the server
		Assert.assertTrue(getIoSocketHandler().getIncomingQueueSize() == 1200);
		
		bc.close();

		server.shutdown();
	}
	
	private IoSocketHandler getIoSocketHandler() {
		IoHandler ioHandler = serverSideConnection.getIOHandler();
		do {
			if (ioHandler instanceof IoSocketHandler) {
				return (IoSocketHandler) ioHandler;
			}
			ioHandler = ioHandler.getSuccessor();
		} while (ioHandler != null);
		
		return null;
	}
	
	
	private byte[] generateData(int size) {
		byte[] data = new byte[size];
		
		for (byte b : data) {
			b = 4;
		}
		
		return data;
	}



	private static final class TestHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			serverSideConnection = (NonBlockingConnection) connection;
		
			// simlulate blocking
			try {
				Thread.sleep(1000000);
			} catch (InterruptedException ignore) {  }
		
			return true;
		}
	}
}
