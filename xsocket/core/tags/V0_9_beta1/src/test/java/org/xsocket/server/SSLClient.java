// $Id$
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


import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;




/**
*
* @author grro@xsocket.org
*/
public final class SSLClient {

	private int port = 7795;
	
	private SSLContext sslContext = null;
	
	public static void main(String... args) throws Exception {
		new SSLClient().launch();
	}
	

	public void launch() throws Exception {
		
        for (int i = 0; i < 3; i++) {
            InputStream in = null;
            OutputStream out = null;
    		
			try {
				sslContext = new SSLTestContextFactory().getSSLContext();
		        SocketFactory socketFactory = sslContext.getSocketFactory();
		        Socket socket = socketFactory.createSocket("127.0.0.1", port);
			    
		        in = socket.getInputStream();
		        out = socket.getOutputStream();
		        
		        in.read();
		        
		        System.out.println("ready");

				
			} catch (Throwable e) {
				e.printStackTrace();
			} finally {
				if (in != null) {
					try {
						in.close();
					} catch (Exception ignore) { }
				}
				if (out != null) {
					try {
						out.close();
					} catch (Exception ignore) { }
				}

			}
							
		}
	}
}
