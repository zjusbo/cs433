// $Id: ManagementTest.java 1485 2007-07-11 06:34:39Z grro $
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

import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;




import org.junit.Assert;
import org.junit.Test;
import org.xsocket.JmxServer;
import org.xsocket.QAUtil;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;
import org.xsocket.stream.MultithreadedServerMBeanProxyFactory;
import org.xsocket.stream.StreamUtils;



/**
*
* @author grro@xsocket.org
*/
public final class ManagementTest {


	@Test
	public void testSimple() throws Exception  {

		// start jmx server
		int port = 9929;
		JmxServer jmxServer = new JmxServer();
		JMXServiceURL url = jmxServer.start("testmanagement", port);



		// create server and register it as mbean
		MultithreadedServer server = new MultithreadedServer(InetAddress.getLocalHost(), 9988, new ServerHandler());
		StreamUtils.start(server);
		MultithreadedServerMBeanProxyFactory.createAndRegister(server, "test");


		// get client jmx connection to server
        JMXConnector connector = JMXConnectorFactory.connect(url);


        IBlockingConnection connection = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
        connection.write("helo");


		// make some tests
		server.setIdleTimeoutSec(60);
		QAUtil.sleep(100);

        ObjectName serverObjectName = new ObjectName("test" + ":type=MultithreadedServer,name=" + server.getLocalAddress().getHostName() + "." + server.getLocalPort());
        Assert.assertNotNull(connector.getMBeanServerConnection().getObjectInstance(serverObjectName));
        Assert.assertTrue(60 == (Integer) connector.getMBeanServerConnection().getAttribute(serverObjectName, "IdleTimeoutSec"));


        server.setIdleTimeoutSec(90);
		QAUtil.sleep(100);

        Assert.assertTrue(90 == (Integer) connector.getMBeanServerConnection().getAttribute(serverObjectName, "IdleTimeoutSec"));

        connector.close();

        connection.close();
        server.close();

		jmxServer.stop();
	}


	private static class ServerHandler implements IDataHandler {

		public boolean onData(INonBlockingConnection connection) throws IOException {
			connection.write(connection.readAvailable());
			return true;
		}
	}
}
