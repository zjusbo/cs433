// $Id: ITimeoutHandler.java 1049 2007-03-21 16:42:48Z grro $

/*
 *  Copyright (c) xsocket.org, 2006 - 2007. All rights reserved.
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




/**
 * Handles timeout. The timeouts will be defined by the server. To modify the timeouts
 * the proper server methods has to be called. E.g.<br>
 * <pre>
 *    ...
 *    IMultithreadedServer server = new MultithreadedServer(new MyHandler());
 *    server.setIdleTimeoutSec(60);
 *    new Thread(server).start();
 *    ...
 *    
 *    
 *    class MyHandler implements ITimeoutHandler {
 *    
 *        public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
 *           ...
 *           connection.close();
 *           return true; // true -> signals the server that this event has been handled
 *        }
 *        
 *        public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
 *           ...
 *           connection.close();
 *           return true;  // true -> signals the server that this event has been handled
 *        }
 *    }
 * </pre>
 * 
 * @author grro@xsocket.org
 */
public interface ITimeoutHandler extends IHandler {

	/**
	 * handles the idle timeout.
	 * 
	 * @param connection the underlying connection
	 * @return true if the timeout event has been handled (in case of false the connection will be closed by the server)
	 * @throws IOException if an error occurs
	 */
	public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException ;
	
	
	/**
	 * handles the connection timeout.
	 * 
	 * @param connection the underlying connection 
	 * @return true if the timeout event has been handled (in case of false the connection will be closed by the server)
 	 * @throws IOException if an error occurs
	 */
	public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException ;
}
