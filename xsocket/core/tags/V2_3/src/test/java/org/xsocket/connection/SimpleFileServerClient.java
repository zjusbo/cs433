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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.channels.FileChannel;



import org.junit.Assert;
import org.junit.Test;
import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.IConnection.FlushMode;




/**
*
* @author grro@xsocket.org
*/
public final class SimpleFileServerClient implements Closeable {

    
    private final String host;
    private final int port;
    
    private final BlockingConnectionPool pool = new BlockingConnectionPool(); 

   
    
    public SimpleFileServerClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void close() throws IOException {
        pool.close();
    }
    
    
    public void send(String name, File file) throws IOException {
        
        IBlockingConnection con = null;
        try {
            con = pool.getBlockingConnection(host, port);
            
            con.write("put\r\n");
            con.write(name + "\r\n");
            con.write((int) file.length());
            
            FileChannel fc = new RandomAccessFile(file, "r").getChannel();
            con.transferFrom(fc);
            fc.close();
            
            con.close();
        } catch (IOException ioe) {
            if (con != null) {
                pool.destroy(con);
            }
        }
    }
    
    
    
    public void read(String name, File file) throws IOException {
        
        IBlockingConnection con = null;
        try {
            con = pool.getBlockingConnection(host, port);
            
            con.write("get\r\n");
            con.write(name + "\r\n");
            
            int size = con.readInt();
            FileChannel fc = new RandomAccessFile(file, "rw").getChannel();
            con.transferTo(fc, size);
            fc.close();
            
            con.close();
        } catch (IOException ioe) {
            if (con != null) {
                pool.destroy(con);
            }
        }
    }
}
