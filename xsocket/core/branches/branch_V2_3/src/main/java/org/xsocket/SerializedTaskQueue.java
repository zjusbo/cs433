/*
 *  Copyright (c) xsocket.org, 2006 - 2009. All rights reserved.
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

import java.util.LinkedList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;




/**
 * Serialized Task Queue
 * 
 * <br/><br/><b>This is a xSocket internal class and subject to change</b> 
 * 
 * @author grro@xsocket.org
 */
public final class SerializedTaskQueue  {
	
	private static final Logger LOG = Logger.getLogger(SerializedTaskQueue.class.getName());

	private final LinkedList<Runnable> taskQueue = new LinkedList<Runnable>();
	private final MultithreadedTaskProcessor multithreadedTaskProcessor = new MultithreadedTaskProcessor();
	

	/**
	 * process a task non threaded synchronized by the internal task queue. If the 
	 * task queue is empty the task will be processed by the current thread. If a task running 
	 * or the task queue size is not empty, the task will be executed by a dedicated thread.   
	 * 
	 * @param task the task to process
	 */
	public void performNonThreaded(Runnable task) {
	
		synchronized (taskQueue) {
			
			// no running worker -> process non threaded
			if (taskQueue.isEmpty()) {
				task.run();
			
			// worker is currently running -> add task to queue (the task will be handled by the running worker)
			} else {
				taskQueue.addLast(task);
			}				
		}
	}

	
	/**
	 * process a task multi threaded synchronized by the internal task queue. The task will
	 * be processed by a dedicated thread. The given worker pool <i>can</i> be used to perform this 
	 * tasks (as well as other task of the queue).  
	 * 
	 * @param task the task to process
	 */
	public void performMultiThreaded(Runnable task, Executor workerpool) {

		boolean isWorkerRequired = false;
		
		synchronized (taskQueue) {
			
			// (Multithreaded) worker is not running
			if (taskQueue.isEmpty()) {
				taskQueue.addLast(task);
				isWorkerRequired = true;
			
			// worker is currently running -> add task to queue (the task will be handled by the running worker)
			} else {
				taskQueue.addLast(task);
			}				
		}
		
		
		if (isWorkerRequired) {
			try {
				workerpool.execute(multithreadedTaskProcessor);
			} catch (RejectedExecutionException ree) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("task has been rejected by worker pool " + workerpool + " (worker pool cosed?) performing task by starting a new thread");
				}
				Thread t = new Thread(multithreadedTaskProcessor, "SerializedTaskQueueFallbackThread");
				t.setDaemon(true);
				t.start();
			}
		}
	}

	
	
	private void performPendingTasks() {
		
		boolean taskToProcess = true;
		
		// handle all pending tasks
		while(taskToProcess) {
			
			// get task from queue
			Runnable task = null;
			synchronized (taskQueue) {
				task = taskQueue.getFirst();
			}
			assert (task != null) : "a task should always be available";

			
			// perform it 
			task.run();
			
			
			// and remove it from the queue
			synchronized (taskQueue) {
				
				// remaining tasks to process				
				if (taskQueue.size() > 1) {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.finer("more task to process. process next task");
					}
					taskQueue.remove(task);
					taskToProcess = true;
					
				} else {
					taskQueue.remove(task);
					taskToProcess = false;
				}
			}
		}
	}


	private final class MultithreadedTaskProcessor implements Runnable {
		
		public void run() {
			performPendingTasks();
		}
	}	
}
