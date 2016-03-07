// $Id: SSLTest.java 1023 2007-03-16 16:27:41Z grro $
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
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

import javax.net.ssl.SSLContext;


import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.SSLTestContextFactory;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.Server;
import org.xsocket.stream.IConnection.FlushMode;



/**
*
* @author grro@xsocket.org
*/
public final class SSLxSocketBothSideTest {

	private static final String DELIMITER = System.getProperty("line.separator");


	@BeforeClass
	public static void setUp() {
		SSLContext.setDefault(new SSLTestContextFactory().getSSLContext());
	}



	@Test
	public void testXSocket() throws Exception {
		SSLContext sslCtx = SSLContext.getDefault();
		System.out.println("got ssl context for " + sslCtx.getProtocol());
		IServer sslTestServer = new Server(0, new SSLHandler(), true, sslCtx);
		StreamUtils.start(sslTestServer);


		IBlockingConnection connection = new BlockingConnection("localhost", sslTestServer.getLocalPort(), SSLContext.getDefault(), true);
		connection.setAutoflush(false);

		connection.write("test" + DELIMITER);
		connection.flush();

		String response = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		connection.close();

		Assert.assertEquals("test", response);

		sslTestServer.close();
	}


	@Test
	public void testLengthField() throws Exception {
		Server server = new Server(0, new LengthFieldHandler(), true, SSLContext.getDefault());
		StreamUtils.start(server);

		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort(), new SSLTestContextFactory().getSSLContext(), true);
		connection.setAutoflush(false);

        for (int i = 1; i < 10; i++) {
        	byte[] data = QAUtil.generateByteArray(i);

        	// write
        	ByteBuffer lengthField = ByteBuffer.allocate(4);
        	lengthField.putInt(i);
        	lengthField.flip();
        	byte[] bytes = lengthField.array();
        	connection.write(bytes);
        	connection.write(data);
        	connection.flush();

        	QAUtil.sleep(100);

        	// read
        	int length = connection.readInt();
        	byte[] receiveData = connection.readBytesByLength(length);

        	Assert.assertTrue(QAUtil.isEquals(data, receiveData));
        }

        connection.close();
        server.close();
	}


	private static final class LengthFieldHandler implements IDataHandler, IConnectHandler {

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			connection.setFlushmode(FlushMode.ASYNC);

			return true;
		}

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			int length = StreamUtils.validateSufficientDatasizeByIntLengthField(connection);
			String word = connection.readStringByLength(length);
			connection.write(length);
			connection.write(word);

			connection.flush();

			return true;
		}
	}


	private static final class SSLHandler implements IDataHandler {

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			connection.setAutoflush(false);

			String word = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
			connection.write(word + DELIMITER);

			connection.flush();
			return true;
		}
	}
}
