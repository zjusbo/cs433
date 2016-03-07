//$Id: IServerContext.java 1692 2007-08-20 10:43:10Z grro $
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

import java.net.InetAddress;
import java.util.concurrent.Executor;

import org.xsocket.Resource;



/**
 * Represents the handler`s server context. The context object will be set by using dependency injection. 
 * To do this the {@link Resource} annotation has to be used.<br>
 * 
 * E.g. 
 * <pre> 
 *  class MyHandler implements IDataHandler {
 *
 *     &#064Resource
 *     private IServerContext ctx;
 *     
 *     public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
 *         ...
 *         int srvPort = ctx.getLocalePort();
 *         ...
 *         
 *         return true;
 *     }
 *  }
 * </pre> 
 * 
 * @author grro@xsocket.org
 */
public interface IServerContext {

	/**
	 * get the local server port 
	 * 
	 * @return the local server port
	 */
	public int getLocalePort();
		
	
	/**
	 * get the local server address
	 * 
	 * @return the local server address
	 */
	public InetAddress getLocaleAddress();
	
	/**
	 * get the number of the open server connections   
	 * 
	 * @return the number of the open connections
	 */
	public int getNumberOfOpenConnections();
		

	/**
	 * return the worker pool
	 *
	 * @return the worker pool
	 */
	public Executor getWorkerpool();	 		
}
