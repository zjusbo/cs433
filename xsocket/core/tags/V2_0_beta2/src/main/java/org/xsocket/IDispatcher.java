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

import java.io.Closeable;
import java.io.IOException;
import java.util.Set;




/**
 * A Dispatcher encapsulates an underlying Selector. It is responsible
 * for the channel handle management. If a readiness event occurs, the
 * assigned {@link IDispatcherEventHandler} will be called.
 * 
 * <br/><br/><b>This is a xSocket internal class and subject to change</b> 
 *
 * @author grro@xsocket.org
 */
public interface IDispatcher<T extends IHandle> extends Runnable, Closeable {

	/**
	 * get the event handler of this dispatcher <br><br>.
	 *
	 * This method is thread save
	 *
	 * @return the event handler
	 */
	public IDispatcherEventHandler<T> getEventHandler();



	/**
	 * register a new handle. <br><br>.
	 *
	 * This method is thread save
	 *
	 * @param handle   the handle to register
     * @param ops      the interest set
     * @throws IOException If some I/O error occurs
	 */
	public void register(T handle, int ops) throws IOException;


	/**
	 * deregister a handle. <br> <br>
	 *
	 * This method is thread save
	 *
	 * @param handle   the handle to deregister
     * @throws IOException If some I/O error occurs
	 */
	public void deregister(final T handle) throws IOException;


	/**
	 * return the registered handles
	 *
	 * @return a list of the registered handles
	 */
	public Set<T> getRegistered();


	/**
	 * announce a write for he given handle. <br><br>
	 *
	 * This method is thread save
	 *
	 * @param handle   the handle for the write need
	 * @param ops      the interest set
	 * @throws IOException  if the given hnadle is invalid
	 */
	public void updateInterestSet(T handle, int ops) throws IOException;
}
