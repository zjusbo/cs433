// $Id: FlushOnCloseTest.java 1379 2007-06-25 08:43:44Z grro $
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
package org.xsocket.web.http;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.channels.FileChannel;
import java.util.logging.Level;

import org.junit.Test;
import org.xsocket.stream.Server;
import org.xsocket.stream.StreamUtils;
import org.xsocket.web.http.HttpProtocolHandler;
import org.xsocket.web.http.IHeader;
import org.xsocket.web.http.IHttpConnection;
import org.xsocket.web.http.IMessageHandler;
import org.xsocket.web.http.IResponseHeader;
import org.xsocket.web.http.IWriteableChannel;




/**
*
* @author grro@xsocket.org
*/
public final class HttpConnectionServerTest {

	
	@Test 
	public void testServer() throws Exception {
		QAUtil.setLogLevel(Level.FINE);

		Server server = new Server(8080, new HttpProtocolHandler(new Handler()));
		StreamUtils.start(server);
		
		QAUtil.sleep(10000000);
		
		server.close();
	}
	
	
	
	private static final class Handler implements IMessageHandler {
		
		@Override
		public void onMessageHeader(IHttpConnection httpConnection) throws BufferUnderflowException, IOException {
			IHeader header = httpConnection.receiveMessageHeader();
			System.out.println(header);
			
			
			IResponseHeader responseHeader = httpConnection.createResponseHeader(200);
			responseHeader.addHeader("Content-Encoding", "text/plain");
			
			byte[] data = (System.currentTimeMillis() + "  Hello how are you?").getBytes();
			//byte[] data = ("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789").getBytes();
			
			IWriteableChannel bodyHandle = httpConnection.sendChunkedMessageHeader(responseHeader);
			//IBodyWriteHandle bodyHandle = httpConnection.sendMessage(responseHeader, data.length);
			
			bodyHandle.transferFrom(getFileChannel(data));
			//bodyHandle.write(data); 
			bodyHandle.close();
		}
		
		private FileChannel getFileChannel(byte[] data) throws IOException {
			File file = File.createTempFile("temp", "temp");
			file.deleteOnExit();
			
			FileOutputStream fos = new FileOutputStream(file);
			fos.write(data);
			fos.close();
			
			FileChannel fc = new RandomAccessFile(file, "r").getChannel();
			return fc;
		}
	}
}
