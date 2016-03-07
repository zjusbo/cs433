// $Id: ChainedRecordsCallTest.java 1423 2007-07-02 06:56:18Z grro $
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
import java.nio.ByteBuffer;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;




/**
*
* @author grro@xsocket.org
*/
public final class ChainedRecordsCallTest {


	private static final String DELIMITER = ";";
	
	
	
	@Test 
	public void testSimple() throws Exception {
		final IMultithreadedServer server = new MultithreadedServer(new Handler());
		StreamUtils.start(server);

		IBlockingConnection bc = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
		bc.setAutoflush(false);
		
		String request = "REQUEST";
		
		bc.write("RE");
		bc.flush();
		
		bc.write("QUEST");
		bc.flush();
		bc.write(DELIMITER);
		
		bc.write(request);
		bc.write(DELIMITER);
		
		bc.write(request);
		bc.write(DELIMITER);
		bc.flush();
		
		
		String response = bc.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		Assert.assertEquals(request, response);
		
		response = bc.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		Assert.assertEquals(request, response);
		
		response = bc.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		Assert.assertEquals(request, response);
		
		bc.close();

		server.close();
	}



	private class Handler implements IDataHandler, IConnectHandler {
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			return true;
		}

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			
			
			if (connection.getNumberOfAvailableBytes() < 4) {
				return true;
			}
			
			
			ByteBuffer[] data = connection.readByteBufferByDelimiter(DELIMITER, Integer.MAX_VALUE);
			connection.write(data);
			connection.write(DELIMITER);
			
			connection.flush();
			return true;
		}
	}
}
