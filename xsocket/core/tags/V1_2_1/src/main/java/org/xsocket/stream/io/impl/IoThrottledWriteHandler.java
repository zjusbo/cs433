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
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.io.spi.IIoHandlerCallback;




/**
 * Delayed write IO handler 
 * 
 * @author grro@xsocket.org
 */
final class IoThrottledWriteHandler extends ChainableIoHandler {

	private static final Logger LOG = Logger.getLogger(IoThrottledWriteHandler.class.getName());
	
	
	// write queue
	private final LinkedList<DelayQueueEntry> sendQueue = new LinkedList<DelayQueueEntry>(); 
	
	
	
	// timer handling
	private int sendBytesPerSec = INonBlockingConnection.UNLIMITED;
	private TimerTask delayedDelivererTask = null;

	

	public void init(IIoHandlerCallback callbackHandler) throws IOException {
		setPreviousCallback(callbackHandler);
		getSuccessor().init(callbackHandler);
	}
	
	

	/**
	 * constructor
	 * @param successor  the successor
	 */
	IoThrottledWriteHandler(ChainableIoHandler successor) {
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
    @Override
    public int getPendingWriteDataSize() {
    	return getSendQueueSize() + super.getPendingWriteDataSize();
    }

    
    @SuppressWarnings("unchecked")
	private int getSendQueueSize() {
    	int size = 0;
    
    	LinkedList<DelayQueueEntry> copy = null;
    	synchronized (sendQueue) {
    		copy = (LinkedList<DelayQueueEntry>) sendQueue.clone();
		}
    	
    	for (DelayQueueEntry entry : copy) {
			size += entry.buffer.remaining();
		}
    	
    	return size;
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
	public void writeOutgoing(ByteBuffer buffer) {

		// append to delay queue
		int size = buffer.remaining();
		if (size > 0) {
			
			DelayQueueEntry delayQueueEntry = new DelayQueueEntry(buffer.duplicate(), sendBytesPerSec);
			
 			if (LOG.isLoggable(Level.FINE)) {
 				LOG.fine("[" + getId() + "] add " + delayQueueEntry + " to delay queue");
 			}
	 		synchronized (sendQueue) {
	 			sendQueue.offer(delayQueueEntry);
	 		}
		}

		// create delivery task if not exists
		if (delayedDelivererTask == null) {
			int period = 500;
			
 			if (LOG.isLoggable(Level.FINE)) {
 				LOG.fine("[" + getId() + "] delay delivery task is null. Starting task (period=" + DataConverter.toFormatedDuration(period) + ")");
 			}
			delayedDelivererTask = new DeliveryTask();			
			IoProvider.getTimer().schedule(delayedDelivererTask, 0, 500);
		}

	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void writeOutgoing(LinkedList<ByteBuffer> buffers) {
		for (ByteBuffer buffer : buffers) {
			writeOutgoing(buffer);
		}
	}
	

	
	/**
	 * {@inheritDoc}
	 */
	public void flushOutgoing() throws IOException {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("flush remaning data");
		}

		
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
						IoThrottledWriteHandler.this.getSuccessor().writeOutgoing(buffer);
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

	
	private final class DeliveryTask extends TimerTask {

		@Override
		public void run() {
			synchronized(sendQueue) {

				long currentTime = System.currentTimeMillis();
				while(!sendQueue.isEmpty()) {
					try {
						
						// get the oldest entry and write based on rate 
						DelayQueueEntry qe = sendQueue.peek();
						int remaingSize = qe.write(currentTime);
						
						// if all data of this entry is written remove entry and stay in loop 
						if (remaingSize == 0) {
							sendQueue.remove(qe);
							
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("throttling write queue is emtpy");
							}
							
							
						// ... else break loop and wait for next time event	
						} else {
							break;
						}
						
					} catch (Throwable e) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("[" + getId() + "] Error occured while write delayed. Reason: " + e.toString());
						}
					}
				}
			}
		}
	}
	
	
	private final class DelayQueueEntry {
		private ByteBuffer buffer = null;
		private int bytesPerSec = 0;
		private long lastWriteTime = 0;
 		
		
		DelayQueueEntry(ByteBuffer buffer, int bytesPerSec) {
			this.buffer = buffer;
			this.bytesPerSec = bytesPerSec;
			this.lastWriteTime = System.currentTimeMillis();
		}

		
		ByteBuffer getBuffer() {
			return buffer;
		}
		
		
		int write(long currentTime) throws IOException {
			int remaingSize = buffer.remaining();
			
			long elapsedTimeMillis = currentTime - lastWriteTime;
			
			if (elapsedTimeMillis > 0) {
				int elapsedTimeSec = ((int) (elapsedTimeMillis)) / 1000; 
				
				if (elapsedTimeSec > 0) {
					int sizeToWrite = bytesPerSec * elapsedTimeSec;
					
					if (sizeToWrite > 0) {
						ByteBuffer bytesToWrite = null; 
						if (buffer.remaining() <= sizeToWrite) {
							bytesToWrite = buffer;
							remaingSize = 0;
							
						} else {
							int saveLimit = buffer.limit();
							buffer.limit(sizeToWrite);
							bytesToWrite = buffer.slice();
							buffer.position(buffer.limit());
							buffer.limit(saveLimit);
							buffer = buffer.slice();
							remaingSize = buffer.remaining();
						}
						
						lastWriteTime = currentTime;
						if (LOG.isLoggable(Level.FINE)) {
			 				LOG.fine("[" + getId() + "] release " + sizeToWrite + " bytes from delay queue");
			 			}
						getSuccessor().writeOutgoing(bytesToWrite);
					}
				}
			}
			
			return remaingSize;
		}
		
		
		
		@Override
		public String toString() {
			return "buffer " + DataConverter.toFormatedBytesSize(buffer.remaining()) + " (write rate " + bytesPerSec + " bytes/sec)";
		}
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
   	@Override
	public String toString() {
   		try {
	   		return this.getClass().getSimpleName() + "(pending delayQueueSize=" + DataConverter.toFormatedBytesSize(getPendingWriteDataSize()) + ") ->" + "\r\n" + getSuccessor().toString(); 
   		} catch (Exception e) {
   			return super.toString();
   		}
	}

}
