// $Id: ManagementTest.java 1237 2007-05-13 16:55:37Z grro $
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
package org.xsocket.stream.io.grizzly;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.rmi.server.UID;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.xsocket.stream.io.spi.IAcceptor;
import org.xsocket.stream.io.spi.IAcceptorCallback;
import org.xsocket.stream.io.spi.IIoHandler;
import org.xsocket.stream.io.spi.IIoHandlerContext;
import org.xsocket.stream.io.spi.IServerIoProvider;



import com.sun.grizzly.Controller;
import com.sun.grizzly.TCPSelectorHandler;


/**
*
* @author grro@xsocket.org
*/
public final class GrizzlyIoProvider implements IServerIoProvider {


	private static String implementationVersion = null; 
	
	
	// id
	private static String idPrefix = null;
	private final AtomicInteger nextId = new AtomicInteger();

	private SynchronizedMemoryManager memoryManager = new SynchronizedMemoryManager(65536, true);
	
	static {
		
	   	// prepare id prefix 
		String base = null;
		try {
			base = InetAddress.getLocalHost().getCanonicalHostName();
		} catch (Exception e) {
			base = new UID().toString();
		}

		int random = 0;
		Random rand = new Random();
		do {
			random = rand.nextInt();
		} while (random < 0);
		idPrefix = Integer.toHexString(base.hashCode()) + "." + Long.toHexString(System.currentTimeMillis()) + "." + Integer.toHexString(random);		
	}
	
	
	public String getImplementationVersion() {
		if (implementationVersion == null) { 
			try {
				LineNumberReader lnr = new LineNumberReader(new InputStreamReader(this.getClass().getResourceAsStream("/org/xsocket/stream/io/grizzly/version.txt")));
				String line = null;
				do {
					line = lnr.readLine();
					if (line != null) {
						if (line.startsWith("Implementation-Version=")) {
							implementationVersion = line.substring("Implementation-Version=".length(), line.length()).trim();
							break;
						}
					}
				} while (line != null);
				
				lnr.close();
			} catch (Exception ignore) { }
		}
		
		return implementationVersion;
	}

	
	

	/**
	 * {@inheritDoc}
	 */
	public IAcceptor createAcceptor(IAcceptorCallback callback, IIoHandlerContext handlerContext, InetSocketAddress address, int backlog, Map<String, Object> options) throws IOException {
		return new Acceptor(callback, handlerContext, address, backlog, options);
	}
	

	

    IoHandler createIoHandler(IIoHandlerContext handlerContex, boolean isClient, Controller controller, TCPSelectorHandler selectorHandler, SocketChannel channel) throws IOException {
    	
    	String connectionId = null;
    	if (isClient) {
    		connectionId = idPrefix + ".c." + nextId.incrementAndGet();
    	} else {
    		connectionId = idPrefix + ".s." + nextId.incrementAndGet();
    	}

    	return new IoHandler(handlerContex, connectionId, selectorHandler, channel, memoryManager);
    }	
	
    
    
	/**
	 * {@inheritDoc}
	 */
	public IIoHandler setWriteTransferRate(IIoHandler ioHandler, int bytesPerSecond) throws IOException {
		throw new UnsupportedOperationException("setWriteTransferRate is not yet implemented");
	}
	
	
}
