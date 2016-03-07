/*
 * Copyright (c) xlightweb.org, 2006 - 2010. All rights reserved.
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
package org.xsocket.connection;


import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.xsocket.DataConverter;




/**
 * The acceptor is responsible to accept new incoming connections, and
 * register these on the dispatcher.<br><br>
 *
 * @author grro@xsocket.org
 */
final class IoAcceptor  {

    private static final Logger LOG = Logger.getLogger(IoAcceptor.class.getName());

	private static final String SO_RCVBUF = IServer.SO_RCVBUF;
	private static final String SO_REUSEADDR = IServer.SO_REUSEADDR;

    @SuppressWarnings("unchecked")
	private static final Map<String, Class> SUPPORTED_OPTIONS = new HashMap<String, Class>();

    static {
        SUPPORTED_OPTIONS.put(SO_RCVBUF, Integer.class);
        SUPPORTED_OPTIONS.put(SO_REUSEADDR, Boolean.class);
    }


    // io handler
    private IIoAcceptorCallback callback;

    
    // open flag
    private final AtomicBoolean isOpen = new AtomicBoolean(true);



    // Socket
    private final ServerSocketChannel serverChannel;


    // SSL
    private final boolean sslOn;
    private final SSLContext sslContext;


    // dispatcher pool
    private final IoSocketDispatcherPool dispatcherPool;

    
    // statistics
    private long acceptedConnections;
	private long lastRequestAccpetedRate = System.currentTimeMillis();


    public IoAcceptor(IIoAcceptorCallback callback, InetSocketAddress address, int backlog, boolean isReuseAddress) throws IOException {
        this(callback, address, backlog, null, false, isReuseAddress);
    }



    public IoAcceptor(IIoAcceptorCallback callback, InetSocketAddress address, int backlog, SSLContext sslContext, boolean sslOn, boolean isReuseAddress) throws IOException {
        this.callback = callback;
        this.sslContext = sslContext;
        this.sslOn = sslOn;


        LOG.fine("try to bind server on " + address);

        // create a new server socket
        serverChannel = ServerSocketChannel.open();
        assert (serverChannel != null);
        
        serverChannel.configureBlocking(true);
        serverChannel.socket().setSoTimeout(0);  // accept method never times out
        serverChannel.socket().setReuseAddress(isReuseAddress); 
        
        
        try {
            serverChannel.socket().bind(address, backlog);
            
            dispatcherPool = new IoSocketDispatcherPool("Srv" + getLocalPort(), IoProvider.getServerDispatcherInitialSize());
        	
        } catch (BindException be) {
        	serverChannel.close();
            LOG.warning("could not bind server to " + address + ". Reason: " + DataConverter.toString(be));
            throw be;
        }

    }

    
    void setOption(String name, Object value) throws IOException {

        if (name.equals(SO_RCVBUF)) {
            serverChannel.socket().setReceiveBufferSize((Integer) value);

        } else if (name.equals(SO_REUSEADDR)) {
            serverChannel.socket().setReuseAddress((Boolean) value);

        } else {
            LOG.warning("option " + name + " is not supproted for " + this.getClass().getName());
        }
    }

    
    boolean isSSLSupported() {
    	return (sslContext != null);
    }
		
    boolean isSSLOn() {
    	return sslOn;
    }

    

    /**
     * {@inheritDoc}
     */
    public Object getOption(String name) throws IOException {

        if (name.equals(SO_RCVBUF)) {
            return serverChannel.socket().getReceiveBufferSize();

        } else if (name.equals(SO_REUSEADDR)) {
            return serverChannel.socket().getReuseAddress();

        } else {
            LOG.warning("option " + name + " is not supproted for " + this.getClass().getName());
            return null;
        }
    }


    @SuppressWarnings("unchecked")
	public Map<String, Class> getOptions() {
        return Collections.unmodifiableMap(SUPPORTED_OPTIONS);
    }
    
    
    IoSocketDispatcherPool getDispatcherPool() {
    	return dispatcherPool;
    }

	

    /**
     * {@inheritDoc}
     */
    public InetAddress getLocalAddress() {
    	return serverChannel.socket().getInetAddress();
    }


    /**
     * {@inheritDoc}
     */
    public int getLocalPort() {
    	return serverChannel.socket().getLocalPort();
    }
    
        

    /**
     * {@inheritDoc}
     */
    public void listen() throws IOException {
    	callback.onConnected();
    	accept();
    }


    private void accept() {

        while (isOpen.get()) {
            try {

                // blocking accept call
                SocketChannel channel = serverChannel.accept();

                // create IoSocketHandler
                IoSocketDispatcher dispatcher = dispatcherPool.nextDispatcher();
                IoChainableHandler ioHandler = ConnectionUtils.getIoProvider().createIoHandler(false, dispatcher, channel, sslContext, sslOn);

                // notify call back
                callback.onConnectionAccepted(ioHandler);
    			acceptedConnections++;

            } catch (Exception e) {
                // if acceptor is running (<socket>.close() causes that any
                // thread currently blocked in accept() will throw a SocketException)
                if (serverChannel.isOpen()) {
                	LOG.warning("error occured while accepting connection: " + DataConverter.toString(e));
                }
            }
        }
    }

    

	
    

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        if (isOpen.get()) {
            isOpen.set(false);

            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("closing acceptor");
            }
         
            try {
                // closes the server socket
                serverChannel.close();
            } catch (Exception e) {
                // eat and log exception
            	if (LOG.isLoggable(Level.FINE)) {
            		LOG.fine("error occured by closing " + e.toString());
            	}
            }

            dispatcherPool.close();

            callback.onDisconnected();
            callback = null;   // unset reference to server
        }
    }

    
     	
	void setDispatcherSize(int size) {
		dispatcherPool.setDispatcherSize(size);
    }
    
	int getDispatcherSize() {
    	return dispatcherPool.getDispatcherSize();
    }
	
    
	boolean getReceiveBufferIsDirect() {
		return dispatcherPool.getReceiveBufferIsDirect();
	}

	void setReceiveBufferIsDirect(boolean isDirect) {
		dispatcherPool.setReceiveBufferIsDirect(isDirect);
	}

	
	boolean isReceiveBufferPreallocationMode() {
    	return dispatcherPool.isReceiveBufferPreallocationMode();
	}
	
	
	void setReceiveBufferPreallocationMode(boolean mode) {
		dispatcherPool.setReceiveBufferPreallocationMode(mode);	}

	
	void setReceiveBufferPreallocatedMinSize(Integer minSize) {
		dispatcherPool.setReceiveBufferPreallocatedMinSize(minSize);
	}
	
	
	Integer getReceiveBufferPreallocatedMinSize() {
   		return dispatcherPool.getReceiveBufferPreallocatedMinSize();
 	}
	
	
	/**
	 * get the size of the preallocation buffer,
	 * for reading incoming data
	 *
	 * @return preallocation buffer size
	 */
	Integer getReceiveBufferPreallocationSize() {
		return dispatcherPool.getReceiveBufferPreallocationSize();
	}

	/**
	 * set the size of the preallocation buffer,
	 * for reading incoming data
	 *
	 * @param size the preallocation buffer size
	 */
	void setReceiveBufferPreallocationSize(int size) {
		dispatcherPool.setReceiveBufferPreallocationSize(size);
	}

	  
    double getAcceptedRateCountPerSec() {
     	double rate = 0;
		
    	long elapsed = System.currentTimeMillis() - lastRequestAccpetedRate;
    	
    	if (acceptedConnections == 0) {
    		rate = 0;
    		
    	} else if (elapsed == 0) {
    		rate = Integer.MAX_VALUE;
    		
    	} else {
    		rate = (((double) (acceptedConnections * 1000)) / elapsed);
    	}
    		
    	lastRequestAccpetedRate = System.currentTimeMillis();
    	acceptedConnections = 0;

    	return rate;
    }
    
	
	long getSendRateBytesPerSec() {
		return dispatcherPool.getSendRateBytesPerSec();
	}
	
	
	long getReceiveRateBytesPerSec() {
		return dispatcherPool.getReceiveRateBytesPerSec();
	}
}
