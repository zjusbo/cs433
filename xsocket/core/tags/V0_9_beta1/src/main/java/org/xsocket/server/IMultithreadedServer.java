// $Id: IMultithreadedServer.java 41 2006-06-22 06:30:23Z grro $
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
 * A server accepts new incomming connections, and delegates the handling of the 
 * <code>NonBlockingConnection</code> to the assigned handler. A handler could be a
 * <code>LifeCycleHandler</code> or <code>DataHandler</code> <br><br>
 * 
 * The server consists of <code>Dispatcher</code>s, which are responsible to
 * accept new connections, read and write data. A connection is assigned to one
 * <code>Dispatcher</code>. 
 * The processing of the incoming data and the preperation of the appropriate response
 * will be done by the registered <code>Handler</code>. Each <code>Dispatcher</code>
 * runs under a dedicated thread. <br>
 * If a handler implements the <code>IConnectionScoped</code>-Interface, for each new
 * incomming connection a dedicated instance of the handler will be created by
 * the clone-Method of the <code>IConnectionScoped</code>-Interface.
 * 
 *  
 * @author grro@xsocket.org
 */
public interface IMultithreadedServer extends Runnable {
 
	/**
	 * shutdown   
	 */
	public void shutdown();

	/**
	 * signals, if service is running
	 * 
	 * @return true, if the server is running 
	 */
	public boolean isRunning();
	

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
	 * get the number of the terminated connection, caused by receivingt timeout. 
	 *  
	 * @return terminated connections
	 */
	public int getNumberOfReceivingTimeout();
	
	
	
	/**
	 * set the thread (worker) size of a Disptacher instance
	 *  
	 * @param size the worker size of the Dispatcher
	 */
	public void setDispatcherWorkerSize(int size);
	
	
	/**
	 * get the thread (worker) size of a Disptacher instance
	 *  
	 * @return the worker size of the Dispatcher
	 */
	public int getDispatcherWorkerSize();	
	

	/**
	 * set the size of the preallocation buffer, for reading incomming data
	 *
	 * @param size preallocation buffer size
	 */

	public void setReceiveBufferPreallocationSize(int size);
	
	/**
	 * get the size of the preallocation buffer, for reading incomming data
	 *   
	 * @return preallocation buffer size
	 */
	public int getReceiveBufferPreallocationSize();
	

	/**
	 * returns the receiving timeout  
	 * 
	 * @return receiving timeout
	 */
	public long getReceivingTimeout();
	
	
	/**
	 * sets the receiving timeout  
	 * 
	 * @param timeout receiving timeout
	 */
	public void setReceivingTimeout(long timeout);
	
	
	/**
	 * gets the connection timeout
	 * 
	 * @return connection timeout
	 */
	public long getConnectionTimeout();
	
	
	/**
	 * sets the connection timeout
	 * 
	 * @param timeout the connection timeout
	 */
	public void setConnectionTimeout(long timeout);
	
	
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
	 * get the server port 
	 * 
	 * @return the server port
	 */
	public int getPort();
	

	
	/**
	 * set the number of the used <code>Dispatcher</code>s
	 * 
	 * @param size the number of the Dispatchers
	 */
	public void setDispatcherSize(int size);
	
	/**
	 * get the number of the used <code>Dispatcher</code>s
	 * 
	 * @return the number of the Dispatchers
	 */
	public int getDispatcherSize();
	

	/**
	 * return the disptachers 
	 * 
	 * @return the dispatchers
	 */
	public List<Dispatcher> getDispatcher();
	
	
	/**
	 * set the assigned handler
	 * 
	 * @param handler the handler 
	 */
	public void setHandler(IHandler handler);
}
