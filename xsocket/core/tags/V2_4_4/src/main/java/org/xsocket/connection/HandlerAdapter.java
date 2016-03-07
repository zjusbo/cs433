/*
 *  Copyright (c) xsocket.org, 2006 - 2009. All rights reserved.
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
import org.xsocket.SerializedTaskQueue;
import org.xsocket.connection.ConnectionUtils.HandlerInfo;


/**
*
* @author grro@xsocket.org
*/
class HandlerAdapter  {
	
	private static final Logger LOG = Logger.getLogger(HandlerAdapter.class.getName());
	
	private static final IHandler NULL_HANDLER = new NullHandler();
	private static final HandlerAdapter NULL_HANDLER_ADAPTER = new HandlerAdapter(NULL_HANDLER, ConnectionUtils.getHandlerInfo(NULL_HANDLER));
	

	private final IHandler handler;
	private final HandlerInfo handlerInfo;
	

	HandlerAdapter(IHandler handler, HandlerInfo handlerInfo) {
		this.handler = handler;
		this.handlerInfo = handlerInfo;
	}
	
	
	static HandlerAdapter newInstance(IHandler handler) {
		if (handler == null) {
			return NULL_HANDLER_ADAPTER;
			
		} else {
			HandlerInfo handlerInfo = ConnectionUtils.getHandlerInfo(handler);
			return new HandlerAdapter(handler, handlerInfo);
		}
	}
	
	
	final IHandler getHandler() {
		return handler;
	}
	
	final HandlerInfo getHandlerInfo() {
		return handlerInfo;
	}
	
	   
    
    public boolean onConnect(final INonBlockingConnection connection, final SerializedTaskQueue taskQueue) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

        if (handlerInfo.isConnectHandler()) {
    
            if (handlerInfo.isHandlerUnsynchronized()) { 
                performOnConnect(connection, taskQueue);
                
            } else {
                Runnable task = new Runnable() {
                    public void run() {
                        try {
                            performOnConnect(connection, taskQueue);
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
                    taskQueue.performNonThreaded(task, connection.getWorkerpool());
                }
            }
            
        } 
        
        return true;
    }
    
    
    private boolean performOnConnect(INonBlockingConnection connection, SerializedTaskQueue taskQueue) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
        try {
            ((IConnectHandler) handler).onConnect(connection);
        } catch (MaxReadSizeExceededException mee) {
            closeSilence(connection);

        } catch (BufferUnderflowException bue) {
            //  ignore

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
    
    
    
    

    public boolean onData(final INonBlockingConnection connection, final SerializedTaskQueue taskQueue, final boolean ignoreException) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
      
        if (handlerInfo.isDataHandler()) {
            
            if (handlerInfo.isHandlerUnsynchronized()) {
                performOnData(connection, taskQueue, ignoreException);
            
            } else {
                
                Runnable task = new Runnable() {
                    public void run() {
                        try {
                           performOnData(connection, taskQueue, ignoreException);
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
                    taskQueue.performNonThreaded(task, connection.getWorkerpool());
                }
            }
            
        } else {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("assigned handler " + handler.getClass().getName() + " is not a data handler");
            }
        }
        
        return true;
    }   
    

	
	private boolean performOnData(INonBlockingConnection connection, SerializedTaskQueue taskQueue, boolean ignoreException) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		
	    // loop until readQueue is empty or nor processing has been done (readQueue has not been modified)
        while ((connection.available() != 0) && !connection.isReceivingSuspended()) {
            
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

                if (version == connection.getReadBufferVersion()) {
                    return true;
                }

                
            } catch (MaxReadSizeExceededException mee) {
            	if (!ignoreException) {
            		closeSilence(connection);
            	}
                return true;

            } catch (BufferUnderflowException bue) {
                return true;

            } catch (RuntimeException re) {
            	if (!ignoreException) {
	                if (LOG.isLoggable(Level.FINE)) {
	                    LOG.fine("[" + connection.getId() + "] ERROR closing connection because an error has been occured by handling data by appHandler. " + handler + " Reason: " + re.toString());
	                }
	                closeSilence(connection);
            	}
                throw re;
                        
            } catch (IOException ioe) {
            	if (!ignoreException) {
	                if (LOG.isLoggable(Level.FINE)) {
	                    LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling data by appHandler. " + handler + " Reason: " + ioe.toString());
	                }
	                closeSilence(connection);
            	}
                throw ioe;
            }
        } 
    		
		return false;
	}
	


    public boolean onDisconnect(final INonBlockingConnection connection, final SerializedTaskQueue taskQueue) throws IOException {
        if (handlerInfo.isDisconnectHandler()) {
    
            if (handlerInfo.isHandlerUnsynchronized()) { 
                performOnDisconnect(connection, taskQueue);
                
            } else {
                Runnable task = new Runnable() {
                    public void run() {
                        try {
                            performOnDisconnect(connection, taskQueue);
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
                    taskQueue.performNonThreaded(task, connection.getWorkerpool());
                }
            }
        }
        
        return true;
    }
    
    
	
	private boolean performOnDisconnect(INonBlockingConnection connection, SerializedTaskQueue taskQueue) throws IOException {
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
	
	
	

    public boolean onConnectException(final INonBlockingConnection connection, final SerializedTaskQueue taskQueue, final IOException ioe) throws IOException {

        if (handlerInfo.isConnectExceptionHandler()) {
            
            if (handlerInfo.isHandlerUnsynchronized()) { 
                performOnConnectException(connection, taskQueue, ioe);
                
            } else {
                Runnable task = new Runnable() {
                    public void run() {
                        try {
                            performOnConnectException(connection, taskQueue, ioe);
                        } catch (IOException e) {
                            if (LOG.isLoggable(Level.FINE)) {
                                LOG.fine("error occured while performing onDisconnect multithreaded " + getHandler() + " " + e.toString());
                            }
                        }
                    }
                };
                
                if (getHandlerInfo().isConnectExceptionHandlerMultithreaded()) {
                    taskQueue.performMultiThreaded(task, connection.getWorkerpool());
                    
                } else {
                    taskQueue.performNonThreaded(task, connection.getWorkerpool());
                }
            }
        }
        
        return true;
    }

    
	
	
	private boolean performOnConnectException(INonBlockingConnection connection, SerializedTaskQueue taskQueue, IOException ioe) throws IOException {

	    try {
            return ((IConnectExceptionHandler) handler).onConnectException(connection, ioe);
            
        }  catch (RuntimeException re) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onDisconnect by appHandler. " + handler + " Reason: " + re.toString());
            }
            closeSilence(connection);
            throw re;
            
        }  catch (IOException e) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onDisconnect by appHandler. " + handler + " Reason: " + ioe.toString());
            }
            closeSilence(connection);
            throw ioe;
        }   
	}
	
	
	    
    public boolean onIdleTimeout(final INonBlockingConnection connection, final SerializedTaskQueue taskQueue) throws IOException {
        
        if (handlerInfo.isIdleTimeoutHandler()) {
            
            if (handlerInfo.isHandlerUnsynchronized()) { 
                performOnIdleTimeout(connection, taskQueue);
                
            } else {
                Runnable task = new Runnable() {
                    public void run() {
                        try {
                            performOnIdleTimeout(connection, taskQueue);
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
                    taskQueue.performNonThreaded(task, connection.getWorkerpool());
                }
            }
        } else {
            closeSilence(connection);
        }
            
        return true;
    }
    
	
	
	private boolean performOnIdleTimeout(INonBlockingConnection connection, SerializedTaskQueue taskQueue) throws IOException {
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
        
        return true;
	}
	
	
	

    public boolean onConnectionTimeout(final INonBlockingConnection connection, final SerializedTaskQueue taskQueue) throws IOException {
        
        if (handlerInfo.isConnectionTimeoutHandler()) {
            
            if (handlerInfo.isHandlerUnsynchronized()) { 
                performOnConnectionTimeout(connection, taskQueue);
                
            } else {
                Runnable task = new Runnable() {
                    public void run() {
                        try {
                            performOnConnectionTimeout(connection, taskQueue);
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
                    taskQueue.performNonThreaded(task, connection.getWorkerpool());
                }
            }
            
        } else {
            closeSilence(connection);
        }
        
        return true;
    }
    
	
	private boolean performOnConnectionTimeout(INonBlockingConnection connection, SerializedTaskQueue taskQueue) throws IOException {
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
            //  ignore

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
		    // eat and log exception
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by closing connection " + connection + " " + e.toString());
			}
		}
	}

	

	@Execution(Execution.NONTHREADED)
	private static final class NullHandler implements IHandler {
	}
}
