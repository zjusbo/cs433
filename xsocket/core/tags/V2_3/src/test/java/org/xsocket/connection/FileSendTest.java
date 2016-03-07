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
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;



import org.junit.Assert;
import org.junit.Test;

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.Server;




/**
*
* @author grro@xsocket.org
*/
public final class FileSendTest {


	@Test
	public void testSimple() throws Exception {
	    
		ServerHandler hdl = new ServerHandler();
		Server server = new Server(hdl);
		server.start();


		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.write("get\r\n");
		
		int length = connection.readInt();
		File file = File.createTempFile("test", null);
		file.deleteOnExit();
		
		FileChannel fc = new RandomAccessFile(file, "rw").getChannel();
		
		connection.transferTo(fc, length);
		fc.close();
		
		Assert.assertTrue(QAUtil.isEquals(file, new File(QAUtil.getNameTestfile_400k())));
		
		connection.close();
		server.close();
	}





	private static final class ServerHandler implements IDataHandler {

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
		    
		    String cmd = connection.readStringByDelimiter("\r\n");
		    
		    if (cmd.equals("get")) {
    		    String filename = QAUtil.getNameTestfile_400k();
    		    
    		    RandomAccessFile raf = new RandomAccessFile(filename, "r");
    		    FileChannel fc = raf.getChannel();

    		    connection.write((int) raf.length());
    		    connection.transferFrom(fc);
    		    
    		    fc.close();
    		    raf.close();
    		    
    		    System.out.println(raf.length() + " bytes written");
    		    
		    } else {
		        connection.close();
		    }
		    
			return true;
		}
	}
}
