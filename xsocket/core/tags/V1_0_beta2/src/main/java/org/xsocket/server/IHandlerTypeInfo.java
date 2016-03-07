// $Id: IHandlerTypeInfo.java 446 2006-12-07 09:56:30Z grro $
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


/**
 * Meta infromation about a handler.
 * 
 * @author grro@xsocket.org
 */
interface IHandlerTypeInfo {
	
	/**
	 * return if the assigned handler is a connect handler
	 * 
	 * @return true, if the assigned handler is a connect handler
	 */
	public boolean isConnectHandler();
	
	
	/**
	 * return if the assigned handler is a diconnect handler
	 * 
	 * @return true, if the assigned handler is a diconnect handler
	 */
	public boolean isDisconnectHandler();
	
	
	/**
	 * return if the assigned handler is a data handler
	 * 
	 * @return true, if the assigned handler is a data handler
	 */
	public boolean isDataHandler();
	
	/**
	 * return if the assigned handler is a timeout handler
	 * 
	 * @return true, if the assigned handler is a timeout handler
	 */
	public boolean isTimeoutHandler();
	
	/**
	 * return if the assigned handler is a connection scoped handler
	 * 
	 * @return true, if the assigned handler is a connection scoped handler
	 */
	public boolean isConnectionScoped();
	
	/**
	 * return if the assigned handler is a life cycle handler
	 * 
	 * @return true, if the assigned handler is a life cycle handler
	 */
	public boolean isLifeCycleHandler();
}
