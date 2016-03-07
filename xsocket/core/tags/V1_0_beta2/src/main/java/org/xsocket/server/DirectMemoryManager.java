/*
 *  Copyright (c) xsocket.org, 2006. All rights reserved.
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
package org.xsocket.server;

import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * a direct memory manager
 * 
 * @author grro@xsocket.org
 */
final class DirectMemoryManager {

	private static final Logger LOG = Logger.getLogger(DirectMemoryManager.class.getName());

	private int preallocationSize = 524288;	
	private ByteBuffer freeBuffer = null;

	
	/**
	 * constructor 
	 * 
	 * @param preallocationSize the preallocation size
	 */
	DirectMemoryManager(int preallocationSize) {
		this.preallocationSize = preallocationSize;
	}
		
	
	/**
	 * set the preallocation size
	 * @param preallocationSize  the preallocation size
	 */
	public final void setPreallocationSize(int preallocationSize) {
		this.preallocationSize = preallocationSize;
	}
	
	
	/**
	 * get the preallocation size
	 * @return the preallocation size
	 */
	public final int getPreallocationSize() {
		return preallocationSize;
	}

	
	/**
	 * get the current preallocation buffer size
	 *  
	 * @return the current preallocation buffer size
	 */
	public int getCurrentPreallocationBufferSize() {
		ByteBuffer b = freeBuffer;
		if (b == null) {
			return 0;
		} else {
			return b.remaining();
		}
	}
		

	/**
	 * recycle a ByteBuffer
	 * 
	 * @param buffer  the ByteBuffer to recycle 
	 */
	public void recycleMemory(ByteBuffer buffer) {
		assert (Dispatcher.isDispatcherThread()) : "io memory methods has to be called within dispatcher thread";
		
		freeBuffer = buffer;
	}
	
		
	/**
	 * acquire ByteBuffer with free memory
	 * @return the ByteBuffer with free memory 
	 */
	public final ByteBuffer acquireMemory() {
		assert (Dispatcher.isDispatcherThread()) : "io memory methods has to be called within dispatcher thread";
		
		ByteBuffer buffer = null;
		
		if (freeBuffer != null) {
			if (freeBuffer.hasRemaining()) {
				buffer = freeBuffer;
				freeBuffer = null;
			}
		}
		
		if (buffer == null) {
			buffer = newBuffer();
		}
		
		return buffer;
	}
	
	

	final ByteBuffer newBuffer() {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("allocate new physical memory (size: " + preallocationSize + ") by thread " + Thread.currentThread().getName());
		}

		return ByteBuffer.allocateDirect(preallocationSize);
	}
}