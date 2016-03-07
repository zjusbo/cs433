// $Id: ManagementTest.java 1108 2007-03-29 16:44:02Z grro $
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
package org.xsocket.stream.management;

import java.io.IOException;

import javax.management.InstanceNotFoundException;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;




import org.junit.Assert;
import org.junit.Test;
import org.xsocket.JmxServer;
import org.xsocket.QAUtil;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;



/**
*
* @author grro@xsocket.org
*/
public final class ManagementTest {
	
	
	@Test public void testSimple() throws Exception  {

		// start jmx server
		int port = 9922;
		JmxServer jmxServer = new JmxServer();
		JMXServiceURL url = jmxServer.start("testmanagement", port);

		
		
		// create server and register it as mbean
		MultithreadedServer server = new MultithreadedServer(new ServerHandler());
		new Thread(server).start();
		MultithreadedServerMBeanProxyFactory.createAndRegister(server, "test");
		
		
		// get client jmx connection to server
        JMXConnector connector = JMXConnectorFactory.connect(url);

        
		// make some tests
		server.setReceiveBufferPreallocationSize(555);
		QAUtil.sleep(100);
		
        ObjectName serverObjectName = new ObjectName("test" + ":type=MultithreadedServer,name=" + server.getLocalAddress().getHostName() + "." + server.getLocalPort());
        Assert.assertNotNull(connector.getMBeanServerConnection().getObjectInstance(serverObjectName));
        Assert.assertTrue(555 == (Integer) connector.getMBeanServerConnection().getAttribute(serverObjectName, "ReceiveBufferPreallocationSize"));

        
        server.setReceiveBufferPreallocationSize(6666);
		QAUtil.sleep(100);

        Assert.assertTrue(6666 == (Integer) connector.getMBeanServerConnection().getAttribute(serverObjectName, "ReceiveBufferPreallocationSize"));


        
        
		server.setDispatcherPoolSize(1);
		server.setReceiveBufferPreallocationSize(90000);

		IBlockingConnection connection = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
		connection.write("rtrt");
		connection.flush();
		connection.close();
		QAUtil.sleep(100);
		
    
        
        connector.close();
		jmxServer.stop();
	}
	
	
	private static class ServerHandler implements IDataHandler {

		public boolean onData(INonBlockingConnection connection) throws IOException {
			connection.write(connection.readAvailable());
			return true;
		}
	}
}
