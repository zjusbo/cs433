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



import java.io.IOException;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;

import javax.management.JMException;

import static org.junit.Assert.*;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.IConnection.FlushMode;
import org.xsocket.datagram.IConnectedEndpoint;

 

/**
*
* @author grro@xsocket.org
*/
public final class GroupEndpoint { 
		
	private boolean isClosed = false;
	
	private GroupMember groupMember = null;

	private IServer server = null;
	private GUID guid = null; 	
	
	GroupEndpoint(InetAddress groupAddress, int groupPort) throws IOException {
		server = new Server(new Handler());
		ConnectionUtils.start(server);
		
		guid = new GUID(server.getLocalAddress(), server.getLocalPort());
		
		groupMember = new GroupMember(groupAddress, groupPort, 64, guid.toString(), server.getWorkerpool(), new GroupMemberListener());
		try {
			GroupMemberMBeanProxyFactory.createAndRegister(groupMember, "groupendpoint");
		} catch (JMException e) {
			e.printStackTrace();
		}
	}

	
	
	public void write(String test) {
		System.out.println("write");
	}
	
	
	public String readStringByDelimiter(String delimiter) {
		return null;
	}
	
	public void close() {
		if (!isClosed) {
			isClosed = true;
			try {
				groupMember.close();
			} catch (Exception ignore) { }
			
			try {
				server.close();
			} catch (Exception ignore) { }
		}
	}
	
	
	private final class GroupMemberListener implements IGroupMemberListener {
		public void onInit() {
		}
		
		public void onDestroy() {
			close();
		}
		
		public void onEnterConsistentState() {
			// TODO Auto-generated method stub
			
		}
		
		public void onEnterInconsistentState() {
			// TODO Auto-generated method stub
			
		}
	}
	
	
	
	private final class Handler implements IConnectHandler, IDataHandler {
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			connection.setFlushmode(FlushMode.ASYNC);
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

			return true;
		}
	}	
}
