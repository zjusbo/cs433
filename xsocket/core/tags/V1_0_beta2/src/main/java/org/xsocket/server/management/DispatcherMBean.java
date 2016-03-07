// $Id: DispatcherMBean.java 444 2006-12-07 06:28:54Z grro $
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

package org.xsocket.server.management;




/**
 * mangement interface of the dispatcher 
 * 
 * @author grro@xsocket.org
 */
public interface DispatcherMBean {

			
	/**
	 * get the number of handled connections
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
	 * get the number of the terminated connection, 
	 * caused by the connection timeout 
	 *  
	 * @return terminated connections
	 */
	public int getNumberOfConnectionTimeout();
	
	
	/**
	 * get the number of the terminated connection, 
	 * caused by idle timeout. 
	 *  
	 * @return terminated connections
	 */
	public int getNumberOfIdleTimeout();

	
	/**
	 * get the current size of the preallocated buffer
	 * 
	 * @return current size of the preallocated buffer
	 */
	public int getCurrentPreallocatedBufferSize();
	
	
	/**
	 * get the size of the preallocation buffer, 
	 * for reading incomming data
	 *   
	 * @return preallocation buffer size
	 */
	public int getReceiveBufferPreallocationSize();
}
