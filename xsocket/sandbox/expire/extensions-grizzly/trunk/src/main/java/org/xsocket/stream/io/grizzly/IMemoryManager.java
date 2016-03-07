// $Id: IMemoryManager.java 1304 2007-06-02 13:26:34Z grro $
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
package org.xsocket.stream.io.grizzly;

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
	 * @return the ByteBuffer with free memory 
	 */
	public ByteBuffer acquireMemory(int minSize);
	
	
	/**
	 * recycle a ByteBuffer (if remaining larger than min size) 
	 * 
	 * @param buffer  the ByteBuffer to recycle 
	 * @param minSize the min preallocated size
	 */
	public void recycleMemory(ByteBuffer buffer, int minSize);

	
	/**
	 * preallocate, if preallocated size is smaller the given minSize
	 * 
	 * @param minSize the min preallocated size
	 */
	public void preallocate(int minSize);

	
	
	/**
	 * get the current free preallocated buffer size 
	 * @return the current free preallocated buffe size
	 */
	public int getFreeBufferSize();
}
