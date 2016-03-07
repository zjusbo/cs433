// $Id: AbstractEndpoint.java 910 2007-02-12 16:56:19Z grro $
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


import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.WorkerPool;



/**
 * Endpoint implementation base 
 *
 * @author grro
 */
abstract class AbstractEndpoint implements IEndpoint {

	private static final Logger LOG = Logger.getLogger(AbstractEndpoint.class.getName());
	
	// ids
	private static long nextId = 0; 
	private long id = 0;
	
	
	
	// encoding
	private String defaultEncoding = "UTF-8";
	
	
	// workers 
	private static WorkerPool globalWorkerPool = null;
	private WorkerPool workerPool = null;

	
	// statistics & jmx
	private long handleIncomingDatagrams = 0;
	private long handleOutgoingDatagrams = 0;
	
 
	
    /**
     * constructor
     *  
     * @param workerPoolSize  the instance exclusive workerpool size or 0 if global workerpool shot be used  
     */
    AbstractEndpoint(int workerPoolSize) {
    	
    	id = ++nextId;
		
		if (workerPoolSize > 0) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("create instance specific workerpool with size " + workerPoolSize);
			}				
			workerPool = new WorkerPool();
			workerPool.setSize(workerPoolSize);
				
		} else {	
			workerPool = getGlobalWorkerPool();
    	}
    }
    
    
    private synchronized WorkerPool getGlobalWorkerPool() {
    	if (globalWorkerPool == null) {
    		globalWorkerPool = new WorkerPool(GLOBAL_WORKER_POOL_SIZE);
    	}
    	return globalWorkerPool;
    }
    
    
    
    
	
	/**
	 * get the worker pool
	 * 
	 * @return the worker pool
	 */
	protected final WorkerPool getWorkerPool() {
		return workerPool; 
	}
	
	
	/**
	 * stops the worker pool
	 */
	protected final void stopWorkerPool() {
		getWorkerPool().stopPooling();
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
	 * increase the number of handled incomming datagram
	 */
	protected final void incNumberOfHandledIncomingDatagram() {
		handleIncomingDatagrams++;
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
