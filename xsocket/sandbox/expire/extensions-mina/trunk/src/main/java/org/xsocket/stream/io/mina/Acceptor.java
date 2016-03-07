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
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.mina.common.DefaultIoFilterChainBuilder;
import org.apache.mina.common.IoService;
import org.apache.mina.common.IoServiceListener;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.executor.ExecutorFilter;
import org.apache.mina.filter.executor.UnorderedExecutorFilter;
import org.apache.mina.transport.socket.nio.SocketAcceptor;

import org.xsocket.stream.io.spi.IAcceptor;
import org.xsocket.stream.io.spi.IAcceptorCallback;
import org.xsocket.stream.io.spi.IIoHandlerContext;



/**
*
*  
* @author grro@xsocket.org
*/
final class Acceptor implements IAcceptor {

	private static final Logger LOG = Logger.getLogger(Acceptor.class.getName());
	
	private SocketAcceptor acceptor = null;

	
	private static final AtomicInteger NEXT_ID = new AtomicInteger();
	private final AtomicInteger countOpenConnections = new AtomicInteger(); 
	
	private IAcceptorCallback callback = null;
	private IIoHandlerContext handlerContext = null;
	private InetSocketAddress address = null;
	private int backlog = 0;
	private Map<String, Object> options = null;
	
	
	
	public Acceptor(IAcceptorCallback callback, IIoHandlerContext handlerContext, InetSocketAddress address, int backlog, Map<String, Object> options) {
		this.callback = callback;
		this.handlerContext = handlerContext;
		this.address = address;
		this.backlog = backlog;
		this.options = options;
	}

	public InetSocketAddress getLocalAddress() {
		return acceptor.getLocalAddress();
	}
	
	public void listen() throws IOException {
		acceptor = new SocketAcceptor();
		acceptor.addListener(new MinaAcceptorListener());

		DefaultIoFilterChainBuilder chain = acceptor.getFilterChain();
		

		// multithreaded? -> add executor filter 
		if (handlerContext.isMultithreaded()) {
			
			// is handler thread safe? -> unsychronized executor filter
			if (handlerContext.isAppHandlerThreadSafe()) {
				chain.addLast("unsynchronizedExecutor", new UnorderedExecutorFilter(handlerContext.getWorkerpool()));
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("mina unsynchronized executorfilter added");
				}
				
			// ... no -> synchronized executor filter 
			} else {
				chain.addLast("executor", new ExecutorFilter(handlerContext.getWorkerpool()));
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("mina (synhronized) executorfilter added");
				}
			}
		}

        acceptor.setLocalAddress(address);
        acceptor.setHandler(new MinaIoHandler(new ServerConnectHandler()));
        acceptor.setBacklog(backlog);

        acceptor.bind();
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("mina acceptor initialized");
		}
		
		callback.onConnected();
	}
	
	
	public int getNumberOfOpenConnections() {
		return countOpenConnections.get();
	}
	
	public void close() throws IOException {
		acceptor.unbind();
	}
	
	public Object getOption(String name) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}
	
	public Map<String, Class> getOptions() {
		// TODO Auto-generated method stub
		return null;
	}
	
	
	private final class MinaAcceptorListener implements IoServiceListener {
	
		public void sessionCreated(IoSession session) {
			countOpenConnections.incrementAndGet();
		}
		
		public void sessionDestroyed(IoSession session) {
			countOpenConnections.decrementAndGet();
		}
		
		public void serviceActivated(IoService service) {
			
		}
		
		public void serviceDeactivated(IoService service) {
			callback.onDisconnected();			
		}
	}
	

	private final class ServerConnectHandler implements IMinaIoHandlerCallback {
				
		public void onConnect(IoSession session) throws IOException {
			IoHandler hdl = new IoHandler(session, session.toString() + ".s." + NEXT_ID.incrementAndGet());
			session.setAttachment(hdl);
			callback.onConnectionAccepted(hdl);					
		}
	}	
}
