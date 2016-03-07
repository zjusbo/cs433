// $Id: Context.java 293 2006-10-09 16:15:39Z grro $

/*
 *  Copyright (c) xsocket.org, 2006 - 2007. All rights reserved.
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
package org.xsocket.web.http.servlet;




import java.io.IOException;
import java.net.BindException;
import java.net.HttpURLConnection;
import java.net.URL;


public final class BulkPingClient {


	/**
	 * @param args
	 */
	public static void main(final String[] args)  {
	
		System.out.println("start test client for server " +  args[0] + ":" + args[1] + " with " + args[2] + " threads");
		int size = Integer.parseInt(args[2]); 
		for (int i = 0; i < size; i++) {
			Thread t = new Thread() {
				
				public void run() {

					HttpURLConnection connection = null;
					
					
					while (true) {
						try {
							connection = (HttpURLConnection) new URL("http://localhost:8080/echo/EchoServlet").openConnection();
							
						} catch (BindException be) {
							System.out.print("b");
							try {
								Thread.sleep(500);
							} catch (InterruptedException igonre) { }
							
						} catch (IOException ioe) {
							System.out.print(ioe.toString());
							
						} finally {
							if (connection != null) {
								connection.disconnect();
							}
						}
					}
				};
			};
			t.start();
		}
	}	
}
