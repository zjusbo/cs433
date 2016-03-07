// $Id: IoSocketDispatcherPool.java 1301 2007-06-01 15:52:16Z grro $
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
package org.xsocket.stream.io.impl;


import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;



/**
 * Single thread memory manager
 * 
 * 
 * @author grro@xsocket.org
 */
final class UnsynchronizedMemoryManager implements IMemoryManager {
		
	private static final Logger LOG = Logger.getLogger(UnsynchronizedMemoryManager.class.getName());
	
	private ByteBuffer freeBuffer = null;
	
	private boolean useDirectMemory = false;
	private int preallocationSize = 65536;	
	
	
	/**
	 * constructor
	 * 
	 * @p
	 * @param useDirectMemory     true, if direct memory should be used
	 */
	UnsynchronizedMemoryManager(int preallocationSize, boolean useDirectMemory) {
		this.preallocationSize = preallocationSize;
		this.useDirectMemory = useDirectMemory;
	}
	
	/**
	 * {@inheritDoc}
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
	 * {@inheritDoc}
	 */
	public void recycleMemory(ByteBuffer buffer, int minSize) {
		int remaining = buffer.remaining(); 
		if (remaining >= minSize) {
			freeBuffer = buffer;
		}
	}
		
	
	/**
	 * {@inheritDoc}
	 */
	public final ByteBuffer acquireMemory(int minSize) {
		preallocate(minSize);

		ByteBuffer buffer = freeBuffer;
		freeBuffer = null;

		return buffer;
	}
	

	/**
	 * {@inheritDoc}
	 */
	public void preallocate(int minSize) {

		// sufficient size?
		if (freeBuffer != null) {
			if (freeBuffer.remaining() >= minSize) {
				return;
			}
		}
			
		// no, allocate new 
		freeBuffer = newBuffer(preallocationSize);
	}
	
		
	/**
	 * {@inheritDoc}
	 */
	private ByteBuffer newBuffer(int preallocationSize) {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("allocate new physical memory (size: " + preallocationSize + ") by thread " + Thread.currentThread().getName());
		}
	
		if (useDirectMemory) {
			return ByteBuffer.allocateDirect(preallocationSize);
		} else {
			return ByteBuffer.allocate(preallocationSize);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public int getFreeBufferSize() {
		if (freeBuffer != null) {
			return freeBuffer.remaining();
		} else {
			return 0;
		}
	}
}
