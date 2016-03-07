// $Id: DisconnectTest.java 1156 2007-04-15 08:10:39Z grro $
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
package org.xsocket.connection;

import java.io.IOException;

  
/**
*
* @author grro@xsocket.org
*/
public final class BenchmarkingProxies {

	public static final void main(String... args) throws Exception {
		if (args.length != 6) {
			System.out.println("usage java org.xsocket.stream.BenchmarkProxy <host1> <port1> <host2> <port2> <host3> <port3>");
			System.exit(-1);
		}
		
		new BenchmarkingProxies().launch(args[0], Integer.parseInt(args[1]), args[2], Integer.parseInt(args[3]), args[4], Integer.parseInt(args[5]));
	}
	
	
	public void launch(String host1, int port1, String host2, int port2, String host3, int port3) throws IOException {
		
		System.out.println("warm up");
		BenchmarkingClients.callDirect(host1, port1, 10000, 5, 1);
		BenchmarkingClients.callDirect(host2, port2, 10000, 5, 1);
		
		
		System.out.println("\n\nstart");
		String hs1 = host1 + ":" + port1;
		String hs2 = host2 + ":" + port2 ;
		String hs3 = host3 + ":" + port3 ;
		System.out.println("loops, dataSize, packages, elapsed/" + hs1 + " , elapsed/" + hs2 + " , elapsed/" + hs3);
		
		compare(host1, port1, host2, port2, host3, port3, 10, 5000, 1);

		// pause, to wait that system tcp connection has been released   
		pause(60 * 1000L);
		
		compare(host1, port1, host2, port2, host3, port3, 10, 1000, 5);
		pause(60 * 1000L);
		
		
		compare(host1, port1, host2, port2, host3, port3, 1000, 5000, 1);
		pause(60 * 1000L);

		compare(host1, port1, host2, port2, host3, port3, 1000, 1000, 5);
		pause(60 * 1000L);

		compare(host1, port1, host2, port2, host3, port3, 50000, 100, 1);
		pause(30 * 1000L);

		compare(host1, port1, host2, port2, host3, port3, 50000, 100, 5);
		pause(60 * 1000L);

		compare(host1, port1, host2, port2, host3, port3, 200000, 100, 1);
		pause(60 * 1000L);
		
		compare(host1, port1, host2, port2, host3, port3, 1000000, 100, 1);
	}	
	
	
	private void pause(long time) {
		try {
			Thread.sleep(time);
		} catch (InterruptedException ignore) { }
	}
	
	
	private void compare(String host1, int port1, String host2, int port2, String host3, int port3, int size, int loops, int packages) throws IOException {
		long elapsed1 = BenchmarkingClients.callDirect(host1, port1, size, loops, packages);
		long elapsed2 = BenchmarkingClients.callDirect(host2, port2, size, loops, packages);
		long elapsed3 = BenchmarkingClients.callDirect(host3, port3, size, loops, packages); 

		System.out.println(loops + ",  " + size + ",  " + packages + ",  "+ elapsed1 + ",  " + elapsed2 + ",  " + elapsed3);
	}
}
