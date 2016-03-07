
// $Id: MultithreadedServer.java 41 2006-06-22 06:30:23Z grro $
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
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * internal representation of the handler   
 * 
 * @author grro@xsocket.org
 */
final class InternalHandler implements Cloneable {
	
	private static final Logger LOG = Logger.getLogger(InternalHandler.class.getName());


	private ExecutorService pool = null;
		
	private IHandler handler = null;
    private boolean isConnectHandler = false;
	private boolean isDataHandler = false;
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
		
		if (handler instanceof IConnectionScoped) {
			isConnetionScoped = true;
		}
	}

	
	/**
	 * set the executor pool to perform processing
	 * 
	 * @param pool the executor pool
	 */
	public void setExecutorPool(ExecutorService pool) {
		this.pool = pool;
	}

	
	/**
	 * handles the connection opening
	 * 
	 * @param connection the connection
	 */
	public void onConnectionOpened(final NonBlockingConnection connection) {
		if (isConnectHandler) {
			pool.execute(new Runnable() {
				public void run() {
					try {
						((IConnectHandler) handler).onConnectionOpening(connection);
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
	public void onDataReceived(final NonBlockingConnection connection) throws IOException {		
		if (isDataHandler) {
			pool.execute(new Runnable() {
				public void run() {
					try {
						((IDataHandler) handler).onData(connection);
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
	 * handles the connection closing
	 * 
	 * @param connection the connection
	 */
	public void onConnectionClose(NonBlockingConnection connection) {
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

