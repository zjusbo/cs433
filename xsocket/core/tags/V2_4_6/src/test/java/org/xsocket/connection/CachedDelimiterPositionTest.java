/*
 *  Copyright (c) xsocket.org, 2006 - 2009. All rights reserved.
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


import org.junit.Assert;
import org.junit.Test;

import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;



/**
*
* @author grro@xsocket.org
*/
public final class CachedDelimiterPositionTest  {
	
	private static final String DELIMITER = "\r\n"; 


	@Test 
	public void testSimple() throws Exception {
		Handler hdl = new Handler();
		IServer server = new Server(hdl);
		server.start();

		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		
		con.write("123456");
		QAUtil.sleep(1000);

		Assert.assertEquals(6, hdl.available);

		
		con.write("78901");
		QAUtil.sleep(1000);
		
		Assert.assertEquals(11, hdl.available);
		
		
		con.write("234");
		QAUtil.sleep(1000);
		
		Assert.assertEquals(14, hdl.available);
		
		con.write(DELIMITER);
		QAUtil.sleep(1000);
	
		Assert.assertEquals(0, hdl.available);
		Assert.assertTrue(hdl.found);
		
		con.close();
		server.close();
		
	}

		
	
	
	private static final class Handler implements IDataHandler {
		
		private boolean found = false;
		private int available = 0; 
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			available = connection.available();
			
			connection.readStringByDelimiter(DELIMITER);
			
			available = connection.available();
			found = true;
			
			return true;
		}		
	}	
}
