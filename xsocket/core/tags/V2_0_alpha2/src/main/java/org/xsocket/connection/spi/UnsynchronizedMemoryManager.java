/*
 *  Copyright (c) xsocket.org, 2006 - 2008. All rights reserved.
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
package org.xsocket.connection.spi;


import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;




/**
 * Single thread memory manager
 * 
 * 
 * @author grro@xsocket.org
 */
final class UnsynchronizedMemoryManager extends AbstractMemoryManager {
		
	private static final Logger LOG = Logger.getLogger(UnsynchronizedMemoryManager.class.getName());
	
	private ByteBuffer freeBuffer = null;

	
	
	/**
	 * constructor
	 * 
	 * @param allocationSize               the buffer to allocate
	 * @param preallocate                  true, if buffer should be preallocated
	 * @param minPreallocatedBufferSize    the minimal buffer size
	 * @param useDirectMemory  true, if direct memory should be used
	 */
	private UnsynchronizedMemoryManager(int preallocationSize, boolean preallocate, int minPreallocatedBufferSize, boolean useDirectMemory) {
		super(preallocationSize, preallocate, minPreallocatedBufferSize, useDirectMemory);
	}
	
	
	public static UnsynchronizedMemoryManager createPreallocatedMemoryManager(int preallocationSize, int minBufferSze, boolean useDirectMemory) {
		return new UnsynchronizedMemoryManager(preallocationSize, true, minBufferSze, useDirectMemory);
	}
	
	
	public static UnsynchronizedMemoryManager createNonPreallocatedMemoryManager(boolean useDirectMemory) {
		return new UnsynchronizedMemoryManager(0, false, 1, useDirectMemory);
	}
	
	


	/**
	 * {@inheritDoc}
	 */
	public int getCurrentSizePreallocatedBuffer() {
		if (freeBuffer != null) {
			return freeBuffer.remaining();
		} else {
			return 0;
		}
	}
	
		

	/**
	 * {@inheritDoc}
	 */
	public void recycleMemory(ByteBuffer buffer) {
		
		// preallocate mode?
		if (isPreallocationMode()) {
			if (buffer.remaining() >= getPreallocatedMinBufferSize()) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("recycling " + DataConverter.toFormatedBytesSize(buffer.remaining()));
				}
				freeBuffer = buffer;
			}
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public ByteBuffer acquireMemoryStandardSizeOrPreallocated(int standardSize) {
		if (isPreallocationMode()) {
			preallocate();
		} else {
			freeBuffer = newBuffer(standardSize);
		}

		ByteBuffer buffer = freeBuffer;
		freeBuffer = null;

		return buffer;
	}
	

	

	/**
	 * {@inheritDoc}
	 */
	public void preallocate() {
		if (isPreallocationMode()) {
			
			// sufficient size?
			if (freeBuffer != null) {
				if (freeBuffer.remaining() >= getPreallocatedMinBufferSize()) {
					return;
				}
			}
				
			// no, allocate new 
			freeBuffer = newBuffer(gettPreallocationBufferSize());
		}
	}
	

}
