// $Id: ManagementTest.java 1237 2007-05-13 16:55:37Z grro $
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
package org.xsocket.stream.io.spi;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.xsocket.ClosedConnectionException;
import org.xsocket.DataConverter;
import org.xsocket.stream.io.spi.IIoHandler;
import org.xsocket.stream.io.spi.IIoHandlerCallback;
import org.xsocket.stream.io.spi.IIoHandlerContext;



/**
*
* @author grro@xsocket.org
*/
final class ExampleIoHandler implements IIoHandler {
		
	private static final Logger LOG = Logger.getLogger(ExampleIoHandler.class.getName());

	private final TaskQueue taskQueue = new TaskQueue();
	
	private volatile boolean isRunning = true;

	private LinkedList<ByteBuffer> readBuffer = new LinkedList<ByteBuffer>();
	private Socket socket = null;
	private Thread readerThread = null;

	private int conectionTimeoutSec = 60;
	private int idleTimeoutSec = 60;
	
	private IIoHandlerCallback callback = null;
	private IIoHandlerContext ctx = null;
	private String id = null;
		
	
		
	public ExampleIoHandler(String id, Socket socket, IIoHandlerContext ctx) {
		this.id = id;
		this.socket = socket;
		this.ctx = ctx;
	}
		
	
	public void init(final IIoHandlerCallback callbackHandler) throws IOException {
		this.callback = callbackHandler;
			
	
		readerThread = new Thread() {
			@Override
			public void run() {
				setName("readerThread");

				try {
					InputStream is = socket.getInputStream();
					byte[] buffer = new byte[1024];
					int length = 0;
						
					while ((length = is.read(buffer)) > 0) {
						if (!isRunning) {
							break;
						}

						ByteBuffer bb = ByteBuffer.wrap(buffer, 0, length);
						synchronized (readBuffer) {
							readBuffer.add(bb);
						}
			
						if (ctx.isAppHandlerListenForDataEvent()) {
							taskQueue.processTask(new Runnable() {
								public void run() {
									callbackHandler.onDataRead();
								}
							});
						}
							
						buffer = new byte[socket.getReceiveBufferSize()];
							
					}
						
				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
			}
		};	
		readerThread.start();
			
			
			
		if (ctx.isAppHandlerListenForConnectEvent()) {
			taskQueue.processTask(new Runnable() {
				public void run() {
					callbackHandler.onConnect();
				}
			});
			}			
		}

		
	public boolean isOpen() {
		return !socket.isClosed();
	}
	
	public void resumeRead() {
		// TODO Auto-generated method stub
		
	}
	
	public void suspendRead() {
		// TODO Auto-generated method stub
		
	}
		
		
	public void close(boolean immediate) throws IOException {	
		shutdown();
			
		if (ctx.isAppHandlerListenforDisconnectEvent()) {
			taskQueue.processTask(new Runnable() {
				public void run() {
					callback.onDisconnect();
				}
			});
		}
	}
	
	public int getPendingWriteDataSize() {
		return 0;
	}

		
	public void writeOutgoing(ByteBuffer buffer) throws ClosedConnectionException, IOException {
		try {
			byte[] bytes = DataConverter.toBytes(buffer);
			socket.getOutputStream().write(bytes);
			socket.getOutputStream().flush();
				
			callback.onWritten();
		} catch (final IOException ioe) {
			callback.onWriteException(ioe);
		}			
	}
	
	public void writeOutgoing(LinkedList<ByteBuffer> buffers) throws ClosedConnectionException, IOException {
		try {
			for (ByteBuffer buffer : buffers) {
				byte[] bytes = DataConverter.toBytes(buffer);
				socket.getOutputStream().write(bytes);
				socket.getOutputStream().flush();
			}
				
			callback.onWritten();
		} catch (final IOException ioe) {
			callback.onWriteException(ioe);
		}			
	}
	
	
	public LinkedList<ByteBuffer> drainIncoming() {
		LinkedList<ByteBuffer> result = null;
		synchronized (readBuffer) {
			result = readBuffer;
			readBuffer = new LinkedList<ByteBuffer>();
		}
			
		return result;
	}
	
	
	public InetAddress getLocalAddress() {
		return socket.getLocalAddress();
	}
		
	public int getLocalPort() {
		return socket.getLocalPort();
	}
		
	public InetAddress getRemoteAddress() {
		return socket.getInetAddress();
	}
		
	public int getRemotePort() {
		return socket.getPort();
	}
		
	
	public int getConnectionTimeoutSec() {
		return conectionTimeoutSec;
	}
	
	public String getId() {
		return id;
	}
	
	public int getIdleTimeoutSec() {
		return idleTimeoutSec;
	}
	
	public void setConnectionTimeoutSec(int timeout) {
		this.conectionTimeoutSec = timeout;
		
	}
	
	public void setIdleTimeoutSec(int timeout) {
		this.idleTimeoutSec = timeout;
	}
	
	public void setOption(String name, Object value) throws IOException {
		
	}
	
	public Map<String, Class> getOptions() {
		return null;
	}
	
	public Object getOption(String name) throws IOException {
		return null;
	}

	void shutdown() {
		isRunning = false;
		readerThread.interrupt();
		try {
			socket.close();
		} catch (Exception ignore) { }
	}
	
	
	
	
	
	private final class TaskQueue {
			
		private final TaskQueueProcessor taskProcessor = new TaskQueueProcessor();
		private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<Runnable>(); 
			
		
		public void processTask(Runnable task) {
				
			// multithreaded? -> put task into fifo queue and process it to ensure the right order 
			if (ctx.isMultithreaded()) {
				// add task to task queue
				tasks.offer(task);
			
				// process the task
				ctx.getWorkerpool().execute(taskProcessor);
					
			// no (task will be performed within acceptor/disptacher thread)				
			} else {
				task.run();
			}
		}
	}
	
	
	private final class TaskQueueProcessor implements Runnable {
		
		public void run() {
			Runnable task = taskQueue.tasks.poll();
			if (task != null) {
				try {					
					// synchroization required?
					if ((ctx.isMultithreaded()) && (!ctx.isAppHandlerThreadSafe())) {
						synchronized (ExampleIoHandler.this) {
							task.run();		
						} 

					// .. no
					} else {
						task.run();
					}					
				} catch (Exception e) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("error occured by proccesing task " + task);
					}
				}
			}
		}
	}
}
