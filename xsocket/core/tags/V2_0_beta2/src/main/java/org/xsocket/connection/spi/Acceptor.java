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
package org.xsocket.connection.spi;


import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.xsocket.IDispatcher;



/**
 * The acceptor ist responsible to accept new incoming connections, and
 * register these on the dispatcher.<br><br>
 *
 * @author grro@xsocket.org
 */
final class Acceptor implements IAcceptor {

    private static final Logger LOG = Logger.getLogger(Acceptor.class.getName());

    @SuppressWarnings("unchecked")
	private static final Map<String, Class> SUPPORTED_OPTIONS = new HashMap<String, Class>();

    static {
        SUPPORTED_OPTIONS.put(SO_RCVBUF, Integer.class);
        SUPPORTED_OPTIONS.put(SO_REUSEADDR, Boolean.class);
    }


    // io handler
    private static final DefaultIoProvider IO_PROVIDER = new DefaultIoProvider();
    private IAcceptorCallback callback = null;

    
    // running flag
    private volatile boolean isRunning = true;


    // Socket
    private ServerSocketChannel serverChannel = null;


    // SSL
    private boolean sslOn = false;
    private SSLContext sslContext = null;


	// memory management
	private int preallocationSize = DefaultIoProvider.DEFAULT_READ_BUFFER_PREALLOCATION_SIZE;
	private int bufferMinsize = DefaultIoProvider.DEFAULT_READ_BUFFER_MIN_SIZE;
	private boolean preallocation = true; 
	private boolean useDirect = false;

 
	// dispatcher management
	private final LinkedList<IoSocketDispatcher> dispatchers = new LinkedList<IoSocketDispatcher>();
	private int size = 0;
	private int pointer = 0;

	
	// listeners
	private final ArrayList<IAcceptorListener> listeners = new ArrayList<IAcceptorListener>();

    
    // statistics
    private long acceptedConnections = 0;
	private long lastRequestAccpetedRate = System.currentTimeMillis();


    public Acceptor(IAcceptorCallback callback, InetSocketAddress address, int backlog) throws IOException {
        this(callback, address, backlog, null, false);
    }



    public Acceptor(IAcceptorCallback callback, InetSocketAddress address, int backlog, SSLContext sslContext, boolean sslOn) throws IOException {
        this.callback = callback;
        this.sslContext = sslContext;
        this.sslOn = sslOn;


        LOG.fine("try to bind server on " + address);

        // create a new server socket
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(true);
        serverChannel.socket().setSoTimeout(0);  // accept method never times out

        serverChannel.socket().setReuseAddress(true); // set reuse address by default (can be override by socketConfig)
        
        
        try {
            setDispatcherSize(Runtime.getRuntime().availableProcessors() + 1);

            assert (serverChannel != null);
            serverChannel.socket().bind(address, backlog);
        } catch (BindException be) {
            if (serverChannel != null) {
                serverChannel.close();
            }
            LOG.info("error occured while binding server on on " + address + ". Reason: " + be.toString());
            throw be;
        }

    }
    
    
    void addListener(IAcceptorListener listener) {
		listeners.add(listener);
	}


	boolean removeListener(IAcceptorListener listener) {
		return listeners.remove(listener);
	}




    void setOption(String name, Object value) throws IOException {

        if (name.equals(IAcceptor.SO_RCVBUF)) {
            serverChannel.socket().setReceiveBufferSize((Integer) value);

        } else if (name.equals(IAcceptor.SO_REUSEADDR)) {
            serverChannel.socket().setReuseAddress((Boolean) value);

        } else {
            LOG.warning("option " + name + " is not supproted for " + this.getClass().getName());
        }
    }


    /**
     * {@inheritDoc}
     */
    public Object getOption(String name) throws IOException {

        if (name.equals(IAcceptor.SO_RCVBUF)) {
            return serverChannel.socket().getReceiveBufferSize();

        } else if (name.equals(IAcceptor.SO_REUSEADDR)) {
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

        // acceptor loop
        while (isRunning) {
            try {

                // blocking accept call
                SocketChannel channel = serverChannel.accept();

                // create IoSocketHandler
                IoSocketDispatcher dispatcher = nextDispatcher();
                IIoHandler ioHandler = IO_PROVIDER.createIoHandler(false, dispatcher, channel, sslContext, sslOn);

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

        } // acceptor loop
    }

    


	private IoSocketDispatcher nextDispatcher() {
		IoSocketDispatcher result = null;
		
		try {
			// round-robin approach
			pointer++;
			if (pointer >= size) {
				pointer = 0;
			}
	
			result = dispatchers.get(pointer);
		} catch (Exception concurrentException) {
			if (isRunning) {
				result = nextDispatcher();
			} 
		}
		
		return result;
	}



	@SuppressWarnings("unchecked")
	private void updateDispatcher() {
		if (isRunning) {
			synchronized (dispatchers) {
				int currentRunning = dispatchers.size();
	
				if (currentRunning != size) {
					if (currentRunning > size) {
						for (int i = size; i <  currentRunning; i++) {
							IDispatcher<IoSocketHandler> dispatcher = dispatchers.getLast();
							dispatchers.remove(dispatcher);
							try {
								dispatcher.close();
							} catch (IOException ioe) {
								if (LOG.isLoggable(Level.FINE)) {
									LOG.fine("error occured by closing the dispatcher " + dispatcher + ". reason " + ioe.toString());
								}
							}
	
							for (IAcceptorListener listener : listeners) {
								listener.onDispatcherRemoved(dispatcher);
							}
						}
	
					} else if ( currentRunning < size) {
						for (int i = currentRunning; i < size; i++) {
							UnsynchronizedMemoryManager memoryManager = null;
							if (preallocation) {
								memoryManager = UnsynchronizedMemoryManager.createPreallocatedMemoryManager(preallocationSize, bufferMinsize, useDirect);
							} else {
								memoryManager = UnsynchronizedMemoryManager.createNonPreallocatedMemoryManager(useDirect);
							}
							
							IoSocketDispatcher dispatcher = new IoSocketDispatcher(memoryManager);
							dispatchers.addLast(dispatcher);
	
							Thread t = new Thread(dispatcher);
							t.setDaemon(false);
							t.setName(IoSocketDispatcher.DISPATCHER_PREFIX + "#" + i);
							t.start();
	
							for (IAcceptorListener listener : listeners) {
								listener.onDispatcherAdded(dispatcher);
							}
	
						}
					}
				}
	
				IDispatcher<IoSocketHandler>[] connectionDispatchers = new IDispatcher[dispatchers.size()];
				for (int i = 0; i < connectionDispatchers.length; i++) {
					connectionDispatchers[i] = dispatchers.get(i);
				}
			}
		}
	}


    

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        if (isRunning) {
            isRunning = false;

            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("closing acceptor");
            }

            try {
                // closes the server socket
                serverChannel.close();
            } catch (Exception ignore) { }

            shutdownDispatcher();

            callback.onDisconnected();
        }
    }

    
    

	/**
	 * shutdown the pool
	 *
	 */
	private void shutdownDispatcher() {
        if (LOG.isLoggable(Level.FINER)) {
			LOG.fine("terminate dispatchers");
		}

        synchronized (dispatchers) {
			for (IDispatcher<IoSocketHandler> dispatcher : dispatchers) {
				try {
					dispatcher.close();
	
					for (IAcceptorListener listener : listeners) {
						listener.onDispatcherRemoved(dispatcher);
					}
	
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("error occured by closing the dispatcher " + dispatcher + ". reason " + ioe.toString());
					}
				}
	        }
        }

		dispatchers.clear();
	}


	
	public int getNumberOfOpenConnections() {
    	return getOpenConntionInfos().size();
    }

    
    public List<String> getOpenConntionInfos() {
    	List<String> result = new ArrayList<String>();
    	
        for (IDispatcher<IoSocketHandler> dispatcher : getDispatchers()) {
            for (IoSocketHandler handler : dispatcher.getRegistered()) {
                result.add(handler.toString());
            }
        }
        return result;
    }
    
    
	@SuppressWarnings("unchecked")
	List<IDispatcher<IoSocketHandler>> getDispatchers() {
		List<IDispatcher<IoSocketHandler>> result = null;
		synchronized (dispatchers) {
			result = (List<IDispatcher<IoSocketHandler>>) dispatchers.clone();
		}
		return result;
	}

    
	
    void setDispatcherSize(int size) {
    	this.size = size;
		updateDispatcher();
    }
    
    int getDispatcherSize() {
    	return size;
    }

    
	boolean getReceiveBufferIsDirect() {
		return useDirect;
	}

	void setReceiveBufferIsDirect(boolean isDirect) {
		this.useDirect = isDirect; 
		for (IoSocketDispatcher dispatcher: dispatchers) {
			dispatcher.setReceiveBufferIsDirect(isDirect);
		}
	}

	
	boolean isReceiveBufferPreallocationMode() {
    	return preallocation;
	}
	    
	void setReceiveBufferPreallocationMode(boolean mode) {
		this.preallocation = mode;

		synchronized (dispatchers) {
			for (IoSocketDispatcher dispatcher: dispatchers) {
				dispatcher.setReceiveBufferPreallocationMode(mode);
			}
		}
	}
	
	void setReceiveBufferPreallocatedMinSize(Integer minSize) {
		this.bufferMinsize = minSize;
		
		synchronized (dispatchers) {
			for (IoSocketDispatcher dispatcher: dispatchers) {
				dispatcher.setReceiveBufferPreallocatedMinSize(minSize);
			}
		}
	}
	
	
	Integer getReceiveBufferPreallocatedMinSize() {
   		return bufferMinsize;
 	}
	
	
	
	/**
	 * get the size of the preallocation buffer,
	 * for reading incoming data
	 *
	 * @return preallocation buffer size
	 */
	Integer getReceiveBufferPreallocationSize() {
		return preallocationSize;
	}

	/**
	 * set the size of the preallocation buffer,
	 * for reading incoming data
	 *
	 * @param size the preallocation buffer size
	 */
	void setReceiveBufferPreallocationSize(int size) {
		preallocationSize = size;
		
		for (IoSocketDispatcher dispatcher: dispatchers) {
			dispatcher.setReceiveBufferPreallocatedSize(size);
		}
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
		long rate = 0;
		for (IoSocketDispatcher dispatcher : dispatchers) {
			rate += dispatcher.getSendRateBytesPerSec();
		}
		
		return rate;
	}
	
	
	long getReceiveRateBytesPerSec() {
		long rate = 0;
		for (IoSocketDispatcher dispatcher : dispatchers) {
			rate += dispatcher.getReceiveRateBytesPerSec();
		}
		
		return rate;
	}
	


	@SuppressWarnings("unchecked")
	long getNumberOfConnectionTimeouts() {
		long timeouts = 0;

		LinkedList<IoSocketDispatcher> copy = null;
		synchronized (dispatchers) {
			copy = (LinkedList<IoSocketDispatcher>) dispatchers.clone();
		}
		for (IoSocketDispatcher dispatcher : copy) {
			timeouts += dispatcher.getCountConnectionTimeout();
		}
		return timeouts;
	}


	@SuppressWarnings("unchecked")
	public long getNumberOfIdleTimeouts() {
		long timeouts = 0;

		LinkedList<IoSocketDispatcher> copy = null;
		synchronized (dispatchers) {
			copy = (LinkedList<IoSocketDispatcher>) dispatchers.clone();
		}
		for (IoSocketDispatcher dispatcher : copy) {
			timeouts += dispatcher.getCountIdleTimeout();
		}
		return timeouts;
	}
}
