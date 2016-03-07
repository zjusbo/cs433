/*
 *  Copyright (c) xsocket.org, 2006-2008. All rights reserved.
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


import org.xsocket.QAUtil;




/**
*
* @author grro@xsocket.org
*/
public final class DeleteMe {

	
	public static void main(String[] args) throws Exception {
		
		
		
		Stream stream = new Stream();
		
		ByteBuffer buffer1 = QAUtil.generateByteBuffer(1000);
		ByteBuffer buffer2 = QAUtil.generateByteBuffer(1000);
		ByteBuffer[] buffers = new ByteBuffer[2];
		buffers[0] = buffer1;
		buffers[1] = buffer2;
		
		
		while (true) {
			stream.append(buffers, 2000);
			int available = stream.available();
			stream.readByteBufferByLength(available);
		}
		
	}
	
	
	
	private static final class Stream extends AbstractNonBlockingStream {
		
		@Override
		protected boolean isDataWriteable() {
			return true;
		}
		
		@Override
		protected boolean isMoreInputDataExpected() {
			return true;
		}

		public boolean isOpen() {
			return true;
		}
		
		public void append(ByteBuffer[] buffers, int size) {
			super.appendDataToReadBuffer(buffers, size);
		}
	}
	
}
