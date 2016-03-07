// $Id: LargeDataTransferTest.java 1023 2007-03-16 16:27:41Z grro $
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
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.MultithreadedServer;



/**
*
* @author grro@xsocket.org
*/
public final class MaxReadSizeExceededTest  {

	private static final String DELIMITER = "\r";


	@Test 
	public void testHandled() throws Exception {
		Handler hdl = new Handler();
		IMultithreadedServer server = new MultithreadedServer(hdl);
		StreamUtils.start(server);

		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);
		
		connection.write("This text ist to long");
		connection.write(DELIMITER);
		connection.flush();
		QAUtil.sleep(300);
		
		Assert.assertTrue(hdl.exceptionOccured);
		Assert.assertTrue(connection.isOpen());
		
		connection.close();
		server.close();
	}

	
	@Test 
	public void testUnhandled() throws Exception {
		PlainHandler hdl = new PlainHandler();
		IMultithreadedServer server = new MultithreadedServer(hdl);
		StreamUtils.start(server);

		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);
		
		connection.write("This text ist to long");
		connection.write(DELIMITER);
		connection.flush();
		QAUtil.sleep(300);
		
		Assert.assertFalse(connection.isOpen());
		
		server.close();
	}

	
	private static final class Handler implements IDataHandler {
		
		private boolean exceptionOccured = false;
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			try {
				connection.readStringByDelimiter(DELIMITER, 5);
			} catch (MaxReadSizeExceededException exception) {
				exceptionOccured = true;
			}
			
			return true;
		}
	}
	
	private static final class PlainHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			connection.readStringByDelimiter(DELIMITER, 5);
			return true;
		}
	}	

}
