package org.xsocket.stream;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.Dispatcher;
import org.xsocket.IEventHandler;



final class IoSocketDispatcher extends Dispatcher<IoSocketHandler> {
	
	private IMemoryManager memoryManager = null;
	
	IoSocketDispatcher(IMemoryManager memoryManager) {
		super(new DispatcherEventHandler(memoryManager));
		
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
		
		DispatcherEventHandler(IMemoryManager memoryManager) {
			this.memoryManager = memoryManager;
		}
		
		IMemoryManager getMemoryManager() {
			return memoryManager;
		}
		
		
		public void onHandleReadableEvent(final IoSocketHandler socketIOHandler) throws IOException {			
			try {
				
				// read data from socket
				socketIOHandler.readSocketIntoReceiveQueue();	
				socketIOHandler.onDataEvent();
								
			} catch (Throwable t) {
				socketIOHandler.close(false);
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
					
					socketIOHandler.onDisconnectEvent();
				}				
			}
		}

		public void onHandleRegisterEvent(final IoSocketHandler socketIOHandler) throws IOException {
			socketIOHandler.getIOEventHandler().onConnectEvent();
		}
		
		
		public void onDispatcherCloseEvent(final IoSocketHandler socketIOHandler) {
			socketIOHandler.onDispatcherClose();	
		}
	}	
}
