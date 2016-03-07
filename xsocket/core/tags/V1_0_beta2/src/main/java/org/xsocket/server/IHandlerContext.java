//$Id: IHandlerContext.java 446 2006-12-07 09:56:30Z grro $
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

import java.net.InetAddress;



/**
 * Context object of the handler. The context object will be set by using dependency injection. 
 * To do this the <code>Resource</code> annotation has to be used.<br>
 * 
 * E.g. 
 * <pre> 
 *  class MyHandler implements IDataHandler {
 *
 *     &#064Resource
 *     private IHandlerContext ctx;
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
public interface IHandlerContext {

	/**
	 * get the locale server port 
	 * 
	 * @return the locale server port
	 */
	public int getLocalePort();
		
	
	/**
	 * get the locale server address
	 * 
	 * @return the locale server address
	 */
	public InetAddress getLocaleAddress();
	
	
	/**
	 * get the domain name 
	 * 
	 * @return the domain name
	 */
	public String getDomainname();
}
