// $Id: NonBlockingEndpoint.java 919 2007-02-13 12:34:01Z grro $
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
package org.xsocket.datagram;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.Dispatcher;
import org.xsocket.IEventHandler;
import org.xsocket.IHandle;





/**
 * an implementation of a disconnected non blocking endpoint
 * 
 * @author grro
 */
public class NonBlockingEndpoint extends AbstractEndpoint {
	
	private static final Logger LOG = Logger.getLogger(NonBlockingEndpoint.class.getName());

	private static int nextId = 0;
	private static final MemoryManager memoryManager = new MemoryManager(65536, false);
	private static Dispatcher<DispatcherHandle> defaultDispatcher = null;
	
	
	// socket 
	private DatagramSocket socket = null;
	private DatagramChannel channel = null;
	private ByteOrder byteOrder = ByteOrder.BIG_ENDIAN;
	
	
	private int packetSize = 0;
	
	
	// is client mode
	private boolean isClientMode = true;
	
	// dispather
	private Dispatcher<DispatcherHandle> dispatcher = null;
	private boolean useDefaultDispatcher = true;
	
	
	// write queue
	private final List<Packet> bufferQueue = Collections.synchronizedList(new LinkedList<Packet>());

	
	// memory management
	private int preallocationSize = 65536;	
	
	
	private DispatcherHandle dispatcherHandle = null;
	
	private IDatagramHandler dataHandler = null;


	
		
	/**
	 * constructor<br><br>
	 * 
	 * Constructs a datagram socket and binds it to any 
	 * available port on the local host machine. The socket 
	 * will be bound to the wildcard address, an IP address 
	 * chosen by the kernel.
	 *  
     * @throws IOException If some I/O error occurs
	 */
	public NonBlockingEndpoint() throws IOException {
		this(0, null, 0, 0);
	}

	
	/**
	 * constructor<br><br>
     * 
     * the global workerpool will be used to handle messages.
     * 
  	 * Constructs a datagram socket and binds it to any 
	 * available port on the local host machine. The socket 
	 * will be bound to the wildcard address, an IP address 
	 * chosen by the kernel.
     * 
     * @param dataHandler             the data handler
     * @param receivePacketSize       the receive packet size  
     * @throws IOException If some I/O error occurs
	 */
	public NonBlockingEndpoint(IDatagramHandler dataHandler, int receivePacketSize, int workerPoolSize) throws IOException {
		this(0, dataHandler, receivePacketSize, workerPoolSize);
	}
	

	/**
	 * constructor
	 * 
	 * @param localePort               the local port which must be between 0 and 65535 inclusive.
     * @param dataHandler              the data handler
     * @param receivePacketSize        the receive packet size
     * @param workerPoolSize  the instance exclusive workerpool size or 0 if global workerpool should be used 
     * @throws IOException If some I/O error occurs
	 */
	public NonBlockingEndpoint(int localePort, IDatagramHandler dataHandler, int receivePacketSize, int workerPoolSize) throws IOException {
		super(workerPoolSize);
			
		this.dataHandler = dataHandler;
		this.packetSize = receivePacketSize;
		this.isClientMode = (dataHandler == null);
			 
		channel = DatagramChannel.open();
		channel.configureBlocking(false);
			
		socket = channel.socket();
		socket.setReuseAddress(true);
			

		InetSocketAddress address = new InetSocketAddress(InetAddress.getLocalHost(), localePort);
		socket.bind(address);
		
		if (!isClientMode) {
			if (workerPoolSize == 0) { 
				logFine("using global dispatcher");
				useDefaultDispatcher = true;
				
				dispatcher = getDefaultDispatcher();
				
			} else {
				logFine("create instance specific dispatcher");
				useDefaultDispatcher = false;
				
				dispatcher = createDispatcher();
			}
			
			dispatcherHandle = new DispatcherHandle(this);
			dispatcher.register(dispatcherHandle, SelectionKey.OP_READ);
			
			logFine("enpoint has been bound to locale port " + getLocalPort() + " (server mode)");
			} else {
			logFine("enpoint has been bound to locale port " + getLocalPort() + " (client mode)");		
		}
	}
		
	
	/**
	 * {@inheritDoc}
	 */
	final DatagramChannel getChannel() {
		return channel;
	}
		
	
	private Dispatcher<DispatcherHandle> getDefaultDispatcher() {
		if (defaultDispatcher == null) {
			defaultDispatcher = createDispatcher();
		}
			
		return defaultDispatcher;
	}
		
		
	@SuppressWarnings("unchecked")
	private Dispatcher<DispatcherHandle> createDispatcher() {
		Dispatcher<DispatcherHandle> disp = new Dispatcher<DispatcherHandle>(nextId(), new DispatcherEventHandler());
		Thread t = new Thread(disp);
		t.start();	
	    
		return disp;
	}
	
		
		
		
	private String nextId() {
		nextId++;
		if (nextId < 0) {
			nextId = 1;
		}
		return Integer.toString(nextId);
	}
		

		
	
	/**
	 * {@inheritDoc}
	 */
	public void close() {
		if (isOpen()) {
			try {
				logFine("closing " + toCompactString());
				channel.close();
			} catch (IOException ioe) {
				logFine("error occured by closing connection. Reason " + ioe.toString());
			}
		}	
			if (!useDefaultDispatcher) {
			dispatcher.shutdown();
		}		
	}
		
	
	/**
	 * {@inheritDoc}
	 */
	public SocketAddress getLocalSocketAddress() {
		return socket.getLocalSocketAddress();
	}
	

	/**
	 * {@inheritDoc}
	 */
	public InetAddress getLocalAddress() {
		return socket.getLocalAddress();
	}

	
	/**
	 * {@inheritDoc}
	 */
	public int getLocalPort() {
		return socket.getLocalPort();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final boolean isOpen() {
		return channel.isOpen();
	}
		


	/**
	 * log a fine msg 
	 * 
	 * @param msg the log message
	 */
	public final void logFine(String msg) {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + "/:" + getLocalPort() + " " + getId() + "] " + msg);
		}
	}

	
	/**
	 * {@inheritDoc}
	 */
	public void send(Packet packet) throws IOException {
		logFine("add datagram packet (" + packet + ") to write queue");
		
		packet.prepareforSend(); 
		
		if (isClientMode) {
			bufferQueue.add(packet);
			writePhysical();					
			
		} else {
			bufferQueue.add(packet);
			dispatcher.updateInterestSet(dispatcherHandle, SelectionKey.OP_WRITE);
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return socket.getLocalSocketAddress().toString() + " (ID=" + getId() + ")";
	}
	
		
		
	/**
	 * write the outgoing data to socket 
	 *
	 */
	protected final void writePhysical() {
		if (!bufferQueue.isEmpty()) {
			synchronized(bufferQueue) {
				Packet[] packets = bufferQueue.toArray(new Packet[bufferQueue.size()]);		
				bufferQueue.clear();
				for (Packet packet : packets) {
					try {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("[" + "/:" + getLocalPort() + " " + getId() + "] sending datagram " + packet.toString());
						}

						int dataToSend = packet.getPacketSize();
						int written = channel.send(packet.getData(), packet.getRemoteSocketAddress());
						
						if (LOG.isLoggable(Level.FINE)) {
							if (dataToSend != written) {
								LOG.fine("Error occured by sending datagram. Size DataToSend=" + dataToSend + ", written=" + written);
							}
						}
					
						incNumberOfHandledOutgoingDatagram();
					} catch (IOException ioe) {
						LOG.warning("couldn't write datagram to " + packet.getRemoteAddress() + " .Reason: " + ioe.toString());
					}
				}
			}
		}
	}
		
		
	/**
	 * receive data 
	 * 
	 * @return the received data 
     * @throws IOException If some I/O error occurs
	 */
	@SuppressWarnings("unchecked")
	protected final Packet receive() throws IOException {
		ByteBuffer readBuffer = memoryManager.acquireMemory(packetSize);
		readBuffer.order(byteOrder);
		SocketAddress address = channel.receive(readBuffer);
				
		// datagram is not immediately available
		if (address == null) {
			return null;
			
		// datagram is available
		} else {
			
			// nothing has been read
			if (readBuffer.position() == 0) {
				return null;
			} 
				
			incNumberOfHandledIncomingDatagram();
			readBuffer.clear();
			Packet packet = new Packet(address, readBuffer, getDefaultEncoding());
			
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + "/:" + getLocalPort() + " " + getId() + "] datagram received: " + packet.toString());
			}
			
			return packet;
		}
	}
		
		
	/**
	 * a compact string of this endpoint
	 */
	public String toCompactString() {
		return this.getClass().getSimpleName() + " " + socket.getLocalAddress().getCanonicalHostName() + ":" + getLocalPort();
	}
			 
	
	
	private final static class DispatcherHandle implements IHandle {
			
		private NonBlockingEndpoint endpoint = null;
		
		DispatcherHandle(NonBlockingEndpoint endpoint) {
			this.endpoint = endpoint;
		}
			
		public SelectableChannel getChannel() {
			return endpoint.channel;
		}
	}
		
		


	private final static class DispatcherEventHandler<T extends IEndpoint> implements IEventHandler<DispatcherHandle> {
			
		/**
		 * {@inheritDoc}
		 */
		public void onHandleRegisterEvent(DispatcherHandle handle) throws IOException {
			
		}
		

		/**
		 * {@inheritDoc}
		 */
		@SuppressWarnings("unchecked")
		public void onHandleReadableEvent(final DispatcherHandle handle) {
			if (handle.endpoint.isOpen()) {
			
				try {
					// perform non-blocking read operation
					final Packet packet = handle.endpoint.receive();
						
					if (packet != null) {
						
						handle.endpoint.getWorkerPool().execute(new Runnable() {
							@SuppressWarnings("unchecked")
							public void run() {
								try {
									synchronized (handle.endpoint) {
										handle.endpoint.dataHandler.onData(handle.endpoint, packet);
									}
								} catch (Throwable e) {
									handle.endpoint.logFine("error occured by performing onData task. Reason: " + e.toString());
								}							
							}
						});
					}
				} catch (IOException ioe) {
					handle.endpoint.logFine("error occured while receiving. Reason: " + ioe.toString());
				}
			}
		}
		
			
		/**
		 * {@inheritDoc}
		 */
		public void onHandleWriteableEvent(DispatcherHandle handle) throws IOException {
			handle.endpoint.writePhysical();
		}
		
		
		/**
		 * {@inheritDoc}
		 */
		public void onDispatcherCloseEvent(final DispatcherHandle handle) {
				handle.endpoint.getWorkerPool().execute(new Runnable() {
				public void run() {
					try {
						synchronized (handle) {
							handle.endpoint.close();
						}
					} catch (BufferUnderflowException bue) {
						// ignore 
				
					} catch (Throwable e) {
						handle.endpoint.logFine("error occured by performing onDispatcherCloseEvent. Reason: " + e.toString());
					}							
				}
			});
		}
	}
}
