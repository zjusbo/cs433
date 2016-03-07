// $Id: EchoHandler.java 882 2007-02-07 07:46:06Z grro $
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
import java.lang.management.ManagementFactory;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;


import javax.management.ObjectName;
import javax.management.StandardMBean;

import org.xsocket.Resource;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.ILifeCycle;
import org.xsocket.stream.INonBlockingConnection;



/**
*
* @author grro@xsocket.org
*/
final class EchoHandler implements IDataHandler, ILifeCycle {
	
	private static final Logger LOG = Logger.getLogger(EchoHandler.class.getName());
	
	static final String DELIMITER = "\r";
	
	private static boolean TIME_TRACE = false;
	
	@Resource
	private IHandlerServerContext ctx;


	private EchoHandlerManagement management = null;  
	private ObjectName mbeanName = null;
	

	
	public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
		ByteBuffer[] buffer = connection.readByteBufferByDelimiter(DELIMITER);
		connection.write(buffer);
		connection.write(DELIMITER);
		System.out.print(".");
		management.registerCall();

		return true;
	}
	


	
	public void onInit() {
		
        try {
        	management = new EchoHandlerManagement();
        	StandardMBean mbean = new StandardMBean(management , EchoHandlerMBean.class);
        	mbeanName = new ObjectName(ctx.getApplicationDomain() + ":type=EchoHandler,name=EchoHandler");
			ManagementFactory.getPlatformMBeanServer().registerMBean(mbean, mbeanName);
        } catch (Exception mbe) {
        	LOG.warning("error " + mbe.toString() + " occured while registering mbean");
        }
	}
	
	
	public void onDestroy() {
        try {
        	ManagementFactory.getPlatformMBeanServer().unregisterMBean(mbeanName);
        } catch (Exception mbe) {
        	LOG.warning("error " + mbe.toString() + " occured while deregistering mbean");
        }
	}

		
	private static final class EchoHandlerManagement implements EchoHandlerMBean {
		
		private long calls = 0; 
		private long time = System.currentTimeMillis();
		
		synchronized void registerCall() {
			calls++;
		}

		public long getNumberOfCallsTotalSinceReset() {
			return calls;
		}
		
		public long getNumberOfCallsPerSecSinceReset() {
			return (long) ((((double) calls) * 1000) / getElapsedTimeSinceReset());
		}

		synchronized public void reset() {
			calls = 0;
			time = System.currentTimeMillis();
		}
		
		public int getElapsedTimeSinceReset() {
			return (int) (System.currentTimeMillis() - time);
		}
	}
	
	
}
