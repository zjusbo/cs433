// $Id: ByteBufferQueue.java 1383 2007-06-27 08:10:17Z grro $
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
package org.xsocket;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.LinkedList;



/**
 * A ByteBuffer queue for framework-internal usage
 *
 *
 * @author grro@xsocket.org
 */
public final class ByteBufferQueue {

	
	private static final Integer UNKNOWN = null;
	
	private LinkedList<ByteBuffer> buffers = null;
	private Integer currentSize = UNKNOWN; 
	private int insertVersion = 0;
	
	


	/**
	 * returns true, if empty
	 *
	 * @return true, if empty
	 */
	public synchronized boolean isEmpty() {
		
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
	public synchronized int getSize() {
		
		// performance optimization if size has been already calculated
		if (currentSize != UNKNOWN) {
			return currentSize;
		}
		
		
		if (isEmpty()) {
			return 0;
			
		} else {
			int size = 0;
			for (ByteBuffer buffer : buffers) {
				size += buffer.remaining();
			}
			
			currentSize = size;
			return size;
		}
	}

	
	
	

	/**
	 * get the size of the first ByteBuffer in queue
	 *
	 * @return the size of the first ByteBuffer
	 */
	public synchronized int getFirstBufferSize() {
		if (buffers == null) {
			return 0;
		}

		if (buffers.isEmpty()) {
			buffers = null;
			return 0;
		}

		ByteBuffer buffer = buffers.getFirst();
		return buffer.remaining();
	}
	

	
	/**
	 * append a byte buffer to this queue.
	 *
	 * @param data the ByteBuffer to append
	 */
	public synchronized void append(ByteBuffer data) {

		// is data available?
		if (data != null) {
			
			// really?
			if (data.hasRemaining()) {
				// yes
				currentSize = UNKNOWN; 
				insertVersion++;

				if (buffers == null) {
					buffers= new LinkedList<ByteBuffer>();
				}

				buffers.add(data);
			}
		}
	}


	/**
	 * append a list of byte buffer to this queue. By adding a list,
	 * the list becomes part of to the buffer, and shouldn't be modified outside the buffer
	 * to avoid side effects
	 *
	 * @param bufs  the list of ByteBuffer
	 */
	public synchronized void append(LinkedList<ByteBuffer> bufs) {

		//is data available?
		if (bufs != null) {
			
			// really?
			if (bufs.size() > 0) {

				// yes
				currentSize = UNKNOWN;
				insertVersion++;
				
				if (buffers == null) {
					buffers = bufs;
					
				}  else {
					buffers.addAll(bufs);
				}
			}
		}
	}


	/**
	 * add a byte buffer at the first position
	 *
	 * @param buffer the ByteBuffer to add
	 */
	public synchronized void addFirst(ByteBuffer buffer) {

		// is data available?
		if (buffer != null) {
			
			// really?
			if (buffer.hasRemaining()) {
				
				// yes
				currentSize = UNKNOWN;
				insertVersion++;

				if (buffers == null) {
					buffers= new LinkedList<ByteBuffer>();
				}

				buffers.addFirst(buffer);
			}
		}
	}

	
	

	/**
	 * add a list of byte buffer to the first position. By adding a list,
	 * the list becomes part of to the buffer, and shouldn't be modified outside the buffer
	 * to avoid side effects.
	 *
	 *
	 * @param bufs the list of ByteBuffer to add
	 */
	public synchronized void addFirst(LinkedList<ByteBuffer> bufs) {

		// is data available?
		if (bufs != null) {
			
			// really?
			if (bufs.size() > 0) {
				
				// yes
				currentSize = UNKNOWN;
				insertVersion++;
	
				if (buffers == null) {
					buffers = bufs;
					
				} else {
					bufs.addAll(buffers);
					buffers = bufs;
				}
			}
		}
	}
	
	

	/**
	 * add a list of byte buffer to the first position. By adding a list,
	 * the list becomes part of to the buffer, and shouldn't be modified outside the buffer
	 * to avoid side effects.
	 * Buffers will be added silence, which means the insertVersion will not be modified
	 * 
	 *
	 *
	 * @param bufs the list of ByteBuffer to add
	 */
	public synchronized void addFirstSilence(LinkedList<ByteBuffer> bufs) {

		// is data available?
		if (bufs != null) {
			
			// really?
			if (bufs.size() > 0) {
				
				// yes
				currentSize = UNKNOWN;
	
				if (buffers == null) {
					buffers = bufs;
					
				} else {
					bufs.addAll(buffers);
					buffers = bufs;
				}
			}
		}
	}


	/**
	 * drain the queue
	 *
	 * @return the queue content
	 */
	public synchronized LinkedList<ByteBuffer> drain() {
		
		// is data available?  
		if (buffers != null) {
			
			// really?
			if (!buffers.isEmpty()) {
				
				// yes
				currentSize = UNKNOWN;
				
				LinkedList<ByteBuffer> result = buffers;
				buffers = null;
				
				return result;
				
			} else {
				// no ... just a empty list 
				buffers = null;
			}
		}

		// no...  return empty list 
		return new LinkedList<ByteBuffer>();
	}




	/**
	 * remove the first ByteBuffer
	 * @return the first ByteBuffer
	 */
	public synchronized ByteBuffer removeFirst() {

		// is data available?
		if (buffers == null) {
			return null;

		} else { 
	
			// really?
			if (buffers.isEmpty()) {
				buffers = null;
				return null;
			
			} else {
				// yes
				currentSize = UNKNOWN;
				return buffers.removeFirst();
			}
		}
	}

	

	/**
	 * return a int, which represent the insert version.
	 * this value will increase with each insert modification
	 *
	 * @return the modify version
	 */
	public int getInsertVersionVersion() {
		return insertVersion;
	}

	/**
	 * read bytes
	 *
	 * @param length  the length
	 * @return the read bytes
 	 * @throws BufferUnderflowException if the buffer's limit has been reached
	 */
	public synchronized ByteBuffer read(int length) throws BufferUnderflowException {

		// data available?
		if (buffers == null) {
			throw new BufferUnderflowException();
		}

		// enough bytes available ?
		if (!isSizeEqualsOrLargerThan(length)) {
			throw new BufferUnderflowException();
		}


		// get first buffer
		currentSize = UNKNOWN;
		ByteBuffer buffer = buffers.removeFirst();
		int remainingFirst = buffer.remaining();


		// remainig of first buffer  == required length
		if (remainingFirst == length) {
			return buffer;


		// remainig of first buffer > required length
		} else if(remainingFirst > length) {
			int savedLimit = buffer.limit();
			int savedPos = buffer.position();

			buffer.limit(buffer.position() + length);

			ByteBuffer resultBuf = buffer.slice();

			buffer.position(savedPos + length);
			buffer.limit(savedLimit);
			addFirst(buffer.slice());

			return resultBuf;


		// remainig of first buffer < required length
		} else {

			ByteBuffer result = ByteBuffer.allocate(length);
			int written = 0;

			while (true) {
				// read  the buffer
				while(buffer.hasRemaining()) {
					result.put(buffer.get());
					written++;
					if (written == length) {
						if (buffer.position() < buffer.limit()) {
							buffers.addFirst(buffer.slice());
						}
						result.clear();
						return result;
					}
				}

				buffer = buffers.poll();
			}
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
		StringBuilder sb = new StringBuilder();
		if (buffers != null) {
			ByteBuffer[] copy = new ByteBuffer[buffers.size()];
			try {
				for (int i = 0; i < copy.length; i++) {
					copy[i] = buffers.get(i).duplicate();
				}
				sb.append(DataConverter.toString(copy, "US-ASCII", Integer.MAX_VALUE));
			} catch (Exception ignore) { 
				sb.append(DataConverter.toHexString(copy, Integer.MAX_VALUE));
			}
		}

		return sb.toString();
	}
}
