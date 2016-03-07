// $Id: LocalMulticastEndpoint.java 764 2007-01-15 06:26:17Z grro $
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
import java.net.MulticastSocket;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.xsocket.ClosedConnectionException;





/**
 * Mutlicast endpoint <br><br>
 * 
 * Cased by the missing channel support for multicast Datagram this
 * class is implemented by using the "classic" blocking approach
 * 
 * @author grro
 */
public final class LocalMulticastEndpoint extends AbstractEndpoint implements IConnectedEndpoint {

	/*
	 *  * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4527345
     */   

	
	private static Logger LOG = Logger.getLogger(LocalMulticastEndpoint.class.getName());
	
		
	// run flag
	private boolean isRunning = true;
	

	// app handler
	private IConnectedEndpointHandler appHandler = null;

		
	// socket
	private MulticastSocket socket = null;
	private InetAddress group = null;
	private int port = 0;
	
	
	// statistics & jmx
    private long connectionOpenedTime = -1;


    
    /**
     * constructor <br><br>
     * 
     * the global workerpool will be used to handle messages
     * 
     * @param address                 the group address
     * @param port                    the port
     * @throws IOException If some I/O error occurs
     */
    public LocalMulticastEndpoint(String address, final int port) throws IOException {
    	this(address, port, 0, null);
    }

    
    
    /**
     * constructor <br><br>
     * 
     * the global workerpool will be used to handle messages
     * 
     * @param address                 the group address
     * @param port                    the port
     * @param receiveDatasize         the size of the data packet to receive 
     * @param appHandler              the application handler
     * @throws IOException If some I/O error occurs
     */
    public LocalMulticastEndpoint(String address, final int port, int receiveDatasize, IConnectedEndpointHandler appHandler) throws IOException {
    	this(address, port, receiveDatasize, appHandler, 0);
    }
    
	
    	
    /**
     * constructor 
     * 
     * @param address                 the group address
     * @param port                    the port
     * @param receiveDatasize         the size of the data packet to receive 
     * @param appHandler              the application handler
     * @param instanceWorkerPoolSize  the instance exclusive workerpool size or 0 if global workerpool shot be used 
     * @throws IOException If some I/O error occurs
     */
	public LocalMulticastEndpoint(String address, final int port, int receiveDatasize, IConnectedEndpointHandler appHandler, int instanceWorkerPoolSize) throws IOException {
		super(receiveDatasize, instanceWorkerPoolSize);
		
		group = InetAddress.getByName(address);
		this.port = port;
		this.appHandler = appHandler;

		connectionOpenedTime = System.currentTimeMillis();
		
		
		socket = new MulticastSocket(port);
		socket.joinGroup(group);

		if (appHandler != null) {
			startReceiver();
		}
		
		if (LOG.isLoggable(Level.FINE)) {
			if (appHandler != null) {
				LOG.fine("upd multicast endpoint bound to " + group.getCanonicalHostName() + "/" + port + " (server mode: receiveDataSize=" + receiveDatasize + ", appHandler=" + appHandler.toString() + ")");
			} else {
				LOG.fine("upd multicast endpoint bound to " + group.getCanonicalHostName() + "/" + port + " (client mode)");
			}
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public long getConnectionOpenedTime() {
		return connectionOpenedTime;
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
		assert (appHandler != null);
		
	    try {
			byte[] buf = new byte[getReceivePacketSize()];
			final DatagramPacket packet = new DatagramPacket(buf, buf.length);
			socket.receive(packet);
			incNumberOfHandledIncomingDatagram();
			
			logFine("datagram package received (size=" + buf.length + ")");

			getWorkerPool().execute(new Runnable() {
				public void run() {
					try {
						appHandler.onData(LocalMulticastEndpoint.this, ByteBuffer.wrap(packet.getData()));
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
	public String toCompactString() {
		return "MulticastEndpoint " + socket.getLocalAddress().getCanonicalHostName() + "/" + socket.getLocalPort();
	}
	

	
	/**
	 * {@inheritDoc}
	 */
	public void close() {
		if (isRunning) {
			isRunning = false;	
			stopWorkerPool();
			socket.close();
		}
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
	public boolean isOpen() {
		return !socket.isClosed();
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
	public void send(final ByteBuffer data) throws ClosedConnectionException, IOException {
		byte[] bytes = new byte[data.remaining()];
		data.get(bytes);
		DatagramPacket dataPacket = new DatagramPacket(bytes, bytes.length, group, port);
					
		logFine("sending datagram package (size=" + bytes.length + ")");
					
		socket.send(dataPacket);
		incNumberOfHandledOutgoingDatagram();
	}
}
