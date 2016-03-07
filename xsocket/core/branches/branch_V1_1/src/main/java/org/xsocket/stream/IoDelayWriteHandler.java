// $Id: IoDelayWriteHandler.java 1276 2007-05-28 15:38:57Z grro $
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
package org.xsocket.stream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;




/**
 * Delayed write IO handler 
 * 
 * @author grro@xsocket.org
 */
final class IoDelayWriteHandler extends IoHandler {

	private static final Logger LOG = Logger.getLogger(IoDelayWriteHandler.class.getName());
	
	
	// write queue
	private final Queue<DelayQueueEntry> sendQueue = new LinkedList<DelayQueueEntry>(); 
	
	
	
	// timer handling
	private int sendBytesPerSec = INonBlockingConnection.UNLIMITED;
	private static Timer timer = null;
	private TimerTask delayedDelivererTask = null;

	
	@Override
	void open() throws IOException {
		getSuccessor().open();
	}
	
	
	/**
	 * constructor
	 * @param successor  the successor
	 */
	IoDelayWriteHandler(IoHandler successor) {
		super(successor);
	}

	
	/**
	 * set the write rate in sec
	 * 
	 * @param writeRateSec  the write rate
	 */
	void setWriteRateSec(int writeRateSec) {
		this.sendBytesPerSec = writeRateSec;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
/*	@Override
	/*	String getId() {
		return getSuccessor().getId();
	}
	*/
	
	/**
	 * {@inheritDoc}
	 */
	/*	@Override
	InetAddress getLocalAddress() {
		return getSuccessor().getLocalAddress();
	}
	*/
	
	/**
	 * {@inheritDoc}
	 */
/*	@Override
	int getLocalPort() {
		return getSuccessor().getLocalPort();
	}
	*/
	
	/**
	 * {@inheritDoc}
	 */
/*	@Override
	InetAddress getRemoteAddress() {
		return getSuccessor().getRemoteAddress();
	}
	*/
	
	/**
	 * {@inheritDoc}
	 */
/*	@Override
	int getRemotePort() {
		return getSuccessor().getRemotePort();
	}
*/
	
	/**
	 * {@inheritDoc}
	 */
	/*@Override
	boolean isOpen() {
		return getSuccessor().isOpen();
	}
	
*/	
	/**
	 * {@inheritDoc}
	 */
	@Override
	void setIOEventHandler(IIOEventHandler ioEventHandler) {
		getSuccessor().setIOEventHandler(ioEventHandler);
	}
	
	
	@Override
	boolean isChainSendBufferEmpty() {
		if (getSuccessor() != null) {
			return (sendQueue.isEmpty() && getSuccessor().isChainSendBufferEmpty());
		} else {
			return sendQueue.isEmpty();
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
    @Override
    IIOEventHandler getIOEventHandler() {
    	return getSuccessor().getIOEventHandler();
    }


	/**
	 * {@inheritDoc}
	 */
	@Override
	LinkedList<ByteBuffer> drainIncoming() {
		return getSuccessor().drainIncoming();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	void close(boolean immediate) throws IOException {
		if (!immediate) {
			flushOutgoing();
		} 
		
		getSuccessor().close(immediate);
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	void writeOutgoing(ByteBuffer buffer) {
		addToDelayedQueue(buffer);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	void writeOutgoing(LinkedList<ByteBuffer> buffers) {
		for (ByteBuffer buffer : buffers) {
			addToDelayedQueue(buffer);
		}
	}
	
	
	private void addToDelayedQueue(ByteBuffer buffer) {	
		// append to delay queue
		int size = buffer.remaining();
		if (size > 0) {
	 		int delayTime = (size * 1000) / sendBytesPerSec;
	 		
	 		synchronized (sendQueue) {
	 			if (LOG.isLoggable(Level.FINE)) {
	 				LOG.fine("[" + getId() + "] add " + buffer.remaining() + " bytes to delay queue");
	 			}
	 			sendQueue.offer(new DelayQueueEntry(System.currentTimeMillis() + delayTime, buffer.duplicate()));
	 		}
		}

		// create delivery task if not exists
		if (delayedDelivererTask == null) {
 			if (LOG.isLoggable(Level.FINE)) {
 				LOG.fine("[" + getId() + "] delay delivery task is null. Starting task");
 			}
			delayedDelivererTask = new DeliveryTask();			
			getTimer().schedule(delayedDelivererTask, 0, 500);
		}
	}

	
	/**
	 * {@inheritDoc}
	 */
	@Override
	void flushOutgoing() throws IOException {
		synchronized (sendQueue) {
			if (!sendQueue.isEmpty()) {
				DelayQueueEntry[] entries = sendQueue.toArray(new DelayQueueEntry[sendQueue.size()]);
				sendQueue.clear();
								
				ByteBuffer[] buffers = new ByteBuffer[entries.length];
				for (int i = 0; i < buffers.length; i++) {
					buffers[i] = entries[i].getBuffer();
				}
				
	 			if (LOG.isLoggable(Level.FINE)) {
	 				LOG.fine("[" + getId() + "] flushing " + buffers.length + " buffers of delay queue");
	 			}
				
				for (ByteBuffer buffer : buffers) {
					try {
						IoDelayWriteHandler.this.getSuccessor().writeOutgoing(buffer);
					} catch (Exception e) {
			 			if (LOG.isLoggable(Level.FINE)) {
			 				LOG.fine("[" + getId() + "] error occured while writing. Reason: " + e.toString());
			 			}						
					}
				}
			}
		}		
		
		getSuccessor().flushOutgoing();
	}

	
	private static synchronized Timer getTimer() {
		if (timer == null) {
			timer = new Timer("IoDelayWriteTimer", true);
		}
		
		return timer;
	}

	
	
	
	
	private final class DeliveryTask extends TimerTask {
		@Override
		public void run() {
			Thread.currentThread().setName("DeliveryThread");
			long current = System.currentTimeMillis();
			
			synchronized(sendQueue) {
				while(!sendQueue.isEmpty()) {
					DelayQueueEntry qe = sendQueue.peek();
					if (current >= qe.getDeliveryTime()) {
						try {								
							sendQueue.remove(qe);
						
				 			if (LOG.isLoggable(Level.FINE)) {
				 				LOG.fine("[" + getId() + "] release " + qe.getBuffer().remaining() + " bytes from delay queue");
				 			}
							getSuccessor().writeOutgoing(qe.getBuffer());
						} catch (Throwable e) {
				 			if (LOG.isLoggable(Level.FINE)) {
				 				LOG.fine("[" + getId() + "] Error occured while write delayed. Reason: " + e.toString());
				 			}
						}
					} else {
						break;
					}
				}
			}
		}
	}
	
	
	private static final class DelayQueueEntry {
		private long deliveryTime = 0;
		private ByteBuffer buffer = null;
		
		DelayQueueEntry(long deliveryTime, ByteBuffer buffer) {
			this.deliveryTime = deliveryTime;
			this.buffer = buffer;
		}

		ByteBuffer getBuffer() {
			return buffer;
		}

		long getDeliveryTime() {
			return deliveryTime;
		}
	}
}
