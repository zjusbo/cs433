/*
 *  Copyright (c) xsocket.org, 2006 - 2008. All rights reserved.
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
package org.xsocket.connection.multiplexed;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.SerializedTaskQueue;
import org.xsocket.connection.IConnectionScoped;
import org.xsocket.connection.IHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.multiplexed.MultiplexedUtils.HandlerInfo;



/**
*
* @author grro@xsocket.org
*/
class MultithreadedPipelineHandlerAdapter extends PipelineHandlerAdapter {
	
	private static final Logger LOG = Logger.getLogger(MultithreadedPipelineHandlerAdapter.class.getName());

	private final SerializedTaskQueue taskQueue = new SerializedTaskQueue();
	
	
	MultithreadedPipelineHandlerAdapter(IHandler handler, HandlerInfo handlerInfo) {
		super(handler, handlerInfo);
	}

	
	@Override
	public boolean onConnect(final INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		Runnable task = new Runnable() {
			public void run() {
				try {
					MultithreadedPipelineHandlerAdapter.super.onConnect(connection);
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("error occured while performing onConnect multithreaded " + getHandler() + " " + ioe.toString());
					}
				}
			}
		};
		
		if (getHandlerInfo().isConnectHandlerMultithreaded()) {
			taskQueue.performMultiThreaded(task, connection.getWorkerpool());
			
		} else {
			taskQueue.performNonThreaded(task);
		}
		
		return true;
	}
	
	
	@Override
	public boolean onData(final INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		Runnable task = new Runnable() {
			public void run() {
				try {
					MultithreadedPipelineHandlerAdapter.super.onData(connection);
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("error occured while performing onData multithreaded " + getHandler() + " " + ioe.toString());
					}
				}
			}
		};
		
		if (getHandlerInfo().isDataHandlerMultithreaded()) {
			taskQueue.performMultiThreaded(task, connection.getWorkerpool());
			
		} else {
			taskQueue.performNonThreaded(task);
		}
		
		return true;
	}	
	

	
	@Override
	public boolean onDisconnect(final INonBlockingConnection connection) throws IOException {
		Runnable task = new Runnable() {
			public void run() {
				try {
					MultithreadedPipelineHandlerAdapter.super.onDisconnect(connection);
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("error occured while performing onDisconnect multithreaded " + getHandler() + " " + ioe.toString());
					}
				}
			}
		};
		
		if (getHandlerInfo().isDisconnectHandlerMultithreaded()) {
			taskQueue.performMultiThreaded(task, connection.getWorkerpool());
			
		} else {
			taskQueue.performNonThreaded(task);
		}
		
		return true;
	}
	
	
	@Override
	public boolean onIdleTimeout(final INonBlockingConnection connection) throws IOException {
		Runnable task = new Runnable() {
			public void run() {
				try {
					MultithreadedPipelineHandlerAdapter.super.onIdleTimeout(connection);
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("error occured while performing onIdleTimeout multithreaded " + getHandler() + " " + ioe.toString());
					}
				}
			}
		};
		
		if (getHandlerInfo().isIdleTimeoutHandlerMultithreaded()) {
			taskQueue.performMultiThreaded(task, connection.getWorkerpool());
			
		} else {
			taskQueue.performNonThreaded(task);
		}
		
		return true;
	}
	
	
	@Override
	public boolean onConnectionTimeout(final INonBlockingConnection connection) throws IOException {
		Runnable task = new Runnable() {
			public void run() {
				try {
					MultithreadedPipelineHandlerAdapter.super.onConnectionTimeout(connection);
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("error occured while performing onConnectionTimeout multithreaded " + getHandler() + " " + ioe.toString());
					}
				}
			}
		};
		
		if (getHandlerInfo().isConnectionTimeoutHandlerMultithreaded()) {
			taskQueue.performMultiThreaded(task, connection.getWorkerpool());
			
		} else {
			taskQueue.performNonThreaded(task);
		}
		
		return true;
	}
	
	
	PipelineHandlerAdapter getConnectionInstance() {
		if (getHandlerInfo().isConnectionScoped()) {
			try {
				IHandler hdlCopy = (IHandler) ((IConnectionScoped) getHandler()).clone();
				return new MultithreadedPipelineHandlerAdapter(hdlCopy, getHandlerInfo());
			} catch (CloneNotSupportedException cnse) {
				throw new RuntimeException(cnse.toString());
			}
			
		} else {
			return new MultithreadedPipelineHandlerAdapter(getHandler(), getHandlerInfo());
		}
	}
}
