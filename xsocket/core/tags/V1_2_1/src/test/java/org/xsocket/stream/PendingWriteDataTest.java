// $Id: LifeCycleTest.java 1379 2007-06-25 08:43:44Z grro $
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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.MultithreadedServer;
import org.xsocket.stream.IConnection.FlushMode;





/**
*
* @author grro@xsocket.org
*/
public final class PendingWriteDataTest  {


	@Test 
	public void testDelayWrite() throws Exception {
		IMultithreadedServer server = new MultithreadedServer(new EchoHandler());
		StreamUtils.start(server);
		
		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort());
		connection.setFlushmode(FlushMode.ASYNC);
		connection.setWriteTransferRate(10);
		
		byte[] data = QAUtil.generateByteArray(100);
		connection.write(data);
		
		QAUtil.sleep(1500);
		
		int pendingSize = connection.getPendingWriteDataSize();
		Assert.assertTrue((pendingSize > 80) && (pendingSize < 100));
		
		connection.close();
		server.close();
	}
}
