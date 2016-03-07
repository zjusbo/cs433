// $Id: IWorkerPool.java 1043 2007-03-20 18:59:28Z grro $

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


/**
 * A worker pool executes {@link Runnable} objects by using managed workers (threads). These workers are
 * managed internally by implementing strategies like prestarted threads or reusing threads.
 * 
 * @author grro@xsocket.org
 */
public interface IWorkerPool {
	
	/**
	 * Executes the given command at some time in the future. 
     *
	 * @param command  the commnd to execute 
	 */
	public void execute(Runnable command);

	
	/**
     * Returns the current number of workers in the pool.
     *
     * @return the number of the workers
     */
    public int getPoolSize();
    

    /**
     * Returns the approximate number of workers that are actively
     * executing tasks.
     *
     * @return the number of workes
     */
    public int getActiveCount();
}
