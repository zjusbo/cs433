// $Id: ByteBufferParser.java 764 2007-01-15 06:26:17Z grro $
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

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.LinkedList;


/**
 * Helper class to parse a ByteBuffer queue for a delimiter and 
 * to extract data based on a length field or the delimiter 
 * 
 * @author grro@xsocket.org
 */
final class ByteBufferParser {
	
	
	private static final WritableByteChannel DEV0 = new WritableByteChannel() {
		public boolean isOpen() {
			return true;
		};
		
		public int write(ByteBuffer buffer) throws IOException {  
			return buffer.remaining();
		};
		
		public void close() throws IOException {  };
	};
	 
	
	
	/**
	 * returns a index, which gives the position 
	 * of a record by using the delimiter
	 * 
	 * @param bufferQueue the queue
	 * @param delimiter the delimiter
	 * 
	 * @return the index 
	 */
	public Index find(LinkedList<ByteBuffer> bufferQueue, String delimiter) {
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
	public Index find(LinkedList<ByteBuffer> bufferQueue, Index index) {
		
		int queueSize = bufferQueue.size();
		
		// iterate all buffers (beginning with new since last scanned for index)
		for (int bufNr = index.scannedBuffers; (bufNr <queueSize) && (!index.hasDelimiterFound); bufNr++) {
	
			// retieve buffer
			ByteBuffer buffer = bufferQueue.get(bufNr); 

			// save current buffer positions
			int savedPos = buffer.position();
			int savedLimit = buffer.limit();
		
				
			// iterator over buffer content
			bufferLoop: for (int pos = 0; (buffer.hasRemaining() && !index.hasDelimiterFound); pos++) {
					
				byte b = buffer.get();
				index.readBytes++;

				// intermediate delimiter byte check
				if (index.delimiterPos > 0) {
					if (b == index.delimiterBytes[index.delimiterPos]) {

						// is last byte of delimiter?							
						if ((index.delimiterPos + 1) == index.delimiterLength) {
							index.hasDelimiterFound = true;
							break bufferLoop;
						} 
							
						// inc position 
						index.delimiterPos++;
							
							
					// no delimiter byte found -> reset counter				
					} else {
						index.delimiterPos = 0;
					}	
						
						
				// first delimiter byte check						
				} else if (index.delimiterPos == 0) {
					if (b == index.delimiterBytes[index.delimiterPos]) { 
						// inc position 
						index.delimiterPos++;
						
						// is single byte delimiter?							
						if (index.delimiterLength == 1) {
							index.hasDelimiterFound = true;
							break bufferLoop;
						} 
					}				
				}   
			} // end buffer loop
				
			index.scannedBuffers++;
			
			
			// restore buffer positions
			buffer.position(savedPos);
			buffer.limit(savedLimit);				
		}
		
		return index;
	}
	
	
	
	/**
	 * extracts the record from the given buffer by using a length field 
	 * 
	 * @param inOutBuffer  the buffer, which contains the data. The extracted data will be removed 
	 * @param length        the length to read
	 * @param outChannel    the channel to write the available bytes 
 	 * @throws BufferUnderflowException if the delimiter has not been found 
	 * @throws IOException If some other I/O error occurs 
	 */
	public void extract(LinkedList<ByteBuffer> inOutBuffer, int length, WritableByteChannel outChannel) throws IOException, BufferUnderflowException {
		
		int remainingToExtract = length;
		ByteBuffer buffer = null;
		
		do {
			// get the next buffer
			buffer = inOutBuffer.remove();
			if (buffer == null) {
				throw new BufferUnderflowException();
			}
			
			// can complete buffer be taken? 
			int bufLength = buffer.limit() - buffer.position();
			if (remainingToExtract >= bufLength) {
				// write taken into out channel
				outChannel.write(buffer);
				remainingToExtract -= bufLength;
				
			// .. no
			} else {
				int savedLimit = buffer.limit();
				
				// extract the takenable
				buffer.limit(buffer.position() + remainingToExtract);
				ByteBuffer leftPart = buffer.slice();
				outChannel.write(leftPart);				
				buffer.position(buffer.limit());
				buffer.limit(savedLimit);
				ByteBuffer rightPart = buffer.slice();
				inOutBuffer.addFirst(rightPart);
				return;
			}
			
		} while (remainingToExtract > 0);			
	}
	
	

	/**
	 * extracts the record from the given buffer by using the index
	 * 
	 * @param inOutBuffer   the buffer, which contains the data. The extracted data will be removed 
	 * @param index         the index
	 * @param outChannel    the channel to write the available bytes 
	 * @throws IOException If some other I/O error occurs
 
	 */
	public void extract(LinkedList<ByteBuffer> inOutBuffer, Index index, WritableByteChannel outChannel) throws IOException {
		assert (index.isValid) : "Index is invalid";
		assert (index.hasDelimiterFound());
		
		// extract content data based on delimiter position 
		extract(inOutBuffer, index.getReadBytes() - index.getDelimiterLength(), outChannel);

		// extract delimiter into dev0
		extract(inOutBuffer, index.getDelimiterLength(), ByteBufferParser.DEV0);
	}
	
	
	 

	/**
	 * the index to mark the position of the delimiter 
	 *
	 */	
	final static class Index {
		public static final int NULL = -1; 
		
		// flags
		private boolean isValid = true;
		private boolean hasDelimiterFound = false;
		

		// delimiter
		private String delimiter = null;
		private byte[] delimiterBytes = null;
		private int delimiterLength = 0;
		private int delimiterPos = 0;

		
		// positions
		private int scannedBuffers = 0;
		private int readBytes = 0;
		

		
		Index(String delimiter) {
			this.delimiter = delimiter;
			delimiterBytes = delimiter.getBytes();
			this.delimiterLength =  delimiterBytes.length;
		}
		
		boolean hasDelimiterFound() {
			return hasDelimiterFound;
		}
		
		int getReadBytes() {
			return readBytes;
		}
		
		String getDelimiter() {
			return delimiter;
		}
		
		int getDelimiterLength() {
			return delimiterLength;
		}
		
		int getDelimiterPos() {
			return delimiterPos;
		}
	}	
}