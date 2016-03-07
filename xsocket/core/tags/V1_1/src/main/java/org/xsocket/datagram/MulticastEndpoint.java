// $Id: MulticastEndpoint.java 1281 2007-05-29 19:48:07Z grro $
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
import java.net.SocketOptions;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.xsocket.ClosedConnectionException;



/**
 * non blocking Mutlicast endpoint <br><br>
 * 
 * Caused by the missing channel support for multicast Datagram (JSE 6.0) this
 * class is implemented by using the "classic" MulticastSocket
 * 
 * @author grro@xsocket.org
 */
public final class MulticastEndpoint extends AbstractEndpoint implements IConnectedEndpoint {

	/*
	 *  * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4527345
     */   

	
	private static Logger LOG = Logger.getLogger(MulticastEndpoint.class.getName());
	
		
	// run flag
	private boolean isRunning = true;
	
		
	// socket
	private MulticastSocket socket = null;
	private InetSocketAddress multicastAddress = null;
	
	
    /**
  	 * Constructs a datagram socket and
  	 * connects it to the given address  
     * 
     * @param address                 the group address
     * @param port                    the port
     * @throws IOException If some I/O error occurs
     */
	public MulticastEndpoint(InetAddress address, final int port) throws IOException {
		this(address, port, null, 0, null);
	}

	
    /**
  	 * Constructs a datagram socket and
  	 * connects it to the given address  
     * 
     * @param address                 the group address
     * @param port                    the port
	 * @param socketConfiguration     the socket configuration 
     * @throws IOException If some I/O error occurs
     */
	public MulticastEndpoint(InetAddress address, final int port, DatagramSocketConfiguration socketConfiguration) throws IOException {
		this(address, port, socketConfiguration, 0, null);
	}

	

    /**
  	 * Constructs a datagram socket and
  	 * connects it to the given address  
     * 
     * @param address                 the group address
     * @param port                    the port
     * @param receiveSize             the size of the data packet to receive 
     * @param datagramHandler         the datagram handler
     * @throws IOException If some I/O error occurs
     */
	public MulticastEndpoint(String address, final int port, int receiveSize, IDatagramHandler datagramHandler) throws IOException {
		this(InetAddress.getByName(address), port, null, receiveSize, datagramHandler);
	}

	
    /**
  	 * Constructs a datagram socket and
  	 * connects it to the given address  
     * 
     * @param address                 the group address
     * @param port                    the port
	 * @param socketConfiguration     the socket configuration 
     * @param receiveSize             the size of the data packet to receive 
     * @param datagramHandler         the datagram handler
     * @throws IOException If some I/O error occurs
     */
	public MulticastEndpoint(String address, final int port, DatagramSocketConfiguration socketConfiguration, int receiveSize, IDatagramHandler datagramHandler) throws IOException {
		this(InetAddress.getByName(address), port, socketConfiguration, receiveSize, datagramHandler);
	}

	
	
    /**
  	 * Constructs a datagram socket and
  	 * connects it to the given address  
     * 
     * @param address                 the group address
     * @param port                    the port
     * @param receiveSize             the size of the data packet to receive 
     * @param datagramHandler         the datagram handler
     * @throws IOException If some I/O error occurs
     */
	public MulticastEndpoint(InetAddress address, final int port, int receiveSize, IDatagramHandler datagramHandler) throws IOException {
		this(address, port, null, receiveSize, datagramHandler);
	}
	

    /**
  	 * Constructs a datagram socket and
  	 * connects it to the given address  
     * 
     * @param address                 the group address
     * @param port                    the port
	 * @param socketConfiguration     the socket configuration  
     * @param receiveSize             the size of the data packet to receive 
     * @param datagramHandler         the datagram handler
     * @throws IOException If some I/O error occurs
     */
	public MulticastEndpoint(InetAddress address, int port, DatagramSocketConfiguration socketConfiguration, int receiveSize, IDatagramHandler datagramHandler) throws IOException {
		super(datagramHandler, receiveSize);
				
		socket = new MulticastSocket(port);
		if (socketConfiguration != null) {
			socketConfiguration.setOptions(socket);
		}
		
		socket.joinGroup(address);
		multicastAddress = new InetSocketAddress(address, port);
		
		if (datagramHandler != null) {
			startReceiver();
		}
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("upd multicast endpoint bound to " + address.getCanonicalHostName() + "/" + port);
		}
	}
	
	
	
	/**
	 * return the socket options
	 * 
	 * @return the socket options
	 */
	public SocketOptions getSocketOptions() {
		return getSocketOptions(socket);
	}
	
	
	private void startReceiver() {
		Thread receiverThread = new Thread() {
			public void run() {
				while (isRunning) {
					receiveData();
				}
			}
		};
		
		receiverThread.setDaemon(true);
		receiverThread.setName("MulticastReceiver#" + hashCode());
		receiverThread.start();			
	}

	
	
	private void receiveData() {		
	    try {
			byte[] buf = new byte[getReceiveSize()];
			DatagramPacket dp = new DatagramPacket(buf, buf.length);
			socket.receive(dp);
			
            ByteBuffer data = ByteBuffer.wrap(dp.getData());
            data.limit(dp.getLength());   // handles if received byte size is smaller than predefined receive size
            
			onData(new InetSocketAddress(dp.getAddress(), dp.getPort()), data);
			
	    } catch(IOException e) {
	    	if (!socket.isClosed()) {
	    		if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by receiving data. Reason: " + e.toString());
				}
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
			
			try {
				socket.leaveGroup(multicastAddress.getAddress());
				socket.close();
			} catch (Exception e) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by closing multicast socket. Reason: " + e.toString());
				}
			}
			
			super.close();
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
	public void send(UserDatagram packet) throws ClosedConnectionException, IOException {
		if (LOG.isLoggable(Level.FINER)) {
			LOG.finer("[" + "/:" + getLocalPort() + " " + getId() + "] sending datagram " + packet.toString());
		}
		
		packet.prepareForSend();
		
		byte[] bytes = new byte[packet.getData().remaining()];
		packet.getData().get(bytes);
		DatagramPacket dataPacket = new DatagramPacket(bytes, bytes.length, multicastAddress);
		socket.send(dataPacket);
			
		incNumberOfHandledOutgoingDatagram();
	}
}
