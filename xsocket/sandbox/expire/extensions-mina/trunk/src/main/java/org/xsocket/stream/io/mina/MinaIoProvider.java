// $Id: MemoryManager.java 1304 2007-06-02 13:26:34Z grro $
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
package org.xsocket.stream.io.mina;




import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoSession;
import org.apache.mina.transport.socket.nio.SocketConnector;
import org.xsocket.stream.io.spi.IAcceptor;
import org.xsocket.stream.io.spi.IClientIoProvider;
import org.xsocket.stream.io.spi.IIoHandler;
import org.xsocket.stream.io.spi.IServerIoProvider;
import org.xsocket.stream.io.spi.IAcceptorCallback;
import org.xsocket.stream.io.spi.IIoHandlerContext;




/**
*
*  
* @author grro@xsocket.org
*/
public final class MinaIoProvider implements IServerIoProvider, IClientIoProvider {
	
	private static String implementationVersion = null; 

	
	private static final Executor DEFAULT_WORKER_POOL = Executors.newCachedThreadPool();
	
	private static final AtomicInteger NEXT_ID = new AtomicInteger();
	
	
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public String getImplementationVersion() {
		if (implementationVersion == null) { 
			try {
				LineNumberReader lnr = new LineNumberReader(new InputStreamReader(this.getClass().getResourceAsStream("/org/xsocket/stream/io/mina/version.txt")));
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
	
	
	/**
	 * {@inheritDoc}
	 */
	public IIoHandler createClientIoHandler(IIoHandlerContext ctx, InetSocketAddress remoteAddress, Map<String, Object> options) throws IOException {
		IoConnector connector = new SocketConnector(1, DEFAULT_WORKER_POOL);
		
		ClientConnectHandler connectHdl = new ClientConnectHandler();
		connector.setHandler(new MinaIoHandler(connectHdl));
		connector.connect(remoteAddress);

		return connectHdl.getXSocketHandler();
	}

	
	
	/**
	 * {@inheritDoc}
	 */
	public IIoHandler setWriteTransferRate(IIoHandler ioHandler, int bytesPerSecond) throws IOException {
		IoHandler xsocketIoHandler = (IoHandler) ioHandler;
		xsocketIoHandler.setWriteRateSec(bytesPerSecond);		
		return xsocketIoHandler;
	}
	
	
	private static final class ClientConnectHandler implements IMinaIoHandlerCallback {
		
		private IoHandler xSocketHandler = null;

		
		public void onConnect(IoSession session) {
			synchronized (this) {
				xSocketHandler = new IoHandler(session, session.toString() + ".c." + NEXT_ID.incrementAndGet());
				this.notify();
			}
			session.setAttachment(xSocketHandler);
		}
		
		public IoHandler getXSocketHandler() {
			
			do {
				synchronized (this) {
					if (xSocketHandler == null) {
						try {
							this.wait();
						} catch (InterruptedException ignore) { }
					}
				}
			} while (xSocketHandler == null);
			
			return xSocketHandler;
		}
	}
}
