// $Id: MultithreadedServerMBean.java 784 2007-01-16 10:20:58Z grro $
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

import java.util.List;



/**
 *  mangement interface of the multithreaded server 
 *  
 * @author grro@xsocket.org
 */
public interface MultithreadedServerMBean {
 
	
	/**
	 * shutdown the server 
	 */
	public void shutdown();

	
	/**
	 * set the thread worker pool size, shared
	 * by all dispatchers 
	 *  
	 * @param size the worker pool size 
	 */
	public void setWorkerPoolSize(int size);
	
	
	/**
	 * get the thread worker pool size, shared
	 * by all dispatchers  
	 *  
	 * @return the worker pool size 
	 */
	public int getWorkerPoolSize();	
	
	

	/**
	 * set the size of the preallocation buffer, 
	 * for reading incomming data
	 *
	 * @param size preallocation buffer size
	 */

	public void setReceiveBufferPreallocationSize(int size);

	
	/**
	 * get the size of the preallocation buffer, 
	 * for reading incomming data
	 *   
	 * @return preallocation buffer size
	 */
	public int getReceiveBufferPreallocationSize();
	

	
	/**
	 * gets the idle timeout in sec 
	 * 
	 * @return idle timeout in sec
	 */
	public int getIdleTimeoutSec();
	
	
	/**
	 * sets the idle timeout in sec 
	 * 
	 * @param timeoutInSec idle timeout in sec
	 */
	public void setIdleTimeoutSec(int timeoutInSec);
	
	
	/**
	 * gets the connection timeout
	 * 
	 * @return connection timeout
	 */
	public int getConnectionTimeoutSec();
	
	
	/**
	 * sets the connection timeout in sec
	 * 
	 * @param timeoutSec the connection timeout in sec
	 */
	public void setConnectionTimeoutSec(int timeoutSec);
	
	
	/**
	 * get the server port 
	 * 
	 * @return the server port
	 */
	public int getLocalPort();
	

	
	/**
	 * set the dispatcher pool size
	 * 
	 * @param size the dispatcher pool size
	 */
	public void setDispatcherPoolSize(int size);
	
	
	/**
	 * get the dispatcher pool size
	 * 
	 * @return the dispatcher pool size
	 */
	public int getDispatcherPoolSize();



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
	 * get connection info about the open 
	 * connections
	 * 
	 * @return open connection info
	 */
	public List<String> getOpenConnections();
	


	/**
	 * get the number of the terminated connection, 
	 * caused by the connection timeout 
	 *  
	 * @return terminated connections
	 */
	public int getNumberOfConnectionTimeout();
	

	/**
	 * get the number of the terminated connection, 
	 * caused by the idle timeout 
	 *  
	 * @return terminated connections
	 */
	public int getNumberOfIdleTimeout();
}
