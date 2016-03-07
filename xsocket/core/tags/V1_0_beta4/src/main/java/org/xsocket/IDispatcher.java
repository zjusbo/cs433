// $Id: IDispatcher.java 919 2007-02-13 12:34:01Z grro $
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

package org.xsocket;

import java.io.IOException;
import java.util.Set;




/**
 * A Dispatcher encapsulates the underlying Selector. He is responsible
 * for the management of the registered channel handles. If readiness events 
 * for a channel occur, the assigned <code>EventHandler</code> will be called. 
 * 
 * @author grro@xsocket.org
 */
public interface IDispatcher<T extends IHandle> {

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
	 */
	public void updateInterestSet(T handle, int ops);
	
	
	/**
	 * get the number of handled registractions
	 * 
	 * @return the number of handled registractions
	 */
	public long getNumberOfHandledRegistrations();
	

	/**
	 * get the number of handled reads
	 * 
	 * @return the number of handled reads
	 */
	public long getNumberOfHandledReads();

	
	/**
	 * get the number of handled writes
	 * 
	 * @return the number of handled writes
	 */
	public long getNumberOfHandledWrites();
}
