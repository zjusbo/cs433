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
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.util.HashMap;


import org.junit.Assert;
import org.junit.Test;

import org.xsocket.QAUtil;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;



/**
*
* @author grro@xsocket.org
*/
public final class ClientLocalAddressTest  {

    
    
    @Test 
    public void testBlocking() throws Exception {
        
        int localPort = 11223;
        
        IServer server = new Server(new ServerHandler());
        server.start();

        BlockingConnection con = new BlockingConnection(new InetSocketAddress("localhost", server.getLocalPort()), new InetSocketAddress("localhost", localPort), true, Integer.MAX_VALUE, new HashMap<String, Object>(), null, false);
        Assert.assertEquals(localPort, con.getLocalPort());
        
        con.write("test\r\n");
        Assert.assertEquals("test", con.readStringByDelimiter("\r\n"));
        
        con.close();
        server.close();
    }
    

    
    

	@Test 
	public void testNonBlocking() throws Exception {
	    
        int localPort = 11228;
	    
		IServer server = new Server(new ServerHandler());
		server.start();

		INonBlockingConnection con = new NonBlockingConnection(new InetSocketAddress("localhost", server.getLocalPort()), new InetSocketAddress("localhost", localPort), null, true, Integer.MAX_VALUE, new HashMap<String, Object>(), null, false);
		Assert.assertEquals(localPort, con.getLocalPort());
		
		con.write("test\r\n");
		
		do {
		    QAUtil.sleep(200);
		} while (con.available() < 6);
		
		Assert.assertEquals("test", con.readStringByDelimiter("\r\n"));
		
		con.close();
		server.close();
	}
	
	
	
	
	
	
	
	private static final class ServerHandler implements IConnectHandler, IDataHandler {
	
	    private INonBlockingConnection con;
	    
	    
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			con = connection;
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
		    connection.write(connection.readByteBufferByLength(connection.available()));
		    
			return true;
		}		
		
		INonBlockingConnection getCon() {
		    return con;
		}
	}	
}
