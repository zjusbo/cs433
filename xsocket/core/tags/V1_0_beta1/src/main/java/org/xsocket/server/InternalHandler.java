// $Id: InternalHandler.java 45 2006-06-22 16:21:07Z grro $

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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.util.TextUtils;


/**
 * internal representation of the handler   
 * 
 * @author grro@xsocket.org
 */
final class InternalHandler implements Cloneable {
	
	private static final Logger LOG = Logger.getLogger(InternalHandler.class.getName());


	private WorkerPool workerPool = null;
		
	private IHandler handler = null;
    private boolean isConnectHandler = false;
	private boolean isDataHandler = false;
	private boolean isTimeoutHandler = false;
	private boolean isConnetionScoped = false;
		
	/**
	 * constructor 
	 * 
	 * @param handler the external handler
	 */
	InternalHandler(IHandler handler) {
		this.handler = handler;

		if (handler instanceof IConnectHandler) {
			isConnectHandler = true;
		}
		
		if (handler instanceof IDataHandler) {
			isDataHandler = true;
		} 

		if (handler instanceof ITimeoutHandler) {
			isTimeoutHandler = true;
		}

		if (handler instanceof IConnectionScoped) {
			isConnetionScoped = true;
		}
	}

	
	/**
	 * set worker pool to perform processing
	 * 
	 * @param pool the executor pool
	 */
	public void setWorkerPool(WorkerPool workerPool) {
		this.workerPool = workerPool;
	}

	
	/**
	 * handles the connection opening
	 * 
	 * @param connection the connection
	 */
	public void onConnect(final NonBlockingConnection connection) {
		if (isConnectHandler) {
			workerPool.execute(new Runnable() {
				public void run() {
					try {
						synchronized (connection) {
							((IConnectHandler) handler).onConnect(connection);
						}
					} catch (Throwable e) {
						if (LOG.isLoggable(Level.FINER))  {
							LOG.finer("error occured by handling connection opening by handler " + handler.getClass().getName() + "#" + handler.hashCode() + ". Reason: "+ e.toString());
						}
					}
				}
			});	
		}	
	}

	
	/**
	 * handles the incoming data
	 * 
	 * @param connection the connection
	 */
	public void onData(final NonBlockingConnection connection) throws IOException {		
		if (isDataHandler) {
			workerPool.execute(new Runnable() {
				public void run() {
					try {
						synchronized (connection) {
							((IDataHandler) handler).onData(connection);
						}
					} catch (Throwable e) {
						if (LOG.isLoggable(Level.FINER))  {
							LOG.finer("error occured by handling connection data by handler " + handler.getClass().getName() + "#" + handler.hashCode() + ". Reason: "+ e.toString());
						}
					}
				}
			});	
		}
	}
	

	/**
	 * signals that the idle timeout has been occured
	 * 
	 * @param connection the connection
	 * 
	 * @throws IOException If some other I/O error occurs
	 */
	public void onIdleTimeout(NonBlockingConnection connection) throws IOException {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("idle timeout (" + TextUtils.printFormatedDuration(connection.getIdleTimeout()) + ") reached for connection " + connection.toString());
		}

		if (isTimeoutHandler) {
			boolean handled = ((ITimeoutHandler) handler).onIdleTimeout(connection);
			if (handled) {
				return;
			}
		}

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("connection will be closed");
		}
		connection.close();
	}

	
	/**
	 * signals that the connection timeout has been occured
	 * 
	 * @param connection the connection
	 * 
	 * @throws IOException If some other I/O error occurs
	 */
	public void onConnectionTimeout(NonBlockingConnection connection) throws IOException {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("connection timeout (" + TextUtils.printFormatedDuration(connection.getConnectionTimeout()) + ") reached for connection " + connection.toString());
		}

		if (isTimeoutHandler) {
			boolean handled = ((ITimeoutHandler) handler).onConnectionTimeout(connection);
			if (handled) {
				return;
			}
		}
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("connection will be closed");
		}
		connection.close();
	}

	
	
	/**
	 * handles the connection closing
	 * 
	 * @param connection the connection
	 */
	public void onClose(NonBlockingConnection connection) {
		if (isConnetionScoped) {
			close();
		}
	}


	/**
	 * closes the handler
	 */
	public void close() {
	}
	
	
	
	
	/**
	 * @see Object
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		if (isConnetionScoped) {
			InternalHandler copy = (InternalHandler) super.clone();
			copy.handler = (IHandler) ((IConnectionScoped) this.handler).clone();
			return copy;
		} else {
			return this;
		}
	}
}
