/*
 * Copyright (c) xlightweb.org, 2006 - 2010. All rights reserved.
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
import java.nio.BufferUnderflowException;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.ILifeCycle;
import org.xsocket.QAUtil;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;





/**
*
* @author grro@xsocket.org
*/
public final class LifeCycleTest  {



	@Test 
	public void testInstanceScoped() throws Exception {
		InstanceTestHandler instanceTestHdl = new InstanceTestHandler();
		IServer server = new Server(instanceTestHdl);
		ConnectionUtils.start(server);

		
		Assert.assertTrue(instanceTestHdl.isInitCalled());
		Assert.assertFalse(instanceTestHdl.isDestroyedCalled());


		server.close();
		QAUtil.sleep(200);
		
		Assert.assertTrue("init for old handler should have been called", instanceTestHdl.isInitCalled());
		Assert.assertTrue("destroyed for old handler should have been called", instanceTestHdl.isDestroyedCalled());
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
