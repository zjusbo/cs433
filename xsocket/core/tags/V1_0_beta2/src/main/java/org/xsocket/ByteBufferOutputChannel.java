// $Id: ByteBufferOutputChannel.java 438 2006-12-05 17:12:12Z grro $
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
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
 
 
/**
 * This class implements an writeable channel in which the data
 * is written into a ByteBuffer array.
 * The written data can be retrieved using toByteBufferArray()
 * 
 * 
 * @author grro@xsocket.org
 */
final class ByteBufferOutputChannel implements WritableByteChannel {

	private boolean isOpen = true;
	private ByteBuffer[] buffers = new ByteBuffer[0];

	/**
	 * @see java.nio.channels.Channel#isOpen()
	 */
	public boolean isOpen() {
		return isOpen;
	}


	/**
	 * {@inheritDoc}
	 */
	public void close() throws IOException {
		isOpen = false;
	}

	/**
	 * {@inheritDoc}
	 */
	public int write(ByteBuffer buffer) throws IOException {
		if ((buffer.limit() - buffer.position()) > 0) {
			buffers = incArray(buffers, buffer);
			return buffer.limit() - buffer.position();
		} else {
			return 0;
		}
	}

	/**
	 * returns the collected byted 
	 * 
	 * @return the collecte bytes
	 */
	public ByteBuffer[] toByteBufferArray() {
		// findbugs note: for performance reasons the internal representaion will be exposed
		return buffers;
	}

	
	private static ByteBuffer[] incArray(ByteBuffer[] original, ByteBuffer newElement) {
		ByteBuffer[] newArray =  (ByteBuffer[]) copyOf(original, original.length + 1, original.getClass());
		newArray[original.length] = newElement;
			return newArray;
	}


	/**
	 * @see Arrays (Java 1.6)
	 */
	@SuppressWarnings("unchecked")
	private static <T,U> T[] copyOf(U[] original, int newLength, Class<? extends T[]> newType) {
		T[] copy;
		if ((Object) newType == (Object) Object[].class) {
		copy = (T[]) new Object[newLength];
		} else {
			copy = (T[]) Array.newInstance(newType.getComponentType(), newLength);
		}
		System.arraycopy(original, 0, copy, 0, Math.min(original.length, newLength));
		return copy;
    }
}

	