package org.xsocket.stream;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.Dispatcher;
import org.xsocket.IEventHandler;
import org.xsocket.IWorkerPool;



final class IoSocketDispatcher extends Dispatcher<IoSocketHandler> {
	
	private IMemoryManager memoryManager = null;
	
	IoSocketDispatcher(IMemoryManager memoryManager, IWorkerPool workerPool) {
		super(new DispatcherEventHandler(memoryManager, workerPool));
		
		this.memoryManager = memoryManager;
	}
	
	@Override
	public void register(IoSocketHandler handle, int ops) throws IOException {
		handle.setMemoryManager(memoryManager);
		super.register(handle, ops);
	}
	
	public int getPreallocatedReadMemorySize() {
		return memoryManager.getFreeBufferSize();
	}
	
	
	private static final class DispatcherEventHandler implements IEventHandler<IoSocketHandler> {

		private static final Logger LOG = Logger.getLogger(DispatcherEventHandler.class.getName());
		
		
		private IMemoryManager memoryManager = null;
		private IWorkerPool workerPool = null;
		
		DispatcherEventHandler(IMemoryManager memoryManager, IWorkerPool workerPool) {
			this.memoryManager = memoryManager;
			this.workerPool = workerPool;
		}
		
		IMemoryManager getMemoryManager() {
			return memoryManager;
		}
		
		
		public void onHandleReadableEvent(final IoSocketHandler socketIOHandler) throws IOException {			
			try {
				
				// read data from socket
				socketIOHandler.readSocketIntoReceiveQueue();	
				
				
				Runnable task = new Runnable() {
					public void run() {
						socketIOHandler.onDataEvent();
					}
				};
				socketIOHandler.getProcessQueue().processTask(workerPool, task);

		
								
			} catch (Throwable t) {
				socketIOHandler.close();
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by handling readable event. reason: " + t.toString());
				}
			}
		}
		
		
		@SuppressWarnings("unchecked")
		public void onHandleWriteableEvent(final IoSocketHandler socketIOHandler) throws IOException {
			
			socketIOHandler.getDispatcher().updateInterestSet(socketIOHandler, SelectionKey.OP_READ);
			
			// write data to socket 
			try {
				socketIOHandler.writeSendQueueDataToSocket();
				
				socketIOHandler.onWrittenEvent();
			} catch(IOException ioe)  {
				socketIOHandler.onWrittenExceptionEvent(ioe);
			}
				
			
			// are there remaining data to send -> announce write demand 
			if (socketIOHandler.getSendQueueSize() > 0) {
				socketIOHandler.getDispatcher().updateInterestSet(socketIOHandler, SelectionKey.OP_WRITE);
				
			// all data send -> check for close
			} else {
				if (socketIOHandler.shouldClosedPhysically()) {
					// deregister handler and close SocketChannel 
					try {
						socketIOHandler.getDispatcher().deregister(socketIOHandler);
						socketIOHandler.closePhysically();
					} catch (Exception e) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("error occured by closing connection. reason: " + e.toString());
						}
					}
					
					
					Runnable task = new Runnable() {
						public void run() {
							socketIOHandler.onDisconnectEvent();
						}
					};
					socketIOHandler.getProcessQueue().processTask(workerPool, task);
				}				
			}
		}

		public void onHandleRegisterEvent(final IoSocketHandler socketIOHandler) throws IOException {
			
			if (socketIOHandler.getIOEventHandler().listenForConnect()) {
				
				Runnable task = new Runnable() {
					public void run() {
						socketIOHandler.getIOEventHandler().onConnectEvent();
					}
				};
				socketIOHandler.getProcessQueue().processTask(workerPool, task);
			}
		}
		
		
		public void onDispatcherCloseEvent(final IoSocketHandler socketIOHandler) {
			try {
				socketIOHandler.close();
			} catch (IOException ignore) { }			
		}
	}	
}
