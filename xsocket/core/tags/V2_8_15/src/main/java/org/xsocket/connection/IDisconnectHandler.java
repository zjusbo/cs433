/*
 * Copyright (c) xlightweb.org, 2006 - 2010. All rights reserved.
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

import java.io.IOException;





/**
 * Handles the disconnecting of connections. <br>
 * The disconnecting of a connection occurs in three ways:
 * <ul>
 *   <li> the client initiates the disconnect by closing the connection. In this case the 
 *         <code>onDisconnect</code> method will be called immediately
 *   <li> the connection breaks or the client disconnects improperly and the underlying SocketChannel 
 *        detects the broken connection. In this case the <code>onDisconnect</code> method will be
 *        called, when
 *        the broken connection will be detected
 *   <li> the connection breaks or the client disconnects improperly and the underlying SocketChannel 
 *        does not detect the broken connection. In this case the <code>onDisconnect</code> method 
 *        will only be called after a connection or idle timeout (see {@link IIdleTimeoutHandler}, {@link IConnectionTimeoutHandler}) 
 * </ul>
 * 
 * 
 * @author grro@xsocket.org
 */
public interface IDisconnectHandler extends IHandler {
	
	
	/**
	 * handles disconnecting of a connection
	 * 
	 * 
	 * @param connection the <i>closed</i> connection 
     * @return true for positive result of handling, false for negative result of handling
     * @throws IOException If some I/O error occurs. 
	 */
	boolean onDisconnect(INonBlockingConnection connection) throws IOException;	

}
