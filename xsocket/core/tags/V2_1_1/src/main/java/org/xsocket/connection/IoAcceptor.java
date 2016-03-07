/*
 *  Copyright (c) xsocket.org, 2006 - 2008. All rights reserved.
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
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;




/**
 * The acceptor ist responsible to accept new incoming connections, and
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
    private IIoAcceptorCallback callback = null;

    
    // open flag
    private volatile boolean isOpen = true;


    // Socket
    private ServerSocketChannel serverChannel = null;


    // SSL
    private boolean sslOn = false;
    private SSLContext sslContext = null;


    // dispatcher pool
    private IoSocketDispatcherPool disptacherPool = null;

	
    
    // statistics
    private long acceptedConnections = 0;
	private long lastRequestAccpetedRate = System.currentTimeMillis();


    public IoAcceptor(IIoAcceptorCallback callback, InetSocketAddress address, int backlog) throws IOException {
        this(callback, address, backlog, null, false);
    }



    public IoAcceptor(IIoAcceptorCallback callback, InetSocketAddress address, int backlog, SSLContext sslContext, boolean sslOn) throws IOException {
        this.callback = callback;
        this.sslContext = sslContext;
        this.sslOn = sslOn;


        LOG.fine("try to bind server on " + address);

        // create a new server socket
        serverChannel = ServerSocketChannel.open();
        assert (serverChannel != null);
        
        serverChannel.configureBlocking(true);
        serverChannel.socket().setSoTimeout(0);  // accept method never times out

        serverChannel.socket().setReuseAddress(true); // set reuse address by default (can be override by socketConfig)
        
        
        try {
            serverChannel.socket().bind(address, backlog);
            
            disptacherPool = new IoSocketDispatcherPool("Srv" + getLocalPort());
        	
        } catch (BindException be) {
            if (serverChannel != null) {
                serverChannel.close();
            }
            LOG.info("error occured while binding server on on " + address + ". Reason: " + be.toString());
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
    	return disptacherPool;
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

        while (isOpen) {
            try {

                // blocking accept call
                SocketChannel channel = serverChannel.accept();

                // create IoSocketHandler
                IoSocketDispatcher dispatcher = disptacherPool.nextDispatcher(0);
                IoChainableHandler ioHandler = ConnectionUtils.getIoProvider().createIoHandler(false, dispatcher, channel, sslContext, sslOn);

                // notify call back
                callback.onConnectionAccepted(ioHandler);
    			acceptedConnections++;

            } catch (Exception e) {
                if (LOG.isLoggable(Level.FINE)) {
                    // if acceptor is running (<socket>.close() causes that any
                    // thread currently blocked in accept() will throw a SocketException)
                    if (serverChannel.isOpen()) {
                        LOG.fine("error occured while accepting connection: " + e.toString());
                    }
                }
            }
        }
    }

    

	
    

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        if (isOpen) {
            isOpen = false;

            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("closing acceptor");
            }

            try {
                // closes the server socket
                serverChannel.close();
            } catch (Exception ignore) { }

            disptacherPool.close();

            callback.onDisconnected();
        }
    }

    
     	
	void setDispatcherSize(int size) {
		disptacherPool.setDispatcherSize(size);
    }
    
	int getDispatcherSize() {
    	return disptacherPool.getDispatcherSize();
    }
	
    
	boolean getReceiveBufferIsDirect() {
		return disptacherPool.getReceiveBufferIsDirect();
	}

	void setReceiveBufferIsDirect(boolean isDirect) {
		disptacherPool.setReceiveBufferIsDirect(isDirect);
	}

	
	boolean isReceiveBufferPreallocationMode() {
    	return disptacherPool.isReceiveBufferPreallocationMode();
	}
	
	
	void setReceiveBufferPreallocationMode(boolean mode) {
		disptacherPool.setReceiveBufferPreallocationMode(mode);	}

	
	void setReceiveBufferPreallocatedMinSize(Integer minSize) {
		disptacherPool.setReceiveBufferPreallocatedMinSize(minSize);
	}
	
	
	Integer getReceiveBufferPreallocatedMinSize() {
   		return disptacherPool.getReceiveBufferPreallocatedMinSize();
 	}
	
	
	/**
	 * get the size of the preallocation buffer,
	 * for reading incoming data
	 *
	 * @return preallocation buffer size
	 */
	Integer getReceiveBufferPreallocationSize() {
		return disptacherPool.getReceiveBufferPreallocationSize();
	}

	/**
	 * set the size of the preallocation buffer,
	 * for reading incoming data
	 *
	 * @param size the preallocation buffer size
	 */
	void setReceiveBufferPreallocationSize(int size) {
		disptacherPool.setReceiveBufferPreallocationSize(size);
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
		return disptacherPool.getSendRateBytesPerSec();
	}
	
	
	long getReceiveRateBytesPerSec() {
		return disptacherPool.getReceiveRateBytesPerSec();
	}
}
