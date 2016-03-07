// $Id: SSLClientProcessor.java 438 2006-12-05 17:12:12Z grro $
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

import javax.net.ssl.SSLContext;



/**
 * SSLProcessor of the client side
 * 
 * @author grro@xsocket.org
 */
final class SSLClientProcessor extends SSLProcessor {
	
	
	/**
	 * constructor 
	 * 
	 * @param connection  the underlying connection
	 * @param sslContext  the SSLContent to use
	 */
	SSLClientProcessor(Connection connection, SSLContext sslContext) {
		super(connection, true, sslContext);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	final void start() throws IOException {
		super.start();
		
		// write to initiate SSL handshake -> client_hello
		writeOutgoing(null);
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	int readIncoming() throws IOException, ClosedConnectionException {
		int readSize = read();
					
		if (needWrap()) {
			writeOutgoing(null);
			getConnection().flushOutgoing();
		}
		
		return readSize;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	void writeOutgoing(ByteBuffer[] buffers) throws ClosedConnectionException, IOException{
		if (buffers == null) {
			buffers = new ByteBuffer[] {ByteBuffer.allocateDirect(0)};
		}
			
		write(buffers);
		
		while (isHandshaking()) {
			if (needUnwrap()) {
				readIncoming();
			} else {
				try {
					Thread.sleep(50);
				} catch (InterruptedException ignore) { }
			}
		}
	}
}
