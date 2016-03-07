// $Id: MarkAndResetTest.java 1630 2007-08-02 11:37:20Z grro $
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
package org.xsocket.stream;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.stream.IConnection.FlushMode;



/**
*
* @author grro@xsocket.org
*/
public final class EchoServer implements Closeable {
	private IServer server = null;

	EchoServer(int listenPort) throws IOException {

		IHandler hdl = new EchoHandler();


		////////////////////////
		// uncomment following code for using the first visit throttling filter
		// FirstVisitThrottlingFilter firstVisitFilter = new FirstVisitThrottlingFilter(5);
		// HandlerChain chain = new HandlerChain();
		// chain.addLast(firstVisitFilter);
		// chain.addLast(hdl);
		// hdl = chain;


		server = new Server(listenPort, hdl);
		StreamUtils.start(server);
	}

	public static void main(String... args) throws Exception {
		if (args.length != 1) {
			System.out.println("usage org.xsocket.stream.EchoServer <listenport>");
			System.exit(-1);
		}

		new EchoServer(Integer.parseInt(args[0]));
	}


	public InetAddress getLocalAddress() {
		return server.getLocalAddress();
	}

	public void close() throws IOException {
		if (server != null) {
			server.close();
		}
	}


	private static final class EchoHandler implements IConnectHandler, IDataHandler {


		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setFlushmode(FlushMode.ASYNC);  // set flush mode async for performance reasons
			return true;
		}

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			ByteBuffer[] data = connection.readAvailable();
			connection.write(data);
			return true;
		}
	}



	private static final class FirstVisitThrottlingFilter implements IConnectHandler {

		private final Set<String> knownIps = new HashSet<String>();

		private int writeRate = 0;


		FirstVisitThrottlingFilter(int writeRate) {
			this.writeRate = writeRate;
		}

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			String ipAddress = connection.getRemoteAddress().getHostAddress();
			if (!knownIps.contains(ipAddress)) {
				knownIps.add(ipAddress);
				connection.setFlushmode(FlushMode.ASYNC);
				connection.setWriteTransferRate(writeRate);
			}


			return false;  // false -> successor element in handler chain will be called (true -> chain processing will be terminated)
		}
	}
}
