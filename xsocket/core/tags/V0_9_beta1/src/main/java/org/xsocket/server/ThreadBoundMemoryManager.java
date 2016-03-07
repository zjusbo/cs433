// $Id: IAllHandler.java 41 2006-06-22 06:30:23Z grro $
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
 * Thread bound Memory Manager
 * 
 * @author grro@xsocket.org
 */
final class ThreadBoundMemoryManager {
	
	private static final Logger LOG = Logger.getLogger(ThreadBoundMemoryManager.class.getName());

	
	private static ThreadLocal<Integer> preallocationSize = new ThreadLocal<Integer>();
	private static ThreadLocal<ByteBuffer> buffer = new ThreadLocal<ByteBuffer>();
	


	private ThreadBoundMemoryManager() {
		
	}
	
	static void setPreallocationSize(int size) {
		preallocationSize.set(size);
	}
	
	static ByteBuffer acquireMemory() {
		return acquireMemory(1);
	}
	
	static ByteBuffer acquireMemory(int minBufferSize) {
		ByteBuffer buf = buffer.get();
		
		// no buffer available
		if (buf == null) {
			buf = newBuffer(preallocationSize.get());
			
		// buffer available
		} else {
			// size sufficient?
			if (buf.limit() < minBufferSize) {
				int allocationSize = preallocationSize.get();
				if (allocationSize < minBufferSize) {
					allocationSize = minBufferSize * 10;
				}
				buf = newBuffer(allocationSize);			
			}
		}
		
		buffer.set(null);
		return buf;
	}

	
	static ByteBuffer newBuffer(int size) {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("allocate new physical memory (size: " + size + ")");
		}
		return ByteBuffer.allocateDirect(size);
	}
	
	
	static void recycleMemory(ByteBuffer buf) {
		if (LOG.isLoggable(Level.FINEST)) {
			LOG.finest("free buffer " + buffer + " has been put back");
		}
		buffer.set(buf);
	}
	
	static ByteBuffer extractAndRecycleMemory(ByteBuffer buffer) {
		
		//	 all bytes used?
		if (buffer.limit() == buffer.capacity()) {
			return buffer;
			
		// not all bytes used -> slice used part
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
