// $Id: NonBlockingMulticastEndpoint.java 910 2007-02-12 16:56:19Z grro $
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
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.xsocket.ClosedConnectionException;



/**
 * non blocking Mutlicast endpoint <br><br>
 * 
 * Cased by the missing channel support for multicast Datagram this
 * class is implemented by using the "classic" MulticastSocket
 * 
 * @author grro
 */
public final class NonBlockingMulticastEndpoint extends AbstractEndpoint implements IConnectedEndpoint {

	/*
	 *  * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4527345
     */   

	
	private static Logger LOG = Logger.getLogger(NonBlockingMulticastEndpoint.class.getName());
	
		
	// run flag
	private boolean isRunning = true;
	

	// data handler
	private IDatagramHandler dataHandler = null;

	
	private int receiveSize = 0;
		
	// socket
	private MulticastSocket socket = null;
	private InetSocketAddress multicastAddress = null;
	
	
	// statistics & jmx
    private long connectionOpenedTime = -1;


    	
    /**
     * constructor 
     * 
     * @param address                 the group address
     * @param port                    the port
     * @param receiveSize             the size of the data packet to receive 
     * @param dataHandler             the data handler
     * @param workerPoolSize  the instance exclusive workerpool size or 0 if global workerpool shot be used 
     * @throws IOException If some I/O error occurs
     */
	public NonBlockingMulticastEndpoint(String address, final int port, IDatagramHandler dataHandler, int receiveSize, int workerPoolSize) throws IOException {
		this(InetAddress.getByName(address), port, dataHandler, receiveSize, workerPoolSize);
	}

	

    /**
     * constructor 
     * 
     * @param address                 the group address
     * @param port                    the port
     * @param receiveSize             the size of the data packet to receive 
     * @param dataHandler             the data handler
     * @param workerPoolSize  the instance exclusive workerpool size or 0 if global workerpool shot be used 
     * @throws IOException If some I/O error occurs
     */
	public NonBlockingMulticastEndpoint(InetAddress address, final int port, IDatagramHandler dataHandler, int receiveSize, int workerPoolSize) throws IOException {
		super(workerPoolSize);
		
		this.receiveSize = receiveSize;
		this.dataHandler = dataHandler;

		connectionOpenedTime = System.currentTimeMillis();
		
		
		socket = new MulticastSocket(port);
		socket.joinGroup(address);

		multicastAddress = new InetSocketAddress(address, port);
		
		if (dataHandler != null) {
			startReceiver();
		}
		
		if (LOG.isLoggable(Level.FINE)) {
			if (dataHandler != null) {
				LOG.fine("upd multicast endpoint bound to " + address.getCanonicalHostName() + "/" + port + " (server mode: dataHandler=" + dataHandler.toString() + ")");
			} else {
				LOG.fine("upd multicast endpoint bound to " + address.getCanonicalHostName() + "/" + port + " (client mode)");
			}
		}
	}
	
	
	private void startReceiver() {
		Thread receiverThread = new Thread() {
			public void run() {
				while (isRunning) {
					receive();
				}
			}
		};
		
		receiverThread.start();			
	}

	
	
	private void receive() {
		assert (dataHandler != null);
		
	    try {
			byte[] buf = new byte[receiveSize];
			DatagramPacket dp = new DatagramPacket(buf, buf.length);
			socket.receive(dp);
			incNumberOfHandledIncomingDatagram();
	
			final Packet packet = new Packet(new InetSocketAddress(dp.getAddress(), dp.getPort()), ByteBuffer.wrap(dp.getData()), getDefaultEncoding()); 

			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + "/:" + getLocalPort() + " " + getId() + "] datagram received: " + packet.toString());
			}

			
			getWorkerPool().execute(new Runnable() {
				public void run() {
					try {
						dataHandler.onData(NonBlockingMulticastEndpoint.this, packet);
					} catch (Exception e) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("error occured by handling data. Reason: " + e.toString());
						}
					}
				}
			});
			
			
	    } catch(IOException e) {
	    	if (!socket.isClosed()) {
	    		e.printStackTrace();
	    	}
	    }	
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return multicastAddress.toString() + " (ID=" + getId() + ")";
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void close() {
		if (isRunning) {		
			isRunning = false;	
			stopWorkerPool();
			
			try {
				socket.leaveGroup(multicastAddress.getAddress());
				socket.close();
			} catch (Exception e) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by closing multicast socket. Reason: " + e.toString());
				}
			}
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public SocketAddress getLocalSocketAddress() {
		return multicastAddress;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public InetAddress getLocalAddress() {
		return multicastAddress.getAddress();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getLocalPort() {
		return multicastAddress.getPort();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public SocketAddress getRemoteSocketAddress() {
		return multicastAddress;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public boolean isOpen() {
		return !socket.isClosed();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void send(Packet packet) throws ClosedConnectionException, IOException {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + "/:" + getLocalPort() + " " + getId() + "] sending datagram " + packet.toString());
		}
		
		packet.prepareforSend();
		
		byte[] bytes = new byte[packet.getData().remaining()];
		packet.getData().get(bytes);
		DatagramPacket dataPacket = new DatagramPacket(bytes, bytes.length, multicastAddress);
		socket.send(dataPacket);
			
		incNumberOfHandledOutgoingDatagram();
	}
}
