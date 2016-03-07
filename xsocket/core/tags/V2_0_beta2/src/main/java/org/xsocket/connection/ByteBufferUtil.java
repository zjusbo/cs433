package org.xsocket.connection;

import java.nio.ByteBuffer;




/**
 *
 * 
 * @author grro
 */
final class ByteBufferUtil {
	
	
	/**
	 * returns a index, which gives the position
	 * of a record by using the delimiter
	 *
	 * @param bufferQueue the queue
	 * @param delimiter the delimiter
	 *
	 * @return the index
	 */
	public Index find(ByteBuffer[] bufferQueue, byte[] delimiter) {
		return find(bufferQueue, new Index(delimiter));
	}


	/**
	 * returns a index, which gives the position
	 * of a record by using the delimiter
	 *
	 * @param bufferQueue the queue
	 * @param index index the index
	 *
	 * @return the index
	 */
	public Index find(ByteBuffer[] buffers, Index index) {

		int i = findFirstBufferToScan(buffers, index);
		
		for (; (i < buffers.length) && !index.hasDelimiterFound; i++) {
		
			ByteBuffer buffer = buffers[i];
			if (buffer == null) {
				continue;
			} 

			// save current buffer positions
			int savedPos = buffer.position();
			int savedLimit = buffer.limit();
			
			findInBuffer(buffer, index);
			
			// restore buffer positions
			buffer.position(savedPos);
			buffer.limit(savedLimit);
		}

		return index;
	}


	private int findFirstBufferToScan(ByteBuffer[] buffers, Index index) {
		
		int i = 0;
		
		// jump to next buffer which follows the cached one   
		if (index.lastScannedBuffer != null) {
			
			// find the last scanned buffer 
			for (int j = 0; j < buffers.length; j++) {
				if (buffers[j] == index.lastScannedBuffer) {
					i = j;
					break;
				}
			}
			
			
			// position to next buffer
			i++;
			
			
			// are there more buffers?
			if (i >= buffers.length) {
				// ...no, do nothing
				return i;
			}
			
			
			// filter the empty buffers
			for (int k = i; k < buffers.length; k++) {
				if (buffers[k] != null) {
					i = k;
					break;
				}
			}
			
			
			// are there more buffers?
			if (i >= buffers.length) {
				// ...no, do nothing
				return i;
			}
		}
		return i;
	}

	
	
	private void findInBuffer(ByteBuffer buffer, Index index) {
		
		index.lastScannedBuffer = buffer;
		int dataSize = buffer.remaining();
		
		byte[] delimiter = index.delimiterBytes;
		int delimiterLength = index.delimiterLength;
		int delimiterPosition = index.delimiterPos;
		byte nextDelimiterByte = delimiter[delimiterPosition];
		boolean delimiterPartsFound = delimiterPosition > 0;
		
		
		for (int i = 0; i < dataSize; i++) {
			
			byte b = buffer.get();

			
			// is current byte a delimiter byte?
			if (b == nextDelimiterByte) {
				delimiterPosition++;
				
				// is single byte delimiter?
				if (delimiterLength  == 1) {
					index.hasDelimiterFound = true;
					index.delimiterPos = delimiterPosition;
					index.readBytes += (i + 1);
					return;
				
				// .. no, it is a multi byte delimiter
				} else {
					index.delimiterPos = delimiterPosition;
					
					// last delimiter byte found?
					if (delimiterPosition == delimiterLength) {
						index.hasDelimiterFound = true;
						index.readBytes += (i + 1);
						return;
					}
					
					nextDelimiterByte = delimiter[delimiterPosition];
				}
				
				delimiterPartsFound = true;

			// byte doesn't match
			} else {
				
				if (delimiterPartsFound) {
					delimiterPosition = 0;
					index.delimiterPos = 0;
					nextDelimiterByte = delimiter[delimiterPosition];
					delimiterPartsFound = false;
					
					
					// check if byte is equals to first delimiter byte 
					if (delimiterLength  > 1) {
						if (b == nextDelimiterByte) {
							delimiterPosition++;
							nextDelimiterByte = delimiter[delimiterPosition];
							index.delimiterPos = delimiterPosition;
						}
					}
				}
			}
		}
		
		index.readBytes += dataSize;
	}
	
	


	/**
	 * extracts the record from the given buffer by using a length field
	 *
	 * @param inOutBuffers   the buffer, which contains the data. The extracted data will be removed
	 * @param length         the length to read
	 * @return the extracted part or <code>null</code>
 	 */
	public ByteBuffer[] extract(ByteBuffer[] inOutBuffers, int length) {
		ByteBuffer[] extracted = null;

		int remainingToExtract = length;
		ByteBuffer buffer = null;

		for (int i = 0; i < inOutBuffers.length; i++) {
		
			// get the first buffer 
			buffer = inOutBuffers[i];
			if (buffer == null) {
				continue;
			}

			
			// can complete buffer be taken?
			int bufLength = buffer.limit() - buffer.position();
			if (remainingToExtract >= bufLength) {

				// write taken into out channel
				extracted = appendBuffer(extracted, buffer);
				remainingToExtract -= bufLength;
				inOutBuffers[i] = null;

			// .. no
			} else {
				int savedLimit = buffer.limit();

				// extract the takenable
				buffer.limit(buffer.position() + remainingToExtract);
				ByteBuffer leftPart = buffer.slice();
				extracted = appendBuffer(extracted, leftPart);
				buffer.position(buffer.limit());
				buffer.limit(savedLimit);
				ByteBuffer rightPart = buffer.slice();
				
				inOutBuffers[i] = rightPart;
				remainingToExtract = 0;
			}

			if (remainingToExtract == 0) {
				return extracted;
			}
		}

		return null;
	}

	
	/**
	 * append a buffer to a buffer array. The new, increased buffer array will be returned  
	 * @param buffers  the existing buffer array  
	 * @param buffer   the buffer to append 
	 * @return the new, increased buffer array
	 */
	public static ByteBuffer[] appendBuffer(ByteBuffer[] buffers, ByteBuffer buffer) {
		
		if (buffers == null) {
			ByteBuffer[] result = new ByteBuffer[1];
			result[0] = buffer;
			return result;
			
		} else {
			ByteBuffer[] result = new ByteBuffer[buffers.length + 1];
			System.arraycopy(buffers, 0, result, 0, buffers.length);
			result[buffers.length] = buffer;
			return result;
		}		
	}


	
	
	static final class Index implements Cloneable {
		public static final int NULL = -1;

		private boolean hasDelimiterFound = false;
		private byte[] delimiterBytes = null;
		private int delimiterLength = 0;
		private int delimiterPos = 0;

		
		// consumed bytes
		private int readBytes = 0;
				
		
		// cache support
		private ByteBuffer lastScannedBuffer = null;

				
		
		Index(byte[] delimiterBytes) {
			this.delimiterBytes = delimiterBytes;
			this.delimiterLength =  delimiterBytes.length;
		}
		

		public boolean hasDelimiterFound() {
			return hasDelimiterFound;
		}
	
		
		int getContentLength() {
			if (hasDelimiterFound) {
				return (readBytes - delimiterLength);
			} else {
				return -1;
			}
		}
		

		public int getReadBytes() {
			return readBytes;
		}

		
		public boolean isDelimiterEquals(byte[] other) {

			if (other.length != delimiterLength) {
				return false;
			}

			for (int i = 0; i < delimiterLength; i++) {
				if (other[i] != delimiterBytes[i]) {
					return false;
				}
			}

			return true;
		}

		
		@Override
		protected Object clone() throws CloneNotSupportedException {
			Index copy = (Index) super.clone();			
			return copy;
		}
	
		@Override
		public String toString() {
			return "found=" + hasDelimiterFound + " delimiterPos=" + delimiterPos 
			       + " delimiterLength="+ delimiterLength + " readBytes=" + readBytes;
		}
	}
}