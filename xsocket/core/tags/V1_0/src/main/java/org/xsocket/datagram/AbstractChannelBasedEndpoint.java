//$Id: AbstractChannelBasedEndpoint.java 1049 2007-03-21 16:42:48Z grro $
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
 * Endpoint implementation base
 * 
 * @author grro@xsocket.org
 */
abstract class AbstractChannelBasedEndpoint extends AbstractEndpoint implements IEndpoint {
	
	private static final Logger LOG = Logger.getLogger(AbstractChannelBasedEndpoint.class.getName());

	private static final MemoryManager memoryManager = new MemoryManager(65536, false);
	private static Dispatcher<DispatcherHandle> dispatcher = createDispatcher();
	
	
	// socket 
	private DatagramSocket socket = null;
	private DatagramChannel channel = null;
	private ByteOrder byteOrder = ByteOrder.BIG_ENDIAN;
	
	
	// send queue
	private final List<UserDatagram> sendQueue = Collections.synchronizedList(new LinkedList<UserDatagram>());

	
	private DispatcherHandle dispatcherHandle = null;
	


	/**
	 * constructor
	 *
     * @param useGlobalWorkerpool      true, ifglobal worker pool should be used
	 * @param intOps                   inital ops for the dispatching 
	 * @param address                  the local address
	 * @param port                     the local port which must be between 0 and 65535 inclusive.
     * @param datagramHandler          the datagram handler
     * @param receivePacketSize        the receive packet size
     * @param workerPoolSize  the instance exclusive workerpool size or 0 if global workerpool should be used 
     * @throws IOException If some I/O error occurs
	 */
	AbstractChannelBasedEndpoint(InetAddress address,  int port, IDatagramHandler datagramHandler, int receivePacketSize) throws IOException {
		super(datagramHandler, receivePacketSize);
			
		channel = DatagramChannel.open();
		channel.configureBlocking(false);
			
		socket = channel.socket();
		socket.setReuseAddress(true);
			

		InetSocketAddress addr = new InetSocketAddress(address, port);
		socket.bind(addr);
		
		dispatcherHandle = new DispatcherHandle(this);
		dispatcher.register(dispatcherHandle, SelectionKey.OP_READ);
						
		logFine("enpoint has been bound to locale port " + getLocalPort() + " (server mode)");
	}
	
	
		
	@SuppressWarnings("unchecked")
	private static Dispatcher<DispatcherHandle> createDispatcher() {
		Dispatcher<DispatcherHandle> disp = new Dispatcher<DispatcherHandle>(new DispatcherEventHandler());
		Thread t = new Thread(disp);
		t.start();	
		
		return disp;
	}
	
		
	protected final DatagramChannel getChannel() {
		return channel;
	}
		
		
		
	
	/**
	 * {@inheritDoc}
	 */
	public final void close() {
		if (isOpen()) {
			try {
				logFine("closing " + toCompactString());
				channel.close();
			} catch (IOException ioe) {
				logFine("error occured by closing connection. Reason " + ioe.toString());
			}
			
			super.close();
		}	
	}
		
	
	/**
	 * {@inheritDoc}
	 */
	public final SocketAddress getLocalSocketAddress() {
		return socket.getLocalSocketAddress();
	}
	

	/**
	 * {@inheritDoc}
	 */
	public final InetAddress getLocalAddress() {
		return socket.getLocalAddress();
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final int getLocalPort() {
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
	private void logFine(String msg) {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + "/:" + getLocalPort() + " " + getId() + "] " + msg);
		}
	}

	
	/**
	 * {@inheritDoc}
	 */
	public void send(UserDatagram packet) throws IOException {
		if (packet.getRemoteAddress() == null) {
			throw new IOException("remote socket adress has to be set");
		}
		
		logFine("add datagram packet (" + packet + ") to write queue");
		
		packet.prepareForSend(); 

		sendQueue.add(packet);
		logFine("update interest ops to write");
		updateInteresSet(SelectionKey.OP_WRITE);
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
	private void writePhysical() {
		if (!sendQueue.isEmpty()) {
			synchronized(sendQueue) {
				for (UserDatagram packet : sendQueue) {
					try {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("[" + "/:" + getLocalPort() + " " + getId() + "] sending datagram " + packet.toString());
						}

						int dataToSend = packet.getSize();
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
				
				sendQueue.clear();
			}
		}
	}

	
	private void updateInteresSet(int intOps) throws IOException {
		dispatcher.updateInterestSet(dispatcherHandle, intOps);
	}
	
		
	/**
	 * a compact string of this endpoint
	 */
	public String toCompactString() {
		return this.getClass().getSimpleName() + " " + socket.getLocalAddress().getCanonicalHostName() + ":" + getLocalPort();
	}
			 
	
	
	private final static class DispatcherHandle implements IHandle {
			
		private AbstractChannelBasedEndpoint endpoint = null;
		
		DispatcherHandle(AbstractChannelBasedEndpoint endpoint) {
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
					if (handle.endpoint.getReceiveSize() > 0) {
						ByteBuffer readBuffer = memoryManager.acquireMemory(handle.endpoint.getReceiveSize());
						readBuffer.order(handle.endpoint.byteOrder);
						SocketAddress address = handle.endpoint.channel.receive(readBuffer);
									
						// datagram is not immediately available
						if (address == null) {
							return;
								
						// datagram is available
						} else {
								
							// nothing has been read
							if (readBuffer.position() == 0) {
								return;
							} 
						
							readBuffer.flip();
							handle.endpoint.onData(address, readBuffer);
						}
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
			handle.endpoint.updateInteresSet(SelectionKey.OP_READ);
		}
		
		
		/**
		 * {@inheritDoc}
		 */
		public void onDispatcherCloseEvent(final DispatcherHandle handle) {
			handle.endpoint.close();
		}
	}
}
