//$Id: AbstractChannelBasedEndpoint.java 1049 2007-03-21 16:42:48Z grro $
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
package org.xsocket.group;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.BufferUnderflowException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.xsocket.stream.BlockingConnectionPool;
import org.xsocket.stream.IConnectHandler;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.IServer;
import org.xsocket.stream.Server;
import org.xsocket.stream.StreamUtils;




/**
 *   
 *  
 * @author grro@xsocket.org
 */
public final class GroupEndpoint<T extends Message> extends PeerNode implements IGroupEndpoint<T> {
	
	private static final Logger LOG = Logger.getLogger(GroupEndpoint.class.getName());
	
	private static final long CONNECTIONPOOL_IDLETIMEOUT_MILLIS = 3L * 60L * 1000L;
	private static final long SEND_TIMEOUT_MILLIS = 5L * 1000L;
	
	
	static final byte OK = 0;
	
	private IServer msgReceiveService = null;
	private Address serviceAddress = null;
	
	private IGroupEndpointHandler handler = null;
	
	private final Object readGuard = new Object(); 
	private final Queue<Message> receiveQueue = new ConcurrentLinkedQueue<Message>();

	private Executor workerPool = null;
	private BlockingConnectionPool connectionPool = null;
	private AtomicMessageHandler atomicMessageHandler = new AtomicMessageHandler();


	private final Set<IGroupNodeListener>  listeners = new HashSet<IGroupNodeListener>(); 

	
	private int addressSuffix = new Random().nextInt();
	
	
	// statistics & management
	private long countMessagesReceived = 0;
	private long countMessagesSent = 0;
	
	
	public GroupEndpoint(InetAddress address, int port) throws IOException {
		this(address, port, null);
	}
	
	public GroupEndpoint(InetAddress address, int port, IGroupEndpointHandler handler) throws IOException {
		super(address, port);
		
		this.handler = handler;
		
		msgReceiveService = new Server(new ServiceHandler());
		Thread t = new Thread(msgReceiveService);
		t.setDaemon(true);
		t.setName("GroupEndpointService#" + this.hashCode());
		t.start();
		
		serviceAddress = new Address(msgReceiveService.getLocalAddress(), msgReceiveService.getLocalPort());
		
		connectionPool = new BlockingConnectionPool(CONNECTIONPOOL_IDLETIMEOUT_MILLIS);
		
		workerPool = msgReceiveService.getWorkerpool();
		init(workerPool, msgReceiveService.getLocalAddress(), msgReceiveService.getLocalPort(), 5000);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public Address getLocalAddress() {
		return serviceAddress;
	}
	
	public Set<Address> getPeerAddresses() {
		Set<Address> result = new HashSet<Address>();
		result.addAll(getNeighborsAddresses());
		return result;
	}

	
	/**
	 * {@inheritDoc}
	 */
	public void addListener(IGroupNodeListener listener) {
		listeners.add(listener);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void removeListener(IGroupNodeListener listener) {
		listeners.remove(listener);
	}
	
	
	@Override
	protected void onRemotePeerTimeout(Address address) {
		if (!listeners.isEmpty()) {
			for(IGroupNodeListener listener : listeners) {
				listener.onMemberTimeout(address.toSocketAddress());
			}
		}
	}
	
	@Override
	protected void onRemotePeerJoined(Address address) {
		if (!listeners.isEmpty()) {
			for(IGroupNodeListener listener : listeners) {
				listener.onMemberJoined(address.toSocketAddress());
			}
		}
	}
	
	@Override
	protected void onRemotePeerLeaved(Address address) {
		if (!listeners.isEmpty()) {
			for(IGroupNodeListener listener : listeners) {
				listener.onMemberLeaved(address.toSocketAddress());
			}
		}
	}
	
	
	@Override
	public void close() throws IOException {
		super.close();

		msgReceiveService.close();
		
		for (IGroupNodeListener listener : listeners) {	
			listener.onDestroy();
		}
	}
	
	
	public <E extends Serializable> ObjectMessage<E> createObjectMessage(E obj) throws IOException {
		return new ObjectMessage<E>(serviceAddress, addressSuffix, obj);
	}
	
	
	
	@SuppressWarnings("unchecked")
	public T receiveMessage() throws IOException {
		return (T) receiveQueue.poll();
	}

	
	public T receiveMessage(long receiveTimeout) throws IOException, SocketTimeoutException {
		synchronized (readGuard) {
			long start = System.currentTimeMillis();
			do {
				T msg = receiveMessage();
				if (msg != null) {
					return msg;
				} else {
					try {
						readGuard.wait(receiveTimeout / 10);
					} catch (InterruptedException ignore) { }
				}
			} while ((start + receiveTimeout) > System.currentTimeMillis());
		}
		
		throw new SocketTimeoutException("receive timeout " + receiveTimeout + " reached");
	}

	
	public void send(T message) throws IOException, SocketTimeoutException {
		if (message.getDestinationAddresses().isEmpty()) {
			for (Address peerAddress : getPeerAddresses()) {
				message.addDestinationAddress(peerAddress);
			}
		}
		
		new InternalGroupMessage(workerPool, connectionPool).send(message, getViewId(), SEND_TIMEOUT_MILLIS);
		countMessagesSent++;
	}


	
	long getCountMessagesReceived() {
		return countMessagesReceived;
	}
	
	long getCountMessagesSent() {
		return countMessagesSent;
	}
	

	

	
	private final class ServiceHandler implements IConnectHandler, IDataHandler {
	
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			
			StreamUtils.validateSufficientDatasizeByIntLengthField(connection);

			byte msgType = connection.readByte();
			int viewId = connection.readInt();
			
			atomicMessageHandler.onMessage(msgType, viewId, connection);

			return true;
		}
	}
	
	
	private interface IMessageHandler {
		public void onMessage(byte msgType, int viewId, INonBlockingConnection connection) throws IOException;
	}
	
	
	private final class AtomicMessageHandler implements IMessageHandler {
		
		private final Map<String, Message> uncommittedMessages = new HashMap<String, Message>();
		
		public void onMessage(byte msgType, int viewId, INonBlockingConnection connection) throws IOException {

			
			if (msgType == InternalGroupMessage.CALL) {
				Message message = Message.readFrom(connection);
				String uid = message.getSourceAddress().getAddress().getHostAddress() + ":" + message.getSourceAddressSuffix() + ":" + message.getId();
				
				if (viewId == getViewId()) {
					connection.write(InternalGroupMessage.VOTE_COMMIT);
					uncommittedMessages.put(uid, message); 
					
				} else {
					connection.write(InternalGroupMessage.VOTE_ABORT);
				}
				
			} else if (msgType == InternalGroupMessage.GLOBAL_ABORT) {
				int length = connection.readInt();
				String uid = connection.readStringByLength(length);
				uncommittedMessages.remove(uid);
				
			} else if (msgType == InternalGroupMessage.GLOBAL_COMMIT) {
				int length = connection.readInt();
				String uid = connection.readStringByLength(length);
				Message message = uncommittedMessages.remove(uid);
				
				deliverMessage(message);
			}
		}
		

		private void deliverMessage(Message msg) {
			receiveQueue.add(msg);
				
			synchronized (readGuard) {
				readGuard.notify();
			}
						
			countMessagesReceived++;

				
			if (handler != null) {
				Runnable task = new Runnable() {
					public void run() {
						synchronized (handler) {
							try {
								handler.onMessage(GroupEndpoint.this);
							} catch (Exception e) {
								if (LOG.isLoggable(Level.FINE)) {
									LOG.fine("erro occured by handling message by " + handler + ". Reason " + e.toString());
								}
							}
						}
					}
				};
				workerPool.execute(task);
			}
		}
	}
}
