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
package org.xsocket.bayeux.http;



import java.net.URL;
import java.util.logging.Level;

import org.junit.Test;
import org.xsocket.connection.http.client.HttpClient;

import dojox.cometd.Bayeux;



/**
*
* @author grro@xsocket.org
*/
public final class BayeuxClientTest {

	
	@Test
	public void testSimple() throws Exception {
		
		QAUtil.setLogLevel("org.xsocket.bayeux.http.BayeuxRemoteClient", Level.FINE);
		

		JettyCometdServer server = new JettyCometdServer("/cometd");
		Bayeux bayeux = server.getBayeux();
		
		server.start(9933);
		
		HttpClient httpClient = new HttpClient();
	//	httpClient.setInterceptor(new LogFilter());
		
		BayeuxRemoteClient bayeuxClient = new BayeuxRemoteClient(new URL("http://localhost:" + 9933 + "/cometd/cometD/rt"));
		bayeuxClient.open();
		
		
		bayeuxClient.subscribe("/test");
		
		bayeuxClient.publish("/test", "{\"name\" : \"hans\"}", "45");
		
		bayeuxClient.close();
		server.stop();
	}
	
	
}
