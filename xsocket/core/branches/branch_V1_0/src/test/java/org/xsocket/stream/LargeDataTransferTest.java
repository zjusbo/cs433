// $Id: LargeDataTransferTest.java 1108 2007-03-29 16:44:02Z grro $
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
import java.net.InetAddress;
import java.nio.ByteBuffer;


import org.junit.Assert;
import org.junit.Test;

import org.xsocket.QAUtil;
import org.xsocket.DataConverter;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.EchoHandler;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.MultithreadedServer;



/**
*
* @author grro@xsocket.org
*/
public final class LargeDataTransferTest  {



	@Test public void testData() throws Exception {
		// TestUtil.setLogLevel(Level.FINE);
		
		IMultithreadedServer server = new MultithreadedServer(new EchoHandler());
		new Thread(server).start();

		send(server.getLocalAddress(), server.getLocalPort(), 700000);
		System.gc();

		server.close();
	}

	
	
	private void send(InetAddress address, int port, int dataSizeInBytes) throws IOException{
		
		IBlockingConnection connection = new BlockingConnection(address, port);
				
		for (int i = 0; i < 1; i++) {
			ByteBuffer request = QAUtil.generatedByteBuffer(dataSizeInBytes);
			
			long start = System.currentTimeMillis();
			connection.write(request);
			connection.write(EchoHandler.DELIMITER);
			connection.flush();
			
			ByteBuffer[] response = connection.readByteBufferByDelimiter(EchoHandler.DELIMITER, Integer.MAX_VALUE);
			long elapsed = System.currentTimeMillis() - start;
			
			request.clear();
			Assert.assertTrue(DataConverter.toString(request).equals(DataConverter.toString(response)));
			 
			System.out.println(DataConverter.toFormatedBytesSize(dataSizeInBytes) + " bytes has been send and returned (elapsed time " + DataConverter.toFormatedDuration(elapsed) + ")");
		}		
	}
}
