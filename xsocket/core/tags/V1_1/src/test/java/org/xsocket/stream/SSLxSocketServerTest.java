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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.SSLTestContextFactory;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;
import org.xsocket.stream.IConnection.FlushMode;



/**
*
* @author grro@xsocket.org
*/
public final class SSLxSocketServerTest {
	
	private static final Logger LOG = Logger.getLogger(SSLxSocketServerTest.class.getName());

	private static final String DELIMITER = System.getProperty("line.separator");

	private SSLContext sslContext = null;	
	

	
	@Test public void testSSLClient() throws Exception {
		System.out.println("test ssl client");
		
		IMultithreadedServer sslTestServer = new MultithreadedServer(0, new SSLHandler(), true, new SSLTestContextFactory().getSSLContext());
		StreamUtils.start(sslTestServer);

       
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
	
	
	
	@Test public void testNonSSLClient() throws Exception {
		System.out.println("test non ssl client");
		IMultithreadedServer sslTestServer = new MultithreadedServer(0, new SSLHandler(), true, new SSLTestContextFactory().getSSLContext());
		StreamUtils.start(sslTestServer);

       
        Socket socket = new Socket(sslTestServer.getLocalAddress(), sslTestServer.getLocalPort());
        
        LineNumberReader lnr = new LineNumberReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
        
       	String req = "hello how are how sdfsfdsf sf sdf sf s sf sdf ";
       	pw.write(req + DELIMITER);
       	pw.flush();
       	
       	try {
       		int i = lnr.read();
       		Assert.assertTrue("connection should should have been closed, because server side is ssl", i == -1);
       	} catch (Exception expected) {expected.printStackTrace();}
        			   
        lnr.close();
        pw.close();
        socket.close();
        
        sslTestServer.close();
	}
	

	@Test public void testLengthField() throws Exception {
		MultithreadedServer server = new MultithreadedServer(0, new LengthFieldHandler(), true, new SSLTestContextFactory().getSSLContext());
		StreamUtils.start(server);
       
		SSLContext sslContext = new SSLTestContextFactory().getSSLContext();
        SocketFactory socketFactory = sslContext.getSocketFactory();
        Socket socket = socketFactory.createSocket(server.getLocalAddress(), server.getLocalPort());
        
        InputStream is = socket.getInputStream();
        OutputStream os = socket.getOutputStream();
        
        for (int i = 1; i < 10; i++) {
        	byte[] data = QAUtil.generateByteArray(i);
        	
        	// write
        	LOG.fine("[client] sending... (" + i + " bytes)");
        	ByteBuffer lengthField = ByteBuffer.allocate(4);
        	lengthField.putInt(i);
        	lengthField.flip();
        	byte[] bytes = lengthField.array();
        	os.write(bytes);
        	os.write(data);
        	os.flush();
        	
        	QAUtil.sleep(100);
        	
        	// read
        	LOG.fine("[client] receiving... (" + i + " bytes)");
        	byte[] lengthFieldReceive = new byte[4];
        	is.read(lengthFieldReceive);
        	int j = ByteBuffer.wrap(lengthFieldReceive).getInt();
        	LOG.fine("[client] read length=" + j);
        	byte[] receiveData = new byte[j];
        	is.read(receiveData);
        	
        	Assert.assertTrue(QAUtil.isEquals(data, receiveData));
        	LOG.fine("[client] got result. next loop");

        }
		   
        is.close();
        os.close();
        socket.close();
        
        server.close();
        
        System.out.println("testActivateSslOnConnect passed");
	}


	private static final class SSLHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			LOG.fine("Test dataHandler#onData receiveQueue size=" + connection.getNumberOfAvailableBytes());
			String word = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
			
			LOG.fine("Test dataHandler#onData got word");
			connection.write(word + DELIMITER);
			return true;
		}
	}
	
	private static final class LengthFieldHandler implements IDataHandler, IConnectHandler {

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			connection.setFlushmode(FlushMode.ASYNC);
			
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			LOG.fine("[server] onData " + ((Connection) connection).printInQueue());
			int length = StreamUtils.validateSufficientDatasizeByIntLengthField(connection);
			String word = connection.readStringByLength(length);
			connection.write(length);
			connection.write(word);
			
			LOG.fine("[server] returning: " + word);
			connection.flush();

			return true;
		}
	}
}
