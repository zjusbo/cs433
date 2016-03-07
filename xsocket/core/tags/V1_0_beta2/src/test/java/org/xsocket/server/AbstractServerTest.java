// $Id: AbstractServerTest.java 428 2006-12-04 09:56:00Z grro $
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



import org.xsocket.server.MultithreadedServer;


/**
*
* @author grro@xsocket.org
*/
public abstract class AbstractServerTest {
	
	protected static final IMultithreadedServer createServer(int port, IHandler handler) {
		IMultithreadedServer server = null;
		
		do {
			try {
				server = new MultithreadedServer(port);		

				server.setHandler(handler);
					
				Thread t = new Thread(server);
				t.start();
			
				do {
					try {
						Thread.sleep(250);
					} catch (InterruptedException ignore) { }
				} while (!server.isRunning());
				
			} catch (Exception be) {
				be.printStackTrace();
				port++;
				if (server != null) {
					server.shutdown();
					server = null;
				}
			}
		} while (server == null);
		
		return server;
	}
}
