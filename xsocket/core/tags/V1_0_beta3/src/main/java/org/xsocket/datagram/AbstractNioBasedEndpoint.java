// $Id: AbstractNioBasedEndpoint.java 778 2007-01-16 07:13:20Z grro $
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
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;



import org.xsocket.Dispatcher;
import org.xsocket.IEventHandler;
import org.xsocket.IHandle;




/**
 * 
 * nio based implementation base a endpoint
 * 
 * @author grro
 */
abstract class AbstractNioBasedEndpoint extends AbstractEndpoint {
	
	private static final Logger LOG = Logger.getLogger(AbstractNioBasedEndpoint.class.getName());

	private static int nextId = 0;

	// socket 
	private DatagramSocket socket = null;
	private DatagramChannel channel = null;
	private ByteOrder byteOrder = ByteOrder.BIG_ENDIAN;
	
	// is client mode
	private boolean isClientMode = true;
	
	// dispather
	private Dispatcher<EndpointHandle> dispatcher = null;
	private boolean useDefaultDispatcher = true;
	
	// handle
	private final EndpointHandle handle = new EndpointHandle();
	
	
	// write queue
	private final WriteQueue writeQueue = new WriteQueue();

	
	// memory management
	private int preallocationSize = 65536;	
	
	
	/**
	 * constructor
	 * 
	 * @param receiveDatasize         the size of the received packet
     * @param instanceWorkerPoolSize  the instance exclusive workerpool size or 0 if global workerpool shot be used  
	 * @param localePort              the locale port or 0
	 * @param isClientMode            true, if is in client mode
     * @throws IOException If some I/O error occurs
	 */
	AbstractNioBasedEndpoint(int receiveDatasize, int instanceWorkerPoolSize, int localePort, boolean isClientMode) throws IOException {
		super(receiveDatasize, instanceWorkerPoolSize);
		
		this.isClientMode = isClientMode;
		
		channel = DatagramChannel.open();
		channel.configureBlocking(false);
		
		socket = channel.socket();
		socket.setReuseAddress(true);
		

		socket.bind(new InetSocketAddress(localePort));
		
		
		if (!isClientMode) {
			if (instanceWorkerPoolSize == 0) { 
				logFine("using global dispatcher");
				useDefaultDispatcher = true;
				
				dispatcher = getDefaultDispatcher();
				
			} else {
				logFine("create instance specific dispatcher");
				useDefaultDispatcher = false;
				
				dispatcher = createDispatcher();
			}
			
			dispatcher.register(handle);
			
			logFine("enpoint has been bound to locale port " + getLocalPort() + " (server mode: receiveDataSize=" + receiveDatasize + ")");

		} else {
			logFine("enpoint has been bound to locale port " + getLocalPort() + " (client mode)");		
		}
	}
	
	
	/**
	 * gets the default dispatcher 
	 *  
	 * @return  the default dispatcher 
     * @throws IOException If some I/O error occurs
	 */
	protected abstract Dispatcher<EndpointHandle> getDefaultDispatcher() throws IOException;
	
	
	/**
	 * creates a new dispatcher 
	 * @return the new dispatcher 
     * @throws IOException If some I/O error occurs
	 */
	protected abstract Dispatcher<EndpointHandle> createDispatcher() throws IOException;
	
	
	/**
	 * creates a starts a dispatcher 
	 * 
	 * @param eventHandler   the assigned event handler
	 * @return the new dispatcher 
     * @throws IOException If some I/O error occurs
	 */
	protected final Dispatcher<EndpointHandle> createAndStartDispatcher(IEventHandler<EndpointHandle> eventHandler) throws IOException {
		Dispatcher<EndpointHandle> dispatcher = new Dispatcher<EndpointHandle>(nextId(), eventHandler);
		Thread t = new Thread(dispatcher);
		t.start();	
    
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				close();
			}
		});

		return dispatcher;
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
	 * {@inheritDoc}
	 */
	public final void setByteOrder(ByteOrder byteOrder) {
		this.byteOrder = byteOrder;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int getReceiveBufferPreallocationSize() {
		return preallocationSize;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final void setReceiveBufferPreallocationSize(int preallocationSize) {
		this.preallocationSize = preallocationSize;
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
	 * send a datagram 
	 * 
	 * @param remoteAddress  the remote address
	 * @param data           thedata to send
	 */
	protected final void sendDatagram(SocketAddress remoteAddress, ByteBuffer data) {
		final DataPackage dp = new DataPackage(remoteAddress, data);
		
		logFine("add datagram package (" + dp + ") to write queue");
		
		if (isClientMode) {
			writeQueue.append(dp);
			writePhysical();					
			
		} else {
			writeQueue.append(dp);
			dispatcher.announceWriteNeed(handle);
		}
	}
	
	
	/**
	 * write the outgoing data to socket 
	 *
	 */
	protected final void writePhysical() {
		if (!writeQueue.isEmtpy()) {
			List<DataPackage> dataPackages = writeQueue.readAvailable();
			for (DataPackage dataPackage: dataPackages) {
				try {
					logFine("send datagram package (" + dataPackage + ")");
					
					dataPackage.writeTo(channel);
					incNumberOfHandledOutgoingDatagram();
				} catch (IOException ioe) {
					LOG.warning("couldn't write datagram to " + dataPackage.getAddress() + " .Reason: " + ioe.toString());
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
	protected final DataPackage receive() throws IOException {
		ByteBuffer data = ByteBuffer.allocate(getReceivePacketSize());
		data.order(byteOrder);
		SocketAddress address = channel.receive(data);
		if (address == null) {
			return null;
		} else {
			incNumberOfHandledIncomingDatagram();
			data.clear();
			return new DataPackage(address, data);
		}
	}
	
	
	/**
	 * a compact string of this endpoint
	 */
	public String toCompactString() {
		return this.getClass().getSimpleName() + " " + socket.getLocalAddress().getCanonicalHostName() + ":" + getLocalPort();
	}
	
	
	
	final class EndpointHandle implements IHandle {
		
		public SelectableChannel getChannel() {
			return channel;
		}
		
		public AbstractNioBasedEndpoint getEndpoint() {
			return AbstractNioBasedEndpoint.this;
		}

		
		public void close() {
			AbstractNioBasedEndpoint.this.close();
		}
		
		public void check(long currentTime) {
			
		}
	}
	


	static final class DataPackage {
		
		private ByteBuffer data = null;
		private SocketAddress address = null;
	
		
		public DataPackage(SocketAddress address, ByteBuffer data) {
			this.address = address;
			this.data = data;
		}
		

		public final SocketAddress getAddress() {
			return address;
		}
				
		public ByteBuffer getData() {
			return data;
		}
		
		int writeTo(DatagramChannel channel) throws IOException {
			return channel.send(data, getAddress());
		}
		
		@Override
		public String toString() {
			return "Receiver=" + address.toString() + " dataSize=" + data.limit();
		}
	}


	private static final class WriteQueue {
		private LinkedList<DataPackage> bufferQueue = new LinkedList<DataPackage>();
		
		
		synchronized boolean isEmtpy() {
			return bufferQueue.isEmpty();
		}
		
		
		synchronized void append(DataPackage dataPackage) {
			bufferQueue.add(dataPackage);
		}

		public synchronized LinkedList<DataPackage> readAvailable()  {
			LinkedList<DataPackage> result = bufferQueue;
			bufferQueue = new LinkedList<DataPackage>();
			return result;
		}
	}
}
