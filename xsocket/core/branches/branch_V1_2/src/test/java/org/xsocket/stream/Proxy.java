// $Id: Proxy.java 1630 2007-08-02 11:37:20Z grro $
/*
 *  Copyright (c) xsocket.org, 2006-2007. All rights reserved.
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
package org.xsocket.stream;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.Resource;
import org.xsocket.stream.IConnection.FlushMode;



/**
*
* @author grro@xsocket.org
*/
public final class Proxy implements Closeable {
	private IMultithreadedServer server = null;

	Proxy(int listenPort, String forwardHost, int forwardPort) throws IOException {
		server = new MultithreadedServer(listenPort, new ClientToProxyHandler(InetAddress.getByName(forwardHost), forwardPort));
		StreamUtils.start(server);
	}


	public static void main(String... args) throws Exception {
		if (args.length != 3) {
			System.out.println("usage org.xsocket.stream.Proxy <listenport> <forwardhost> <forwardport>");
			System.exit(-1);
		}

		new Proxy(Integer.parseInt(args[0]), args[1], Integer.parseInt(args[2]));
	}


	public void close() throws IOException {
		if (server != null) {
			server.close();
		}
	}


	private static class ProxyHandler implements IDataHandler, IDisconnectHandler {


		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			INonBlockingConnection reverseConnection = (INonBlockingConnection) connection.getAttachment();
			if (reverseConnection != null) {
				connection.setAttachment(null);
				reverseConnection.close();
			}
			return true;
		}

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			INonBlockingConnection forwardConnection = (INonBlockingConnection) connection.getAttachment();

			ByteBuffer[] data = connection.readAvailable();
			forwardConnection.write(data);

			return true;
		}
	}




	private static final class ClientToProxyHandler extends ProxyHandler implements IConnectHandler {

		private InetAddress forwardHost = null;
		private int forwardPort = 0;


		@Resource
		private IServerContext ctx = null;

		public ClientToProxyHandler(InetAddress forwardHost, int forwardPort) {
			this.forwardHost = forwardHost;
			this.forwardPort = forwardPort;
		}


		public boolean onConnect(INonBlockingConnection clientToProxyConnection) throws IOException {
			clientToProxyConnection.setFlushmode(FlushMode.ASYNC);  // set flush mode async for performance reasons

			Executor workerPool = ctx.getWorkerpool();   // performance optimization -> using server workerpool for client connection, too
			INonBlockingConnection proxyToServerConnection = new NonBlockingConnection(forwardHost, forwardPort, new ProxyHandler(), workerPool);
			proxyToServerConnection.setFlushmode(FlushMode.ASYNC); 
			proxyToServerConnection.setAttachment(clientToProxyConnection);

			clientToProxyConnection.setAttachment(proxyToServerConnection);
			return true;
		}
	}
}
