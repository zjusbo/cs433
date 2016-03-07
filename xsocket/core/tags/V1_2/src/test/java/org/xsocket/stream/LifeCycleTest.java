// $Id: LifeCycleTest.java 1522 2007-07-15 10:51:35Z grro $
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
import java.nio.BufferUnderflowException;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.ILifeCycle;
import org.xsocket.QAUtil;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.MultithreadedServer;





/**
*
* @author grro@xsocket.org
*/
public final class LifeCycleTest  {



	@Test 
	public void testInstanceScoped() throws Exception {
		InstanceTestHandler instanceTestHdl = new InstanceTestHandler();
		IMultithreadedServer server = new MultithreadedServer(instanceTestHdl);
		StreamUtils.start(server);

		
		Assert.assertTrue(instanceTestHdl.isInitCalled());
		Assert.assertFalse(instanceTestHdl.isDestroyedCalled());

		InstanceTestHandler hdl2 = new InstanceTestHandler();
		Assert.assertFalse("init for new handler shouldn't have been called", hdl2.isInitCalled());
		Assert.assertFalse("destroyed for new handler shouldn't have been called", hdl2.isDestroyedCalled());
		
		server.setHandler(hdl2);
		Assert.assertTrue("init for old handler should have been called", instanceTestHdl.isInitCalled());
		Assert.assertTrue("destroyed for old handler should have been called", instanceTestHdl.isDestroyedCalled());
		
		Assert.assertTrue("init for new handler should have been called", hdl2.isInitCalled());
		Assert.assertFalse("destroyed for new handler shouldn't have been called", hdl2.isDestroyedCalled());
		
		server.close();
		QAUtil.sleep(200);
		
		Assert.assertTrue("init for new handler should have been called", hdl2.isInitCalled());
		Assert.assertTrue("destroyed for new handler should have been called", hdl2.isDestroyedCalled());
	}


	
	private static final class InstanceTestHandler implements IDataHandler, ILifeCycle {

		private boolean initCalled = false;
		private boolean detroyCalled = false;
		
		public void onInit() {
			initCalled = true;
		}

		public void onDestroy() {
			detroyCalled = true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			return true;
		}
		
		public boolean isInitCalled() {
			return initCalled;
		}
		
		public boolean isDestroyedCalled() {
			return detroyCalled;
		}
	}
}
