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
package org.xsocket.stream.io.spi;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


import org.xsocket.stream.StreamSocketConfiguration;
import org.xsocket.stream.io.spi.IAcceptorCallback;
import org.xsocket.stream.io.spi.IIoHandler;
import org.xsocket.stream.io.spi.IIoHandlerContext;


/**
*
* @author grro@xsocket.org
*/
final class ExampleAcceptor implements IAcceptor {

	private ServerSocket ssocket = null;
	private boolean isRunning = true;
	
	private static AtomicInteger nextId = new AtomicInteger();

	
	private IAcceptorCallback callback = null;
	private IIoHandlerContext handlerContext = null;
	private InetSocketAddress address = null;
	
	
	public ExampleAcceptor(IAcceptorCallback callback, IIoHandlerContext handlerContext, InetSocketAddress address) {
		this.callback = callback;
		this.handlerContext = handlerContext;
		this.address = address;
	}

	public InetSocketAddress getLocalAddress() {
		return new InetSocketAddress(ssocket.getInetAddress(), ssocket.getLocalPort());
	}
	
	public int getNumberOfOpenConnections() {
		return 0;
	}

	public void setOption(String name, Object value) throws IOException {
		// TODO Auto-generated method stub
		
	}
	
	public Object getOption(String name) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}
	
	public Map<String, Class> getOptions() {
		// TODO Auto-generated method stub
		return null;
	}
	
	
	public void listen() throws IOException {
		ssocket = new ServerSocket(address.getPort());
		
		callback.onConnected();
		
		while (isRunning) {
			try {
				Socket socket = ssocket.accept();
				if (socket != null) {
					callback.onConnectionAccepted(newIoHandler(socket, handlerContext));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	
	public void close() throws IOException {
		isRunning = false;
		ssocket.close();
	}	
	
	
	private IIoHandler newIoHandler(Socket socket, IIoHandlerContext ctx) {
		String id = ssocket.getLocalSocketAddress().hashCode() + "." + System.currentTimeMillis() + "." + nextId.incrementAndGet();
		return new ExampleIoHandler(id, socket, ctx); 
	}
}
