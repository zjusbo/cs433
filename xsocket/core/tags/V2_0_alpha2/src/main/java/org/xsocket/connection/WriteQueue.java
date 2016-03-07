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
import java.util.ArrayList;

import org.xsocket.DataConverter;



/**
 * the WriteQueue
 * 
 * @author grro
 */
final class WriteQueue implements Cloneable {

	// queue
	private ByteBuffer[] buffers = null;
	
	
	// mark support
	private RewriteableBuffer writeMarkBuffer = null;
	private boolean isWriteMarked = false;

	
	
	public void reset() {
		buffers = null;
		
		writeMarkBuffer = null;
		isWriteMarked = false;
	}
	
	
	
	/**
	 * returns true, if empty
	 *
	 * @return true, if empty
	 */
	public boolean isEmpty() {
		return ((buffers == null) && (writeMarkBuffer == null));
	}
	

	/**
	 * return the current size
	 *
	 * @return  the current size
	 */
	public int getSize() {
		
		if (isEmpty()) {
			return 0;
			
		} else {
			int size = 0;
			if (buffers != null) {
				for (int i = 0; i < buffers.length; i++) {
					if (buffers[i] != null) {
						size += buffers[i].remaining();
					}
				}
			}
				
			if (writeMarkBuffer != null) {
				size += writeMarkBuffer.size();
			}
					
			return size;
		}
	}
	
	

	/**
	 * drain the queue
	 *
	 * @return the queue content
	 */
	public ByteBuffer[] drain() {
		ByteBuffer[] result = buffers;
		buffers = null;
					
		return result;
	}

		
	
	/**
	 * append a byte buffer to this queue.
	 *
	 * @param data the ByteBuffer to append
	 */
	public void append(ByteBuffer data) {
		
		if (data == null) {
			return;
		}
				
		if (isWriteMarked) {
			writeMarkBuffer.append(data);

		} else { 
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
	}
	
	
	
	
	/**
	 * append a list of byte buffer to this queue. By adding a list,
	 * the list becomes part of to the buffer, and should not be modified outside the buffer
	 * to avoid side effects
	 *
	 * @param bufs  the list of ByteBuffer
	 */
	public void append(ByteBuffer[] bufs) {
		
		if (bufs == null) {
			return;
		}
		
		if (bufs.length < 1) {
			return;
		}
		

		if (isWriteMarked) {
			for (ByteBuffer buffer : bufs) {
				writeMarkBuffer.append(buffer);	
			}
			
		} else { 
			if (buffers == null) {
				buffers = bufs;
									
			}  else {
				ByteBuffer[] newBuffers = new ByteBuffer[buffers.length + bufs.length];
				System.arraycopy(buffers, 0, newBuffers, 0, buffers.length);
				System.arraycopy(bufs, 0, newBuffers, buffers.length, bufs.length);
				buffers = newBuffers;
			}
		}
	}
	
	


	
	/**
	 * mark the current write position  
	 */
	public final void markWritePosition() {
		removeWriteMark();

		isWriteMarked = true;
		writeMarkBuffer = new RewriteableBuffer();
	}



	/**
	 * remove write mark 
	 */
	public void removeWriteMark() {
		if (isWriteMarked) {
			isWriteMarked = false;
			
			append(writeMarkBuffer.drain());
			writeMarkBuffer = null;
		}
	}
	
	
	/**
	 * reset the write position the the saved mark
	 * 
	 * @return true, if the write position has been marked  
	 */
	public boolean resetToWriteMark() {
		if (isWriteMarked) {
			writeMarkBuffer.resetWritePosition();
			return true;

		} else {
			return false;
		}
	}

	
	
	@Override
	protected Object clone() throws CloneNotSupportedException {
		WriteQueue copy = (WriteQueue) super.clone();
		
		if (this.buffers != null) {
			copy.buffers = new ByteBuffer[this.buffers.length];
			for (int i = 0; i < buffers.length; i++) {
				copy.buffers[i] =this.buffers[i].duplicate();
			}
		}
		
		if (this.writeMarkBuffer != null) {
			copy.writeMarkBuffer = (RewriteableBuffer) this.writeMarkBuffer.clone();
		}
		
		return copy;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (buffers != null) {
			ByteBuffer[] copy = new ByteBuffer[buffers.length];
			try {
				for (int i = 0; i < copy.length; i++) {
					if (buffers[i] != null) {
						copy[i] = buffers[i].duplicate();
					}
				}
				sb.append(DataConverter.toString(copy, "US-ASCII", Integer.MAX_VALUE));
			} catch (Exception ignore) { 
				sb.append(DataConverter.toHexString(copy, Integer.MAX_VALUE));
			}
		}

		return sb.toString();
	}
	
	
	
	
	public String asString(String encoding) {
		StringBuilder sb = new StringBuilder();
		if (buffers != null) {
			ByteBuffer[] copy = new ByteBuffer[buffers.length];
			try {
				for (int i = 0; i < copy.length; i++) {
					if (buffers[i] != null) {
						copy[i] = buffers[i].duplicate();
					}
				}
				sb.append(DataConverter.toString(copy, encoding));
			} catch (Exception ex) { 
				sb.append("error occured by printing WriteQueue: " + ex.toString());
			}
		}

		return sb.toString();
	}
	
	private static final class RewriteableBuffer implements Cloneable {
		private ArrayList<ByteBuffer> bufs = new ArrayList<ByteBuffer>();
		private int writePosition = 0;
		
		
		public void append(ByteBuffer buffer) {
			
			if (buffer.remaining() < 1) {
				return;
			}
			
			if (writePosition == bufs.size()) {
				bufs.add(buffer);
				writePosition++;
				
			} else {
				ByteBuffer currentBuffer = bufs.remove(writePosition);
				
				if (currentBuffer.remaining() == buffer.remaining()) {
					bufs.add(writePosition, buffer);
					writePosition++;
					
				} else if (currentBuffer.remaining() > buffer.remaining()) {
					currentBuffer.position(currentBuffer.position() + buffer.remaining());
					bufs.add(writePosition, currentBuffer);
					bufs.add(writePosition, buffer);
					writePosition++;
					
				} else { // currentBuffer.remaining() < buffer.remaining()
					bufs.add(writePosition, buffer);
					writePosition++;
					
					int bytesToRemove = buffer.remaining() - currentBuffer.remaining();
					while (bytesToRemove > 0) {
						// does tailing buffers exits?
						if (writePosition < bufs.size()) {
							
							ByteBuffer buf = bufs.remove(writePosition);
							if (buf.remaining() > bytesToRemove) {
								buf.position(buf.position() + bytesToRemove);
								bufs.add(writePosition, buf);
							} else {
								bytesToRemove -= buf.remaining();
							}
							
						// ...no
						} else {
							bytesToRemove = 0;
						}
					}
				}
				
			}	
		}
	
		public void resetWritePosition() {
			writePosition = 0;
		}
		
	
		public ByteBuffer[] drain() {
			ByteBuffer[] result = bufs.toArray(new ByteBuffer[bufs.size()]);
			bufs.clear();
			writePosition = 0;
			
			return result;
		}
		
		
		public int size() {
			int size = 0;
			for (ByteBuffer buffer : bufs) {
				size += buffer.remaining();
			}
			
			return size;
		}
		
		@Override
		protected Object clone() throws CloneNotSupportedException {
			RewriteableBuffer copy = (RewriteableBuffer) super.clone();
			
			copy.bufs = new ArrayList<ByteBuffer>();
			for (ByteBuffer buffer : this.bufs) {
				copy.bufs.add(buffer.duplicate());
			}

			return copy;
		}
	}
}