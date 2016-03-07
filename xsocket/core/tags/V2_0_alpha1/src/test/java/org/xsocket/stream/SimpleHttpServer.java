// $Id: SSLTestDirect.java 1023 2007-03-16 16:27:41Z grro $
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


import java.io.IOException;
import java.nio.BufferUnderflowException;


import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.stream.IServer;
import org.xsocket.stream.Server;
import org.xsocket.stream.io.spi.IHandlerIoProvider;
import org.xsocket.stream.io.spi.IIoHandler;
import org.xsocket.stream.io.spi.IIoHandlerContext;



/**
*
* @author grro@xsocket.org
*/
public final class SimpleHttpServer {

	
	private IServer server = null;
	
	
	public SimpleHttpServer(int listenPort, IHttpMessageHandler handler) throws IOException {
		server = new Server(listenPort, new HttpProtocolHandler(handler));
		server.setConnectionFactory(new HttpConnectionFactory());
		StreamUtils.start(server);
	}

	
	public static void main(String... args) throws Exception {
		if (args.length != 1) {
			System.out.println("usage org.xsocket.stream.SimpleHttpServer <listenport>");
			System.exit(-1);
		}
		
		new EchoServer(Integer.parseInt(args[0]));
	}
	
		
		
	public void close() throws IOException {
		if (server != null) {
			server.close();
		}
	}
	
	
	private static final class HttpProtocolHandler implements IDataHandler, IConnectionScoped {

		private IHttpMessageHandler handler = null;
		private boolean isBodyPart = false;
		
		public HttpProtocolHandler(IHttpMessageHandler handler) {
			this.handler = handler;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			if (isBodyPart) {
				
			} else {
				if (connection.indexOf("\r\n\r\n") != -1) {
					handler.onHttpMessage((IHttpConnection) connection);
				}
			}
			return true;
		}
		
		@Override
		public Object clone() throws CloneNotSupportedException {
			HttpProtocolHandler copy = (HttpProtocolHandler) super.clone();
			return copy;
		}
	}
	
	
	private static final class HttpConnectionFactory implements INonBlockingConnectionFactory {
		public INonBlockingConnection createNonBlockingConnection(IIoHandlerContext ctx, IIoHandler ioHandler, IHandler appHandler, IHandlerIoProvider ioProvider) throws IOException {
			return new HttpConnection(ctx, ioHandler, appHandler, ioProvider);
		}
	}
	
	
	private static final class HttpConnection extends NonBlockingConnection implements IHttpConnection {
		HttpConnection(IIoHandlerContext ctx, IIoHandler ioHandler, IHandler appHandler, IHandlerIoProvider ioProvider) throws IOException {
			super(ctx, ioHandler, appHandler, ioProvider); 
			setFlushmode(FlushMode.ASYNC);		
		}
		
		
		public HttpHeader readHeader() throws IOException, BufferUnderflowException {
			String rawHeader = readStringByDelimiter("\r\n\r\n");
			return new HttpHeader(rawHeader);
		}
		
		public void writeHeader(HttpHeader header) throws IOException {
			write(header.getFirstLine() + "\r\n");
			for (String headerLine : header.getHeaderLines()) {
				write(headerLine + "\r\n");
			}
			write("\r\n");
		}
	}
}
