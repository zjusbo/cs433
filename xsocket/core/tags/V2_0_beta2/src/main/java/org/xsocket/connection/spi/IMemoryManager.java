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
package org.xsocket.connection.spi;

import java.nio.ByteBuffer;


/**
 * memory manager 
 * 
 * @author grro
 */
interface IMemoryManager {
	
	
	/**
	 * acquire ByteBuffer with free memory
	 *
	 * @param standardsize the standard size
	 * @return the ByteBuffer with free memory 
	 */
	public ByteBuffer acquireMemoryStandardSizeOrPreallocated(int standardsize);
	
	
	/**
	 * returns true, if memory manager runs in preallocation mode
	 * @return the preallocation mode
	 */
	public boolean isPreallocationMode();
	
	
	/**
	 * sets the preallocation mode 
	 * @param mode the mode
	 */
	public void setPreallocationMode(boolean mode);
	
	
	/**
	 * sets the minimum size of a preallocated buffer 
	 * @param minSize the min size of a preallocated buffer
	 */
	public void setPreallocatedMinBufferSize(Integer minSize);
	
	
	/**
	 * gets the minimum size of a preallocated buffer 
	 * @return the min size of a preallocated buffer
	 */
	public Integer getPreallocatedMinBufferSize();

	
	/**
	 * set the preallocation size 
	 * @param preallocationSize the preallocation size
	 */
	public void setPreallocationBufferSize(Integer preallocationSize);
	
	
	/**
	 * gets the preallocation size 
	 * @return the preallocation size
	 */
	public Integer gettPreallocationBufferSize();

	
	/**
	 * sets if direct buffer should be allocated
	 * @param isDirect  true, if buffer is direct
	 */
	public void setDirect(boolean isDirect);
	
	
	/**
	 * returns if direct buffer should be allocated
	 * @return true, if is direct
	 */
	public boolean isDirect();
	
	
	
	/**
	 * acquire ByteBuffer with free memory
	 *
	 * @param minSize minimal buffer size
	 * @return the ByteBuffer with free memory 
	 */
	public ByteBuffer acquireMemoryMinSize(int minSize);
	
	
	/**
	 * recycle a ByteBuffer.  
	 * 
	 * @param buffer  the ByteBuffer to recycle 
	 */
	public void recycleMemory(ByteBuffer buffer);

	

	/**
	 * extract the read data and recycle the remaining ByteBuffer (if remaining larger than min size) 
	 * 
	 * @param buffer  the ByteBuffer to recycle 
	 * @param read    the readSize
	 * @return the read data
	 */
	public ByteBuffer extractAndRecycleMemory(ByteBuffer buffer, int read);
	
	
	
	/**
	 * preallocate, if preallocated size is smaller the given minSize
	 */
	public void preallocate();

	

	/**
	 * get the current free preallocated buffer size 
	 * @return the current free preallocated buffer size
	 */
	public int getCurrentSizePreallocatedBuffer();
}
