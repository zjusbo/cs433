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
package org.xsocket.connection;

import java.nio.ByteBuffer;



/**
 *
 * @author grro@xsocket.org
 */
final class IoQueue  {

	/*
	 * the implementation is driven by the assumption, that
	 * in most cases just one buffer or one buffer array 
	 * will be enqueued
	 */
	
	
	private ByteBuffer[] buffers = null;
		
	
		
	/**
	 * returns true, if empty
	 *
	 * @return true, if empty
	 */
	public synchronized boolean isEmpty() {
		if (buffers == null) {
			return true;
			
		} else {
			return (getSize() == 0);
		}
	}

	

	/**
	 * return the current size
	 *
	 * @return  the current size
	 */
	public synchronized int getSize() {
		int size = 0; 
		
		if (buffers != null) {
			for (int i = 0; i < buffers.length; i++) {
				if (buffers[i] != null) {
					size += buffers[i].remaining();
				}
			}
		}
		
		return size;
	}
	
	
	
	/**
	 * append a byte buffer to this queue.
	 *
	 * @param data the ByteBuffer to append
	 */
	public synchronized void append(ByteBuffer data) {
		if (data == null) {
			return;
		}
		
		if (buffers == null) {
			buffers = new ByteBuffer[1];
			buffers[0] = data;

		} else {
			ByteBuffer[] newBuffers = new ByteBuffer[buffers.length + 1];
			System.arraycopy(buffers, 0, newBuffers, 0, buffers.length);
			newBuffers[buffers.length] = data;
			buffers = newBuffers;
		}
	}

		
	/**
	 * append a list of byte buffer to this queue. By adding a list,
	 * the list becomes part of to the buffer, and should not be modified outside the buffer
	 * to avoid side effects
	 *
	 * @param bufs  the list of ByteBuffer
	 */
	public synchronized void append(ByteBuffer[] data) {
		if (data == null) {
			return;
		}

		if (buffers == null) {
			buffers = data;

		} else {
			ByteBuffer[] newBuffers = new ByteBuffer[buffers.length + data.length];
			System.arraycopy(buffers, 0, newBuffers, 0, buffers.length);
			System.arraycopy(data, 0, newBuffers, buffers.length, data.length);
			buffers = newBuffers;
		}
	}

	
	
	
	/**
	 * add the given ByteBuffer array into the head of the queue
	 *
	 * @param bufs  the list of ByteBuffer
	 */
	public synchronized void addFirst(ByteBuffer[] data) {
		if (data == null) {
			return;
		}

		if (buffers == null) {
			buffers = data;

		} else {
			ByteBuffer[] newBuffers = new ByteBuffer[buffers.length + data.length];
			System.arraycopy(data, 0, newBuffers, 0, data.length);
			System.arraycopy(buffers, 0, newBuffers, data.length, buffers.length);
			buffers = newBuffers;
		}
	}
	

	/**
	 * drain this queue
	 *  
	 * @return  the contained ByteBuffer array or <code>null</code>
	 */
	public synchronized ByteBuffer[] drain() {
		ByteBuffer[] result = buffers;
		buffers = null;

		return result;
	}
}
