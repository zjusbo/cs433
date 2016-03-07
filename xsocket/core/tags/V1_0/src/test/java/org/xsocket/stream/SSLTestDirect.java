// $Id: SSLTestDirect.java 1023 2007-03-16 16:27:41Z grro $
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
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.BufferUnderflowException;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.SSLTestContextFactory;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;



/**
*
* @author grro@xsocket.org
*/
public final class SSLTestDirect {

	private SSLContext sslContext = null;	
	private static final String DELIMITER = System.getProperty("line.separator");
	


	
	@Test public void testDirect() throws Exception {
		//TestUtil.setLogLevel(Level.FINE);
		IMultithreadedServer sslTestServer = new MultithreadedServer(0, new SSLHandler(), true, new SSLTestContextFactory().getSSLContext());
		new Thread(sslTestServer).start();

		
       
		sslContext = new SSLTestContextFactory().getSSLContext();
        SocketFactory socketFactory = sslContext.getSocketFactory();
        Socket socket = socketFactory.createSocket(sslTestServer.getLocalAddress(), sslTestServer.getLocalPort());
        
        LineNumberReader lnr = new LineNumberReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
        
        for (int i = 0; i < 3; i++) {
        	String req = "hello how are how sdfsfdsf sf sdf sf s sf sdf " + i;
        	pw.write(req + DELIMITER);
        	pw.flush();
        	
        	String res = lnr.readLine();
        	
        	Assert.assertEquals(req, res);
        }
		   
        lnr.close();
        pw.close();
        socket.close();
        
        sslTestServer.close();
	}
	
	private static final class SSLHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			String word = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
			connection.write(word + DELIMITER);
			return true;
		}
	}
}
