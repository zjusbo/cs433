// $Id: LocalConnectedEndpoint.java 778 2007-01-16 07:13:20Z grro $
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



import org.xsocket.ClosedConnectionException;
import org.xsocket.Dispatcher;



/**
 * a implementation of a connected endpoint
 * 
 * @author grro
 */
public final class LocalConnectedEndpoint extends AbstractNioBasedEndpoint implements IConnectedEndpoint {

	private static Dispatcher<EndpointHandle> defaultDispatcher = null;

	private IConnectedEndpointHandler appHandler = null;

	private SocketAddress remoteAddress = null;
	
	
	// statistics & jmx
    private long connectionOpenedTime = -1;

    
    
	
	/**
	 * constructor<br><br>
     * 
     * the global workerpool will be used to handle messages
	 * 
	 * @param localePort              the port
     * @param receiveDatasize         the size of the data packet to receive 
     * @param appHandler              the application handler
     * @param remoteAddress           the remoteAddress
     * @throws IOException If some I/O error occurs
	 */
	public LocalConnectedEndpoint(int localePort, int receiveDatasize, IConnectedEndpointHandler appHandler, SocketAddress remoteAddress) throws IOException {
		this(localePort, receiveDatasize, appHandler, 0, remoteAddress);
	}

	
	
	/**
	 * constructor
	 * 
	 * @param localePort              the port
     * @param receiveDatasize         the size of the data packet to receive 
     * @param appHandler              the application handler
     * @param instanceWorkerPoolSize  the instance exclusive workerpool size or 0 if global workerpool shot be used
     * @param remoteAddress           the remoteAddress
     * @throws IOException If some I/O error occurs
	 */
	public LocalConnectedEndpoint(int localePort, int receiveDatasize, IConnectedEndpointHandler appHandler, int instanceWorkerPoolSize, SocketAddress remoteAddress) throws IOException {
		super(receiveDatasize, instanceWorkerPoolSize, localePort, (appHandler == null));
		
		this.appHandler = appHandler;
		
		connect(remoteAddress);
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public long getConnectionOpenedTime() {
		return connectionOpenedTime;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public boolean isConnected() {
		return (remoteAddress != null);
	}


	private void connect(SocketAddress remoteAddress) throws IOException {
		this.remoteAddress = remoteAddress;
		connectionOpenedTime = System.currentTimeMillis();
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
	@Override
	protected Dispatcher<EndpointHandle> createDispatcher() throws IOException {
		return createAndStartDispatcher(new ConnectedEventHandler());
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Dispatcher<EndpointHandle> getDefaultDispatcher() throws IOException {
		if (defaultDispatcher == null) {
			defaultDispatcher = createAndStartDispatcher(new ConnectedEventHandler());
		}
		return defaultDispatcher;
	}

	/**
	 * {@inheritDoc}
	 */
	public void send(final ByteBuffer data) throws ClosedConnectionException, IOException {
		sendDatagram(remoteAddress, data);
	}
	
	/**
	 * {@inheritDoc}
	 */
	IConnectedEndpointHandler getAppHandler() {
		return appHandler;
	}

	
	/**
	 * {@inheritDoc}
	 */
	public SocketAddress getRemoteAddress() {
		return remoteAddress;
	}
}
