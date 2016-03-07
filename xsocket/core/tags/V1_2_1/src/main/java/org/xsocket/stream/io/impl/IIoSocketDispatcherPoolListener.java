// $Id: IoSocketDispatcherPool.java 1304 2007-06-02 13:26:34Z grro $
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
package org.xsocket.stream.io.impl;




import org.xsocket.IDispatcher;


/**
 * Dispatcher Pool Listener
 * 
 * 
 * @author grro@xsocket.org
 */
interface IIoSocketDispatcherPoolListener {
	
	/**
	 * call back method for a {@link IDispatcher} add event
	 *  
	 * @param dispatcher the added dispatcher
	 */
	@SuppressWarnings("unchecked")
	public void onDispatcherAdded(IDispatcher dispatcher);

	/**
	 * call back method for a {@link IDispatcher} removed event
	 *  
	 * @param dispatcher the removed dispatcher
	 */
	@SuppressWarnings("unchecked")
	public void onDispatcherRemoved(IDispatcher dispatcher);
}
