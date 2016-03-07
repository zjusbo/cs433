// $Id: RunnableEchoClient.java 775 2007-01-15 16:44:47Z grro $
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

import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;


public class RunnableEchoClient {
	
	private static final String DATA = "E"; 
	
	
	public static void main(String... args) {
		new RunnableEchoClient().launch(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
	}
	
	
	
	public void launch(final String host, final int port, int loops, int callsPerLoop) {

		for (int i = 0; i < loops; i++) {
			call(host, port, callsPerLoop);
		}
	}
	
	
	public void call(String host, int port, int callsPerLoop)  {
		IBlockingConnection connection = null;
		try {
			connection = new BlockingConnection(host, port);
			connection.setAutoflush(true);
			
			for (int i = 0; i < callsPerLoop; i++) {
				call(connection);
			}
			
		} catch (IOException ioe) {
			ioe.printStackTrace();
			
		} finally {
			
			if (connection != null) {
				try {
					connection.close();
				} catch (Exception ignore) { }
			}
		}
	}

	
	
	public long call(IBlockingConnection connection)  {
		try {
			long start = System.currentTimeMillis();
			connection.write(DATA + EchoHandler.DELIMITER);
			connection.receiveStringByDelimiter(EchoHandler.DELIMITER);
			long elapsed = System.currentTimeMillis() - start;
			System.out.println(elapsed + " millis");
			
			return elapsed;
		} catch (IOException ioe) {
			ioe.printStackTrace();
			return 0;
		}
	}

}
