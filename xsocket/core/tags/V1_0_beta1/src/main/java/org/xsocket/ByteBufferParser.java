// $Id: ByteBufferParser.java 41 2006-06-22 06:30:23Z grro $

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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.util.TextUtils;


/**
 * parser class for <code>ByteBuffer</code> handling
 * 
 * @author grro@xsocket.org
 */
final class ByteBufferParser {
	
	private static final Logger LOG = Logger.getLogger(ByteBufferParser.class.getName());

	
	/**
	 * returns a index, which gives the position of a record by using the delimiter
	 * 
	 * @param bufferQueue the queue
	 * @param delimiter the delimiter
	 * 
	 * @return the index 
	 */
	public Index find(LinkedList<ByteBuffer> bufferQueue, byte[] delimiter) {
		return find(bufferQueue, new Index(delimiter));
	}
	

	/**
	 * returns a index, which gives the position of a record by using the delimiter
	 * 
	 * @param bufferQueue the queue
	 * @param index index
	 * 
	 * @return the index 
	 */
	public Index find(LinkedList<ByteBuffer> bufferQueue, Index index) {
		
		queueLoop : for (int bufNr = index.numberOfScannedBuffers; bufNr < bufferQueue.size(); bufNr++) {
			ByteBuffer buffer = bufferQueue.get(bufNr); 
				
			if (buffer != null) {
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("pre index " + index.toString());
				}
				if (LOG.isLoggable(Level.FINEST)) {
					LOG.finest("check " + TextUtils.toByteString(buffer.duplicate()));
				}
	

				int savedPos = buffer.position();
				int savedLimit = buffer.limit();
		
				for (int pos = 0; buffer.hasRemaining(); pos++) {					
					boolean found = index.check(buffer, bufNr, pos);
					if (found) {
						buffer.position(savedPos);
						buffer.limit(savedLimit);

						break queueLoop;
					}
				}
						
				buffer.position(savedPos);
				buffer.limit(savedLimit);
			}
		} 
		
		
		if (LOG.isLoggable(Level.FINEST)) {
			LOG.finest("found=" + index.isDelimiterFound() + " post index " + index.toString());
		}

		return index;
	}
	
	

	/**
	 * reads the record from the queue by using the index
	 * 
	 * @param bufferQueue the queue
	 * @param index the index
	 * @param outChannel the channel to write the available bytes 
	 */
	public void extract(LinkedList<ByteBuffer> bufferQueue, Index index, WritableByteChannel outChannel) throws IOException {
		assert (index.isValid) : "Index is invalid";
		
		if (!index.isDelimiterFound()) {
			return;
		}
		

		////////////////////
		// get and remove previous buffers
		for (int i = 0; i < index.startBuffer; i++) {
			outChannel.write(bufferQueue.removeFirst());
		}


		///////////////////////
		// handle start buffer (and "higher")
		ByteBuffer startBuffer = bufferQueue.removeFirst();
		int savedLimit = startBuffer.limit();

		//get and remove content left from terminator 
		startBuffer.position(0).limit(index.startBufferPos);
		ByteBuffer leftPart = startBuffer.slice();
		if (leftPart.limit() > 0) {
			outChannel.write(leftPart);
		}
			
   		// whole terminator is in start buffer contained and no more content right from terminator exists  
		if ((index.startBufferPos + index.delimiterLength) == savedLimit) {
			// do nothing
			
		// whole terminator is in start buffer contained and contents right from terminator exists				
		} else if ((index.startBufferPos + index.delimiterLength) < savedLimit) {
   			startBuffer.limit(savedLimit);
   			startBuffer.position(index.startBufferPos + index.delimiter.length);
   			ByteBuffer rightPart = startBuffer.slice();
   			bufferQueue.addFirst(rightPart);
   			
   		// terminator expands over serveral buffers   				
		} else if ((index.startBufferPos + index.delimiterLength) > savedLimit) {
			int remainingTerminatorLength = index.delimiterLength - (savedLimit - index.startBufferPos);
		
			do {
				ByteBuffer buffer = bufferQueue.removeFirst();
				int bufferLength = buffer.limit() - buffer.position();
				if (remainingTerminatorLength >= bufferLength) {
					remainingTerminatorLength = remainingTerminatorLength - bufferLength;
				} else {
					buffer.position(remainingTerminatorLength);
					ByteBuffer rightPart = buffer.slice();
					bufferQueue.addFirst(rightPart);
					remainingTerminatorLength = 0;
				}
			} while (remainingTerminatorLength > 0);	
		}			
			
		index.isValid = false;
	}
	
	
	
	/**
	 * reads the available ByteBuffer by using the index
	 * 
	 * @param bufferQueue the queue
	 * @param index the index 
	 * @param outChannel the channel to write the available bytes
	 */
	public void extractAvailable(LinkedList<ByteBuffer> bufferQueue, Index index, WritableByteChannel outChannel) throws IOException {		
		assert (index.isValid) : "Index is invalid";
		
		
		if (index.found) {
			extract(bufferQueue, index, outChannel);
		
			
		} else {	
			// first check if already parts of the delimiter has been found
				
			// delimiter found -> return all ByteBuffer previous to the delimiter
			if (index.startBuffer != Index.NULL) {
					
				// get and remove previous buffers
				for (int i = 0; i < index.startBuffer; i++) {
					outChannel.write(bufferQueue.removeFirst());
				}
	
				ByteBuffer startBuffer = bufferQueue.removeFirst();
				int savedLimit = startBuffer.limit();
					
				startBuffer.position(0).limit(index.startBufferPos);
				ByteBuffer leftPart = startBuffer.slice();
				if (leftPart.limit() > 0) {
					outChannel.write(leftPart);
				}
	
				if (index.startBufferPos < savedLimit) {
	   				startBuffer.limit(savedLimit);
	   				startBuffer.position(index.startBufferPos);
	   				ByteBuffer rightPart = startBuffer.slice();
	   				bufferQueue.addFirst(rightPart);
				}
					
					
			// no delimiter parts found
			} else {
				while (!bufferQueue.isEmpty()) {
					outChannel.write(bufferQueue.removeFirst());
				}	
			}
			
			index.isValid = false;		
		}
	}
	
	
	
	
	
	final static class Index {
		
		public static final int NULL = -1; 
		
		private int startBuffer = NULL;
		private int startBufferPos = NULL;

		private boolean isValid = true;
		
		private boolean found = false;
		
		private byte[] delimiter = null;
		private int delimiterLength = 0;
		private int delimiterPos = 0;
		
		private int numberOfScannedBuffers = 0;

		
		Index(byte[] delimiter) {
			this.delimiter = delimiter;
			this.delimiterLength = delimiter.length;
		}
		
		
		private boolean check(ByteBuffer buffer, int bufferNumber,  int currentPosition) {
			numberOfScannedBuffers = bufferNumber + 1;
			
			byte b = buffer.get();

			// intermediate delimiter byte check
			if (delimiterPos > 0) {
				if (b == delimiter[delimiterPos]) {
	
					// is last byte of delimiter?							
					if ((delimiterPos + 1) == delimiterLength) {
						found = true;
						return true;
					} 
					
					// inc position 
					delimiterPos++;
							
				// no delimiter byte found -> reset counter				
				} else {
					delimiterPos = 0;
					startBuffer = NULL;
					startBufferPos = NULL;
				}			
			}
			
			
			// first delimiter byte check
			if (delimiterPos == 0) {
				if (b == delimiter[delimiterPos]) { 
					startBuffer = bufferNumber;
					startBufferPos = currentPosition;

					// inc position 
					delimiterPos++;
				}				
			}   

			
			return false;
		}
		
		public boolean isDelimiterFound() {
			return found;
		}
		
		public boolean isDataAvailable() {
			return !((startBuffer == 0) && (startBufferPos == 0)); 
		}
		
		
		
		@Override
		public String toString() {
			return "delimiter=" + TextUtils.toByteString(delimiter) + " (pos=" + delimiterPos + ")"
			       + " handledBuffers=" + numberOfScannedBuffers
			       + " delimiter Start (buf=" + startBuffer + ", pos=" + startBufferPos + ")"
			       + " found=" + found;
		}
	}
}
