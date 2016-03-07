// $Id: NonBlockingConnection.java 448 2006-12-08 14:55:13Z grro $
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

package org.xsocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.xsocket.Connection;
import org.xsocket.ClosedConnectionException;
import org.xsocket.util.TextUtils;


/**
 * Implementation of the <code>INonBlockingConnection</code> interface. <br><br>
 * 
 * The methods of this class are not thread-safe. 
 * 
 * @author grro@xsocket.org
 */
public class NonBlockingConnection extends Connection implements INonBlockingConnection {
	
	private static final Logger LOG = Logger.getLogger(NonBlockingConnection.class.getName());

	private static final String DELIVER_THREAD_PREXIX = "DeliveryThread"; 
	

	
	// delayed send
	private boolean isSendDelayIsActivated = false;
	private int sendBytesPerSec = UNLIMITED;
	private final Queue<QueueEntry> sendDelayQueue = new LinkedList<QueueEntry>(); 
	private static Timer timer = null;
	private TimerTask delayedDelivererTask = null;
	
	

	
	/**
	 * constructor 
	 * 
	 * @param hostname  the remote host
	 * @param port		the port of the remote host to connect
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port) throws IOException {
		this(hostname, port, null, false);
	}


	
	
	/**
	 * constructor 
	 * 
	 * @param hostname  the remote host
	 * @param port		the port of the remote host to connect
	 * @param sslContext  the sslContext to use
	 * @param sslOn       true, if data should be send and received by using ssl 
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port, SSLContext sslContext, boolean sslOn) throws IOException {
		this(SocketChannel.open(new InetSocketAddress(hostname, port)), null, true, sslContext, sslOn);
		
		init();
	}
		
		 
	/**
	 * constructor
	 *  
	 * @param channel  the underlying socket channel
	 * @param id the connection id
	 * @param clientMode  true, if this connection is running in the client mode
	 * @param sslContext  the sslContext to use
	 * @param sslOn       true, if data should be send and received by using ssl	  
	 * @throws IOException If some other I/O error occurs
	 */
	protected NonBlockingConnection(SocketChannel channel, String id, boolean clientMode, SSLContext sslContext, boolean sslOn) throws IOException {
		super(channel, id, clientMode, sslContext, sslOn);
		channel.configureBlocking(false);
	}
	
	
	

	/**
	 * {@inheritDoc}
	 */
	public final ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException, ClosedConnectionException, BufferUnderflowException {
		readIncoming();

		ByteBufferOutputChannel channel = new ByteBufferOutputChannel();
		extractRecordByDelimiterFromReadQueue(delimiter, channel);
	
		return channel.toByteBufferArray();
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final ByteBuffer[] readByteBufferByLength(int length) throws IOException, ClosedConnectionException, BufferUnderflowException {
		readIncoming();

		ByteBufferOutputChannel channel = new ByteBufferOutputChannel();
		extractRecordByLength(length, channel);
	
		return channel.toByteBufferArray();
	}

	

	/**
	 * {@inheritDoc}
	 */
	public byte[] readBytesByDelimiter(String delimiter) throws IOException, ClosedConnectionException, BufferUnderflowException {
		return toArray(readByteBufferByDelimiter(delimiter));
	}
	
	

	/**
	 * {@inheritDoc}
	 */
	public byte[] readBytesByLength(int length) throws IOException, ClosedConnectionException, BufferUnderflowException {
		return toArray(readByteBufferByLength(length));
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final String readStringByDelimiter(String delimiter) throws IOException, ClosedConnectionException, BufferUnderflowException {
		return readStringByDelimiter(delimiter, getDefaultEncoding());
	}

	/**
	 * {@inheritDoc}
	 */
	public final String readStringByLength(int length) throws IOException, ClosedConnectionException, BufferUnderflowException {
		return readStringByLength(length, getDefaultEncoding());
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final int readInt() throws IOException, ClosedConnectionException, BufferUnderflowException {
		readIncoming();
		
		return extractIntFromReadQueue();
	}


	
	/**
	 * {@inheritDoc}
	 */
	public final long readLong() throws IOException, ClosedConnectionException, BufferUnderflowException {
		readIncoming();
		
		return extractLongFromReadQueue();
	}


	/**
	 * {@inheritDoc}
	 */
	public final double readDouble() throws IOException, ClosedConnectionException, BufferUnderflowException {
		readIncoming();
		
		return extractDoubleFromReadQueue();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final byte readByte() throws IOException, BufferUnderflowException {
		readIncoming();
		
		return extractByteFromReadQueue();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final String readStringByDelimiter(String delimiter, String encoding) throws IOException, ClosedConnectionException, BufferUnderflowException {
		readIncoming();
		
		ByteBufferOutputChannel channel = new ByteBufferOutputChannel();
		extractRecordByDelimiterFromReadQueue(delimiter, channel);

		return TextUtils.toString(channel.toByteBufferArray(), encoding);
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final String readStringByLength(int length, String encoding) throws IOException, ClosedConnectionException, BufferUnderflowException {
		readIncoming();
		
		ByteBufferOutputChannel channel = new ByteBufferOutputChannel();
		extractRecordByLength(length, channel);

		return TextUtils.toString(channel.toByteBufferArray(), encoding);
	}
	

	/**
	 * {@inheritDoc}
	 */	
	public int getNumberOfAvailableBytes() {
		return getReadQueueSize();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final ByteBuffer[] readAvailable() throws ClosedConnectionException, ClosedConnectionException, IOException {
		readIncoming();
		
		LinkedList<ByteBuffer> queue = extractAvailableFromReadQueue();
		if (queue != null) {
			return queue.toArray(new ByteBuffer[queue.size()]);
		} else {
			return new ByteBuffer[0];
		}
	}

	

	/**
	 * {@inheritDoc}
	 */
	public final boolean readAvailableByDelimiter(String delimiter, WritableByteChannel outputChannel) throws IOException, ClosedConnectionException {
		readIncoming();
		
		return extractAvailableFromReadQueue(delimiter, outputChannel);
	}
	

	
	/**
	 * {@inheritDoc}
	 */
	public final void setWriteTransferRate(int delaySec) throws ClosedConnectionException, IOException {
		
		// inifinite delay -> switch off delay 
		if (delaySec == UNLIMITED) {
			
			// is delayed send is activated  -> flush out queue
			if (isSendDelayIsActivated) {
				isSendDelayIsActivated = false;

				// cancel deliveryTask
				delayedDelivererTask.cancel();
				
				// flush remaining out bytes 
				flushDelayQueue();
			}
			
			return;
		}
			
		
		isSendDelayIsActivated = true;
		if (delaySec > 1) {
			sendBytesPerSec = delaySec;
		} else {
			sendBytesPerSec = 1;
		}
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("send delay set with " + sendBytesPerSec + " bytes/sec"); 
		}
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void flushOutgoing() {		
		if (sendDelayQueue != null) {
			try {
				flushDelayQueue();
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("eroor occured by flushing. Reason: " + ioe);
				}
			}
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public final long write(ByteBuffer[] buffers) throws ClosedConnectionException, IOException {
		if (isSendDelayIsActivated) {
			return delayedWrite(buffers);
		} else {
			return super.write(buffers);
		}
	}

	
	
	private void flushDelayQueue() throws IOException {
		synchronized (sendDelayQueue) {
			if (!sendDelayQueue.isEmpty()) {
				QueueEntry[] entries = sendDelayQueue.toArray(new QueueEntry[sendDelayQueue.size()]);
				sendDelayQueue.clear();
				ByteBuffer[] buffers = new ByteBuffer[entries.length];
				for (int i = 0; i < buffers.length; i++) {
					buffers[i] = entries[i].getBuffer();
				}
				super.write(buffers);
			}
		}
	}

	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public final int write(ByteBuffer buffer) throws ClosedConnectionException, IOException {
		if (isSendDelayIsActivated) {
			return delayedWrite(new ByteBuffer[] {buffer});
		} else {
			return super.write(buffer);
		}
	}
	
	

	private int delayedWrite(ByteBuffer[] buffers) {
		
		int written = 0;
		
		// append to delay queue
		for (ByteBuffer buffer : buffers) {
			int size = buffer.remaining();
			if (size > 0) {
				written += size;
		 		int delayTime = (size * 1000) / sendBytesPerSec;
		 		synchronized (sendDelayQueue) {
		 			sendDelayQueue.offer(new QueueEntry(System.currentTimeMillis() + delayTime, buffer));
		 		}
			}			
		}

		// create delivery task if not exists
		if (delayedDelivererTask == null) {
			delayedDelivererTask = new TimerTask() {
				@Override
				public void run() {
					Thread.currentThread().setName(DELIVER_THREAD_PREXIX);
					long current = System.currentTimeMillis();
					
					synchronized(sendDelayQueue) {
						while(!sendDelayQueue.isEmpty()) {
							QueueEntry qe = sendDelayQueue.peek();
							if (current >= qe.getDeliveryTime()) {
								try {
									sendDelayQueue.remove(qe);
									writeOutgoing(new ByteBuffer[] {qe.getBuffer()});
									flushOutgoing();
								} catch (Throwable e) {
									if (LOG.isLoggable(Level.FINE)) {
										LOG.fine("Error occured while write delayed. Reason: " + e.toString());
									}
								}
							} else {
								break;
							}
						}
					}
				}
			};
			
			getTimer().schedule(delayedDelivererTask, 0, 500);
		}
		
		return written;
	}
	
	
	
	private static synchronized Timer getTimer() {
		if (timer == null) {
			timer = new Timer(true);
		}
		
		return timer;
	}

	

	
	
	private static final class QueueEntry {
		private long deliveryTime = 0;
		private ByteBuffer buffer = null;
		
		QueueEntry(long deliveryTime, ByteBuffer buffer) {
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