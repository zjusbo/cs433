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
 * implementation base for a Memory Manager implementation  
 *  
 * @author grro@xsocket.org
 */
abstract class AbstractMemoryManager implements IMemoryManager { 
	
	private static final Logger LOG = Logger.getLogger(AbstractMemoryManager.class.getName());
	

	// direct or non-direct buffer
	private boolean useDirectMemory = false;

	
	// preallocation support
	private int preallocationSize = 65536;
	private int minPreallocatedBufferSize = 1;
	private boolean preallocate = false;
	
	
	
	/**
	 * constructor
	 * 
	 * @param allocationSize               the buffer to allocate
	 * @param preallocate                  true, if buffer should be preallocated
	 * @param minPreallocatedBufferSize    the minimal buffer size
	 * @param useDirectMemory  true, if direct memory should be used
	 */
	protected AbstractMemoryManager(int preallocationSize, boolean preallocate, int minPreallocatedBufferSize, boolean useDirectMemory) {
		this.preallocationSize = preallocationSize;
		this.preallocate = preallocate;
		this.minPreallocatedBufferSize = minPreallocatedBufferSize;
		this.useDirectMemory = useDirectMemory;
	}
	

	
	/**
	 * {@inheritDoc}
	 */
	public final boolean isPreallocationMode() {
		return preallocate;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void setPreallocationMode(boolean mode) {
		this.preallocate = mode;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void setPreallocatedMinBufferSize(Integer minSize) {
		this.minPreallocatedBufferSize = minSize;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final Integer getPreallocatedMinBufferSize() {
		return minPreallocatedBufferSize;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final Integer gettPreallocationBufferSize() {
		return preallocationSize;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void setPreallocationBufferSize(Integer minSize) {
		this.preallocationSize = minSize;
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final boolean isDirect() {
		return useDirectMemory;
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final void setDirect(boolean isDirect) {
		this.useDirectMemory = isDirect;
	}
	
		

	/**
	 * {@inheritDoc}
	 */
	public final ByteBuffer extractAndRecycleMemory(ByteBuffer buffer, int read) {
		
		ByteBuffer readData = null;
		
		if (read > 0) {
			buffer.limit(buffer.position());
			buffer.position(buffer.position() - read);
			
			// slice the read data
			readData = buffer.slice();
			
			// preallocate mode?
			if (preallocate) {
				// does buffer contain remaining free data? -> recycle these
				if (buffer.limit() < buffer.capacity()) {
					buffer.position(buffer.limit());
					buffer.limit(buffer.capacity());
			
					recycleMemory(buffer);
				}			
			}
			
		} else {
			readData = ByteBuffer.allocate(0);
			
			if (preallocate) {
				recycleMemory(buffer);
			}
		}

		

		
		
		return readData;
	}
	
	


	/**
	 * {@inheritDoc}
	 */
	public final ByteBuffer acquireMemoryMinSize(int minSize) {
		
		// preallocation mode?
		if (preallocate) {
			
			// ... yes, but is required size larger than preallocation size?
			if (preallocationSize < minSize) {
				// ... yes. create a new buffer
				return newBuffer(minSize);
				
			// ... no, call method to get preallocated buffer first 
			} else {
				ByteBuffer buffer = acquireMemoryStandardSizeOrPreallocated(minSize);
				
				// buffer to small?
				if (buffer.remaining() < minSize) {
					// yes, create a new one
					return newBuffer(minSize);
				}
				return buffer;
			}
			
		// .. no 	
		} else {
			return newBuffer(minSize);
		}
	}
	
	
	/**
	 * creates a new buffer
	 * @param size  the size of the new buffer
	 * @return the new buffer
	 */
	protected final ByteBuffer newBuffer(int size) {
		if (useDirectMemory) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("allocating " + DataConverter.toFormatedBytesSize(size) + " direct memory");
			}

			return ByteBuffer.allocateDirect(size);

		} else {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("allocating " + DataConverter.toFormatedBytesSize(size) + " heap memory");
			}

			return ByteBuffer.allocate(size);
		}
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("useDirect=" + useDirectMemory + " preallocationOn=" 
				  + preallocate + " preallcoationSize=" + DataConverter.toFormatedBytesSize(preallocationSize)
				  + " preallocatedMinSize=" + DataConverter.toFormatedBytesSize(minPreallocatedBufferSize));		
		return sb.toString();
	}
}