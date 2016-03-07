// $Id: Acceptor.java 1017 2007-03-15 08:03:05Z grro $
/*
 *  Copyright (c) xsocket.org, 2007. All rights reserved.
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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.stream.io.spi.IAcceptor;
import org.xsocket.stream.io.spi.IAcceptorCallback;
import org.xsocket.stream.io.spi.IIoHandlerContext;



import com.sun.grizzly.Context;
import com.sun.grizzly.Controller;
import com.sun.grizzly.ControllerStateListener;
import com.sun.grizzly.TCPSelectorHandler;



/**
 * Grizzly-based Acceptor
 *
 * @author grro@xsocket.org
 */
public final class Acceptor implements IAcceptor {
	
	private static final Logger LOG = Logger.getLogger(Acceptor.class.getName());

	private static final GrizzlyIoProvider IO_PROVIDER = new GrizzlyIoProvider();

	private static final Map<String, Class> SUPPORTED_OPTIONS = new HashMap<String, Class>();
	
	static {
		SUPPORTED_OPTIONS.put(SO_RCVBUF, Integer.class);
		SUPPORTED_OPTIONS.put(SO_REUSEADDR, Boolean.class);
	}

	
	// grizzly 
	private Controller controller  = null;
	private MyTCPSelectorHandler selectorHandler = null;
	private IAcceptorCallback callback = null;
	

	// xSocket
	private IIoHandlerContext handlerContext = null;
	

	
	public Acceptor(IAcceptorCallback callback, IIoHandlerContext handlerContext, InetSocketAddress address, int backlog, Map<String, Object> options) {
		this.handlerContext = handlerContext;
		this.callback = callback;
		
		selectorHandler = new MyTCPSelectorHandler();
		selectorHandler.setInet(address.getAddress());
		selectorHandler.setPort(address.getPort());
		selectorHandler.setSsBackLog(backlog);
		
		if (options.containsKey(IAcceptor.SO_REUSEADDR)) {
			boolean reuseAddress = (Boolean) options.get(IAcceptor.SO_REUSEADDR);
			if (reuseAddress) {
				selectorHandler.setReuseAddress(true);
			} else {
				selectorHandler.setReuseAddress(false);
			}
		}

		if (options.containsKey(IAcceptor.SO_REUSEADDR)) {
			boolean reuseAddress = (Boolean) options.get(IAcceptor.SO_REUSEADDR);
			if (reuseAddress) {
				selectorHandler.setReuseAddress(true);
			} else {
				selectorHandler.setReuseAddress(false);
			}
		}

		
        controller = new Controller();
        controller.setSelectorHandler(selectorHandler);
        controller.addStateListener(new ControllerStateListenerImpl());
        
        Executor workerpool = handlerContext.getWorkerpool();
        // TODO replace controller's pipeline workerpool with handlerContext's one
	}
	

	/**
	 * {@inheritDoc}
	 */
	public void listen() throws IOException {
		controller.start();
	}
	
	
	@Override
	public int getLocalPort() {
		return selectorHandler.getInetSocketAddress().getPort();
	}

	@Override
	public InetAddress getLocalAddress() {
		return selectorHandler.getInetSocketAddress().getAddress();
	}
	
	/**
	 * {@inheritDoc}
	 */	
	public Object getOption(String name) throws IOException {

		if (name.equals(IAcceptor.SO_RCVBUF)) {
			//return selectorHandler..getReceiveBufferSize();
			return null;
			
		} else if (name.equals(IAcceptor.SO_REUSEADDR)) {
			return selectorHandler.isReuseAddress();
			
		} else {
			LOG.warning("option " + name + " is not supproted for " + this.getClass().getName());
			return null;
		}
	}
	
	
	public Map<String, Class> getOptions() {
		return Collections.unmodifiableMap(SUPPORTED_OPTIONS);
	}
	

	/**
	 * {@inheritDoc}
	 */
	public int getNumberOfOpenConnections() {
		// TODO: check if has to be synchronized???
		return selectorHandler.getSelector().keys().size();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void close() throws IOException {
		controller.stop();
	}
	

	
	private final class MyTCPSelectorHandler extends TCPSelectorHandler {

		private boolean firstCall = false;
		
		@Override
		public void preSelect(Context ctx) throws IOException {
			super.preSelect(ctx);
			
			
			if (!firstCall) {
				firstCall = true;
				
				callback.onConnected();
				
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("grizzly acceptor initialized");
				}
			}
		}
		
		
		InetSocketAddress getInetSocketAddress() {
			return new InetSocketAddress(serverSocket.getInetAddress(), serverSocket.getLocalPort());
		}
		
		
		
	    
	    public boolean onAcceptInterest(SelectionKey key, Context ctx) throws IOException{
	    	 SocketChannel channel = (SocketChannel) acceptWithoutRegistration(key);
	         
	         if (channel != null) {
	             configureChannel(channel);
	             SelectionKey readKey = channel.register(selector, SelectionKey.OP_READ);
	             
	 	        IoHandler hdl = IO_PROVIDER.createIoHandler(handlerContext, false, controller, this, channel);
	 	        readKey.attach(hdl);
	 	        
				callback.onConnectionAccepted(hdl);
	         }
	         
	        return false;
	    }
	}
	
	
	
	
	private final class ControllerStateListenerImpl implements ControllerStateListener {
		
		
		public void onException(Throwable e) {
			
		}
		
		public void onReady() {
			
		}
		
		public void onStarted() {
			
			// couldn't be used to call callback onConnected, because the onStarted method 
			// will be called before binding the acceptor to the socket
			// To handle this issue the SelectorHandler (preSelect method) has been overriden
			// which call the onConnect callback method
		}
		
		
		public void onStopped() {
			callback.onDisconnected();
		}
	}
}