// $Id$
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

package org.xsocket;

import java.nio.ByteBuffer;


/**
 * Util class for <code>ByteBuffer</code> handling
 * 
 * @author grro@xsocket.org
 */
final class ByteBufferUtils {
	
	private ByteBufferUtils() { }

	
	/**
	 * returns a index, which gives the position of a record by using the delimiter
	 * 
	 * @param bufferQueue the queue
	 * @param delimiter the delimiter
	 * 
	 * @return the index 
	 */
	public static Index find(ByteBufferQueue bufferQueue, byte[] delimiter) {
		Index index = new Index();
		
		int delimiterPos = 0;
		int recSize = 0;
		int bufferIndex = 0;


		for (ByteBuffer buffer : bufferQueue) {

			int savedPos = buffer.position();
			int savedLimit = buffer.limit();
			
			try {
				// look for terminator in buffer
				for (int i = 0; buffer.hasRemaining(); i++) {
			
					//get next byte 
					recSize++;
					byte b = buffer.get();
					
		
					// intermediate delimiter byte check
					if (delimiterPos > 0) {
		
						if (b == delimiter[delimiterPos]) {
			
							// is last byte of delimiter?							
							if ((delimiterPos + 1) == delimiter.length) {
								index.setDelimiterEndsBufferNum(bufferIndex);
								index.setDelimiterEndsBufferPos(i);
								
								break;
								
							// intermediate position
							} 
							
							// inc position 
							delimiterPos++;
									
						// no delimiter byte found -> reset counter				
						} else {
							delimiterPos = 0;
						}			
					}
					
					
					// first delimiter byte check
					if (delimiterPos == 0) {
						if (b == delimiter[delimiterPos]) { 
							index.setDelimiterStartsBufferNum(bufferIndex);
							index.setDelimiterStartsBufferPos(i);
		
							// inc position 
							delimiterPos++;
						}				
					}   
				}
			} finally {	
				buffer.position(savedPos);
				buffer.limit(savedLimit);
	
				// inc buffer number
				bufferIndex++;					
		    }
		}
		
		if (index.getDelimiterEndsBufferPos() != -1) {
			return index;
		} else {
			return null;
		}
	}
	
	

	/**
	 * reads the record from the queue by using the index
	 * 
	 * @param bufferQueue the queue
	 * @param index the index 
	 * @return the record
	 */
	public static ByteBuffer[] extract(ByteBufferQueue bufferQueue, Index index) {
		ByteBuffer[] result = null;
		
		for (int bufferNumber = 0; bufferNumber <= index.getDelimiterEndsBufferNum(); bufferNumber++) {
			ByteBuffer buffer = bufferQueue.poll();
			
			// current buffer is smaller than buffer with delimiter start byte
    		if (bufferNumber < index.getDelimiterStartsBufferNum()) {
    			result = addToArray(result, buffer);
       		} 
   
    		
    		// the delimiter starts in the current buffer
    		if (bufferNumber == index.getDelimiterStartsBufferNum()) {
    			int savedLimit = buffer.limit();

    			buffer.position(0).limit(index.getDelimiterStartsBufferPos());
    			ByteBuffer leftPart = buffer.slice();
    			if (leftPart.limit() > 0) {
    				result = addToArray(result, leftPart);
    			}
    			buffer.limit(savedLimit);    			
    		}

    		
    		// the delimiter ends in the current buffer
    		if (bufferNumber == index.getDelimiterEndsBufferNum()) {
    			if ((index.getDelimiterEndsBufferPos() + 1) < buffer.limit()) {
	    			buffer.position(index.getDelimiterEndsBufferPos() + 1);
	    			ByteBuffer rightPart = buffer.slice();
	    			if (rightPart.limit() > 0) {
	    				bufferQueue.offerHead(rightPart);
	    			}
    			}
    		}
		}
		
		return result;
	}
	
	
	private static ByteBuffer[] addToArray(ByteBuffer[] array, ByteBuffer newElement) {		
		if (array == null) {
			return new ByteBuffer[] { newElement };
		}
		
		ByteBuffer[] newArray = new ByteBuffer[array.length + 1];
		System.arraycopy(array, 0, newArray, 0, array.length);
		newArray[array.length] = newElement;
		
		return newArray;
	}
}
