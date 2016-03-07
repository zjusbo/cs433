// $Id: AbstractEndpoint.java 1049 2007-03-21 16:42:48Z grro $
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
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;
import org.xsocket.DynamicWorkerPool;
import org.xsocket.IWorkerPool;



/**
 * Endpoint implementation base 
 *
 * @author grro@xsocket.org
 */
abstract class AbstractEndpoint implements IEndpoint {
	
	private static final Logger LOG = Logger.getLogger(AbstractEndpoint.class.getName());
	
	
	private static IWorkerPool GLOBAL_WORKERPOOL = new DynamicWorkerPool(0, 250);
	
	// ids
	private static long nextId = 0; 
	private long id = 0;
	
	
	
	// encoding
	private String defaultEncoding = "UTF-8";

	
	// receive data handling
	private final Object readGuard = new Object();
	private final List<UserDatagram> receiveQueue = Collections.synchronizedList(new ArrayList<UserDatagram>());
	private int receiveSize = -1;  



	// datagram handler
	private IDatagramHandler datagramHandler = null;

	
	// workers
	private IWorkerPool workerPool = null;

	
	// statistics & jmx
	private long handleIncomingDatagrams = 0;
	private long handleOutgoingDatagrams = 0;
	
 
	
    /**
     * constructor
     *  
     * @param useGlobalWorkerpool  true, ifglobal worker pool should be used
     * @param datagramHandler      the datagram handler
     * @param receiveSize          the receive packet size  
     */
    AbstractEndpoint(IDatagramHandler datagramHandler, int receiveSize) {
    	this.datagramHandler = datagramHandler;
    	this.receiveSize = receiveSize;
    	
    	id = ++nextId;
		
    	workerPool = GLOBAL_WORKERPOOL;
		
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				close();
			}
		});
    }

    public void close() {
    	if (workerPool != GLOBAL_WORKERPOOL) {
    		if(workerPool instanceof DynamicWorkerPool) {
    			((DynamicWorkerPool) workerPool).close();
    		}
    	}
    }
    
    public void setWorkerPool(IWorkerPool workerPool) {
    	this.workerPool = workerPool;
    }
    
 
    
	/**
	 * {@inheritDoc}
	 */	
	public final void setReceiveSize(int receivePacketSize) {
		this.receiveSize = receivePacketSize;
	}
	
	/**
	 * {@inheritDoc}
	 */	
	public final int getReceiveSize() {
		return receiveSize;
	}
	
	protected final void onData(SocketAddress address, ByteBuffer data) {
		UserDatagram packet = new UserDatagram(address, data, getDefaultEncoding());
		receiveQueue.add(packet);
			
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + "/:" + getLocalPort() + " " + getId() + "] datagram received: " + packet.toString());
		}
		
		handleIncomingDatagrams++;

		
		if (datagramHandler != null) {
			workerPool.execute(new Runnable() {
				@SuppressWarnings("unchecked")
				public void run() {
					try {
						synchronized (AbstractEndpoint.this) {
							datagramHandler.onDatagram(AbstractEndpoint.this);
						}
					} catch (Throwable e) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("error occured by performing onData task. Reason: " + e.toString());
						}
					}							
				}
			});
		}
	}

	
	
	/**
	 * {@inheritDoc}
	 */
	public final UserDatagram receive(long timeoutMillis) throws IOException, SocketTimeoutException {
		if (getReceiveSize() == -1) {
			throw new IOException("the receive packet size hasn't been set");
		}
		
		UserDatagram datagram = null;
		
		// no timeout set
		if (timeoutMillis <= 0) {
			datagram = receive();
		
			
		// timeout set
		} else {
			long start = System.currentTimeMillis();
			
			synchronized (readGuard) {
				do {
					datagram = receive();
					if (datagram != null) {
						break;
					} else {
						try {
							readGuard.wait(timeoutMillis / 10);
						} catch (InterruptedException ignore) { }					
					}
				} while (System.currentTimeMillis() < (start + timeoutMillis));
			}
		}
			
		if (datagram == null) {
			throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(timeoutMillis) + " reached");
		} else {
			return datagram;
		}	
	}
	

	public UserDatagram receive() {
		if (receiveQueue.isEmpty()) {
			return null;
		} else {
			return receiveQueue.remove(0);
		}
	}
	

	/**
	 * {@inheritDoc}
	 */
	public final String getDefaultEncoding() {
		return defaultEncoding;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void setDefaultEncoding(String defaultEncoding) {
		this.defaultEncoding = defaultEncoding;
	}
	
	
	

	/** 
	 * increase the number of handled outgoing datagram
	 */
	protected final void incNumberOfHandledOutgoingDatagram() {
		handleOutgoingDatagrams++;
	}	
	
	/**
	 * return the id 
	 * 
	 * @return the id
	 */
	protected final long getId() {
		return id;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return Long.toString(id);
	}
}
