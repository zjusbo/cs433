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
package org.xsocket.connection;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.Execution;
import org.xsocket.ILifeCycle;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.ConnectionUtils.HandlerInfo;


/**
*
* @author grro@xsocket.org
*/
class HandlerAdapter implements IConnectHandler, IDataHandler, IDisconnectHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, ILifeCycle {
	
	private static final Logger LOG = Logger.getLogger(HandlerAdapter.class.getName());
	
	
	private static final IHandler NULL_HANDLER = new NullHandler();
	private static final HandlerInfo NULL_HANDLER_INFO = ConnectionUtils.getHandlerInfo(NULL_HANDLER);
	

	private IHandler handler = null;
	private HandlerInfo handlerInfo = null;
	

	HandlerAdapter(IHandler handler, HandlerInfo handlerInfo) {
		this.handler = handler;
		this.handlerInfo = handlerInfo;
	}
	
	
	static HandlerAdapter newInstance(IHandler handler) {
		if (handler == null) {
			return new HandlerAdapter(NULL_HANDLER, NULL_HANDLER_INFO);
			
		} else {
			HandlerInfo handlerInfo = ConnectionUtils.getHandlerInfo(handler);
			if (handlerInfo.isNonthreaded()) {
				return new HandlerAdapter(handler, handlerInfo);
			} else {
				return new MultithreadedHandlerAdapter(handler, handlerInfo);
			}
		}
	}
	
	
	final IHandler getHandler() {
		return handler;
	}
	
	final HandlerInfo getHandlerInfo() {
		return handlerInfo;
	}
	
	public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		if (handlerInfo.isConnectHandler()) {
			try {
				((IConnectHandler) handler).onConnect(connection);
			} catch (MaxReadSizeExceededException mee) {
				closeSilence(connection);

			} catch (BufferUnderflowException bue) {
				// 	ignore

			} catch (RuntimeException re) {		
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onConnect by appHandler. " + handler + " Reason: " + re.toString());
				}
				closeSilence(connection);
				throw re;
				
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onConnect by appHandler. " + handler + " Reason: " + ioe.toString());
				}
				closeSilence(connection);
				throw ioe;
			}

			return false;
		}
		
		return false;
	}

	
	public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		
		if (handlerInfo.isDataHandler()) {
	
			if (LOG.isLoggable(Level.FINER)) {
				if (connection.available() == 0) {
					LOG.fine("no data available. Don't call the handler " + handler);
				}
			}
			
			
			// loop until readQueue is empty or nor processing has been done (readQueue has not been modified)
			while (connection.available() != 0) {
				
				if (connection.getHandler() != handler) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("handler " + handler.getClass().getName() + " replaced by " + 
								 connection.getHandler().getClass().getName() + ". stop handling data for old handler");
					}
					return true;
				}
				
				int version = connection.getReadBufferVersion();
				
				// calling onData method of the handler (return value will be ignored)
				try {
					if (LOG.isLoggable(Level.FINER)) {
						LOG.fine("calling onData method of handler " + handler);
					}
					
					
					((IDataHandler) handler).onData(connection);

					int newVersion = connection.getReadBufferVersion();
					if (newVersion != version) {
						version = newVersion;
					} else {
						return true;
					}
	
					
				} catch (MaxReadSizeExceededException mee) {
					closeSilence(connection);
					return true;

				} catch (BufferUnderflowException bue) {
					// 	ignore
					return true;

				} catch (RuntimeException re) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling data by appHandler. " + handler + " Reason: " + re.toString());
					}
					closeSilence(connection);
					throw re;
							
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling data by appHandler. " + handler + " Reason: " + ioe.toString());
					}
					closeSilence(connection);
					throw ioe;
				}
			} 
		}

		return false;
	}
	
	
	public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
		if (handlerInfo.isDisconnectHandler()) {
			try {
				return ((IDisconnectHandler) handler).onDisconnect(connection);
				
			}  catch (RuntimeException re) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onDisconnect by appHandler. " + handler + " Reason: " + re.toString());
				}
				closeSilence(connection);
				throw re;
				
			}  catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onDisconnect by appHandler. " + handler + " Reason: " + ioe.toString());
				}
				closeSilence(connection);
				throw ioe;
			}	
		}
		
		return false;
	}
	
	
	public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
		if (handlerInfo.isIdleTimeoutHandler()) {
			try {
				boolean isHandled = ((IIdleTimeoutHandler) handler).onIdleTimeout(connection);
				if (!isHandled) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + connection.getId() + "] closing connection because idle timeout has been occured and timeout handler returns true)");
					}
					closeSilence(connection);
				}
				
				return isHandled;
				
			} catch (MaxReadSizeExceededException mee) {
				closeSilence(connection);

			} catch (BufferUnderflowException bue) {
				// ignore

			} catch (RuntimeException re) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onIdleTimeout by appHandler. " + handler + " Reason: " + re.toString());
				}
				closeSilence(connection);
				throw re;
				
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onIdleTimeout by appHandler. " + handler + " Reason: " + ioe.toString());
				}
				closeSilence(connection);
				throw ioe;
			}
		
		} else {
			closeSilence(connection);
		}
		
		return true;
	}
	
	
	
	public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
		if (handlerInfo.isConnectionTimeoutHandler()) {
			try {
				boolean isHandled = ((IConnectionTimeoutHandler) handler).onConnectionTimeout(connection);
				if (!isHandled) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + connection.getId() + "] closing connection because connection timeout has been occured and timeout handler returns true)");
					}
					closeSilence(connection);
				}

			} catch (MaxReadSizeExceededException mee) {
				closeSilence(connection);

			} catch (BufferUnderflowException bue) {
				// 	ignore

			} catch (RuntimeException re) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onConnectionTimeout by appHandler. " + handler + " Reason: " + re.toString());
				}
				closeSilence(connection);
				throw re;
			
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onConnectionTimeout by appHandler. " + handler + " Reason: " + ioe.toString());
				}
				closeSilence(connection);
				throw ioe;
			}
			
		} else {
			closeSilence(connection);
		}
		
		return true;
	}
	
	
	public final void onInit() {
		if (handlerInfo.isLifeCycle()) {
			((ILifeCycle) handler).onInit();
		}		
	}
	
	
	public final void onDestroy() {
		if (handlerInfo.isLifeCycle()) {
			try {
				((ILifeCycle) handler).onDestroy();
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("exception occured by destroying " + handler + " " + ioe.toString());
				}
			}
		}		
	}
	
	
	HandlerAdapter getConnectionInstance() {
		if (handlerInfo.isConnectionScoped()) {
			try {
				IHandler hdlCopy = (IHandler) ((IConnectionScoped) handler).clone();
				return new HandlerAdapter(hdlCopy, handlerInfo);
			} catch (CloneNotSupportedException cnse) {
				throw new RuntimeException(cnse.toString());
			}
			
		} else {
			return this;
		}
	}
	
	
	private void closeSilence(INonBlockingConnection connection) {
		try {
			connection.close();
		} catch (Exception e) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by closing connection " + connection + " " + e.toString());
			}
		}
	}

	

	@Execution(Execution.NONTHREADED)
	private static final class NullHandler implements IHandler {
	}
}
