// $Id: WorkerPool.java 69 2006-06-22 20:41:42Z grro $

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


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A worker pool 
 * 
 * @author grro@xsocket.org
 */
final class WorkerPool {
	
	public static final int DEFAULT_WORKER_SIZE = 3;

	private int workerPoolSize = DEFAULT_WORKER_SIZE;
	private ExecutorService workers = Executors.newFixedThreadPool(3);

	
	
	/**
	 * set the thread (worker) pool size 
	 *  
	 * @param size the worker pool size 
	 */
	public void setSize(int workerSize) {
		workers.shutdown();

		this.workerPoolSize = workerSize;
		workers = Executors.newFixedThreadPool(workerSize);
	}

	
	/**
	 * get the thread (worker) pool size 
	 *  
	 * @return the worker pool size 
	 */
	public int getSize() {
		return workerPoolSize;
	}
	
	/**
	 * shut down the pool
	 *
	 */
	public void shutdownNow() {
		workers.shutdownNow();
	}
	
	public void execute(Runnable command) {
		workers.execute(command);
	}
}
