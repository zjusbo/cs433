// $Id: WorkerPool.java 910 2007-02-12 16:56:19Z grro $

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



import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;



/**
 * a worker pool 
 * 
 * @author grro@xsocket.org
 */
public final class WorkerPool {

	private static final Logger LOG = Logger.getLogger(WorkerPool.class.getName());

	
	private int workerPoolSize = 0;
	private ExecutorService workers = null;

	
	
	/**
	 * constructor
	 *
	 */
	public WorkerPool() {
		
	}
	

	/**
	 * constructor 
	 * 
	 * @param workerSize the pool size
	 */
	public WorkerPool(int workerSize) {
		setSize(workerSize);
	}

	
	/**
	 * set the thread worker pool size 
	 *  
	 * @param workerSize the worker pool size 
	 */
	public void setSize(int workerSize) {
		if (workers != null) {
			workers.shutdown();
		}

		this.workerPoolSize = workerSize;
		workers = Executors.newFixedThreadPool(workerSize);
	}
	
	
	/**
	 * get the thread worker pool size 
	 *  
	 * @return the worker pool size 
	 */
	public int getSize() {
		return workerPoolSize;
	}
	
	
	/**
	 * stops pooling. Further execute calls
	 * will cause a new thread for each call. 
	 *
	 */
	public void stopPooling() {
        LOG.fine("stopping worker pool");

		workers.shutdownNow();
	}
	
	
	/**
	 * execute a given command 
	 * 
	 * @param command the command
	 */
	public void execute(Runnable command) {
		try {
			workers.execute(command);
		} catch (RuntimeException re) {
			// If pool is shutdown -> execute a dedicated thread 
			if (workers.isShutdown()) {
				Thread t = new Thread(command);
				t.start();
			}
		}
	}
}
