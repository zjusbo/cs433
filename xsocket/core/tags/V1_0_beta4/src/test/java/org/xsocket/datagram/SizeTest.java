// $Id: SizeTest.java 910 2007-02-12 16:56:19Z grro $
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
package org.xsocket.datagram;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.TestUtil;



/**
*
* @author grro@xsocket.org
*/
public final class SizeTest {
	
	private int packetSize = 512;
	
	
	
	@Test public void testSmallerSize() throws Exception {		
		Handler hdl = new Handler();
		IEndpoint serverEndpoint = new NonBlockingEndpoint(hdl, packetSize, 3);
		IEndpoint clientEndpoint = new NonBlockingEndpoint();
		
		byte[] request = TestUtil.generatedByteArray(packetSize - 100);
		Packet packet = new Packet(serverEndpoint.getLocalSocketAddress(), request);

		
		clientEndpoint.send(packet);


		TestUtil.sleep(500);
		
		Assert.assertTrue(hdl.received.get(0).length == packetSize);

		clientEndpoint.close();
		serverEndpoint.close();
	}
	
	
	
	@Test public void testLargerSize() throws Exception {		
		Handler hdl = new Handler();
		IEndpoint serverEndpoint = new NonBlockingEndpoint(hdl, packetSize, 3);
		IEndpoint clientEndpoint = new NonBlockingEndpoint();
		
		byte[] request = TestUtil.generatedByteArray(packetSize + 100);
		Packet packet = new Packet(serverEndpoint.getLocalSocketAddress(), request);
	
		clientEndpoint.send(packet);


		TestUtil.sleep(500);
		
		Assert.assertTrue(hdl.received.get(0).length == packetSize);

		clientEndpoint.close();
		serverEndpoint.close();
	}
	
	
	
	private final class Handler implements IDatagramHandler {
		
		private List<byte[]> received = new ArrayList<byte[]>();
		
		public boolean onData(IEndpoint localEndpoint, Packet packet) throws IOException {
			
			received.add(packet.readBytes());
			
			return true;
		}		
	}
}
