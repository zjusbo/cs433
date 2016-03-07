// $Id: MemoryManager.java 778 2007-01-16 07:13:20Z grro $
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
package org.xsocket.stream;

import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;


/**
 * a Memory Manager implementation  
 *  
 * @author grro@xsocket.org
 */
class MemoryManager implements IMemoryManager { 
	
	private static final Logger LOG = Logger.getLogger(MemoryManager.class.getName());
	
		
	private List<SoftReference<ByteBuffer>> memoryBuffer = new ArrayList<SoftReference<ByteBuffer>>();

	private boolean useDirectMemory = false;
	private int preallocationSize = 4096;
		
	/**
	 * constructor 
	 * 
	 * @param preallocationSize   the preallocation size
	 * @param useDirectMemory     true, if direct memory should be used
	 */
	MemoryManager(int preallocationSize, boolean useDirectMemory) {
		this.preallocationSize = preallocationSize;
		this.useDirectMemory = useDirectMemory;
	}
		
	
	/**
	 * return the free memory size
	 * 
	 * @return the free memory size
	 */
	public final synchronized int getFreeBufferSize() {
		int size = 0;
		for (SoftReference<ByteBuffer> bufferRef: memoryBuffer) {
			ByteBuffer buffer = bufferRef.get();
			if (buffer != null) {
				size += buffer.remaining();
			}
		}
		return size;
	}
			
	
	/**
	 * recycle free memory
	 * 
	 * @param buffer the buffer to recycle
	 */
	public final synchronized void recycleMemory(ByteBuffer buffer) {
		if (buffer.hasRemaining()) {
			memoryBuffer.add(new SoftReference<ByteBuffer>(buffer.slice()));
		}
	}

			
	/**
	 * aquire free memory
	 * 
	 * @param minSize  the min size of the aquired memory
	 */
	public final synchronized ByteBuffer acquireMemory(int minSize) {		
		ByteBuffer buffer = null;
					
		if (!memoryBuffer.isEmpty()) {
			SoftReference<ByteBuffer> freeBuffer = memoryBuffer.remove(0);
			buffer = freeBuffer.get();

			if (buffer != null) {
				// size sufficient?
				if (buffer.limit() < minSize) {
					buffer = null;			
				}
			}
		} 
					
				
		if (buffer == null) {
			int size = getPreallocationSize();
			if (getPreallocationSize() < minSize) {
				size = minSize * 4;
			}
				
			
			buffer = newBuffer(size);
		}
					
		return buffer;
	}	
	
	
	/**
	 * return the preallocation size
	 *  
	 * @return the preallocation size
	 */
	int getPreallocationSize() {
		return preallocationSize;
	}
	
	
	
	private final ByteBuffer newBuffer(int size) {
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
	 * extract the taken memory und recycle the remaing by using the 
	 * ByteBuffer position 
	 * 
	 * @param buffer  the buffer the extract and recycle
	 * @return the taken memory
	 */
	public final ByteBuffer extractAndRecycleMemory(ByteBuffer buffer) {
		// all bytes used?
		if (buffer.limit() == buffer.capacity()) {
			return buffer;

		// 	not all bytes used -> slice used part
		} else {
	   		int savedLimit = buffer.limit();
	   		ByteBuffer slicedPart = buffer.slice();

	   		// .. and return the remaining buffer for reuse
		   	buffer.position(savedLimit);
		   	buffer.limit(buffer.capacity());
			ByteBuffer unused = buffer.slice();
			recycleMemory(unused);

			return slicedPart;
		}
	}
}