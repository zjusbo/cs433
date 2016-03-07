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
import java.net.BindException;
import java.nio.BufferUnderflowException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;




/**
*
* @author grro@xsocket.org
*/
public final class TwiceStartTest {
	

	@Test 
	public void testDefault() throws Exception {
		
		ServerHandler srvHdl = new ServerHandler();
		IServer server1 = new Server(srvHdl);
		server1.start();
		
		QAUtil.sleep(1000);
		
        try {
            new Server(server1.getLocalPort(), srvHdl);
            Assert.fail("BindException expected");
        } catch (BindException expected) {  }
        server1.close();
	}
	
	
	@Ignore
	@Test 
    public void testReuse() throws Exception {
        
        ServerHandler srvHdl = new ServerHandler();
        IServer server1 = new Server(srvHdl);
        server1.start();
        
        QAUtil.sleep(1000);
        
        Map<String, Object> options = new HashMap<String, Object>();
        options.put(IConnection.SO_REUSEADDR, true);
        
        IServer server2 = new Server(server1.getLocalPort(), options, srvHdl);
        server2.start();
            
        server1.close();
        server2.close();
    }
    	
	

	
	private static final class ServerHandler implements IConnectHandler {
		
		private INonBlockingConnection connection = null;

		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			this.connection = connection;
			return true;
		}

		INonBlockingConnection getConection() {
			return connection;
		}
	}
}
