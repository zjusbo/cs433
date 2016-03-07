// $Id: NonBlockingClient.java 1630 2007-08-02 11:37:20Z grro $
/*
 *  Copyright (c) xsocket.org, 2006-2007. All rights reserved.
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

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.stream.IConnection.FlushMode;



/**
*
* @author grro@xsocket.org
*/
public final class NonBlockingClient  {

    public static void main(String... args) throws Exception {
        if (args.length != 3) {
            System.out.println("usage org.xsocket.stream.NonBlockingClient <host> <port> <path>");
            System.exit(-1);
        }

        new NonBlockingClient().call(args[0], Integer.parseInt(args[1]), args[2]);
    }


    public void call(String host, int port, String path) throws IOException {

        INonBlockingConnection nbc = new NonBlockingConnection(host, port, new ClientHandler());
        try {
            nbc.write("GET " + path + " HTTP\r\n\r\n");

            // do somthing else
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignore) { }

        } finally {
            nbc.close();
        }
    }


    private static final class ClientHandler implements IConnectHandler, IDataHandler {

        private boolean isHandled = false;


        public boolean onConnect(INonBlockingConnection nbc) throws IOException {
            nbc.setFlushmode(FlushMode.ASYNC);   // for performance reasons (only required by writes, which will not be done in this example)
            return true;
        }


        public boolean onData(INonBlockingConnection nbc) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
            if (!isHandled) {
                String responseCode = nbc.readStringByDelimiter("\r\n");

                if (responseCode.contains("200")) {
                    System.out.println("OK");
                } else {
                    System.out.println("ERROR got " + responseCode);
                }

                isHandled = true;
            }

            return true;
        }
    }

}
