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
import org.junit.Test;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;


/**
*
* @author grro@xsocket.org
*/
public final class GroupNodeWriteReadTest {

  

	@Test
	public void testWriteRead() throws Exception {		
		int broadcastPort = 9987;
		InetAddress broadcastGroupname = InetAddress.getByName("224.0.0.1");

		
		// start jmx server
		int port = 9912;
		JmxServer jmxServer = new JmxServer();
		jmxServer.start("testmanagement", port);


		GroupEndpoint node1 = new GroupEndpoint(broadcastGroupname, broadcastPort);

		GroupEndpoint node2 = new GroupEndpoint(broadcastGroupname, broadcastPort);

		GroupEndpoint node3 = new GroupEndpoint(broadcastGroupname, broadcastPort);

/*
		node1.write("helo\r\n");
		
		
		QAUtil.sleep(300);
		String response2 = node2.readStringByDelimiter("\r\n");
		String response3 = node3.readStringByDelimiter("\r\n");
		
		node1.write("helo2\r\n");
	*/	
		node1.close();
		node2.close();
		node3.close();
		
		jmxServer.stop();
	}	
}
