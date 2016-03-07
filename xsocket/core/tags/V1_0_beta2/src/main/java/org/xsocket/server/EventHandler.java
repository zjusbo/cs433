// $Id: EventHandler.java 449 2006-12-09 07:02:10Z grro $
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
package org.xsocket.server;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.INonBlockingConnection;



/**
 * 
 * @author grro@xsocket.org
 */
final class EventHandler implements IManagedConnectionListener {
	
	private static final Logger LOG = Logger.getLogger(EventHandler.class.getName());
	
	private static final boolean TIME_TRACE_ON = false;

	
	private IDispatcher dispatcher = null;
	private WorkerPool workerPool = null;
	
	EventHandler(IDispatcher dispatcher) {
		this.dispatcher = dispatcher;
	}
	
	void setWorkerPool(WorkerPool workerPool) {
		this.workerPool = workerPool;
	}

	
	void onDispatcherRegisteredEvent(ManagedConnection connection) throws IOException {
		assert (!Dispatcher.isDispatcherThread());
		
		connection.init();
	}

	

	void onDispatcherReadableEvent(final ManagedConnection connection) {
		assert (Dispatcher.isDispatcherThread());
		
		if (connection.isOpen()) {
		
			// perform non-blocking read operation
			int readSize = connection.receive();
			
			
			// if is data handler -> notify handler
			if ((readSize > 0) && (connection.getAttachedAppHandlerTypeInfo().isDataHandler())) {
			
				workerPool.execute(new Runnable() {
					public void run() {
						timeTrace("TASK start task (CTX switch)");

						try {
							synchronized (connection) {
								((IDataHandler) connection.getAttachedAppHandler()).onData(connection);
							}
						} catch (BufferUnderflowException bue) {
							// ignore 
					
						} catch (Throwable e) {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("error occured by performing onData task. Reason: " + e.toString());
							}
							connection.close();
						}							
						connection.flushOutgoing();
						
						timeTrace("TASK  end ");
					}
				});
			}
		}
	}
	
		
			
	void onDispatcherWriteableEvent(ManagedConnection connection) throws IOException {
		assert (Dispatcher.isDispatcherThread());

		handleWriteableEvent(connection);
	}
	
	
	void handleWriteableEvent(ManagedConnection connection) throws IOException {		
		if (connection.isOpen()) {

			// darin write queue
			List<ByteBuffer> buffers = connection.drainWriteQueue();
			if (buffers != null) {
				//  perform non-blocking write operation
				ByteBuffer[] unwritten = connection.realWritePhysical(buffers.toArray(new ByteBuffer[buffers.size()]));
				if (unwritten != null) {
					connection.addAsFirstToWriteQueue(unwritten);
					connection.flushOutgoing();
				}
			}
		}
	}

	
	public void onConnectionConnectEvent(final ManagedConnection connection) {
		// TODO SSL handling should be improved, so that ssl processing will not be done
		// within disptacher thread
		assert (!Dispatcher.isDispatcherThread() || connection.isSSLActivated());
		
		// if is connect handler -> notify handler
		if (connection.getAttachedAppHandlerTypeInfo().isConnectHandler()) {			
			try {
				synchronized (connection) {
					((IConnectHandler) connection.getAttachedAppHandler()).onConnect(connection);
				}
			} catch (BufferUnderflowException bue) {
				// ignore 
		
			} catch (Throwable e) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by performing onConnect task. Reason: " + e.toString());
				}
				connection.close();
			}
				
			connection.flushOutgoing();
		} 			
	}
	
	
	
	
	public void onConnectionCloseEvent(final ManagedConnection connection) {
		// TODO start worker thread, when method is called by dispatcher thread
		// assert (!Dispatcher.isDispatcherThread());
		
		try {					
			
			// if is close handler -> notify handler
			if (connection.getAttachedAppHandlerTypeInfo().isDisconnectHandler()) {
				try {
					synchronized (connection) {
						((IDisconnectHandler) connection.getAttachedAppHandler()).onDisconnect(connection.getId());
					}
				}  catch (Exception ignore) { 
					// 	ignore
				}
			}		

			dispatcher.deregisterConnection(connection);
			
			// set full speed write rate -> this flushes the delay queue 
			connection.setWriteTransferRate(INonBlockingConnection.UNLIMITED);
			
			// initiate a manually triggered writable event
			handleWriteableEvent(connection);
			
			// destroy connection
			connection.destroy();
			
		} catch (Exception e) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by closing connection. Reason: " + e.toString());
			}
			
		}		
	}
	
	
	public void onConnectionDataToSendEvent(ManagedConnection connection) {
		
		dispatcher.announceWriteDemand(connection);
	}
	
	
	
	public void onConnectionIdleTimeoutEvent(ManagedConnection connection) {
		assert (!Dispatcher.isDispatcherThread());
		
		try {
			
			// if is timeout handler -> notify handler			
			if (connection.getAttachedAppHandlerTypeInfo().isTimeoutHandler()) {
				boolean handled = ((ITimeoutHandler) connection.getAttachedAppHandler()).onIdleTimeout(connection);
				connection.flushOutgoing();
				if (!handled) {
					connection.close();
				}
			} else {
				connection.close();
			}		
		} catch (Exception e) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by handling idle timeout event. Reason: " + e.toString());
			}				
		}		
	}
	
	
	public void onConnectionTimeoutEvent(ManagedConnection connection) {
		assert (!Dispatcher.isDispatcherThread());
		
		try {
			
			// if is timeout handler -> notify handler
			if (connection.getAttachedAppHandlerTypeInfo().isTimeoutHandler()) {
				boolean handled = ((ITimeoutHandler) connection.getAttachedAppHandler()).onConnectionTimeout(connection);
				connection.flushOutgoing();
				if (!handled) {
					connection.close();
				}
			} else {
				connection.close();
			}
		} catch (Exception e) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by handling coonection timeout event. Reason: " + e.toString());
			}				
		}		
	}
	
	
	private static void timeTrace(String msg) {
		if (TIME_TRACE_ON) {
			if (msg == null) {
				System.out.println("");
			} else {
				System.out.println((System.nanoTime() / 1000) + " microsec [" + Thread.currentThread().getName() + "] " + msg);
			}
		}
	}
}



