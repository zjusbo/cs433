// $Id: DisconnectedEventHandler.java 778 2007-01-16 07:13:20Z grro $
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
import java.nio.BufferUnderflowException;

import org.xsocket.IEventHandler;
import org.xsocket.datagram.AbstractNioBasedEndpoint.DataPackage;
import org.xsocket.datagram.AbstractNioBasedEndpoint.EndpointHandle;


/**
 * Event Handler for a disconnected endpoint 
 * 
 * @author grro
 */
final class DisconnectedEventHandler implements IEventHandler<EndpointHandle> {
	
	/**
	 * {@inheritDoc}
	 */
	public void onHandleRegisterEvent(EndpointHandle handle) throws IOException {
		
	}
	
		
	/**
	 * {@inheritDoc}
	 */
	public void onHandleReadableEvent(EndpointHandle handle) {
		final LocalDisconnectedEndpoint endpoint = (LocalDisconnectedEndpoint) handle.getEndpoint();
		
		if (endpoint.isOpen()) {
		
			try {
				// perform non-blocking read operation
				final DataPackage dataPackage = endpoint.receive();
				
				if (dataPackage != null) {
				
					endpoint.getWorkerPool().execute(new Runnable() {
						public void run() {
							try {
								synchronized (endpoint) {
									endpoint.getAppHandler().onData(endpoint, dataPackage.getData(), dataPackage.getAddress());
								}
							} catch (Throwable e) {
								endpoint.logFine("error occured by performing onData task. Reason: " + e.toString());
							}							
						}
					});
				}
			} catch (IOException ioe) {
				endpoint.logFine("error occured while receiving. Reason: " + ioe.toString());
			}
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void onHandleWriteableEvent(EndpointHandle handle) throws IOException {
		handle.getEndpoint().writePhysical();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void onDispatcherCloseEvent(final EndpointHandle handle) {

		handle.getEndpoint().getWorkerPool().execute(new Runnable() {
			public void run() {
				try {
					synchronized (handle) {
						handle.close();
					}
				} catch (BufferUnderflowException bue) {
					// ignore 
			
				} catch (Throwable e) {
					handle.getEndpoint().logFine("error occured by performing onDispatcherCloseEvent. Reason: " + e.toString());
				}							
			}
		});
	}
}
