// $Id: HandlerChain.java 1312 2007-06-09 12:39:47Z grro $

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

package org.xsocket.connection;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.Execution;
import org.xsocket.ILifeCycle;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.Resource;
import org.xsocket.connection.ConnectionUtils.HandlerInfo;




/**
 * Implements a handler chain. Each handler of the chain will be called (in the registering order),
 * until one handler signal by the return value true, that the event has been handled. In 
 * this case the remaining handlers will not be called.  <br><br>
 * 
 * Nested chains is not supported yet
 *   
 * <br>
 * E.g. 
 * <pre>
 *   ...
 *   HandlerChain tcpBasedSpamfilter = new HandlerChain();
 *   tcpBasedSpamfilter.addLast(new BlackIPFilter()); 
 *   tcpBasedSpamfilter.addLast(new FirstConnectRefuseFilter());
 *   
 *   HandlerChain mainChain = new HandlerChain();
 *   mainChain.addLast(tcpBasedSpamfilter);
 *   mainChain.addLast(new SmtpProtocolHandler());
 *   
 *   IMultithreadedServer smtpServer = new MultithreadedServer(port, mainChain);
 *   StreamUtils.start(server);
 *   ...
 *   
 * </pre>

 * 
 * 
 * @author grro@xsocket.org
 */
public final class HandlerChain implements  IHandler, IConnectHandler, IDataHandler, IDisconnectHandler, IConnectionTimeoutHandler, IIdleTimeoutHandler, ILifeCycle {

	private static final Logger LOG = Logger.getLogger(HandlerChain.class.getName());
	

	@Resource
	private IServer server = null;


	// handlers 
	private final List<IHandler> handlers = new ArrayList<IHandler>();

	private final List<ILifeCycle> lifeCycleChain = new ArrayList<ILifeCycle>();

	private boolean isOnConnectPathMultithreaded = false;
	private final List<IConnectHandler> connectHandlerChain = new ArrayList<IConnectHandler>();

	private boolean isOnDataPathMultithreaded = false;
	private final List<IDataHandler> dataHandlerChain = new ArrayList<IDataHandler>();
	
	private boolean isOnDisconnectPathMultithreaded = false;
	private final List<IDisconnectHandler> disconnectHandlerChain = new ArrayList<IDisconnectHandler>();

	private boolean isOnIdleTimeoutPathMultithreaded = false;
	private final List<IIdleTimeoutHandler> idleTimeoutHandlerChain = new ArrayList<IIdleTimeoutHandler>();

	private boolean isOnConnectionTimeoutPathMultithreaded = false;
	private final List<IConnectionTimeoutHandler> connectionTimeoutHandlerChain = new ArrayList<IConnectionTimeoutHandler>();
	
	

	/**
	 * constructor 
	 * 
	 */
	public HandlerChain() {
		
	}
	
	
	/**
	 * constructor 
	 * 
	 * @param handlers the initial handlers 
	 */
	public HandlerChain(List<IHandler> handlers) {
		for (IHandler hdl : handlers) {
			addLast(hdl);
		}
	}

	
	
	public void onInit() {
		for (IHandler handler : handlers) {
			ConnectionUtils.injectServerField(server, handler);
		}
		
		for (ILifeCycle lifeCycle : lifeCycleChain) {
			lifeCycle.onInit();
		}
	}
	
	
	public void onDestroy() throws IOException {
		for (ILifeCycle lifeCycle : lifeCycleChain) {
			lifeCycle.onDestroy();
		}		
	}


	
	
	
	/**
	 * add a handler to the end og the chain
	 * 
	 * @param handler the handler to add
	 */
	public void addLast(IHandler handler) {
		
		if (handler instanceof HandlerChain) {
			throw new RuntimeException("a nested chains are not supported");
		}
		
		handlers.add(handler);
		
		computePath();
	}
	
	
	
	private void computePath() {
		
		lifeCycleChain.clear();
		
		connectHandlerChain.clear();
		isOnConnectPathMultithreaded = false;
		
		dataHandlerChain.clear();
		isOnDataPathMultithreaded = false;

		disconnectHandlerChain.clear();
		isOnDisconnectPathMultithreaded = false;

		idleTimeoutHandlerChain.clear();
		isOnIdleTimeoutPathMultithreaded = false;

		connectionTimeoutHandlerChain.clear();
		isOnConnectionTimeoutPathMultithreaded = false;

		
		for (IHandler handler : handlers) {
			HandlerInfo handlerInfo = ConnectionUtils.getHandlerInfo(handler);

			if (handlerInfo.isLifeCycle()) {
				lifeCycleChain.add((ILifeCycle) handler);
			}
			
			
			if (handlerInfo.isConnectHandler()) {
				connectHandlerChain.add((IConnectHandler) handler);
				isOnConnectPathMultithreaded = isOnConnectPathMultithreaded || handlerInfo.isConnectHandlerMultithreaded();
			}
			
			
			if (handlerInfo.isDataHandler()) {
				dataHandlerChain.add((IDataHandler) handler);
				isOnDataPathMultithreaded = isOnDataPathMultithreaded || handlerInfo.isDataHandlerMultithreaded();
			}
			
			if (handlerInfo.isDisconnectHandler()) {
				disconnectHandlerChain.add((IDisconnectHandler) handler);
				isOnDisconnectPathMultithreaded = isOnDisconnectPathMultithreaded || handlerInfo.isDisconnectHandlerMultithreaded();
			}
			
			if (handlerInfo.isIdleTimeoutHandler()) {
				idleTimeoutHandlerChain.add((IIdleTimeoutHandler) handler);
				isOnIdleTimeoutPathMultithreaded = isOnIdleTimeoutPathMultithreaded || handlerInfo.isIdleTimeoutHandlerMultithreaded();
			}
			
			if (handlerInfo.isConnectionTimeoutHandler()) {
				connectionTimeoutHandlerChain.add((IConnectionTimeoutHandler) handler);
				isOnConnectionTimeoutPathMultithreaded = isOnConnectionTimeoutPathMultithreaded || handlerInfo.isConnectionTimeoutHandlerMultithreaded();
			}
		}		
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Execution(Execution.NONTHREADED)	
	public boolean onConnect(final INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		if (connectHandlerChain.isEmpty()) {
			return false;
		}
		
		if (isOnConnectPathMultithreaded) {

			Runnable task = new Runnable() {
				
				public void run() {
					try {
						callOnConnectCallback(connection);
					} catch (IOException ioe) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("Error occured by calling onConnect callback " + ioe.toString());
						}
					}
				}
			};
			
			connection.getWorkerpool().execute(task);
			
		} else {
			callOnConnectCallback(connection);
		}
		
		return true;
	}
	
	
	
	private boolean callOnConnectCallback(INonBlockingConnection connection) throws IOException {
		
		for (IConnectHandler connectHandler : connectHandlerChain) {
			boolean result = connectHandler.onConnect(connection);
			if (result == true) {
				return true;
			}
		}	
		
		return false;
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Execution(Execution.NONTHREADED)
	public boolean onData(final INonBlockingConnection connection) throws IOException {
		
		if (dataHandlerChain.isEmpty()) {
			return false;
		}
		
		if (isOnDataPathMultithreaded) {

			Runnable task = new Runnable() {
				
				public void run() {
					try {
						callOnDataCallback(connection);
					} catch (IOException ioe) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("Error occured by calling onData callback " + ioe.toString());
						}
					}
				}
			};
			
			connection.getWorkerpool().execute(task);
			
		} else {
			callOnDataCallback(connection);
		}
		
		return true;
	}
	
	
	
	private boolean callOnDataCallback(INonBlockingConnection connection) throws IOException {
		
		for (IDataHandler dataHandler : dataHandlerChain) {
			boolean result = dataHandler.onData(connection);
			if (result == true) {
				return true;
			}
		}	
		
		return false;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Execution(Execution.NONTHREADED)	
	public boolean onDisconnect(final INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		if (disconnectHandlerChain.isEmpty()) {
			return false;
		}
		
		if (isOnDisconnectPathMultithreaded) {

			Runnable task = new Runnable() {
				
				public void run() {
					try {
						callOnDisconnectCallback(connection);
					} catch (IOException ioe) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("Error occured by calling onDisconnect callback " + ioe.toString());
						}
					}
				}
			};
			
			connection.getWorkerpool().execute(task);

		} else {
			callOnDisconnectCallback(connection);
		}
		
		return true;
	}
	
	
	
	private boolean callOnDisconnectCallback(INonBlockingConnection connection) throws IOException {
		
		for (IDisconnectHandler disconnectHandler : disconnectHandlerChain) {
			boolean result = disconnectHandler.onDisconnect(connection);
			if (result == true) {
				return true;
			}
		}	
		
		return false;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Execution(Execution.NONTHREADED)	
	public boolean onIdleTimeout(final INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		if (idleTimeoutHandlerChain.isEmpty()) {
			return false;
		}
		
		if (isOnIdleTimeoutPathMultithreaded) {

			Runnable task = new Runnable() {
				
				public void run() {
					try {
						callOnIdleTimeoutCallback(connection);
					} catch (IOException ioe) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("Error occured by calling onIdleTimeout callback " + ioe.toString());
						}
					}
				}
			};
			
			connection.getWorkerpool().execute(task);

		} else {
			callOnIdleTimeoutCallback(connection);
		}
		
		return true;
	}
	
	
	
	private boolean callOnIdleTimeoutCallback(INonBlockingConnection connection) throws IOException {
		
		for (IIdleTimeoutHandler idleTimeoutHandler : idleTimeoutHandlerChain) {
			boolean result = idleTimeoutHandler.onIdleTimeout(connection);
			if (result == true) {
				return true;
			}
		}	
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + connection.getId() + "] closing connection because idle timeout has been occured and timeout handler returns true)");
		}
		
		connection.close();
		
		return true;
	}


	/**
	 * {@inheritDoc}
	 */
	@Execution(Execution.NONTHREADED)	
	public boolean onConnectionTimeout(final INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		if (connectionTimeoutHandlerChain.isEmpty()) {
			return false;
		}
		
		if (isOnConnectionTimeoutPathMultithreaded) {

			Runnable task = new Runnable() {
				
				public void run() {
					try {
						callOnConnectionTimeoutCallback(connection);
					} catch (IOException ioe) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("Error occured by calling onConnectionTimeout callback " + ioe.toString());
						}
					}
				}
			};
			
			connection.getWorkerpool().execute(task);

		} else {
			callOnConnectionTimeoutCallback(connection);
		}
		
		return true;
	}
	
	
	
	private boolean callOnConnectionTimeoutCallback(INonBlockingConnection connection) throws IOException {
		
		for (IConnectionTimeoutHandler connectionTimeoutHandler : connectionTimeoutHandlerChain) {
			boolean result = connectionTimeoutHandler.onConnectionTimeout(connection);
			if (result == true) {
				return true;
			}
		}	
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + connection.getId() + "] closing connection because coonection timeout has been occured and timeout handler returns true)");
		}
		
		connection.close();
		
		return true;
	}
}
