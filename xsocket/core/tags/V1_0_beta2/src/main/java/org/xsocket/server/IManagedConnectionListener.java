//$Id: IManagedConnectionListener.java 446 2006-12-07 09:56:30Z grro $
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
 * A listerner of a <code>ManagedConnection</code>
 * 
 * @author grro@xsocket.org
 */
interface IManagedConnectionListener {
	
	
	/**
	 * notify the connect event 
	 * 
	 * @param connection the assigned connection
	 */
	public void onConnectionConnectEvent(ManagedConnection connection);
	
	/**
	 * notify the connect event 
	 * 
	 * @param connection the assigned connection
	 */
	public void onConnectionCloseEvent(ManagedConnection connection);

	/**
	 * notify the data to send  event 
	 * 
	 * @param connection the assigned connection
	 */
	public void onConnectionDataToSendEvent(ManagedConnection connection);
	
	/**
	 * notify the idel timeout event 
	 * 
	 * @param connection the assigned connection
	 */
	public void onConnectionIdleTimeoutEvent(ManagedConnection connection);
	
	/**
	 * notify the the connection timeout event 
	 * 
	 * @param connection the assigned connection
	 */
	public void onConnectionTimeoutEvent(ManagedConnection connection);
}
