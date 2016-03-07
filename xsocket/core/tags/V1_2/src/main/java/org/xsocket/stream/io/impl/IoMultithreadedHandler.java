// $Id: IoDelayWriteHandler.java 1316 2007-06-10 08:51:18Z grro $
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
package org.xsocket.stream.io.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.stream.io.spi.IIoHandlerCallback;
import org.xsocket.stream.io.spi.IIoHandlerContext;




/**
 * Delayed write IO handler 
 * 
 * @author grro@xsocket.org
 */
final class IoMultithreadedHandler extends ChainableIoHandler {

	private static final Logger LOG = Logger.getLogger(IoMultithreadedHandler.class.getName());
	
	
	private String id = "<null>";
	private IIoHandlerContext ctx = null;		
	private final TaskQueue taskQueue = new TaskQueue();

	private final IOEventHandler eventHandler = new IOEventHandler();
	
	
	/**
	 * constructor
	 * @param successor  the successor
	 */
	IoMultithreadedHandler(ChainableIoHandler successor, IIoHandlerContext ctx) {
		super(successor);
		this.ctx = ctx;
		
		setSuccessor(successor);
	}
	
	public void init(IIoHandlerCallback callbackHandler) throws IOException {
		setPreviousCallback(callbackHandler);
		getSuccessor().init(eventHandler);	
	}

	




	/**
	 * {@inheritDoc}
	 */
	public LinkedList<ByteBuffer> drainIncoming() {
		return getSuccessor().drainIncoming();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void close(boolean immediate) throws IOException {
		if (!immediate) {
			flushOutgoing();
		}

		getSuccessor().close(immediate);
	}
	

	/**
	 * {@inheritDoc}
	 */
	public void writeOutgoing(ByteBuffer buffer) throws IOException {
		getSuccessor().writeOutgoing(buffer);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void writeOutgoing(LinkedList<ByteBuffer> buffers) throws IOException {
		getSuccessor().writeOutgoing(buffers);
	}
	

	
	/**
	 * {@inheritDoc}
	 */
	public void flushOutgoing() throws IOException {
		getSuccessor().flushOutgoing();
	}

	
	private final class IOEventHandler implements IIoHandlerCallback {
		
		public void onWriteException(IOException ioException) {
			getPreviousCallback().onWriteException(ioException);
		}

		public void onWritten() {
			getPreviousCallback().onWritten();
		}

				
		public void onConnectionAbnormalTerminated() {
			getPreviousCallback().onConnectionAbnormalTerminated();
		}

		
		public void onConnect() {
			if (ctx.isAppHandlerListenForConnectEvent()) {
				Runnable task = new Runnable() {
					public void run() {
						try {
							getPreviousCallback().onConnect();
						} catch (Exception e) {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("[" + id + "] error occured by handling connect. Reason: " + e.toString());
							}
						}
					}
				};
					
				taskQueue.processTask(task);
			}	
		}

		
		public void onDataRead() {
			if (ctx.isAppHandlerListenForDataEvent()) {
				Runnable task = new Runnable() {
					public void run() {
						try {
							getPreviousCallback().onDataRead();
						} catch (Exception e) {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("[" + id + "] error occured by handling data. Reason: " + e.toString());
							}
						}
					}
				};
				
				taskQueue.processTask(task);
			}
		}


		
		public void onDisconnect() {
			if (ctx.isAppHandlerListenforDisconnectEvent()) {
				Runnable task = new Runnable() {
					public void run() {
						try {
							getPreviousCallback().onDisconnect();
						} catch (Exception e) {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("[" + id + "] error occured by handling connect. Reason: " + e.toString());
							}
						}
					}
				};
				
				taskQueue.processTask(task);
			}

		}

		
		public void onConnectionTimeout() {
			Runnable task = new Runnable() {
				public void run() {
					try {
						getPreviousCallback().onConnectionTimeout();
					} catch (Exception e) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("[" + id + "] error occured by handling onConnectionTimeout. Reason: " + e.toString());
						}
					}
				}
			};
			
			taskQueue.processTask(task);
		}

		
		public void onIdleTimeout() {
			Runnable task = new Runnable() {
				public void run() {
					try {
						getPreviousCallback().onIdleTimeout();
					} catch (Exception e) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("[" + id + "] error occured by handling onIdleTimeout. Reason: " + e.toString());
						}
					}
				}
			};
				
			taskQueue.processTask(task);
		}
	}	
	
	
	
	
	private final class TaskQueue {
		
		private final TaskQueueProcessor taskProcessor = new TaskQueueProcessor();
		
		private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<Runnable>(); 
		
		public void processTask(Runnable task) {
			
			// workpool available (task will performed be handled within worker thread)? -> put task into fifo queue and process it to ensure the right order 
			if (ctx.getWorkerpool() != null) {
				
				// add task to task queue
				tasks.offer(task);
		
				// process the task
				ctx.getWorkerpool().execute(taskProcessor);
				
				
			// no workerpool (no multithreading, task will be performed within acceptor/disptacher thread)				
			} else {
				task.run();
			}
		}
	}
	

	private final class TaskQueueProcessor implements Runnable {
		
		public void run() {
			if (!ctx.isAppHandlerThreadSafe()) {
				synchronized (IoMultithreadedHandler.this) {
					Runnable task = taskQueue.tasks.poll();
					processTask(task);
				}
			} else {
				Runnable task = null;
				task = taskQueue.tasks.poll();
				processTask(task);
			}					
		}

		private void processTask(Runnable task) {
			if (task != null) {
				try {
					task.run();
				} catch (Exception e) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("error occured by proccesing task " + task);
					}
				}
			}
		}
	}
	
}
