/*
 *  Copyright (c) xsocket.org, 2006 - 2008. All rights reserved.
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



import java.net.InetAddress;
import java.util.logging.Level;

import static org.junit.Assert.*;

import org.junit.Assert;
import org.junit.Test;


/**
*
* @author grro@xsocket.org
*/
public final class GroupNodeJoinAndLeaveTest {



	@Test
	public void testGoodCaseJoinAndLeave() throws Exception {
		int broadcastPort = 9977;
		InetAddress broadcastGroupname = InetAddress.getByName("224.0.0.1");


		// start jmx server
		int port = 9927;
		JmxServer jmxServer = new JmxServer();
		jmxServer.start("testmanagement", port);

		// create server and register it as mbean
		GroupMember node1 = new GroupMember(broadcastGroupname, broadcastPort, 64, "0.0.45435");
		GroupMemberMBeanProxyFactory.createAndRegister(node1, "testGroup");
		Assert.assertTrue("node1 should be in state stable", node1.isGroupConsistent());
		Assert.assertTrue("node1 initial group size should be 1", node1.getInitialGroupSize() == 1);

		GroupMember node2 = new GroupMember(broadcastGroupname, broadcastPort, 64, "0.0.454377");
		GroupMemberMBeanProxyFactory.createAndRegister(node2, "testGroup");
		QAUtil.sleep(100);

		assertTrue("node1 should be in state stable", node1.isGroupConsistent());
		assertTrue("node2 should be in state stable", node2.isGroupConsistent());
		assertTrue("node2 initial group size should be 2", node2.getInitialGroupSize() == 2);
		assertTrue("node1 group size should be 2", node1.getGroupSize() == 2);

		node2.close();
		QAUtil.sleep(100);

		assertTrue(node1.isGroupConsistent());
		assertTrue(node1.getGroupSize() == 1);


		GroupMember node3 = new GroupMember(broadcastGroupname, broadcastPort, 64, "0.0.4543rder5");
		GroupMemberMBeanProxyFactory.createAndRegister(node3, "testGroup");
		QAUtil.sleep(100);

		assertTrue(node1.isGroupConsistent());
		assertTrue(node3.isGroupConsistent());
		assertTrue(node1.getGroupSize() == 2);


		GroupMember node4 = new GroupMember(broadcastGroupname, broadcastPort, 64, "0.0.454444");
		GroupMemberMBeanProxyFactory.createAndRegister(node4, "testGroup");
		QAUtil.sleep(100);

		assertTrue(node1.isGroupConsistent());
		assertTrue(node3.isGroupConsistent());
		assertTrue(node4.isGroupConsistent());
		assertTrue(node1.getGroupSize() == 3);

		QAUtil.sleep(100);


		node1.close();
		node3.close();
		node4.close();

		jmxServer.stop();
	}
}
