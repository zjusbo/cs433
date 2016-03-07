// $Id: IWorkerPool.java 1365 2007-06-23 10:06:40Z grro $

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

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;


/**
 * @deprecated  replaced by {@link Executor}
 * 
 * @author grro@xsocket.org
 */
public interface IWorkerPool extends Executor {
	
	/**
     *
	 * @param command  the commnd to execute 
	 */
	public void execute(Runnable command);

	
	/**
	 *      
	 * @param tasks the collection of tasks
	 * @return A list of Futures representing the tasks, 
	 *         in the same sequential order as produced by 
	 *         the iterator for the given task list, each of which has completed.
	 * @throws InterruptedException  if interrupted while waiting, in which case unfinished tasks are cancelled.
	 */
	public <T> List<Future<T>> invokeAll(Collection<Callable<T>> tasks) throws InterruptedException;
	
	
	
	
	/**
     *
     * @return the number of the workers
     */
    public int getPoolSize();
    

    /**
     *
     * @return the number of workes
     */
    public int getActiveCount();
}
