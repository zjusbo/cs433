// $Id: IDispatcher.java 449 2006-12-09 07:02:10Z grro $
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
package org.xsocket.server;

import java.io.IOException;



/**
 * A <code>Dispatcher</code> is responsible to register new connections 
 * and to check the selector for read and write events. Based on this events 
 * the assigned <code>EventHandler</code> will be notified. <br>
 * Each <code>Dispatcher</code> runs under a dedicated thread. 
 * A connection is assigned to one <code>Dispatcher</code>. <br>.  
 * 
 * @author grro@xsocket.org
 */
interface IDispatcher {
	
	/**
	 * register a new connection 
	 * 
	 * @param connection the connection to register
	 * @throws IOException If some other I/O error occurs
	 */
	public void registerConnection(ManagedConnection connection) throws IOException ;
	
	/**
	 * deregister a connection 
	 * 
	 * @param connection the connection to deregister
	 * @throws IOException If some other I/O error occurs
	 */
	public void deregisterConnection(ManagedConnection connection) throws IOException;
	
	
	/**
	 * announce the a socket I/O write operation is 
	 * required 
	 * 
	 * @param connection the connection which requires the write
	 */
	public void announceWriteDemand(ManagedConnection connection);
	

	/**
	 * return if the dispatcher is closed
	 * 
	 * @return true, if the dispatcher is closed
	 */
	public boolean isClosed();

}
