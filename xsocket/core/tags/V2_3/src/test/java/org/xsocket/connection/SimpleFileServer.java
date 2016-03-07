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
import java.nio.channels.FileChannel;



import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;




/**
*
* @author grro@xsocket.org
*/
public final class SimpleFileServer extends Server {
    
    
    
    public SimpleFileServer(int port, String dir) throws IOException {
        super(port, new ServerHandler(dir));
    }
    
    
    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(args[0]);
        String dir = args[1];
        SimpleFileServer server = new SimpleFileServer(port, dir);
        server.run();
    }

    
    
    private static final class ServerHandler implements IDataHandler {
        
        private String dir = null;
            
        public ServerHandler(String dir) {
            this.dir = dir;
            System.out.println("SimpleFileServer filestore " + dir);
        }
    
            
        public boolean onData(INonBlockingConnection con) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

            // mark read position
            con.markReadPosition();
            
            try {
            
                String cmd = con.readStringByDelimiter("\r\n");
                String name = con.readStringByDelimiter("\r\n");
                
                // upload
                if (cmd.equalsIgnoreCase("put")) {
                    int size = con.readInt();
                    con.removeReadMark();
                    con.setHandler(new NetToFileStreamer(this, new File(dir + File.separator + name), size));
                    
                // download
                } else if (cmd.equalsIgnoreCase("get")) {
                    con.removeReadMark();

                    File file = new File(dir + File.separator + name);
                    FileChannel fc = new RandomAccessFile(file, "r").getChannel();
                    con.write((int) file.length());
                    con.transferFrom(fc);
                    fc.close();
                    
                    System.out.println("SimpleFileServer file download " + file.getAbsolutePath());
                        
                    
                // illegal command
                } else {
                    con.write("unsupported command\r\n");
                    con.close();
                }
                
                return true;
            
            } catch (BufferUnderflowException bue) {
               con.resetToReadMark();
               return true;
            }
        }
    }
  
    
    
    
    private static final class NetToFileStreamer implements IDataHandler {
            
        private final IDataHandler orgHandler;
        private final File file;
        private FileChannel fc;
        private int remaining = 0;
    
        public NetToFileStreamer(IDataHandler orgHandler, File file, int size) throws IOException {
            this.orgHandler = orgHandler;
            this.file = file;
            remaining = size;
        }
            
                    
        public boolean onData(INonBlockingConnection con) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
            
            if (fc == null) {
                if (!file.exists()) {
                    file.createNewFile();
                }
                fc = new RandomAccessFile(file, "rw").getChannel();
            }
            
            
            try {
                int available = con.available();
                
                if ((available <= 0) || (remaining == 0)) {
                    return true;
                }
                
                if (available < remaining) {
                    con.transferTo(fc, available);
                    remaining = remaining - available;
                    
                } else {
                    con.transferTo(fc, remaining);
                    fc.close();
                    
                    System.out.println("SimpleFileServer file uploaded " + file.getAbsolutePath());
                    remaining = 0;
                    con.setHandler(orgHandler);
                }
            } catch (IOException ioe) {
                fc.close();
                file.delete();
                throw ioe;
            }
                
            return true;
        }
    }
}
