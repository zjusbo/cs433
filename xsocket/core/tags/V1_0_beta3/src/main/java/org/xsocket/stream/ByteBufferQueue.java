// $Id: ByteBufferQueue.java 776 2007-01-15 17:15:41Z grro $
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

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

import org.xsocket.DataConverter;


/**
 * A ByteBuffer queue. <br><br>
 * 
 * All method are thread save
 * 
 * 
 * @author grro@xsocket.org
 */
final class ByteBufferQueue {
	
	private LinkedList<ByteBuffer> buffers = null; 

	
	/**
	 * returns true, if empty
	 * 
	 * @return true, if empty
	 */
	synchronized boolean isEmpty() {
		if (buffers == null) {
			return true;
		} else {
			return buffers.isEmpty();
		}
	}

	
	/**
	 * return the current size
	 *  
	 * @return  the current size
	 */
	synchronized int getSize() {
		if (isEmpty()) {
			return 0;
		} else {
			int size = 0;
			for (ByteBuffer buffer : buffers) {
				size += buffer.remaining();
			}
			return size;
		}
	}


	/**
	 * append a byte buffer to this queue. 
	 * 
	 * @param data the ByteBuffer to append
	 */
	synchronized void append(ByteBuffer data) {
		if (data != null) {
			if (data.hasRemaining()) {
				if (buffers == null) {
					buffers= new LinkedList<ByteBuffer>();
				}
				
				buffers.add(data);
			}
		}
	}

	
	/**
	 * append a list of byte buffer to this queue.
	 * 
	 * @param datas  the list of ByteBuffer
	 */
	synchronized void append(LinkedList<ByteBuffer> datas) {
		if (datas != null) {
		
			if (buffers == null) {
				buffers = datas;
			}  else {
				buffers.addAll(datas);
			}
		}
	}

	
	/**
	 * add a byte buffer at the first position
	 * 
	 * @param data the ByteBuffer to add
	 */
	synchronized void addFirst(ByteBuffer data) {
		if (data != null) {
			if (data.hasRemaining()) {
				if (buffers == null) {
					buffers= new LinkedList<ByteBuffer>();
				}
			
				buffers.addFirst(data);
			}
		}
	}

	
	/**
	 * add a list of byte buffer to the first position
	 * 
	 * @param datas the list of ByteBuffer to add
	 */
	synchronized void addFirst(LinkedList<ByteBuffer> datas) {
		if (datas != null) {
			if (buffers == null) {
				buffers= new LinkedList<ByteBuffer>();
			}
			
			datas.addAll(buffers);
			buffers = datas;
		}
	}
	
	
	/**
	 * drain the queue
	 * 
	 * @return the queue content 
	 */
	synchronized LinkedList<ByteBuffer> drain() {
		LinkedList<ByteBuffer> result = buffers;
		buffers = null;
		return result;
	}
	
	
	/**
	 * get the size of the first ByteBuffer in queue
	 * 
	 * @return the size of the first ByteBuffer
	 */
	synchronized int getFirstBufferSize() {
		if (buffers == null) {
			return 0;
		}  

		if (buffers.isEmpty()) {
			return 0;
		}

		ByteBuffer buffer = buffers.getFirst();
		return buffer.remaining();
	}
	
	
	/**
	 * remove the first ByteBuffer 
	 * @return the first ByteBuffer
	 */
	public synchronized ByteBuffer removeFirst() {
		if (buffers == null) {
			return null;
		}
		
		return buffers.removeFirst();
	}
	
	
	/**
	 * read bytes 
	 * 
	 * @param length  the length
	 * @return the read bytes
 	 * @throws BufferUnderflowException if the buffer's limit has been reached  
	 */
	public synchronized byte[] read(int length) throws BufferUnderflowException {
		if (buffers == null) {
			throw new BufferUnderflowException();
		}
		
		
		byte[] bytes = new byte[length];
		int pointer = 0;

		// enough bytes available ?
		if (!isSizeEqualsOrLargerThan(length)) {
			throw new BufferUnderflowException();
		}

		ByteBuffer buffer = buffers.removeFirst();

		while (true) {
			// read  the buffer
			while(buffer.hasRemaining()) {
				bytes[pointer] = buffer.get();
				pointer++;
				if (pointer == length) {
					if (buffer.position() < buffer.limit()) {
						buffers.addFirst(buffer.slice());
					}
					return bytes;
				}
			}

			buffer = buffers.poll();
		}
	}
	
	
	
	private boolean isSizeEqualsOrLargerThan(int size) {
		if (buffers == null) {
			return false;
		}
		
		int l = 0;

		for (ByteBuffer buffer : buffers) {
			l += buffer.remaining();
			if (l >= size) {
				return true;
			}
		}

		return false;
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		if (buffers == null) {
			return "";
		} else {
			return DataConverter.toTextOrHexString(buffers.toArray(new ByteBuffer[buffers.size()]), "US-ASCII", 500);
		}
	}
}
