// $Id: LifeCycleTest.java 429 2006-12-04 16:00:43Z grro $
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
package org.xsocket.server;


import org.junit.Assert;
import org.junit.Test;




/**
*
* @author grro@xsocket.org
*/
public final class LifeCycleTest  {

	private int port = 9321;
	private MultithreadedServer server = null;
	
	private InstanceTestHandler instanceTestHdl = new InstanceTestHandler();
	
	private void startServer() throws Exception {
		do {
			try {
				server = new MultithreadedServer(port);
			
				server.setDispatcherPoolSize(3);
				server.setWorkerPoolSize(12);
				server.setReceiveBufferPreallocationSize(4096);
				server.setConnectionTimeoutSec(60 * 60);
				server.setIdleTimeoutSec(10 * 60);
				
				server.setHandler(instanceTestHdl);
					
				Thread t = new Thread(server);
				t.start();
			
				do {
					try {
						Thread.sleep(100);
					} catch (InterruptedException ignore) { }
				} while (!server.isRunning());
				
			} catch (Exception be) {
				port++;
				if (server != null) {
					server.shutdown();
					server = null;
				}
			}
		} while (server == null);
	}
	 

	 private void stopServer() {
		 server.shutdown();
	 }


	@Test public void testInstanceScoped() throws Exception {
		startServer();
		
		Assert.assertTrue(instanceTestHdl.isInitCalled());
		Assert.assertFalse(instanceTestHdl.isDestroyedCalled());

		InstanceTestHandler hdl2 = new InstanceTestHandler();
		Assert.assertFalse(hdl2.isInitCalled());
		Assert.assertFalse(hdl2.isDestroyedCalled());
		
		server.setHandler(hdl2);
		Assert.assertTrue(instanceTestHdl.isInitCalled());
		Assert.assertTrue(instanceTestHdl.isDestroyedCalled());
		
		Assert.assertTrue(hdl2.isInitCalled());
		Assert.assertFalse(hdl2.isDestroyedCalled());
		
		stopServer();
		Assert.assertTrue(hdl2.isInitCalled());
		Assert.assertTrue(hdl2.isDestroyedCalled());
	}


	
	private static final class InstanceTestHandler implements ILifeCycle {

		private boolean initCalled = false;
		private boolean detroyCalled = false;
		
		public void onInit() {
			initCalled = true;
		}

		public void onDestroy() {
			detroyCalled = true;
		}
		
		public boolean isInitCalled() {
			return initCalled;
		}
		
		public boolean isDestroyedCalled() {
			return detroyCalled;
		}
	}
}
