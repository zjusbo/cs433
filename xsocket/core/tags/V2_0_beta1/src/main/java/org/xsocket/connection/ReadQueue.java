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

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.ClosedException;
import org.xsocket.DataConverter;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.ByteBufferUtil.Index;


/**
 *
 * 
 * @author grro
 */
final class ReadQueue implements Cloneable {
	
	private static final Logger LOG = Logger.getLogger(ReadQueue.class.getName());

	
	// queue
	private Queue queue = new Queue(); 
	

	// mark support
	private ByteBuffer[] readMarkBuffer = null;
	private boolean isReadMarked = false;
	private int readMarkVersion = -1;
	private int readMarkAppendVersion = -1;

	

	
	public void reset() {
		readMarkBuffer = null;
		isReadMarked = false;
		queue.reset();
	}

	
	/**
	 * returns true, if empty
	 *
	 * @return true, if empty
	 */
	public boolean isEmpty() {
		return queue.isEmpty();
	}

	
	/**
	 * return a int, which represent the modification version.
	 * this value will increase with each modification
	 *
	 * @return the modify version
	 */
	public int geVersion() {
		return queue.getVersion();
	}

	

	/**
	 * append a byte buffer array to this queue. By adding a list,
	 * the list becomes part of to the buffer, and should not be modified outside the buffer
	 * to avoid side effects
	 *
	 * @param bufs  the ByteBuffers
	 */
	public void append(ByteBuffer[] bufs) {
		
		//is data available?
		if (bufs != null) {
			
			// really?
			if (bufs.length > 0) {
				queue.append(bufs);
			}
		}
	}

	
	
	/**
	 * return the index of the delimiter or -1 if the delimiter has not been found 
	 * 
	 * @param delimiter         the delimiter
	 * @param maxReadSize       the max read size
	 * @return the position of the first delimiter byte
	 * @throws IOException in an exception occurs
	 * @throws MaxReadSizeExceededException if the max read size has been reached 
	 */
	public int retrieveIndexOf(byte[] delimiter, int maxReadSize) throws IOException, MaxReadSizeExceededException {
		return queue.retrieveIndexOf(delimiter, maxReadSize);
	}

	

	

	/**
	 * return the current size
	 *
	 * @return  the current size
	 */
	public int getSize() {
		return queue.getSize();
	}

	
	
	public ByteBuffer[] readAvailable() {
		ByteBuffer[] buffers = queue.drain();
		onExtracted(buffers);

		buffers = removeEmptyBuffers(buffers);
		return buffers;
	}

	
	public ByteBuffer[] readByteBufferByDelimiter(byte[] delimiter, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		ByteBuffer[] buffers = queue.readByteBufferByDelimiter(delimiter, maxLength);
		onExtracted(buffers, delimiter);
		
		return buffers;
	}


	
	public ByteBuffer[] readByteBufferByLength(int length) throws BufferUnderflowException {
		ByteBuffer[] buffers = queue.readByteBufferByLength(length);
		onExtracted(buffers);
		
		return buffers;
	}
	

	public ByteBuffer readSingleByteBuffer(int length) throws BufferUnderflowException {
		if (getSize() < length) {
			throw new BufferUnderflowException();
		}
		

		ByteBuffer buffer = queue.readSingleByteBuffer(length);
		onExtracted(buffer);
		
		return buffer;
	}

	
	
	private static ByteBuffer[] removeEmptyBuffers(ByteBuffer[] buffers) {
		if (buffers == null) {
			return buffers;
		}
		
		int countEmptyBuffers = 0;
		for (int i = 0; i < buffers.length; i++) {
			if (buffers[i] == null) {
				countEmptyBuffers++;
			}
		}
		
		if (countEmptyBuffers > 0) {
			if (countEmptyBuffers == buffers.length) {
				return new ByteBuffer[0];

			} else {
				ByteBuffer[] newBuffers = new ByteBuffer[buffers.length - countEmptyBuffers];
				int num = 0;
				for (int i = 0; i < buffers.length; i++) {
					if (buffers[i] != null) {
						newBuffers[num] = buffers[i];
						num++;
					}
				}
				return newBuffers;
			}
			
		} else {
			
			return buffers;
		}
	}
	
	
	
	public long transferTo(WritableByteChannel target) throws ClosedException ,IOException ,SocketTimeoutException {
		long written = 0;
		
		ByteBuffer[] buffers = readAvailable();
		onExtracted(buffers);
		
		buffers = removeEmptyBuffers(buffers);
		for (ByteBuffer buffer : buffers) {
			written += target.write(buffer);
		}
		
		return written;
	}

	
	

	public long transferTo(WritableByteChannel target, int maxLength) throws ClosedException ,IOException ,SocketTimeoutException {
		long written = 0;
		
		int length = maxLength;
		int available = getSize();
		if (available < length) {
			length = available;
		}

		ByteBuffer[] buffers = queue.readByteBufferByLength(length);
		onExtracted(buffers);
		
		buffers = removeEmptyBuffers(buffers);
		for (ByteBuffer buffer : buffers) {
			written += target.write(buffer);
		}
		
		return written;
	}
		
	
	

	public final void markReadPosition() {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("mark read position");
		}
		removeReadMark();

		isReadMarked = true;
		readMarkVersion = queue.getVersion();
		readMarkAppendVersion = queue.getAppendVersion();
	}

	

	
	public final boolean resetToReadMark() {
		if (isReadMarked) {
			if (readMarkBuffer != null) {
				queue.addFirst(readMarkBuffer);
				removeReadMark();
				
				// size unmodified (no data has been appended)? -> reset version
				queue.resetVersionIfAppendVersionMatch(readMarkVersion, readMarkAppendVersion);
				

			} else {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("reset read mark. nothing to return to read queue");
				}
			}
			return true;

			
		} else {
			return false;
		}
	}

	

	public final void removeReadMark() {
		isReadMarked = false;
		readMarkBuffer = null;
	}

	
	
	private void onExtracted(ByteBuffer[] buffers) {
		if (isReadMarked) {
			for (ByteBuffer buffer : buffers) {
				onExtracted(buffer);
			}
		}
	}


	private void onExtracted(ByteBuffer[] buffers, byte[] delimiter) {
		if (isReadMarked) {
			if (buffers != null) {
				for (ByteBuffer buffer : buffers) {
					onExtracted(buffer);
				}
			}
			
			onExtracted(ByteBuffer.wrap(delimiter));
		}
	}

	
	private void onExtracted(ByteBuffer buffer) {
		if (isReadMarked) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("add data (" + DataConverter.toFormatedBytesSize(buffer.remaining()) + ") to read mark buffer ");
			}
			
			if (readMarkBuffer == null) {
				readMarkBuffer = new ByteBuffer[1];
				readMarkBuffer[0] = buffer.duplicate();
				
			} else {
				ByteBuffer[] newReadMarkBuffer = new ByteBuffer[readMarkBuffer.length + 1];
				System.arraycopy(readMarkBuffer, 0, newReadMarkBuffer, 0, readMarkBuffer.length);
				newReadMarkBuffer[readMarkBuffer.length] = buffer.duplicate();
				readMarkBuffer = newReadMarkBuffer;
			}			
		}
	}
	
	
	@Override
	public String toString() {
		return queue.toString();
	}

	
	public String asString(String encoding) {
		return queue.asString(encoding);
	}
	

	@Override
	protected Object clone() throws CloneNotSupportedException {
		ReadQueue copy = (ReadQueue) super.clone();
		
		copy.queue = (Queue) this.queue.clone();
		
		if (this.readMarkBuffer != null) {
			copy.readMarkBuffer = new ByteBuffer[this.readMarkBuffer.length];
			for (int i = 0; i < readMarkBuffer.length; i++) {
				copy.readMarkBuffer[i] = this.readMarkBuffer[i].duplicate();
			}
		}
		
		return copy;
	}

	
	private static final class Queue implements Cloneable {
		
		private static final int THRESHOLD_COMPACT_BUFFER_COUNT_TOTAL = 20;
		private static final int THRESHOLD_COMPACT_BUFFER_COUNT_EMPTY = 10;
		
		
		// parser 
		private static final ByteBufferUtil BYTEBUFFER_UTIL = new ByteBufferUtil();

	
		// queue
		private ByteBuffer[] buffers = null;
		private Integer currentSize = null; 
		private int version = 0;
		private int appendVersion = 0;


		// cache support
		private ByteBufferUtil.Index cachedIndex = null;

		

		/**
		 * clean the queue
		 */
		public synchronized void reset() {
			buffers = null;
			currentSize = null;
			cachedIndex = null;			
		}
		


		/**
		 * return a int, which represent the  version.
		 * this value will increase with modification
		 *
		 * @return the modify version
		 */
		public synchronized int getVersion() {
			return version;
		}
		
		
		/**
		 * return a int, which represent the append version.
		 * this value will increase with append operations
		 *
		 * @return the modify version
		 */
		synchronized int getAppendVersion() {
			return appendVersion;
		}
		
		
		synchronized void resetVersionIfAppendVersionMatch(int version, int appendVer) {
			if (appendVer == appendVersion) {
				this.version = version;
			}
		}

		
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
			// performance optimization if size has been already calculated
			if (currentSize != null) {
				return currentSize;
			}
				
				
			if (buffers == null) {
				return 0;
					
			} else {
				int size = 0;
				for (int i = 0; i < buffers.length; i++) {
					if (buffers[i] != null) {
						size += buffers[i].remaining();
					}
				}
					
				currentSize = size;
				return size;
			}
		}

		



		/**
		 * append a byte buffer array to this queue. By adding a array,
		 * the array becomes part of to the buffer, and should not be modified outside the buffer
		 * to avoid side effects
		 *
		 * @param bufs  the ByteBuffers
		 */
		public synchronized void append(ByteBuffer[] bufs) {
			appendVersion++;
			version++;
			currentSize = null;

								
			if (buffers == null) {
				buffers = bufs;
									
			}  else {
				ByteBuffer[] newBuffers = new ByteBuffer[buffers.length + bufs.length];
				System.arraycopy(buffers, 0, newBuffers, 0, buffers.length);
				System.arraycopy(bufs, 0, newBuffers, buffers.length, bufs.length);
				buffers = newBuffers;
			}			
		}
		

		
		
	
		public synchronized void addFirst(ByteBuffer[] bufs) {
			version++;
			currentSize = null;
			cachedIndex = null;

			addFirstSilence(bufs);	
		}

		
		private void addFirstSilence(ByteBuffer[] bufs) {
			currentSize = null;
			
			if (buffers == null) {
				buffers = bufs;
							
			} else {
				ByteBuffer[] newBuffers = new ByteBuffer[buffers.length + bufs.length]; 
				System.arraycopy(bufs, 0, newBuffers, 0, bufs.length);
				System.arraycopy(buffers, 0, newBuffers, bufs.length, buffers.length);
				buffers = newBuffers;
			}			
		}


		
		
		/**
		 * drain the queue
		 * 
		 * @return the content
		 */
		public synchronized ByteBuffer[] drain() {
			currentSize = null;
			cachedIndex = null;

			ByteBuffer[] result = buffers;
			buffers = null;

			if (result != null) {
				version++;
			}
			return result;
		}
		
		
		
		
		/**
		 * read bytes
		 *
		 * @param length  the length
		 * @return the read bytes
	 	 * @throws BufferUnderflowException if the buffer`s limit has been reached
		 */
		public synchronized ByteBuffer readSingleByteBuffer(int length) throws BufferUnderflowException {
			
			// data available?
			if (buffers == null) {
				throw new BufferUnderflowException();
			}
		
			// enough bytes available ?
			if (!isSizeEqualsOrLargerThan(length)) {
				throw new BufferUnderflowException();
			}
		
			// length 0 requested?
			if (length == 0) {
				return ByteBuffer.allocate(0);
			}

			
			ByteBuffer result = null;
			int countEmptyBuffer = 0;
			
			bufLoop : for (int i = 0; i < buffers.length; i++) {
				if (buffers[i] == null) {
					countEmptyBuffer++;
					continue;
				}
					
				
				// length first buffer == required length
				if (buffers[i].remaining() == length) {
					result = buffers[i];
					buffers[i] = null;
					break bufLoop;
			
					
				// length first buffer > required length
				} else if(buffers[i].remaining() > length) {
					int savedLimit = buffers[i].limit();
					int savedPos = buffers[i].position();
			
					buffers[i].limit(buffers[i].position() + length);
			
					result = buffers[i].slice();
			
					buffers[i].position(savedPos + length);
					buffers[i].limit(savedLimit);
					buffers[i] = buffers[i].slice();
			
					break bufLoop;
			
			
				// length first buffer < required length
				} else {
					result = ByteBuffer.allocate(length);
					int written = 0;
			
					for (int j = i; j < buffers.length; j++) {
						if (buffers[j] == null) {
							countEmptyBuffer++;
							continue;
							
						} else {
							while(buffers[j].hasRemaining()) {
								result.put(buffers[j].get());
								written++;
									
								// all data written
								if (written == length) {
									if (buffers[j].position() < buffers[j].limit()) {
										buffers[j] = buffers[j].slice();
									} else {
										buffers[j] = null;			
									}
									result.clear();
										
									break bufLoop;
								}
							}
						}
			
						buffers[j] = null;
					}
				}
			}
			
			
			if (result == null) {
				throw new BufferUnderflowException();
				
			} else {
				if (countEmptyBuffer >= THRESHOLD_COMPACT_BUFFER_COUNT_EMPTY) {
					compact();
				}
				
				currentSize = null;
				cachedIndex = null;

				version++;
				return result;
			}
		}

		
		
		
		public ByteBuffer[] readByteBufferByLength(int length) throws BufferUnderflowException {

			if (length == 0) {
				return new ByteBuffer[0];
			}
	 
			return extract(length, 0);
		}
		
		
		
		public ByteBuffer[] readByteBufferByDelimiter(byte[] delimiter, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			
			synchronized (this) {
				if (buffers == null) {
					throw new BufferUnderflowException();
				}
			}
			

			int index = retrieveIndexOf(delimiter, maxLength);
				
			// delimiter found?
			if (index >= 0) {
				return extract(index, delimiter.length);

			} else { 
				throw new BufferUnderflowException();
			}
		}


		

		private synchronized ByteBuffer[] extract(int length, int countTailingBytesToRemove) throws BufferUnderflowException {
			ByteBuffer[] extracted = null;
			
			if ((buffers == null) || (getSize() < length)) {
				throw new BufferUnderflowException();
			}
			
			if (length > 0) {
				extracted = BYTEBUFFER_UTIL.extract(buffers, length);
			} 


			if (countTailingBytesToRemove > 0) {
				BYTEBUFFER_UTIL.extract(buffers, countTailingBytesToRemove); // remove tailing bytes
			}
			
			compact();
			
			currentSize = null;
			cachedIndex = null;

			version++;
			return extracted;
		}
		
		
		
		
		
		

		
		/**
		 * return the index of the delimiter or -1 if the delimiter has not been found 
		 * 
		 * @param delimiter         the delimiter
		 * @param maxReadSize       the max read size
		 * @return the position of the first delimiter byte
		 * @throws IOException in an exception occurs
		 * @throws MaxReadSizeExceededException if the max read size has been reached 
		 */
		public int retrieveIndexOf(byte[] delimiter, int maxReadSize) throws IOException, MaxReadSizeExceededException {

			
			ByteBuffer[] bufs = null;
			
			// get the current buffers
			synchronized (this) {
				if (buffers == null) {
					return -1;
				}
					
				bufs = buffers;
				buffers = null;
			}

			
			// .. scan it
			int index = retrieveIndexOf(delimiter, bufs, maxReadSize);
			
			
			// .. and return the buffers
			synchronized (this) {
				addFirstSilence(bufs);
			}
				
			if (index == -2) {
				throw new MaxReadSizeExceededException();
					
			} else {
				return index;
			}
		}

		
		private int retrieveIndexOf(byte[] delimiter, ByteBuffer[] buffers, int maxReadSize) {
			Integer length = null;

			// is data available?
			if (buffers == null) {
				return -1;
			}
				
			Index index = scanByDelimiter(buffers, delimiter);
		
			// index found?
			if (index.hasDelimiterFound()) {
		
				// ... within the max range?
				if (index.getReadBytes() <= maxReadSize) {
					length = index.getReadBytes() - delimiter.length;

				// .. no
				} else {
					length = null;
				}
		
			// .. no
			} else {
				length = null;
			}
		
			cachedIndex = index;


			// delimiter not found (length is not set)
			if (length == null) {

				// check if max read size has been reached
				if (index.getReadBytes() >= maxReadSize) {
					return -2;
				}
				
				return -1;

			// delimiter found -> return length
			} else {
				return length;
			}
		}
		
		


		private Index scanByDelimiter(ByteBuffer[] buffers, byte[] delimiter) {

			// does index already exists (-> former scan)
			if (cachedIndex != null) {

				// same delimiter?
				if (cachedIndex.isDelimiterEquals(delimiter)) {
					// delimiter already found?
					if (cachedIndex.hasDelimiterFound()) {
						return cachedIndex;
		
					// .. no
					} else {
						// cached index available -> use index to find
						return BYTEBUFFER_UTIL.find(buffers, cachedIndex);
					}
				}
			}
			

			// no cached index -> find by delimiter
			return BYTEBUFFER_UTIL.find(buffers, delimiter);
		}

	

		
			
		private boolean isSizeEqualsOrLargerThan(int requiredSize) {
			if (buffers == null) {
				return false;
			}

			int bufferSize = 0;
			for (int i = 0; i < buffers.length; i++) {
				if (buffers[i] != null) {
					bufferSize += buffers[i].remaining();
					if (bufferSize >= requiredSize) {
						return true;
					}
				}
			}

			return false;
		}

		  
				
		private void compact() {

			if (buffers != null) {
				if (buffers.length > THRESHOLD_COMPACT_BUFFER_COUNT_TOTAL) {
			
					// count empty buffers
					int emptyBuffers = 0;
					for (int i = 0; i < buffers.length; i++) {
						if (buffers[i] == null) {
							emptyBuffers++;
						}
					}
							
					// enough empty buffers found? create new compact array
					if (emptyBuffers > THRESHOLD_COMPACT_BUFFER_COUNT_EMPTY) {
						if (emptyBuffers == buffers.length) {
							buffers = null;
									
						} else {
							ByteBuffer[] newByteBuffer = new ByteBuffer[buffers.length - emptyBuffers];
							int num = 0;
							for (int i = 0; i < buffers.length; i++) {
								if (buffers[i] != null) {
									newByteBuffer[num] = buffers[i];
									num++;
								}
							}
									
							buffers = newByteBuffer;
						}
					}
				}
			}
		}
		
		
		
		@Override
		protected synchronized Object clone() throws CloneNotSupportedException {
			Queue copy = (Queue) super.clone();
			

			if (this.buffers != null) {
				ByteBuffer[] bufs = new ByteBuffer[this.buffers.length];
				for (int i = 0; i < buffers.length; i++) {
					copy.buffers[i] = this.buffers[i].duplicate();
				}
				
				copy.buffers = bufs; 
			}
			
			copy.version = this.version;
			
			if (this.cachedIndex != null) {
				copy.cachedIndex = (ByteBufferUtil.Index) this.cachedIndex.clone();
			}
			
			return copy;
		}
		
		
		
  		@Override
   		public String toString() {
			try {
				ByteBuffer[] copy = buffers.clone();
				for (int i = 0; i < copy.length; i++) {
					if (copy[i] != null) {
						copy[i] = copy[i].duplicate();
					}
				}
				return DataConverter.toTextAndHexString(copy, "UTF-8", 300);
				
			} catch (NullPointerException npe) {
   				return "";
   				
   			} catch (Exception e) {
   				return e.toString();
   			}
   		}
  		 
  	 
  		synchronized String asString(String encoding) {
			try {
				ByteBuffer[] copy = buffers.clone();
				for (int i = 0; i < copy.length; i++) {
					if (copy[i] != null) {
						copy[i] = copy[i].duplicate();
					}
				}
				return DataConverter.toString(copy, encoding);
				
			} catch (NullPointerException npe) {
   				return "";
   				
   			} catch (Exception e) {
   				return e.toString();
   			}
   		}
	}
}