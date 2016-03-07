/*
 *  Copyright (c) xsocket.org, 2006-2008. All rights reserved.
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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.DataConverter;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.NonBlockingConnection;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.IConnection.FlushMode;



/**
*
* @author grro@xsocket.org
*/
public final class ReadableByteBufferTest {

	
	private static final int SIZE = 1000;

	@Test 
	public void testSimple() throws Exception {
		IServer server = new Server(new ServerHandler());
		ConnectionUtils.start(server);
	
		
		INonBlockingConnection nbc = new NonBlockingConnection("localhost", server.getLocalPort());
		
		QAUtil.sleep(300);
		
		ByteBuffer buffer = ByteBuffer.allocate(300);
		int i = nbc.read(buffer);
		
		Assert.assertEquals(300, i);

		
		buffer = ByteBuffer.allocate(300);
		i = nbc.read(buffer);
		
		Assert.assertEquals(300, i);
		
		
		buffer = ByteBuffer.allocate(600);
		i = nbc.read(buffer);
		
		Assert.assertEquals(400, i);
		
		
		buffer = ByteBuffer.allocate(300);
		i = nbc.read(buffer);
		
		Assert.assertEquals(0, i);
		
		
		nbc.close();

		
		buffer = ByteBuffer.allocate(300);
		i = nbc.read(buffer);
		
		Assert.assertEquals(-1, i);

		server.close();
	}


	
	
	@Test 
	public void testHttp() throws Exception {
		
		BlockingConnection con = new BlockingConnection("tools.ietf.org", 80);
		con.setAutoflush(false);
		con.setFlushmode(FlushMode.ASYNC);
		
		con.write("GET /html/rfc2616 HTTP/1.1\r\n" + 
                  "Host: tools.ietf.org:80\r\n" + 
                  "User-Agent: xSocket-http/2.0-alpha-3\r\n" +
                  "\r\n");
		con.flush();
		
		String header = con.readStringByDelimiter("\r\n\r\n");

		File file = File.createTempFile("test", null);
		file.createNewFile();
		System.out.println("write to file " + file.getAbsolutePath());
			
		FileChannel fc = new RandomAccessFile(file, "rw").getChannel();
			
			
		ReadableByteChannel bodyChannel = con;
		ByteBuffer transferBuffer = ByteBuffer.allocateDirect(8192);
			    
		while (bodyChannel.read(transferBuffer) != -1) {
			transferBuffer.flip();
				
			while (transferBuffer.hasRemaining()) {
				fc.write(transferBuffer);
			}
			transferBuffer.clear();
		}
		fc.close();
		
		LineNumberReader lnr = new LineNumberReader(new FileReader(file));
		int countLines = 0;
		String lastLine = null;
		String line = null;
		
		do {
			line = lnr.readLine();
			if (line != null) {
				lastLine = line;
				countLines++;
			}
		} while(line != null); 

		Assert.assertEquals(9988, countLines);
		Assert.assertEquals("</body></html>", lastLine);
	}
	
	
	private static final class ServerHandler implements IConnectHandler {
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			connection.write(QAUtil.generateByteArray(SIZE));
			return true;
		}
	}
}
