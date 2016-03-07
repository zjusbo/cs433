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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.util.TextUtils;



/**
 * Implementation of the <code>IBlockingConnection</code> interface 
 * 
 * @author grro@xsocket.org
 */
public final class BlockingConnection extends AbstractConnection implements IBlockingConnection {

	private static final Logger LOG = Logger.getLogger(BlockingConnection.class.getName());

	public static final int DEFAULT_PREALLOCATION_SIZE = 1024;
		
	
	
	// memory management
	private int preallocationSize = DEFAULT_PREALLOCATION_SIZE;
	private ByteBuffer preallocatedBuffer = null;
	

	/**
	 * constructor
	 *  
	 * @param channel  the underlying socket channel
	 * @throws IOException if the channel can not be configured ain a non-blocking mode
	 */
	public BlockingConnection(SocketChannel channel) throws IOException {
		this(channel, DEFAULT_PREALLOCATION_SIZE);
	}
	
	
	/**
	 * Constructor
	 * 
	 * @param channel  the underlying socket channel
	 * @param receivebufferPreallocationSize  the preallocationsize of the receive buffer 
	 * @throws IOException if the channel can not be configured ain a non-blocking mode
	 */	
	public BlockingConnection(SocketChannel channel, int receivebufferPreallocationSize) throws IOException {	
		super(channel);

		this.preallocationSize = receivebufferPreallocationSize;
		channel.configureBlocking(true);
	}


	/**
	 * @see IBlockingConnection
	 */
	public ByteBuffer[] receiveRecord(String delimiter) throws IOException {
		
		readPhysical();
		
		ByteBuffer[] buf = null;
		do {
			buf = readRecordFromReceiveReceiveQueue(delimiter);
		} while (buf == null);
		return buf;
	}
	
	
	/**
	 * @see IBlockingConnection
	 */	
	public String receiveWord(String delimiter, String encoding) throws IOException {

		readPhysical(); 
		
		String word = null;
		do {
			word = readWordFromReceiveQueue(delimiter, encoding);
		} while (word == null);
		return word;
	}

	
	/**
	 * @see IBlockingConnection
	 */
	public String receiveWord(String delimiter) throws IOException {
		return receiveWord(delimiter, getDefaultEncoding());
	}
	
	/**
	 * @see IConnection
	 */
	public long write(ByteBuffer[] buffers) throws ClosedConnectionException, IOException {
		long written = 0;
		for (ByteBuffer buffer : buffers) {
			written += buffer.limit() - buffer.position();
			writePhysical(buffers);
		}
		return written;
	}
	

	
	/**
	 * @see AbstractConnection
	 */
	@Override
	protected ByteBuffer acquireMemory() {
		if (preallocatedBuffer == null) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("allocate new physical memory (new size: " + TextUtils.printFormatedBytesSize(preallocationSize) + ")");
			}
			preallocatedBuffer = ByteBuffer.allocate(preallocationSize);
		} 
		
		ByteBuffer result = preallocatedBuffer;
		preallocatedBuffer = null;
		return result;
	}
	
	

	/**
	 * @see AbstractConnection
	 */
	@Override
	protected void recycleMemory(ByteBuffer buffer) {
		if (LOG.isLoggable(Level.FINEST)) {
			LOG.finest("free buffer " + buffer + " has been put back");
		}
		this.preallocatedBuffer = buffer;
	}	
}
