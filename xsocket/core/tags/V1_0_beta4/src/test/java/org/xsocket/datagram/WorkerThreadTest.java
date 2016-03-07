// $Id: WorkerThreadTest.java 910 2007-02-12 16:56:19Z grro $
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.TestUtil;



/**
*
* @author grro@xsocket.org
*/
public final class WorkerThreadTest {
	
	private static final int PACKET_SIZE = 512;

	
	
	@Test public void testGlobalWorkerPool() throws Exception {

		Handler hdl = new Handler();
		
		List<IEndpoint> endpoints = new ArrayList<IEndpoint>();
		List<IEndpoint> clientEndpoints = new ArrayList<IEndpoint>();
		
		
		int serverCount = 3;
		int callLoops = 30;
		
		
		for (int i = 0; i < serverCount; i++) {
			// new server endpoint by using global default worker pool
			IEndpoint serverEndpoint = new NonBlockingEndpoint(hdl, PACKET_SIZE, 0);
			endpoints.add(serverEndpoint);
			
			IEndpoint clientEndpoint = new NonBlockingConnectedEndpoint(serverEndpoint.getLocalSocketAddress());
			endpoints.add(clientEndpoint);
			clientEndpoints.add(clientEndpoint);
		}
		
		
		byte[] request = TestUtil.generatedByteArray(PACKET_SIZE);
		
		for (int i = 0; i < callLoops; i++) {
			for (IEndpoint clientEndpoint : clientEndpoints) {
				Packet packet = new Packet(request);
				clientEndpoint.send(packet);
			}
		}
		
		int l = 0;
		do {
			TestUtil.sleep(500);
			l++;
		} while ((hdl.requestCount < (callLoops * serverCount)) && (l < 15));
		
				
		for (IEndpoint endpoint : endpoints) {
			endpoint.close();
		}
		
		if (hdl.requestCount != (callLoops * serverCount)) {
			System.out.println("expected request=" + (callLoops * serverCount) + ". occured=" + hdl.requestCount);
		}

		
		Assert.assertTrue(hdl.requestCount == (callLoops * serverCount));
		Assert.assertTrue(hdl.threadIds.size() <= IEndpoint.GLOBAL_WORKER_POOL_SIZE);
	}
	

	
	
	
	@Test public void testExclusiveWorkerPool() throws Exception {

		Handler hdl = new Handler();
		
		List<IEndpoint> endpoints = new ArrayList<IEndpoint>();
		List<IEndpoint> clientEndpoints = new ArrayList<IEndpoint>();
		
		
		int serverCount = 2;
		int callLoops = 30;
		
		int workerPoolSize = 3;
		
		for (int i = 0; i < serverCount; i++) {
			// new server endpoint by using global default worker pool
			IEndpoint serverEndpoint = new NonBlockingEndpoint(hdl, PACKET_SIZE, workerPoolSize);
			endpoints.add(serverEndpoint);
			
			IEndpoint clientEndpoint = new NonBlockingConnectedEndpoint(serverEndpoint.getLocalSocketAddress());
			endpoints.add(clientEndpoint);
			clientEndpoints.add(clientEndpoint);
		}
		
		
		byte[] request = TestUtil.generatedByteArray(PACKET_SIZE);
		
		for (int i = 0; i < callLoops; i++) {
			for (IEndpoint clientEndpoint : clientEndpoints) {
				Packet packet = new Packet(request);
				clientEndpoint.send(packet);
			}
		}
		
		
		int l = 0;
		do {
			TestUtil.sleep(500);
			l++;
		} while ((hdl.requestCount < (callLoops * serverCount)) && (l < 15));
		
		for (IEndpoint endpoint : endpoints) {
			endpoint.close();
		}

		if (hdl.requestCount != (callLoops * serverCount)) {
			System.out.println("expected request=" + (callLoops * serverCount) + ". occured=" + hdl.requestCount);
		}

		Assert.assertTrue(hdl.requestCount == (callLoops * serverCount));
		Assert.assertTrue((hdl.threadIds.size() <= (serverCount * workerPoolSize)) && (hdl.threadIds.size() > IEndpoint.GLOBAL_WORKER_POOL_SIZE));
	}

	
	
	
	
	
	
	private final class Handler implements IDatagramHandler {
		
		private int requestCount = 0;
		private final Set<Long> threadIds = new HashSet<Long>(); 
		
		public boolean onData(IEndpoint localEndpoint, Packet packet) throws IOException {
			threadIds.add(Thread.currentThread().getId());
			requestCount++;
			
			return true;
		}		
	}
}
