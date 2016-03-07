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

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;





/**
 * WorkerPool implementation
 * 
 * @author grro@xsocket.org
 */
public final class WorkerPool extends ThreadPoolExecutor {

    
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
     * @param minSize   min worker size
     * @param maxSize   max worker size
     * @param keepalive the keepalive
     * @param timeunit  the timeunit
     * @param isDaemon  true, if worker threads are daemon threads
     */
    public WorkerPool(int minSize, int maxSize, long keepalive, TimeUnit timeunit, boolean isDaemon) {
        super(minSize, maxSize, keepalive, timeunit, new WorkerPoolAwareQueue(), new DefaultThreadFactory(isDaemon));
        ((WorkerPoolAwareQueue) getQueue()).init(this);
    }
    

    
    
    @SuppressWarnings("serial")
    private static final class WorkerPoolAwareQueue extends LinkedBlockingQueue<Runnable> {
        
        private WorkerPool workerPool;
        
        public void init(WorkerPool workerPool) {
            this.workerPool = workerPool;
        }
        
        public boolean offer(Runnable task) {
            
            // active count smaller than pool size?
            if (workerPool.getActiveCount() < workerPool.getPoolSize()) {
                // add the task to the queue. The task should be handled immediately
                // because free worker threads are available
                return super.offer(task);
            }

            // max size reached?
            if (workerPool.getPoolSize() >= workerPool.getMaximumPoolSize()) {
                // add the task to the end of the queue and wait
                // until one of the threads becomes free to handle the task
                return super.offer(task);

            // ... no
            } else {
                // return false, which forces starting a new thread. The new
                // thread performs the task and will be added to workerpool
                // after performing it. As result the workerpool is increased
                // If the thread reached its keepalive timeout (waiting for new tasks),
                // the thread will terminated itself
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
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(isDaemon);

            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }
}
