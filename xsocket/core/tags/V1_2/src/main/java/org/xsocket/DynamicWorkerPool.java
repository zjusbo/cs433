// $Id: DynamicWorkerPool.java 1365 2007-06-23 10:06:40Z grro $
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
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.IWorkerPool;


/**
 * @deprecated use a ThreadPool implementation of java.util.concurrent package ({@link Executor}) instead
 *  
 * @author grro@xsocket.org
 */
public final class DynamicWorkerPool implements IWorkerPool {
	
	private static final Logger LOG = Logger.getLogger(DynamicWorkerPool.class.getName());

	public static final String WORKER_PREFIX = "xWorker";
	
	public static final int DEFAULT_LOAD_THRESHOLD_DECREASE = 20;
	public static final int DEFAULT_LOAD_THRESHOLD_INCREASE = 80;
	public static final int DEFAULT_ADJUST_CHECK_PERIOD = 1000;
	
	
	private static final int SLUGGISH_PERIOD = 30 * 1000;

	private final Timer timer = new Timer("xDynamicWorkerPoolTimer", true);

	
	private ThreadPoolExecutor executor = null;
	private final Object decLock = new Object();

	private SizeManager sizeManager = null;
	private int minSize = 0;
	private int maxSize = 0;

	
	/**
	 * 
	 * @param minSize         the min pool size (and the initial size)
	 * @param maxSize         the max pool size
	 */
	public DynamicWorkerPool(int minSize, int maxSize) {
		this.minSize = minSize;
		this.maxSize = maxSize;
		
		int initial = minSize;
		if (initial < 1) {
			initial = 1;
		}
		
		executor = new ThreadPoolExecutor(initial, initial
										  , 0L, TimeUnit.MILLISECONDS
										  , new LinkedBlockingQueue<Runnable>()
										  , new WorkerThreadFactory());
		
		sizeManager = new SizeManager(DEFAULT_ADJUST_CHECK_PERIOD);
		
		// reduce timer priority
		timer.schedule(new TimerTask() {
		      public void run() {
		        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
		      }
		    }, 0);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void execute(Runnable command) {
		try {
			if (minSize < 1) {
				synchronized (decLock) {
					if (getPoolSize() < 1) {
						sizeManager.timeLastChange = System.currentTimeMillis();  // prevent immediately decrease
						executor.setCorePoolSize(1);
					}
				}
			}
			
			executor.execute(command);
			
		} catch (RejectedExecutionException e) {
			
			// is pool is shutdown run dedicated thread
			if (executor.isShutdown()) {
				Thread t = new Thread(command);
				t.setDaemon(true);
				t.start();
				
			// houldn't occur
			} else {
				LOG.warning("couldn't process command " + command + ". Reason: " + e.toString());
			}
		}
	}
		
	

	/**
	 * {@inheritDoc}
	 */
	public <T> List<Future<T>> invokeAll(Collection<Callable<T>> tasks) throws InterruptedException {
		return executor.invokeAll(tasks);
	}
    
	
	/**
	 * {@inheritDoc}
	 */
    public int getActiveCount() {
		return executor.getActiveCount();
	}

	
	/**
	 * {@inheritDoc}
	 */
	public int getPoolSize() {
		return executor.getCorePoolSize();
	}


	
    /**
     * return the load (range: 0...100)
     * 
     * @return the load
     */
    public int getLoad() {
		return (int) sizeManager.load.getValue();
	}
	
	private int currentLoad() {
		int currentSize = getPoolSize();
		if (currentSize == 0) {
			return 0;
		}
		
		int activeCount = getActiveCount();
		if (activeCount == 0) {
			return 0;
		}
		
		return (int) ((activeCount * 100) / currentSize);
	}
	

    /**
     * @return the maximum pool size
     */
    public int getMaximumPoolSize() {
		return maxSize;
	}
	
	

    /**
     * @return the minimum pool size
     */
    public int getMinimumPoolSize() {
		return minSize;
	}
    
    
    /**
     * @return true, if the worker pool is open
     */
    public boolean isOpen() {
		return !executor.isShutdown();
	}
	
	
    /**
     *
     */
	public void close() {
		sizeManager.shutdown();
		executor.shutdownNow();
		
		timer.cancel();
	}
	

	/** 
	 * 
	 * @param adjustPeriodSec the adjust check period of the pool size in seconds 
	 */
	public void setAdjustPeriod(int adjustPeriodSec) {
		sizeManager.setAdjustPeriod(adjustPeriodSec);	
	}
	
	
	/**
	 * @return the adjust check period of the pool size in seconds 
	 */
	public int getAdjustPeriod() {
		return sizeManager.getAdjustPeriod();
	}

	
	/**
	 * @return the increase threshold
	 */
	public int getThresholdIncrease() {
		return sizeManager.getIncThreshold();
	}

	
	/**
	 * @param incThreshold  the increase threshold
	 */
	public void setThresholdIncrease(int incThreshold) {
		sizeManager.setIncThreshold(incThreshold);
	}


	/**
	 * @return  the decrease threshold 
	 */
	public int getThresholdDecrease() {
		return sizeManager.getDecThreshold();
	}
	
	
	/**
	 * @param decThreshold the decrease threshold  
	 */
	public void setThresholdDecrease(int decThreshold) {
		sizeManager.setDecThreshold(decThreshold);
	}
	


	/**
	 * @return the load
	 */
	int getLoadSluggish() {
		return (int) sizeManager.sluggishLoad.getValue();
	}
	
	
	/**
	 * @return the decrease rate
	 */
	long getDecRate() {
		return sizeManager.getDecRate();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return "DynamicWorkerPool (size=" + getPoolSize() + ", running=" + getActiveCount()+ ", load=" + getLoad() 
		     + ", minSize=" + getMinimumPoolSize() + ", maxSize=" + getMaximumPoolSize() + ", isOpen=" + isOpen() + ")";
	}
	
	
	private static final class WorkerThreadFactory implements ThreadFactory {
		private static int poolCounter = 0;
		private int threadCounter = 0;
		private String namePrefix = null;
		
		WorkerThreadFactory() {
			namePrefix = WORKER_PREFIX + "-" + (++poolCounter) + "-";
		}
		
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r);
			t.setName(namePrefix + (++threadCounter));
			t.setDaemon(true);
			t.setPriority(Thread.NORM_PRIORITY);
			return t;
		}
	}
	
	
	private final class SizeManager {	
		private long timeLastChange = System.currentTimeMillis();
		
		private int adjustPeriodSec = 0;
		
		private int decRate = 0;  
		private Average load = null;
		private Average sluggishLoad = null;
		
		private int incThreshold = DEFAULT_LOAD_THRESHOLD_INCREASE;
		private int decThreshold = DEFAULT_LOAD_THRESHOLD_DECREASE;
		
		private TimerTask task = null;
		

		
		
		public SizeManager(int period) {
			load = new Average(3);
			sluggishLoad = new Average((int) (SLUGGISH_PERIOD / period));
			decRate = 5 * period;

			setAdjustPeriod(period);
		}
		
		
		public int getDecThreshold() {
			return decThreshold;
		}
		
		public int getIncThreshold() {
			return incThreshold;
		}
		
		public long getDecRate() {
			return decRate;
		}
		
		public void setDecThreshold(int decThreshold) {
			this.decThreshold = decThreshold;
		}
		
		public void setIncThreshold(int incThreshold) {
			this.incThreshold = incThreshold;
		}
		
		public void setAdjustPeriod(int period) {
			this.adjustPeriodSec = period;
			
			if (task != null) {
				task.cancel();
			}
			
			
			task = new TimerTask() {
				@Override
				public void run() {
					adjustWorkerSize();
				}
			};
			timer.schedule(task, period, period);
		}
		
		
		public int getAdjustPeriod() {
			return adjustPeriodSec;
		}
		
		
		public void shutdown() {
			if (task != null) {
				task.cancel();
			}
		}
		
		
		private void adjustWorkerSize() {

			try {
				int size = getPoolSize();
				double load = currentLoad();
				
				checkForInc(size, load);
				checkForDec(size, load);
			} catch (Exception e) { 
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured vy adjusting pool size. Reason: " + e.toString());
				}
			}
		}
	
		
		private void checkForInc(int size, double currentLoad) {
			load.add((int) currentLoad);
			
			if ((load.getValue() >= incThreshold)) {
				if (size < maxSize) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("average load is " + load.getValue() + " increase pool size. new size is " 
								+ (size + 1) + " (minSize=" + getMinimumPoolSize() 
								+ ", maxSize=" + getMaximumPoolSize() + ")"); 
					}
					executor.setCorePoolSize(size + 1);
					timeLastChange = System.currentTimeMillis();
				}
			}
		}
		
		
		private void checkForDec(int size, double currentLoad) {
			sluggishLoad.add((int) currentLoad);
	
			if (load.getValue() <= decThreshold) {
				if ((sluggishLoad.getValue() <= decThreshold) && (size > minSize)) {
					if (System.currentTimeMillis() > (timeLastChange + decRate)) {
						if (size == 1) {
							synchronized (decLock) {
								if (!executor.getQueue().isEmpty()) {
									if (LOG.isLoggable(Level.FINE)) {
										LOG.fine("average load is " + sluggishLoad.getValue() + " decrease pool size. new size is " 
												+ (size - 1) + " minSize=" + getMinimumPoolSize() 
												+ ", maxSize=" + getMaximumPoolSize() + ")"); 
									}
									executor.setCorePoolSize(size - 1);
									timeLastChange = System.currentTimeMillis(); 
								}
							}
						}
					}
				}
			}
		}
	}



	
	private static final class Average {
		private LinkedList<Double> list = new LinkedList<Double>();
		private int capacity = 0;
		
		Average(int capacity) {
			this.capacity =	capacity;
		}
		
		public void add(double size) {
			list.addLast(size);
			if (list.size() > capacity) {
				list.removeFirst();
			}
		}
		
		public void clear() {
			list.clear();
		}
		
		@SuppressWarnings("unchecked")
		public double getValue() {
			if (list.size() == 0) {
				return 0;
			}

			double i = 0;
			LinkedList<Double> copy = (LinkedList<Double>) list.clone();
			
			for (double size : copy) {
				i += size;
			}
			
			if (i <= 0) {
				return 0;
			}
			
			i = i / copy.size();
			
			return i;
		}
	}
}
