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
import java.util.concurrent.Executors;
import java.util.logging.Level;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.stream.IServer;
import org.xsocket.stream.Server;




/**
*
* @author grro@xsocket.org
*/
public final class PeerNodeTest {

	private static final String GROUP = "224.0.0.1";
	private static final int PORT = 8975;
	
	@Test public void testSimple() throws Exception {
		//TestUtil.setLogLevel("org.xsocket.group.PeerNode", Level.FINE);
	/*	
		PeerNodeImpl peer1 = new PeerNodeImpl(InetAddress.getByName(GROUP), PORT);
		QAUtil.sleep(100);
		
		Assert.assertTrue(peer1.getInitialGroupSize() == 1);
		Assert.assertTrue(peer1.getGroupMemberSize() == 1);
		
		
		PeerNodeImpl peer2 = new PeerNodeImpl(InetAddress.getByName(GROUP), PORT);
		QAUtil.sleep(100);
		
		Assert.assertTrue(peer2.getInitialGroupSize() == 2);
		Assert.assertTrue(peer2.getGroupMemberSize() == 2);
		Assert.assertTrue(peer1.getGroupMemberSize() == 2);

		
		
		PeerNodeImpl peer3 = new PeerNodeImpl(InetAddress.getByName(GROUP), PORT);
		QAUtil.sleep(100);
		
		Assert.assertTrue(peer3.getInitialGroupSize() == 3);
		Assert.assertTrue(peer1.getGroupMemberSize() == 3);
		Assert.assertTrue(peer2.getGroupMemberSize() == 3);
		Assert.assertTrue(peer3.getGroupMemberSize() == 3);
		
		
		
		peer1.close();
		QAUtil.sleep(100);
		
		Assert.assertTrue(peer2.getGroupMemberSize() == 2);
		Assert.assertTrue(peer3.getGroupMemberSize() == 2);
		
		
		peer2.close();
		QAUtil.sleep(100);
		
		Assert.assertTrue(peer3.getGroupMemberSize() == 1);
		
		peer3.close();*/
	}
	
	
	private static final class PeerNodeImpl extends PeerNode {
		
		private IServer server = null;
		
		public PeerNodeImpl(InetAddress address, int port) throws IOException {
			super(address, port);
			
			server = new Server(null);
			init(Executors.newCachedThreadPool(), server.getLocalAddress(), server.getLocalPort(), 5000);
		}
		
		@Override
		public void close() throws IOException {
			super.close();
			
			server.close();
		}
	}
}
