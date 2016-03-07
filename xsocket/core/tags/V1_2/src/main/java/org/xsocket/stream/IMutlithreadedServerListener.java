// $Id: IMutlithreadedServerListener.java 1507 2007-07-13 07:26:53Z grro $
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


import org.xsocket.IDispatcher;
import org.xsocket.IWorkerPool;




/**
 * listener interface, which defines specific callback methods for a {@link IMultithreadedServer}
 * 
 * @author grro@xsocket.org
 */
public interface IMutlithreadedServerListener extends ILifeCycle, org.xsocket.ILifeCycle {

	/**
	 * call back method for worker pool replaced event 
	 * 
	 * @deprecated
	 * 
	 * @param oldWorkerPool  the old worker pool or <code>null</code>
	 * @param newWorkerPool  the new worker pool or <code>null</code>
	 */
	public void onWorkerPoolUpdated(IWorkerPool oldWorkerPool, IWorkerPool newWorkerPool);
	
	
	/**
	 * call back method for a {@link IDispatcher} add event
	 *  
	 * @deprecated  
	 * @param dispatcher the added dispatcher
	 */
	public void onDispatcherAdded(IDispatcher dispatcher);

	/**
	 * call back method for a {@link IDispatcher} removed event
	 *  
	 * @deprecated  
	 * @param dispatcher the removed dispatcher
	 */
	public void onDispatcherRemoved(IDispatcher dispatcher);
}
