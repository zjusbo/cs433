// $Id: IDispatcher.java 41 2006-06-22 06:30:23Z grro $
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

import java.util.List;


/**
 * A <code>Dispatcher</code> is responsible to accept new connections, read and write data. 
 * A connection is assigned to one <code>Dispatcher</code>. <br> 
 * The processing of the incoming data and the preperation of the appropriate response data 
 * will be done by the registered <code>Handler</code>. Each <code>Dispatcher</code>
 * runs under a dedicated thread.
 *
 * @author grro@xsocket.org
 */
public interface IDispatcher extends Runnable {

	
	/**
	 * shutdown   
	 */
	public void shutdown();
	
	
	/**
	 * get the number of handeld connections
	 * 
	 * @return the number of handled connections
	 */
	public long getNumberOfHandledConnections();
	
	
	/**
	 * get the number of open connections
	 * 
	 * @return the number of open connections
	 */
	public int getNumberOfOpenConnections();

	
	/**
	 * get info regarding to the open connections
	 * 
	 * @return open connection info
	 */
	public List<String> getOpenConnections();
	


	/**
	 * get the number of the terminated connection, caused by the connection timeout 
	 *  
	 * @return terminated connections
	 */
	public int getNumberOfConnectionTimeout();
	
	
	/**
	 * get the number of the terminated connection, caused by idle timeout. 
	 *  
	 * @return terminated connections
	 */
	public int getNumberOfIdleTimeout();
	
	
	
	/**
	 * get the size of the preallocation buffer, for reading incomming data
	 *   
	 * @return preallocation buffer size
	 */
	public int getReceiveBufferPreallocationSize();

	
	/**
	 * sets the check period, to perform timeout checks
	 * 
	 * @param period the check period 
	 */
	public void setTimeoutCheckPeriod(long period);
	
	
	/**
	 * gets the check period, to perform timeout checks
	 * 
	 * @return the check period
	 */
	public long getTimeoutCheckPeriod();
	
	
	/**
	 * set the assigned handler
	 * 
	 * @param handler the handler 
	 */
	public void setHandler(InternalHandler handler);
	
}
