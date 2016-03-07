/*
 * Copyright (c) xlightweb.org, 2006 - 2010. All rights reserved.
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

import java.util.concurrent.LinkedBlockingQueue;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;




/**
 * WorkerPool implementation
 * 
 * @author grro@xsocket.org
 */
public final class WorkerPool extends ThreadPoolExecutor {

    private static final Logger LOG = Logger.getLogger(WorkerPool.class.getName());

    
    
    /**
     * constructor 
     * 
     * @param maxSize   max worker size
     */
    public WorkerPool(int maxSize) {
        this(0, maxSize, 60, TimeUnit.SECONDS, false);
    }
    
    
    /**
     * constructor 
     * 
     * @param minSize   min worker size
     * @param maxSize   max worker size
     */
    public WorkerPool(int minSize, int maxSize) {
        this(minSize, maxSize, 60, TimeUnit.SECONDS, false);
    }
    

    
    /**
     * constructor 
     * 
     * @param minSize       min worker size
     * @param maxSize       max worker size
     * @param taskqueuesize the task queue size 
     */
    public WorkerPool(int minSize, int maxSize, int taskqueuesize) {
        this(minSize, maxSize, 60, TimeUnit.SECONDS, taskqueuesize, false);
    }
        
    
    
    /**
     * constructor 
     * 
     * @param minSize   min worker size
     * @param maxSize   max worker size
     * @param keepalive the keepalive
     * @param timeunit  the timeunit
     * @param isDaemon  true, if worker threads are daemon threads
     */
    public WorkerPool(int minSize, int maxSize, long keepalive, TimeUnit timeunit, boolean isDaemon) {
        this(minSize, maxSize, keepalive, timeunit, Integer.MAX_VALUE, isDaemon);
    }
    
    
    /**
     * constructor 
     * 
     * @param minSize       min worker size
     * @param maxSize       max worker size
     * @param keepalive     the keepalive
     * @param timeunit      the timeunit
     * @param taskqueuesize the task queue size 
     * @param isDaemon  true, if worker threads are daemon threads
     */
    public WorkerPool(int minSize, int maxSize, long keepalive, TimeUnit timeunit, int taskqueuesize, boolean isDaemon) {
        super(minSize, maxSize, keepalive, timeunit, new WorkerPoolAwareQueue(taskqueuesize), new DefaultThreadFactory(isDaemon));
        ((WorkerPoolAwareQueue) getQueue()).init(this);
    }
        

    
    
    @SuppressWarnings("serial")
    private static final class WorkerPoolAwareQueue extends LinkedBlockingQueue<Runnable> {
        
        private WorkerPool workerPool;
        
        public WorkerPoolAwareQueue(int capacity) {
            super(capacity);
        }
        
        public void init(WorkerPool workerPool) {
            this.workerPool = workerPool;
        }
        
        
        public boolean offer(Runnable task) {

            // active count smaller than pool size?
            if (workerPool.getActiveCount() < workerPool.getPoolSize()) {
                // add the task to the queue. The task should be handled immediately
                // because free worker threads should be available (all threads of 
                // the workerpool listens the queue for new tasks)
                return super.offer(task);
            }

            // max size reached?
            if (workerPool.getPoolSize() >= workerPool.getMaximumPoolSize()) {
                // add the task to the end of the queue. The task have to wait
                // until one of the threads becomes free to handle the task
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("add task to queue waiting for the next free one");
                }

                
                return super.offer(task);

            // ... no
            } else {
                // return false, which forces starting a new thread. The new
                // thread performs the task and will be added to workerpool
                // after performing the task 
                // As result the workerpool is increased. If the thread reach
                // its keepalive timeout (waiting for new tasks), the thread 
                // will terminate itself
                
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("initiate creating a new thread");
                }
                return false;
            }
        }
    }
    
    
    private static class DefaultThreadFactory implements ThreadFactory {
        
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        private final boolean isDaemon;

        DefaultThreadFactory(boolean isDaemon) {
            this.isDaemon = isDaemon;
            namePrefix = "xWorkerPool-" + poolNumber.getAndIncrement() + "-thread-";
        }

        public Thread newThread(Runnable r) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("creating new thread");
            }
            
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(isDaemon);

            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }
}
