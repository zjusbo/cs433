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
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;
import org.xsocket.Execution;
import org.xsocket.ILifeCycle;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.SerializedTaskQueue;


/**
*
* @author grro@xsocket.org
*/
class HandlerAdapter  {
	
	private static final Logger LOG = Logger.getLogger(HandlerAdapter.class.getName());
	
	private static final IHandler NULL_HANDLER = new NullHandler();
	private static final HandlerAdapter NULL_HANDLER_ADAPTER = new HandlerAdapter(NULL_HANDLER, ConnectionUtils.getHandlerInfo(NULL_HANDLER));
	

	private final IHandler handler;
	private final IHandlerInfo handlerInfo;
	

	HandlerAdapter(IHandler handler, IHandlerInfo handlerInfo) {
		this.handler = handler;
		this.handlerInfo = handlerInfo;
	}
	
	
	static HandlerAdapter newInstance(IHandler handler) {
		if (handler == null) {
			return NULL_HANDLER_ADAPTER;
			
		} else {
			IHandlerInfo handlerInfo = ConnectionUtils.getHandlerInfo(handler);
			return new HandlerAdapter(handler, handlerInfo);
		}
	}
	
	
	final IHandler getHandler() {
		return handler;
	}
	
	final IHandlerInfo getHandlerInfo() {
		return handlerInfo;
	}
	
	
	private String printHandler() {
	    return  handler.getClass().getName() + "#" + handler.hashCode();
	}
	   
    
    public boolean onConnect(final INonBlockingConnection connection, final SerializedTaskQueue taskQueue, final Executor workerpool) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

        if (handlerInfo.isConnectHandler()) {
    
            if (handlerInfo.isUnsynchronized()) { 
                performOnConnect(connection, taskQueue);
                
            } else {
                if (getHandlerInfo().isConnectHandlerMultithreaded()) {
                    taskQueue.performMultiThreaded(new PerformOnConnectTask(connection, taskQueue), workerpool);
                    
                } else {
                    taskQueue.performNonThreaded(new PerformOnConnectTask(connection, taskQueue), workerpool);
                }
            }
            
        } 
        
        return true;
    }
    
    
    
    private final class PerformOnConnectTask implements Runnable {
    

        private final INonBlockingConnection connection;
        private final SerializedTaskQueue taskQueue;
        
        
        public PerformOnConnectTask(INonBlockingConnection connection, SerializedTaskQueue taskQueue) {
            this.connection = connection;
            this.taskQueue = taskQueue;
        }
        
        
        public void run() {
            
            try {
                
                performOnConnect(connection, taskQueue);
                
            } catch (IOException ioe) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("[" + connection.getId() + "] closing connection. An IO exception error occured while performing onConnect multithreaded " + printHandler() + " " + ioe.toString());
                }
                closeSilence(connection);
                
            } catch (RuntimeException rt) {
                LOG.warning("[" + connection.getId() + "] runtime exception occured while performing onConnectt multithreaded " + printHandler() + " " + rt.toString());
                
            } catch (Error t) {
                LOG.warning("[" + connection.getId() + "] closing connection. Error occured by performing onConnect of " + printHandler() +  " " + t.toString());
                NonBlockingConnection.closeSilence(connection);
            }

        }
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
                LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onConnect by appHandler. " + printHandler() + " Reason: " + re.toString());
            }
            closeSilence(connection);
            throw re;
            
        } catch (IOException ioe) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onConnect by appHandler. " + printHandler() + " Reason: " + ioe.toString());
            }
            closeSilence(connection);
            throw ioe;
        }
        
        return false;
    }
    
    
    
    

    public boolean onData(INonBlockingConnection connection, SerializedTaskQueue taskQueue, Executor workerpool, boolean ignoreException, boolean isUnsynchronized) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
        if (handlerInfo.isDataHandler()) {
            
            if (handlerInfo.isUnsynchronized()) {
                performOnData(connection, taskQueue, ignoreException);
            
            } else {
                
            	 if (getHandlerInfo().isDataHandlerMultithreaded()) {
                     taskQueue.performMultiThreaded(new PerformOnDataTask(connection, taskQueue, ignoreException), workerpool);
                     
                 } else {
                	 if (isUnsynchronized) {
                		 performOnData(connection, taskQueue, ignoreException);
                		 
                	 } else {
                		 taskQueue.performNonThreaded(new PerformOnDataTask(connection, taskQueue, ignoreException), workerpool);
                	 }
                 }
            }
            
        } else {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("[" + connection.getId() + "] assigned handler " + printHandler() + " is not a data handler");
            }
        }
        return true;
    }   

	
    private final class PerformOnDataTask implements Runnable {
    	
    	private final INonBlockingConnection connection;
    	private final SerializedTaskQueue taskQueue;
    	private final boolean ignoreException; 
    	
    	public PerformOnDataTask(INonBlockingConnection connection, SerializedTaskQueue taskQueue, boolean ignoreException) {
    		this.connection = connection;
    		this.taskQueue = taskQueue;
    		this.ignoreException = ignoreException;
		}
    	
    	
        public void run() {
            
            try {
                
               performOnData(connection, taskQueue, ignoreException);
               
            } catch (IOException ioe) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("[" + connection.getId() + "] closing connection. An IO exception occured while performing onData multithreaded " + printHandler() + " " + ioe.toString());
                }
                NonBlockingConnection.closeSilence(connection);
                
            } catch (RuntimeException rt) {
                LOG.warning("[" + connection.getId() + "] runtime exception occured while performing onData multithreaded " + printHandler() + " " + rt.toString());

            } catch (Error t) {
                LOG.warning("[" + connection.getId() + "] closing connection. Error occured by performing onData of " + printHandler() +  " " + t.toString());
                NonBlockingConnection.closeSilence(connection);
            }
        }
        
        @Override
        public String toString() {
            return "PerformOnDataTask#" + hashCode() + " "  + connection.getId();
        }
    }
    

	
	private boolean performOnData(INonBlockingConnection connection, SerializedTaskQueue taskQueue, boolean ignoreException) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
	    
	    // loop until readQueue is empty or nor processing has been done (readQueue has not been modified)
        while ((connection.available() != 0) && !connection.isReceivingSuspended()) {
            
            if (connection.getHandler() != handler) {
                if (LOG.isLoggable(Level.FINE)) {
                	LOG.fine("[" + connection.getId() + "] handler " + " replaced by " + connection.getHandler() + ". stop handling data for old handler");
                }
                return true;
            }
            
            int version = connection.getReadBufferVersion();
            
            // calling onData method of the handler (return value will be ignored)
            try {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("[" + connection.getId() + "] calling onData method of handler " + printHandler());
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
	                    LOG.fine("[" + connection.getId() + "] ERROR closing connection because an error has been occured by handling data by appHandler. " + printHandler() + " Reason: " + DataConverter.toString(re));
	                }
	                closeSilence(connection);
            	}
                throw re;
                        
            } catch (IOException ioe) {
            	if (!ignoreException) {
	                if (LOG.isLoggable(Level.FINE)) {
	                    LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling data by appHandler. " + printHandler() + " Reason: " + DataConverter.toString(ioe));
	                }
	                closeSilence(connection);
            	}
                throw ioe;
            }
        } 
    		
		return false;
	}
	


    public boolean onDisconnect(INonBlockingConnection connection, SerializedTaskQueue taskQueue, Executor workerpool, boolean isUnsynchronized) throws IOException {
        if (handlerInfo.isDisconnectHandler()) {
    
            if (handlerInfo.isUnsynchronized()) { 
                performOnDisconnect(connection, taskQueue);
                
            } else {
                if (getHandlerInfo().isDisconnectHandlerMultithreaded()) {
                    taskQueue.performMultiThreaded(new PerformOnDisconnectTask(connection, taskQueue), workerpool);
                    
                } else {
                	if (isUnsynchronized) {
                		performOnDisconnect(connection, taskQueue);
                	} else {
                		taskQueue.performNonThreaded(new PerformOnDisconnectTask(connection, taskQueue), workerpool);
                	}
                }
            }
        }
        
        return true;
    }
    
        
    
    private final class PerformOnDisconnectTask implements Runnable {

    	private final INonBlockingConnection connection;
    	private final SerializedTaskQueue taskQueue;
    	
    	
    	public PerformOnDisconnectTask(INonBlockingConnection connection, SerializedTaskQueue taskQueue) {
    		this.connection = connection;
    		this.taskQueue = taskQueue;
		}
    	
    	
        public void run() {
            
            try {
                
                performOnDisconnect(connection, taskQueue);
                
            } catch (IOException ioe) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("[" + connection.getId() + "] io exception occured while performing onDisconnect multithreaded " + printHandler() + " " + ioe.toString());
                }
                
            } catch (RuntimeException rt) {
                LOG.warning("[" + connection.getId() + "] runtime exception occured while performing onDisconnect multithreaded " + printHandler() + " " + rt.toString());
                
            } catch (Error t) {
                LOG.warning("[" + connection.getId() + "] Error occured by performing onDisconnect off " + printHandler() +  " " + t.toString());
            }

        }
        
        @Override
        public String toString() {
            return "PerformOnDisconnectTask#" + hashCode() + " "  + connection.getId();
        }
    }
    

    
    
	
	private boolean performOnDisconnect(INonBlockingConnection connection, SerializedTaskQueue taskQueue) throws IOException {
        try {
            return ((IDisconnectHandler) handler).onDisconnect(connection);
            
        }  catch (RuntimeException re) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onDisconnect by appHandler. " + printHandler() + " Reason: " + re.toString());
            }
            closeSilence(connection);
            throw re;
            
        }  catch (IOException ioe) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onDisconnect by appHandler. " + printHandler() + " Reason: " + ioe.toString());
            }
            closeSilence(connection);
            throw ioe;
        }   
	}
	
	
	

    public boolean onConnectException(final INonBlockingConnection connection, final SerializedTaskQueue taskQueue, Executor workerpool, final IOException ioe) throws IOException {

        if (handlerInfo.isConnectExceptionHandler()) {
            
            if (handlerInfo.isUnsynchronized()) { 
                performOnConnectException(connection, taskQueue, ioe);
                
            } else {
                if (getHandlerInfo().isConnectExceptionHandlerMultithreaded()) {
                    taskQueue.performMultiThreaded(new PerformOnConnectExceptionTask(connection, taskQueue, ioe), workerpool);
                    
                } else {
                    taskQueue.performNonThreaded(new PerformOnConnectExceptionTask(connection, taskQueue, ioe), workerpool);
                }
            }
        }
        
        return true;
    }

    
    private final class PerformOnConnectExceptionTask implements Runnable {
        
        private final INonBlockingConnection connection;
        private final SerializedTaskQueue taskQueue;
        private final IOException ioe;
        
        
        public PerformOnConnectExceptionTask(INonBlockingConnection connection, SerializedTaskQueue taskQueue, IOException ioe) {
            this.connection = connection;
            this.taskQueue = taskQueue;
            this.ioe = ioe;
        }
        
        
        public void run() {
            
            try {
                
                performOnConnectException(connection, taskQueue, ioe);
                
            } catch (IOException e) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("[" + connection.getId() + "] io exception occured while performing onConnectException multithreaded " + printHandler() + " " + e.toString());
                }
                
            } catch (RuntimeException rt) {
                LOG.warning("[" + connection.getId() + "] runtime exception occured while performing onConnectException multithreaded " + printHandler() + " " + rt.toString());
                
            } catch (Error t) {
                LOG.warning("[" + connection.getId() + "] closing connection. Error occured by performing onConnectionException of " + printHandler() +  " " + t.toString());
                NonBlockingConnection.closeSilence(connection);
            }
        }
    }
    

    
	
	
	private boolean performOnConnectException(INonBlockingConnection connection, SerializedTaskQueue taskQueue, IOException ioe) throws IOException {

	    try {
            return ((IConnectExceptionHandler) handler).onConnectException(connection, ioe);
            
        }  catch (RuntimeException re) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onDisconnect by appHandler. " + printHandler() + " Reason: " + re.toString());
            }
            closeSilence(connection);
            throw re;
            
        }  catch (IOException e) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onDisconnect by appHandler. " + printHandler() + " Reason: " + ioe.toString());
            }
            closeSilence(connection);
            throw ioe;
        }   
	}
	
	
	    
    public boolean onIdleTimeout(final INonBlockingConnection connection, final SerializedTaskQueue taskQueue, Executor workerpool) throws IOException {
        
        if (handlerInfo.isIdleTimeoutHandler()) {
            
            if (handlerInfo.isUnsynchronized()) { 
                performOnIdleTimeout(connection, taskQueue);
                
            } else {            
                if (getHandlerInfo().isIdleTimeoutHandlerMultithreaded()) {
                    taskQueue.performMultiThreaded(new PerformOnIdleTimeoutTask(connection, taskQueue), workerpool);
                    
                } else {
                    taskQueue.performNonThreaded(new PerformOnIdleTimeoutTask(connection, taskQueue), workerpool);
                }
            }
        } else {
            closeSilence(connection);
        }
            
        return true;
    }
    
	
    
    private final class PerformOnIdleTimeoutTask implements Runnable {
        
        private final INonBlockingConnection connection;
        private final SerializedTaskQueue taskQueue;
        
        
        public PerformOnIdleTimeoutTask(INonBlockingConnection connection, SerializedTaskQueue taskQueue) {
            this.connection = connection;
            this.taskQueue = taskQueue;
        }
        
        
        public void run() {
            
            try {
                
                performOnIdleTimeout(connection, taskQueue);
                
            } catch (IOException ioe) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("[" + connection.getId() + "] closing connection. An IO exception occured while performing onIdleTimeout multithreaded " + printHandler() + " " + ioe.toString());
                }
                closeSilence(connection);
                
            } catch (RuntimeException rt) {
                LOG.warning("[" + connection.getId() + "] runtime exception occured while performing onIdletimeout multithreaded " + printHandler() + " " + rt.toString());
                       
            } catch (Error t) {
                LOG.warning("[" + connection.getId() + "] closing connection. Error occured by performing onIdleTimeout of " + printHandler() +  " " + t.toString());
                NonBlockingConnection.closeSilence(connection);
            }

        }
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
                LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onIdleTimeout by appHandler. " + printHandler() + " Reason: " + re.toString());
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
	
	
	

    public boolean onConnectionTimeout(INonBlockingConnection connection, SerializedTaskQueue taskQueue, Executor workerpool) throws IOException {
        
        if (handlerInfo.isConnectionTimeoutHandler()) {
            
            if (handlerInfo.isUnsynchronized()) { 
                performOnConnectionTimeout(connection, taskQueue);
                
            } else {
                if (getHandlerInfo().isConnectionTimeoutHandlerMultithreaded()) {
                    taskQueue.performMultiThreaded(new PerformOnConnectionTimeoutTask(connection, taskQueue), workerpool);
                    
                } else {
                    taskQueue.performNonThreaded(new PerformOnConnectionTimeoutTask(connection, taskQueue), workerpool);
                }
            }
            
        } else {
            closeSilence(connection);
        }
        
        return true;
    }
    
	
    
    private final class PerformOnConnectionTimeoutTask implements Runnable {
        
        private final INonBlockingConnection connection;
        private final SerializedTaskQueue taskQueue;
        
        
        public PerformOnConnectionTimeoutTask(INonBlockingConnection connection, SerializedTaskQueue taskQueue) {
            this.connection = connection;
            this.taskQueue = taskQueue;
        }
        
        
        public void run() {
            
            try {
                
                performOnConnectionTimeout(connection, taskQueue);
                
            } catch (IOException ioe) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("[" + connection.getId() + "] closing connection. An IO exception occured while performing onConnectionTimeout multithreaded " + printHandler() + " " + ioe.toString());
                }
                closeSilence(connection);
                
            } catch (RuntimeException rt) {
                LOG.warning("[" + connection.getId() + "] runtime exception occured while performing onConnectionTimeout multithreaded " + printHandler() + " " + rt.toString());
                
            } catch (Error t) {
                LOG.warning("[" + connection.getId() + "] closing connection. Error occured by performing onConnectionTimeout of " + printHandler() +  " " + t.toString());
                NonBlockingConnection.closeSilence(connection);
            }

        }
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
                LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onConnectionTimeout by appHandler. " + printHandler() + " Reason: " + re.toString());
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
					LOG.fine("exception occured by destroying " + printHandler() + " " + ioe.toString());
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
