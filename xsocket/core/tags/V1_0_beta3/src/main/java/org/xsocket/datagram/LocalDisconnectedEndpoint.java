// $Id: LocalDisconnectedEndpoint.java 778 2007-01-16 07:13:20Z grro $
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
import java.net.SocketAddress;
import java.nio.ByteBuffer;


import org.xsocket.Dispatcher;




/**
 * a implementation of a disconnected endpoint
 * 
 * @author grro
 */
public final class LocalDisconnectedEndpoint extends AbstractNioBasedEndpoint implements IDisconnectedEndpoint {

	
	// default dispatcher
	private static Dispatcher<EndpointHandle> defaultDispatcher = null;
	
	private IDisconnectedEndpointHandler appHandler = null;

	
	
	/**
	 * constructor<br><br>
     *  
     * @throws IOException If some I/O error occurs
	 */
	public LocalDisconnectedEndpoint() throws IOException {
		this(0, 0, null);
	}

	
	/**
	 * constructor<br><br>
     * 
     * the global workerpool will be used to handle messages
     * 
	 * @param localePort              the port
     * @param receiveDatasize         the size of the data packet to receive 
     * @param appHandler              the application handler
     * @throws IOException If some I/O error occurs
	 */
	public LocalDisconnectedEndpoint(int localePort, int receiveDatasize, IDisconnectedEndpointHandler appHandler) throws IOException {
		this(localePort, receiveDatasize, appHandler, 0);
	}


	
	/**
	 * constructor
	 * 
	 * @param localePort              the port
     * @param receiveDatasize         the size of the data packet to receive 
     * @param appHandler              the application handler
     * @param instanceWorkerPoolSize  the instance exclusive workerpool size or 0 if global workerpool should be used 
     * @throws IOException If some I/O error occurs
	 */
	public LocalDisconnectedEndpoint(int localePort, int receiveDatasize, IDisconnectedEndpointHandler appHandler, int instanceWorkerPoolSize) throws IOException {
		super(receiveDatasize, instanceWorkerPoolSize, localePort, (appHandler == null));
		
		this.appHandler = appHandler;
	}

	
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Dispatcher<EndpointHandle> createDispatcher() throws IOException {
		return createAndStartDispatcher(new DisconnectedEventHandler());
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Dispatcher<EndpointHandle> getDefaultDispatcher() throws IOException {
		if (defaultDispatcher == null) {
			defaultDispatcher = createAndStartDispatcher(new DisconnectedEventHandler());
		}
		return defaultDispatcher;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void send(SocketAddress remoteAddress, ByteBuffer data) throws IOException {
		sendDatagram(remoteAddress, data);
	}
	
	
	/**
	 * send data to a remote host
	 * 
	 * @param remoteAddress  the address of the remote host
	 * @param data           the data to send
     * @throws IOException If some I/O error occurs
	 */
	public static void sendData(SocketAddress remoteAddress, ByteBuffer data) throws IOException {
		IDisconnectedEndpoint endpoint = new LocalDisconnectedEndpoint();
		endpoint.send(remoteAddress, data);
		endpoint.close();
	}
	
	
	/**
	 * get the assigned handler
	 * 
	 * @return the assigned handler
	 */
	IDisconnectedEndpointHandler getAppHandler() {
		return appHandler;
	}
}
