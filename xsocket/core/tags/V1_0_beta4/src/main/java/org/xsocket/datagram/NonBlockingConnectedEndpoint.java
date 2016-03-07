// $Id: NonBlockingConnectedEndpoint.java 914 2007-02-12 19:21:02Z grro $
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
import java.net.InetSocketAddress;
import java.net.SocketAddress;




/**
 * an implementation of a non-blocking connected endpoint
 * 
 * @author grro
 */
public final class NonBlockingConnectedEndpoint extends NonBlockingEndpoint implements IConnectedEndpoint {

	private SocketAddress remoteAddress = null;
	
	
	// statistics & jmx
    private long connectionOpenedTime = -1;

    
	/**
	 * constructor<br><br>
     * 
     * @param host    the remote host
     * @param port    the remote port
     * @throws IOException If some I/O error occurs
	 */
	public NonBlockingConnectedEndpoint(String host, int port) throws IOException {
		this(new InetSocketAddress(host, port), null, 0, 0);
	}

	/**
	 * constructor<br><br>
     * 
     * @param address   the socket address of the remote endpoint
     * @throws IOException If some I/O error occurs
	 */
	public NonBlockingConnectedEndpoint(SocketAddress address) throws IOException {
		this(address, null, 0, 0);
	}

    
	
	/**
	 * constructor
	 * 
     * @param appHandler               the data handler
     * @param receivePacketSize        the receive packet size    
     * @param workerPoolSize           the instance exclusive workerpool size or 0 if global workerpool shot be used
     * @param remoteAddress            the remoteAddress
     * @throws IOException If some I/O error occurs
	 */
	public NonBlockingConnectedEndpoint(SocketAddress remoteAddress, IDatagramHandler appHandler, int receivePacketSize, int workerPoolSize) throws IOException {
		super(0, appHandler, receivePacketSize, workerPoolSize);
		
		this.remoteAddress = remoteAddress;
		getChannel().connect(remoteAddress);
		connectionOpenedTime = System.currentTimeMillis();
	}
	

	
	/**
	 * {@inheritDoc}
	 */
	public final void send(Packet packet) throws IOException {
		packet.setRemoteAddress(remoteAddress);
		super.send(packet);
	}
	
	

	
	/**
	 * {@inheritDoc}
	 */
	public void close() {
		remoteAddress = null;

		super.close();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public SocketAddress getRemoteSocketAddress() {
		return remoteAddress;
	}
}
