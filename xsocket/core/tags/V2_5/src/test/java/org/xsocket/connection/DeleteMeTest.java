/*
 *  Copyright (c) xsocket.org, 2006 - 2009. All rights reserved.
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
import java.nio.BufferUnderflowException;
import java.nio.channels.ClosedChannelException;
import java.util.logging.Level;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;



import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;




/**
*
* @author grro@xsocket.org
*/
public final class DeleteMeTest {

    @Test
    public void testSimple() throws Exception {
        
        HttpServlet servlet = new HttpServlet () {
          
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                resp.getWriter().write("test");
            }
        };
        
        WebContainer webContainer = new WebContainer(servlet);
        webContainer.start();
        
        
        NonBlockingConnectionPool pool = new NonBlockingConnectionPool();

        
        for (int i = 0; i < 2; i++) {
            INonBlockingConnection con = pool.getNonBlockingConnection("localhost", webContainer.getLocalPort());
            con.write("GET /de/themen/unterhaltung/bildergalerien/8114728,image=5.html HTTP/1.1\r\n" +
                      "Host: magazine.web.de\r\n" + 
                      "User-agent: me\r\n\r\n");
            
            QAUtil.sleep(1000);
            
            String header = con.readStringByDelimiter("\r\n\r\n");
            con.readStringByLength(4);
            Assert.assertEquals(0, con.available());
            Assert.assertTrue(header.indexOf("200") != -1);
            con.close();
        }        
        
        pool.close();
        webContainer.stop();
    }
    
    
    
    
    private static final class Handler implements IDataHandler {
        
        
        public boolean onData(INonBlockingConnection connection) throws IOException {
            connection.write(connection.readBytesByLength(connection.available()));
            return true;
        }
    }
}
