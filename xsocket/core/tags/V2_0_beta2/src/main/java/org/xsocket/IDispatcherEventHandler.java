/*
 *  Copyright (c) xsocket.org, 2006 - 2008. All rights reserved.
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
package org.xsocket;


import java.io.IOException;

	

/**
 * Handles readiness events managed by a {@link IDispatcher} 
 * 
 * <br/><br/><b>This is a xSocket internal class and subject to change</b>
 *  
 * @author grro@xsocket.org
 */
public interface IDispatcherEventHandler<T extends IHandle> {
		
	/**
	 * callback method for the <i>handle</i> registered event 
	 * 
	 * @param handle  the assoiated handle 
     * @throws IOException If some I/O error occurs
	 */
	public void onHandleRegisterEvent(T handle) throws IOException;


	
	/**
	 * callback method for a <i>handle</i> readable event 
	 * 
	 * @param handle  the assoiated handle 
     * @throws IOException If some I/O error occurs
	 */
	public void onHandleReadableEvent(T handle) throws IOException;

	
	/**
	 * callback nmethod for a <i>handle</i> writeable event 
	 * 
	 * @param handle  the assoiated handle 
     * @throws IOException If some I/O error occurs
	 */
	public void onHandleWriteableEvent(T handle) throws IOException;
	
	
	
	/**
	 * callback method for a <i>dispatcher</i> close event 
	 *
	 * @param handle the assoiated handle 
	 */
	public void onDispatcherCloseEvent(T handle);

}