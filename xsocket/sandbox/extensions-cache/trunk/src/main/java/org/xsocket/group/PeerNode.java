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
import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;
import org.xsocket.IDataSource;
import org.xsocket.datagram.IConnectedEndpoint;
import org.xsocket.datagram.IDatagramHandler;
import org.xsocket.datagram.IEndpoint;
import org.xsocket.datagram.MulticastEndpoint;
import org.xsocket.datagram.UserDatagram;
import org.xsocket.group.PeerCatalog.Peer;



/**
 * A member of a multicast-based group. It supports required group management services, like  
 * <ul>
 *  <li>group management (join/leave)</i>
 *  <li>failure management (crashes)</i>
 * </ul>
 * 
 *  
 * By j 
 *  
 * @author grro@xsocket.org
 */
abstract class PeerNode {
	
	private static final Logger LOG = Logger.getLogger(PeerNode.class.getName());

	static final byte MESSAGE_PREFIX = 44;

	private static final long OLD_PEER_REMOVE_TIMEOUT = 60 * 60 * 1000;
	private static final int PACKET_SIZE = 128;
	

	
	// commands
	private final Map<Byte,CommandHandler> cmdHandlers = new HashMap<Byte, CommandHandler>();
	
	
	// timer
	private static final Timer TIMER = new Timer(true);
	

	// ping sender
	private long pingPeriodMillis = 0;
	private TimerTask pingSendTask = null;
	

	// timeout check
	private long idleTimeoutMillis = 0;
	private long idleTimeoutCheckPeriodMillis = 0;
	private TimerTask timeoutTimeoutWatchdog = null;

	private TimerTask housekeeper = null;
	
	// broadcast endpoint 
	private IConnectedEndpoint broadcastEndpoint = null;
	private Address serviceAddress = null;


	private final Object initGuard = new Object();
	private int initialGroupSize = -1;
	private boolean isInitialized = false;
	
	
	// neighbor catalog
	private PeerCatalog catalog = null;
	
	
	// statistics & management
	private boolean isGroupHashSynchron = true;
	
	
	static {
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
			}
		};
		TIMER.schedule(task, 0);
	}
	
	
	
	PeerNode(InetAddress groupAddress, int port) throws IOException {
		broadcastEndpoint = new MulticastEndpoint(groupAddress, port, PACKET_SIZE, new DatagramHandler());
		
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				try {
					close();
				} catch (Exception e) { 
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("error occured by shutdown. Reason: " + e.toString());
					}
				}
			}
		});
	}
	

	protected void init(Executor workerpool, InetAddress address, int port, long pingPeriodMillis) throws IOException {
		
		this.serviceAddress = new Address(address, port);
		catalog = new PeerCatalog(serviceAddress);


		cmdHandlers.put(JoinResonseCommand.COMMAND_KEY, new JoinResonseCommand(this));

		long initTimeout = (2 * pingPeriodMillis) + (pingPeriodMillis / 10);

		new JoinCommand(this).send();
	
		long start = System.currentTimeMillis();

		// wait until all peers has response the join
		synchronized (initGuard) {
			while ((start + initTimeout) > System.currentTimeMillis()) {
					
				if (getGroupMemberSize() == initialGroupSize) { 
					break;
				} else {
					try {
						initGuard.wait(initTimeout);
					} catch (InterruptedException ignore) { }
				}
			}
			
			if (getGroupMemberSize() == initialGroupSize) {
				LOG.info("[" + getId() + "] initialized " + (getGroupMemberSize() - 1) + " peers found (init time= " + DataConverter.toFormatedDuration(System.currentTimeMillis() - start) + ")");
			} else {
				LOG.info("[" + getId() + "] initialized " + (getGroupMemberSize() - 1) + " peers found (init timeout " + DataConverter.toFormatedDuration(initTimeout) + " reached)");
			}
		}
	
		isInitialized = true;
			
		cmdHandlers.put(JoinCommand.COMMAND_KEY, new JoinCommand(this));
		cmdHandlers.put(LeaveCommand.COMMAND_KEY, new LeaveCommand(this));
		cmdHandlers.put(PingCommand.COMMAND_KEY, new PingCommand(this));
		cmdHandlers.put(RequirePingCommand.COMMAND_KEY, new RequirePingCommand(this));
			
		initialGroupSize = getGroupMemberSize();  

		setPingPeriodMillis(pingPeriodMillis);
		
		TimerTask housekeeper = new TimerTask() {
			
			@Override
			public void run() {
				catalog.cleanRemovedPeers(OLD_PEER_REMOVE_TIMEOUT);
			}
		};
		TIMER.schedule(housekeeper, (OLD_PEER_REMOVE_TIMEOUT / 10), (OLD_PEER_REMOVE_TIMEOUT / 10));
	}
	
	
	private void registerJoinResponse(Address neighborAddress, int groupSize) {
		synchronized (initGuard) {
			if (groupSize > initialGroupSize) {
				initialGroupSize = groupSize;
			}
			initGuard.notify();
		}
	}

	
	
	final boolean isGroupPeerHashSynchron() {
		return isGroupHashSynchron;
	}
	
	
	final long getIdleTimeoutCheckPeriodMillis() {
		return idleTimeoutCheckPeriodMillis;
	}
	
	
	final long getIdleTimeoutMillis() {
		return idleTimeoutMillis;
	}

	/**
	 * for debug only
	 */
	final int getInitialGroupSize() {
		return initialGroupSize;
	}
	
	
	void setIdleTimeoutMillis(long timeout) {
		this.idleTimeoutMillis = timeout;
		this.idleTimeoutCheckPeriodMillis = timeout / 2;
		
		restartTimeoutWatchdog();
	}
	
	protected void onRemotePeerTimeout(Address address) {
		
	}
	
	protected void onRemotePeerJoined(Address address) {
		
	}

	protected void onRemotePeerLeaved(Address address) {
		
	}

	private void restartTimeoutWatchdog() {
		if (timeoutTimeoutWatchdog != null) {
			timeoutTimeoutWatchdog.cancel();
		}
		
		if (idleTimeoutMillis < Long.MAX_VALUE) {
			timeoutTimeoutWatchdog = new TimerTask() {
				@Override
				public void run() {
					long currentTime = System.currentTimeMillis();
					
					Peer[] peers = catalog.getActivePeers();
					for (Peer peer : peers) {
						if (currentTime > (peer.getLastConnect() + pingPeriodMillis + 20)) {
							new RequirePingCommand(PeerNode.this).send(peer.getAddress());
						}
						
						if (currentTime > (peer.getLastConnect() + idleTimeoutMillis)) {
							if (LOG.isLoggable(Level.INFO)) {
								LOG.info("[" + getId() + "] peer " + peer.getAddress().toString() + " removed (idle timeout " + DataConverter.toFormatedDuration(idleTimeoutMillis)  + " reached)");
							}
							catalog.remove(peer.getAddress());
							
							onRemotePeerTimeout(peer.getAddress());
						}
					}
				}
			};
			TIMER.schedule(timeoutTimeoutWatchdog, idleTimeoutCheckPeriodMillis, idleTimeoutCheckPeriodMillis);
		}
	}
	
	
	final long getPingPeriodMillis() {
		return pingPeriodMillis;
	}

	
	void setPingPeriodMillis(long period) {
		this.pingPeriodMillis = period;
		restartPingSender();
		
		setIdleTimeoutMillis((2 * pingPeriodMillis) + (pingPeriodMillis / 10));
	}

	private void restartPingSender() {
		if (pingSendTask != null) {
			pingSendTask.cancel();
		}
		
		if (pingPeriodMillis < Long.MAX_VALUE) {
			pingSendTask = new TimerTask() {
				@Override
				public void run() {
					new PingCommand(PeerNode.this).send();
				}
			};
			TIMER.schedule(pingSendTask, pingPeriodMillis, pingPeriodMillis);
		}
	}
	
	public void close() throws IOException {

		if (isOpen()) {
			try {
				
				// send leave
				new LeaveCommand(this).send();
				
				// deactivate timeout check
				setIdleTimeoutMillis(Long.MAX_VALUE);
				
				// deactivate ping
				setPingPeriodMillis(Long.MAX_VALUE);

				if (housekeeper!= null) {
					housekeeper.cancel();
				}
				
				try {
					Thread.sleep(200);
				} catch (InterruptedException ignore) { }
				
				// close endpoints
				broadcastEndpoint.close();
				
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine(getId() + " closed");
				}
			} catch (Exception e) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + getId() + "] error occured by closing endpoint " + getId() + ". reason: " + e.toString());
				}
			}
		}
	}
	
	final String getServiceAddress() {
		return serviceAddress.toString();
	}
	
	final int getGroupMemberSize() {
		return getGroupMembers().length;
	}
	
	final int getViewId() {
		return catalog.getViewId();
	}
	
	final String[] getGroupMembers() {
		Peer[] peers = catalog.getActivePeers();
		String[] result = new String[peers.length];
		for (int i = 0; i < peers.length; i++) {
			result[i] = peers[i].toString();
		}
		
		return result;
	}
	
	
	final Set<Address> getNeighborsAddresses() {
		Set<Address> addresses = new HashSet<Address>();
	
		for (Peer peer : catalog.getActivePeers()) {
			Address peerAddress = peer.getAddress();
			if (!peerAddress.equals(serviceAddress)) {
				addresses.add(peerAddress);
			}
		}
		
		return addresses;
	}
	
	final String[] getRemovedGroupMembers() {
		Peer[] peers = catalog.getRemovedPeers();
		String[] result = new String[peers.length];
		for (int i = 0; i < peers.length; i++) {
			result[i] = peers[i].toString();
		}
		
		return result;
	}

	final String getId() {
		return serviceAddress.toString();
	}
	

	public final boolean isOpen() {
		return broadcastEndpoint.isOpen();
	}
	
	
	
	private Address readAndRegisterAddress(IDataSource dataSource, long groupHash, String reason) throws IOException {
		Address neighborAddress = Address.readFrom(dataSource);
		if (!neighborAddress.equals(serviceAddress)) {
			boolean isAdded = catalog.add(neighborAddress);
			if (isAdded) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.info("[" + getId() + "] peer " + neighborAddress.toString() + " added (" + reason + ")");
				}
			}
		}
		
		isGroupHashSynchron = (catalog.getViewId() == groupHash);
		
		return neighborAddress;
	}
	
	private Address readAndDeregisterAddress(IDataSource dataSource, String reason) throws IOException {
		Address neighborAddress = Address.readFrom(dataSource);
		if (!neighborAddress.equals(serviceAddress)) {
			boolean isRemoved = catalog.remove(neighborAddress);
			if (isRemoved) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.info("[" + getId() + "] peer " + neighborAddress.toString() + " removed (" + reason + ")");
				}
			}
		}
		return neighborAddress;
	}
	
	
	protected final void deregisterAddress(Address neighborAddress, String reason) throws IOException {
		if (!neighborAddress.equals(serviceAddress)) {
			boolean isRemoved = catalog.remove(neighborAddress);
			if (isRemoved) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.info("[" + getId() + "] peer " + neighborAddress.toString() + " removed (" + reason + ")");
				}
			}
		}
	}

	@Override
	public String toString() {
		return getId() + " hc=" + catalog.getViewId() + " groupSize=" + catalog.getActivePeers().length;
	}

	private interface CommandHandler {
		public void handle(IDataSource dataSource) throws IOException;
	}
	
	
	private final class DatagramHandler implements IDatagramHandler {
		
		public boolean onDatagram(IEndpoint localEndpoint) throws IOException {
			UserDatagram datagram = localEndpoint.receive();
			handleCommand(datagram);
			return true;
		}
		
		
		public void handleCommand(IDataSource dataSource) throws IOException {
			try {
				byte msgPrefix = dataSource.readByte();
				if (msgPrefix == MESSAGE_PREFIX) {
					
					byte cmdKey = dataSource.readByte();
					CommandHandler cmdHandler = cmdHandlers.get(cmdKey);
					
					if (cmdHandler != null) {
						cmdHandler.handle(dataSource);
					} else {
						if (LOG.isLoggable(Level.FINE)) {
							if (isInitialized) {
								LOG.fine("[" + getId() + "] broadcast message received with unknown command: " + cmdKey);
							}
						}
					}
					
				// unknown message
				} else {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + getId() + "] got unknown datagram");
					}
				}	
			} catch (Exception e) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + getId() + "] error occured by receiving broadcast msg: " + e.toString());
				}				
			}
		}
		
		@Override
		public String toString() {
			return "DatagramHandler@" + serviceAddress.toString();
		}
	}
	
	
	private static final class JoinCommand implements CommandHandler {
			
		static final byte COMMAND_KEY = 10;

		private PeerNode peerNode = null;
		
		JoinCommand(PeerNode peerNode) {
			this.peerNode = peerNode;
		}
		
		void send() {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + peerNode.getId() + "] sending join command");
			}

			try {
				UserDatagram packet = new UserDatagram(PACKET_SIZE);
				packet.write(MESSAGE_PREFIX);
				packet.write(COMMAND_KEY);
				packet.write(peerNode.catalog.getViewId());
				peerNode.serviceAddress.writeTo(packet);
					
				peerNode.broadcastEndpoint.send(packet);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + peerNode.getId() + "] error occured by sending broadcast");
				}
			}
		}
			
			
		public void handle(IDataSource dataSource) throws IOException {
			int viewId = dataSource.readInt();
			Address neighborAddress = peerNode.readAndRegisterAddress(dataSource, viewId, "join command");
			if (!neighborAddress.equals(peerNode.serviceAddress)) {				
				new JoinResonseCommand(peerNode).send(neighborAddress);
				
				peerNode.onRemotePeerJoined(neighborAddress);
			}
		}
	}
	
	
	private static final class JoinResonseCommand implements CommandHandler {
		
		static final byte COMMAND_KEY = 11;
			
		private PeerNode peerNode = null;
		
		public JoinResonseCommand(PeerNode peerNode) {
			this.peerNode = peerNode;
		}
		
		void send(Address neighborAddress) {
			try {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + peerNode.getId() + "] sending join response command to " + neighborAddress);
				}

				UserDatagram packet = new UserDatagram(PACKET_SIZE);
				packet.write(MESSAGE_PREFIX);
				packet.write(COMMAND_KEY);
				packet.write(peerNode.catalog.getViewId());
				peerNode.serviceAddress.writeTo(packet);
				neighborAddress.writeTo(packet);
				packet.write(peerNode.getGroupMemberSize());
					
				peerNode.broadcastEndpoint.send(packet);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + peerNode.getId() + "] error occured by sending join response to " + neighborAddress.toString());
				}
			}
		}
			
			
		public void handle(IDataSource dataSource) throws IOException {
			int viewId = dataSource.readInt();
			Address neighborAddress = peerNode.readAndRegisterAddress(dataSource, viewId, "join response command");
			
			Address targetAddress = Address.readFrom(dataSource);
			if (targetAddress.equals(peerNode.serviceAddress)) {
				int groupsize = dataSource.readInt();
				peerNode.registerJoinResponse(neighborAddress, groupsize);
			}
		}
	}
	
	
	private static final class PingCommand implements CommandHandler {
			
		static final byte COMMAND_KEY = 13;
		
		private PeerNode peerNode = null;
		
		public PingCommand(PeerNode peerNode) {
			this.peerNode = peerNode;
		}
		void send() {
			try {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + peerNode.getId() + "] sending ping command");
				}

				UserDatagram packet = new UserDatagram(PACKET_SIZE);
				packet.write(MESSAGE_PREFIX);
				packet.write(COMMAND_KEY);
				packet.write(peerNode.catalog.getViewId());
				peerNode.serviceAddress.writeTo(packet);
					
				peerNode.broadcastEndpoint.send(packet);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + peerNode.getId() + "] error occured by sending ping");
				}
			}
		}
			
			
		public void handle(IDataSource dataSource) throws IOException {
			int viewId = dataSource.readInt();
			Address neighborAddress = peerNode.readAndRegisterAddress(dataSource, viewId, "ping command");
			
			if (LOG.isLoggable(Level.FINE)) {
				if (!neighborAddress.equals(peerNode.serviceAddress)) {
					LOG.fine("[" + peerNode.getId() + "] got ping command from " + neighborAddress);
				}
			}
		}
	}
	

	private static final class RequirePingCommand implements CommandHandler {
		
		static final byte COMMAND_KEY = 14;
		
		private PeerNode peerNode = null;
		
		public RequirePingCommand(PeerNode peerNode) {
			this.peerNode = peerNode;
		}
		void send(Address neighborAddress) {
			try {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.info("[" + peerNode.getId() + "] sending require ping command for "+ neighborAddress);
				}


				UserDatagram packet = new UserDatagram(PACKET_SIZE);
				packet.write(MESSAGE_PREFIX);
				packet.write(COMMAND_KEY);
				packet.write(peerNode.catalog.getViewId());
				peerNode.serviceAddress.writeTo(packet);
				neighborAddress.writeTo(packet);
				packet.write(peerNode.getGroupMemberSize());
					
				peerNode.broadcastEndpoint.send(packet);	
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + peerNode.getId() + "] error occured by sending ping");
				}
			}
		}
			
			
		public void handle(IDataSource dataSource) throws IOException {
			int viewId = dataSource.readInt();
			peerNode.readAndRegisterAddress(dataSource, viewId, "require ping command");
			
			new PingCommand(peerNode).send();
		}
	}

	
	
	private static final class LeaveCommand implements CommandHandler {
		
		static final byte COMMAND_KEY = 15;
			
		private PeerNode peerNode = null;
		
		
		public LeaveCommand(PeerNode peerNode) {
			this.peerNode = peerNode;
		}
		
		void send() {
			try {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + peerNode.getId() + "] sending leave command");
				}

				UserDatagram packet = new UserDatagram(PACKET_SIZE);
				packet.write(MESSAGE_PREFIX);
				packet.write(COMMAND_KEY);
				peerNode.serviceAddress.writeTo(packet);
					
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + peerNode.getId() + "] sending broadcast leave");
				}

				peerNode.broadcastEndpoint.send(packet);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + peerNode.getId() + "] error occured by sending leave");
				}
			}
		}
	
			
		public void handle(IDataSource dataSource) throws IOException {
			Address neighborAddress = peerNode.readAndDeregisterAddress(dataSource, "leave command");
			if (!neighborAddress.equals(peerNode.serviceAddress)) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + peerNode.getId() + "] broadcast leave received from " + neighborAddress.toString());
				}
				peerNode.onRemotePeerLeaved(neighborAddress);
			}
		}
	}	
}
