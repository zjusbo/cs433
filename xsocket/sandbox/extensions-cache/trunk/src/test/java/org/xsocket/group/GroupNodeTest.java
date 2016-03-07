// $Id: GroupNodeTest.java 1007 2007-03-08 07:15:30Z grro $
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
package org.xsocket.group;

import java.io.IOException;
import java.net.InetAddress;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.DataConverter;
import org.xsocket.group.management.GroupEndpointMBeanProxyFactory;




/**
*
* @author grro@xsocket.org
*/
public final class GroupNodeTest {

  

	@Test public void testSimple() throws Exception {
/*		
		int broadcastPort = 9977;
		InetAddress broadcastGroupname = InetAddress.getByName("224.0.0.1");

		
		// start jmx server
		int port = 9922;
		JmxServer jmxServer = new JmxServer();
		jmxServer.start("testmanagement", port);

		// create server and register it as mbean
		GroupEndpoint<ObjectMessage<String>> node1 = new GroupEndpoint<ObjectMessage<String>>(broadcastGroupname, broadcastPort);
		GroupEndpointMBeanProxyFactory.createAndRegister(node1, "testGroup");
		
		GroupEndpoint<ObjectMessage<String>> node2 = new GroupEndpoint<ObjectMessage<String>>(broadcastGroupname, broadcastPort);
		GroupEndpointMBeanProxyFactory.createAndRegister(node2, "testGroup");
		QAUtil.sleep(100);

		Assert.assertTrue(node2.getInitialGroupSize() == 2);
		Assert.assertTrue(node1.getGroupMemberSize() == 2);
		node2.close();
		QAUtil.sleep(100);
		
		Assert.assertTrue(node1.getGroupMemberSize() == 1);
		
		
		GroupEndpoint<ObjectMessage<String>> node3 = new GroupEndpoint<ObjectMessage<String>>(broadcastGroupname, broadcastPort);
		GroupEndpointMBeanProxyFactory.createAndRegister(node3, "testGroup");
		QAUtil.sleep(100);
		Assert.assertTrue(node1.getGroupMemberSize() == 2);

		
		GroupEndpoint<ObjectMessage<String>> node4 = new GroupEndpoint<ObjectMessage<String>>(broadcastGroupname, broadcastPort);
		GroupEndpointMBeanProxyFactory.createAndRegister(node4, "testGroup");
		QAUtil.sleep(100);
		Assert.assertTrue(node1.getGroupMemberSize() == 3);

		
		ObjectMessage<String> msg = node1.createObjectMessage("test");;
		long start = System.currentTimeMillis();
		node1.send(msg);
		long elapsed = System.currentTimeMillis() - start;
		System.out.println("sending object message (elapsed=" + DataConverter.toFormatedDuration(elapsed) + ")");
	
			
		ObjectMessage<String> msg2 = node3.receiveMessage(1000);
		Assert.assertTrue(msg.getObject().equals(msg2.getObject()));
		node4.receiveMessage(1000);
		Assert.assertTrue(msg.getObject().equals(msg2.getObject()));
			
		QAUtil.sleep(100);
	
		
		node1.close();
		node3.close();
		node4.close();
		
		jmxServer.stop();*/
	}
	
	
	
	private static final class Handler implements IGroupEndpointHandler<ObjectMessage<String>> {
		private ObjectMessage<String> msg = null;

		public boolean onMessage(IGroupEndpoint<ObjectMessage<String>> endpoint) throws IOException {
			msg = endpoint.receiveMessage();
			return true;
		}
	}
}
