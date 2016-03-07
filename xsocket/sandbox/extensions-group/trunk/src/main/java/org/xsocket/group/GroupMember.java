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
package org.xsocket.group;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;
import org.xsocket.IDataSource;
import org.xsocket.datagram.IConnectedEndpoint;
import org.xsocket.datagram.IDatagramHandler;
import org.xsocket.datagram.IEndpoint;
import org.xsocket.datagram.MulticastEndpoint;
import org.xsocket.datagram.UserDatagram;



 
/**
 *   
 *  
 * @author grro@xsocket.org
 */
public final class GroupMember implements IGroupMember {
	
	private static final Logger LOG = Logger.getLogger(GroupMember.class.getName());

	
	// command keys
	private static final Byte JOIN_CMD_KEY = 01;
	private static final Byte JOIN_RESPONSE_CMD_KEY = 03;
	private static final Byte HEARTBEAT_CMD_KEY = 05;
	private static final Byte LEAVE_CMD_KEY = 15;
	
	// packet size 
	private static final int  PACKET_SIZE = 128;
	
	
	// time out check
	private static final long DEFAULT_REMOVED_MEMBERS_CLEAN_PERIOD = 30L * 60L * 1000L;
	private static final long DEFAULT_HEARTBEAT_PERIOD_UNSTABLE = 3L * 1000L;
	private static final long DEFAULT_HEARTBEAT_PERIOD_STABLE = 60L * 1000L;
	private static final Timer TIMER = new Timer("xGroupTimer", true);
	static {
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
			}
		};
		TIMER.schedule(task, 0);
	}
	

	// state
	public enum State { INITIAL, CONSISTENT, INCONSISTENT };
	private State state = State.INITIAL;

	
	private boolean isOpen = false;

	
	// commands
	private final Map<Byte,CommandHandler> cmdHandlers = new HashMap<Byte, CommandHandler>();
	

	// identification
	private String serviceAddress = null;

	// heartbeat 
	private long idleTimeoutMillis = 0;
	private long pulseStableMillis = DEFAULT_HEARTBEAT_PERIOD_STABLE;
	private long pulseUnstableMillis = DEFAULT_HEARTBEAT_PERIOD_UNSTABLE;
	private TimerTask heartbeatTask = null;
	

	// cleaner
	private TimerTask timeoutCheckTask = null;

	
	// broadcast endpoint 
	private IConnectedEndpoint broadcastEndpoint = null;

	
	// neighbor catalog
	private GroupMemberCatalog catalog = null;
	
	private final ArrayList<IGroupMemberListener> listeners = new ArrayList<IGroupMemberListener>();

	
		
	private Integer initialGroupSize = null;
	
	
	
	@SuppressWarnings("unchecked")
	public GroupMember(InetAddress groupAddress, int groupPort, int timeToLive, String serviceAddress) throws IOException {
		this(groupAddress, groupPort, timeToLive, serviceAddress, Executors.newFixedThreadPool(200), null);
	}
	
	

	@SuppressWarnings("unchecked")
	public GroupMember(InetAddress groupAddress, int groupPort, int timeToLive, String serviceAddress, Executor workerpool, IGroupMemberListener listener) throws IOException {
		this.serviceAddress = serviceAddress;
		if (listener != null) {
			addListener(listener);
		}
		
		Map<String ,Object> options = new HashMap<String, Object>();
		options.put(IEndpoint.IP_MULTICAST_TTL, timeToLive);
		broadcastEndpoint = new MulticastEndpoint(groupAddress, groupPort, PACKET_SIZE, new DatagramHandler(), workerpool);
	
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
		
		
		for (IGroupMemberListener l : (ArrayList<IGroupMemberListener>) listeners.clone()) {
			l.onInit();
		}
		
		init((2 * 5000) + (5000 / 10));
	}

	
	final void init(long bootstrapTimeout) throws IOException {
		
		// create node`s member catalog
		catalog = new GroupMemberCatalog(serviceAddress);

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + getId() + "] bootstrapping");
		}
		
		isOpen = true;

		
		// bootstrapping
		bootstrap(bootstrapTimeout);
		
		// add the command handlers
		cmdHandlers.put(JOIN_CMD_KEY, new JoinCommandHandler(this));
		cmdHandlers.put(HEARTBEAT_CMD_KEY, new HeartbeatCommandHandler(this));
		cmdHandlers.put(LEAVE_CMD_KEY, new LeaveCommandHandler(this));		
		
				
		// start heartbeating
		activateHeartbeatTask();
		
		// start timeout checker 
		activateTimeoutCheckTask();
	}

	
	
	private void bootstrap(long discoveryTimeout) {
		long start = System.currentTimeMillis();
		
		
		//	add a temporally bootstrapping join response handler
		JoinResponseCommandHandler responseCommandHandler = new JoinResponseCommandHandler(this);
		cmdHandlers.put(JOIN_RESPONSE_CMD_KEY, responseCommandHandler);
		  
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + getId() + "] sending join");
		}
		new JoinCommandHandler(this).send();
		
		
		// wait until all peers has answered the join request
		synchronized (responseCommandHandler) {
			while ((start + discoveryTimeout) > System.currentTimeMillis()) {
					
				// all peers answered?
				if (responseCommandHandler.isBootstrapFinished()) { 
					break;
					
				// .. no -> wait
				} else {
					long waitTime = discoveryTimeout - (System.currentTimeMillis() - start);
					if (waitTime > 0) {
						try {
							responseCommandHandler.wait(discoveryTimeout);
						} catch (InterruptedException ignore) { }
					}
				}
			}
		}
		
		initialGroupSize = getGroupSize();
		
		if (responseCommandHandler.isBootstrapFinished()) {
			LOG.info("[" + getId() + "] bootstrapping succeeded. " + (initialGroupSize - 1) + " peer(s) found (init time " + DataConverter.toFormatedDuration(System.currentTimeMillis() - start) + ")");

			enterStableState(); 
			
		} else {
			if (initialGroupSize == 1) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.info("[" + getId() + "] bootstrapping succceded. no peer found (current ghc " + catalog.getGroupHashCode() + ", init time " + DataConverter.toFormatedDuration(System.currentTimeMillis() - start) + ")");
				} else {
					LOG.info("[" + getId() + "] bootstrapping succceded. no peer found (init time " + DataConverter.toFormatedDuration(System.currentTimeMillis() - start) + ")");
				}

				enterStableState();
				
			} else {
				String msg = " bootstrapping failed.  not all peers has been found within bootstrapping: " + responseCommandHandler.getRespondedSize() + " responded of " + responseCommandHandler.getReportedSize() + " reported";
				if (LOG.isLoggable(Level.FINE)) {
					LOG.info("[" + getId() + "] " + msg + " (ghc " + catalog.getGroupHashCode() + ", init time " + DataConverter.toFormatedDuration(System.currentTimeMillis() - start) + ")");
				} else {
					LOG.info("[" + getId() + "] " + msg + " (init time " + DataConverter.toFormatedDuration(System.currentTimeMillis() - start) + ")");
				}

				enterUnstableState(msg);
			}
		}
		
		cmdHandlers.remove(JOIN_RESPONSE_CMD_KEY);

	}
	
	
	/**
	 * returns the initial groupsize<br>
	 * <b>for debuging purposes only</b>
	 * 
	 * @return the initial groupsize
	 */
	int getInitialGroupSize() {
		return initialGroupSize;
	}
	
	
	public boolean isGroupConsistent() {
		return (state == State.CONSISTENT);
	}
	
	

	

	
	public void addListener(IGroupMemberListener listener) {
		listeners.add(listener);
	}

	public boolean removeListener(IGroupMemberListener listener) {
		boolean result = listeners.remove(listener);		
		return result;
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public String getServiceAddress() {
		return serviceAddress;
	}
	
	
	final long getIdleTimeoutMillis() {
		return idleTimeoutMillis;
	}


	final long getHeartbeatPeriodStableMillis() {
		return pulseStableMillis;
	}

	void setHeartbeatPeriodStableMillis(long period) {
		this.pulseStableMillis = period;
		activateHeartbeatTask();
	}
	
	final long getHeartbeatPeriodUnstableMillis() {
		return pulseUnstableMillis;
	}

	void setHeartbeatPeriodUnstableMillis(long period) {
		this.pulseUnstableMillis = period;
		activateHeartbeatTask();
	}

	
	void activateTimeoutCheckTask() {
		assert (isOpen);
		
		if (timeoutCheckTask != null) {
			timeoutCheckTask.cancel();
		}

		timeoutCheckTask = new TimerTask() {
			@Override
			public void run() {
				
				// check active list
				List<String> activeMembers = catalog.getActiveMembersWhereLastActivityOlderThan(System.currentTimeMillis() - (pulseStableMillis + (pulseStableMillis / 2)));
				for (String address : activeMembers) {
					catalog.remove(address);
				}
				
				// check old members
				List<String> removedMembers = catalog.getRemovedMembersWhereLastActivityOlderThan(System.currentTimeMillis() - DEFAULT_REMOVED_MEMBERS_CLEAN_PERIOD);
				for (String address : removedMembers) {
					catalog.deleteRemove(address);
				}
			}
		};
		TIMER.schedule(timeoutCheckTask, (pulseUnstableMillis / 3), (pulseUnstableMillis / 3));
	}
		
	


	private void activateHeartbeatTask() {
		assert (isOpen);
		
		if (heartbeatTask != null) {
			heartbeatTask.cancel();
		}
		
		if (pulseStableMillis < Long.MAX_VALUE) {
			heartbeatTask = new TimerTask() {
				@Override
				public void run() {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + getId() + "] sending periodical heartbeat (" + state + ", ghc=" + getGroupHashCode() + ")");
					}
					new HeartbeatCommandHandler(GroupMember.this).send();
				}
			};
			
			if (state == State.CONSISTENT) {
				TIMER.schedule(heartbeatTask, 0, pulseStableMillis);
			} else {
				TIMER.schedule(heartbeatTask, 0, pulseUnstableMillis);
			}
		} 
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public void close() throws IOException {

		if (isOpen()) {
			try {
				
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + getId() + "] sending leave");
				}
				new LeaveCommandHandler(this).send();

				
				// deactivate timeout check
				if (timeoutCheckTask != null) {
					timeoutCheckTask.cancel();
				}
				
				// deactivate heartbeat
				if (heartbeatTask != null) {
					heartbeatTask.cancel();
				}

				
				try {
					Thread.sleep(200);
				} catch (InterruptedException ignore) { }
				
				// close endpoints
				broadcastEndpoint.close();

				for (IGroupMemberListener listener : (ArrayList<IGroupMemberListener>) listeners.clone()) {
					listener.onDestroy();
				}
				
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

	
	final int getGroupSize() {
		return catalog.getSize();
	}

	
	Set<String> getActiveMembers() {
		return catalog.getActiveMembers();
	}


	String[] getMemberInfo() {
		return catalog.getMemberInfo();
	}

	
	String[] getRemovedMemberInfo() {
		return catalog.getRemovedMemberInfo();
	}

	
	final int getGroupHashCode() {
		return catalog.getGroupHashCode();
	}
	

	public String getId() {
		return serviceAddress;
	}
	

	public final boolean isOpen() {
		return broadcastEndpoint.isOpen();
	}
	
	
	
	
	private void handleHeartbeat(String peerAddress) {
		if (!serviceAddress.equals(peerAddress)) {
			catalog.put(peerAddress, null, null);
		}
	}
	
	
	private void handleHeartbeat(String peerAddress, int groupHashcode, int groupSize) {
		if (!serviceAddress.equals(peerAddress)) {
			
			catalog.put(peerAddress, groupHashcode, groupSize);
			
			if(getGroupHashCode() == groupHashcode) {
				enterStableState();
				
			} else {
				enterUnstableState("heartbeat received from " + peerAddress + " with ghc " + groupHashcode + ". current ghc is " + getGroupHashCode());
			}
		}
	}


	private void handlePeerLeave(String peerAddress) {
		if (!serviceAddress.equals(peerAddress)) {
			catalog.remove(peerAddress);
		}
	}


	
	@SuppressWarnings("unchecked")
	protected void enterStableState() {
		if (state != State.CONSISTENT) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + getId() + "] enter consistent state (ghc=" + getGroupHashCode() + ")");
			}
			state = State.CONSISTENT;
			
			// reavticvate hartbeat task to change current pulse
			activateHeartbeatTask();
			
			for (IGroupMemberListener listener : (ArrayList<IGroupMemberListener>) listeners.clone()) {
				listener.onEnterConsistentState();
			}
		}
	}

	
	@SuppressWarnings("unchecked")
	protected void enterUnstableState(String reason) {
		if (state != State.INCONSISTENT) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + getId() + "] enter inconsistent state (ghc=" + getGroupHashCode() + ") Reason: " + reason);
			}
			state = State.INCONSISTENT;
			
			// reavticvate hartbeat task to change current pulse
			activateHeartbeatTask();
			
			
			for (IGroupMemberListener listener : (ArrayList<IGroupMemberListener>) listeners.clone()) {
				listener.onEnterInconsistentState();
			}

		}
	}


	
	
	@Override
	public String toString() {
		return getId() + " hc=" + catalog.getGroupHashCode() + " groupSize=" + getGroupSize();
	}

	
	
	private interface CommandHandler {
		public void handle(MulticastMessageHeader msgHeader, IDataSource body) throws IOException;
	}
	
	
	private final class DatagramHandler implements IDatagramHandler {
		
		public boolean onDatagram(IEndpoint localEndpoint) throws IOException {
			UserDatagram datagram = localEndpoint.receive();
			handleCommand(datagram);
			return true;
		}
		
		
		public void handleCommand(IDataSource dataSource) throws IOException {
			try {
				MulticastMessageHeader msgHeader = MulticastMessageHeader.readFrom(dataSource);
				try {
					CommandHandler cmdHandler = cmdHandlers.get(msgHeader.getCommand());
						
					if (cmdHandler != null) {
						cmdHandler.handle(msgHeader, dataSource);
					} else {
						if (LOG.isLoggable(Level.FINER)) {
							LOG.finer("[" + getId() + "] broadcast message received with unknown command: " + msgHeader.getCommand());
						}
					}
				} catch (Exception e) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + getId() + "] error occured by receiving handling " + msgHeader + " " + e.toString());
					}				
				}
					
			} catch (Exception e) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + getId() + "] protocol error occured: " + e.toString());
				}				
			}
		}
		
		@Override
		public String toString() {
			return "DatagramHandler@" + serviceAddress;
		}
	}
	
	
	private static final class JoinCommandHandler implements CommandHandler {
			
		private GroupMember node = null;
		
		JoinCommandHandler(GroupMember node) {
			this.node = node;
		}
		
		
		/**
		 * send a join message. All peers answer immediately with a heartbeat message
		 */
		void send() {
			try {
				UserDatagram packet = new UserDatagram(PACKET_SIZE);
				new MulticastMessageHeader(node.serviceAddress, JOIN_CMD_KEY).writeTo(packet);
				
				node.broadcastEndpoint.send(packet);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + node.getId() + "] error occured by sending join");
				}
			}
		}
			
			
		public void handle(MulticastMessageHeader msgHeader, IDataSource body) throws IOException {
			if (!node.serviceAddress.equals(msgHeader.getSourceAddress())) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + node.getId() + "] sending join response");
				}
				
				new JoinResponseCommandHandler(node).send();
				node.handleHeartbeat(msgHeader.getSourceAddress());
			}
		}
	}
	
	

	
	private static class HeartbeatCommandHandler implements CommandHandler {
			
		private GroupMember node = null;
		
		public HeartbeatCommandHandler(GroupMember node) {
			this.node = node;
		}
		
		void send() {
			try {
				UserDatagram packet = new UserDatagram(PACKET_SIZE);
				new MulticastMessageHeader(node.serviceAddress, HEARTBEAT_CMD_KEY).writeTo(packet);
				
				packet.write(node.getGroupSize());
				packet.write(node.getGroupHashCode());
 					
				node.broadcastEndpoint.send(packet);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + node.getId() + "] error occured by sending heartbeat");
				}
			}
		}
			

		public void handle(MulticastMessageHeader msgHeader, IDataSource body) throws IOException {
			int peerGroupSize = body.readInt();
			int peerGroupHashcode = body.readInt();
			
			node.handleHeartbeat(msgHeader.getSourceAddress(), peerGroupHashcode, peerGroupSize);		
		}
	}

	
	private static final class JoinResponseCommandHandler implements CommandHandler {
		
		private final  Set<String> responded = new HashSet<String>();
		private Integer reportedGroupSize = null; 
		
		private GroupMember node = null;
		
		public JoinResponseCommandHandler(GroupMember node) {
			this.node = node;
		}
		

		void send() {
			try {
				UserDatagram packet = new UserDatagram(PACKET_SIZE);
				
				new MulticastMessageHeader(node.serviceAddress, JOIN_RESPONSE_CMD_KEY).writeTo(packet);
				packet.write(node.getGroupSize());
 					
				node.broadcastEndpoint.send(packet);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + node.getId() + "] error occured by sending join response");
				}
			}
		}


		
		public void handle(MulticastMessageHeader msgHeader, IDataSource body) throws IOException {
			int peerGroupSize = body.readInt();
			
			if (!node.serviceAddress.equals(msgHeader.getSourceAddress())) {
				node.handleHeartbeat(msgHeader.getSourceAddress());
				
				if (reportedGroupSize == null) {
					reportedGroupSize = peerGroupSize;
				} else {
					if (reportedGroupSize != peerGroupSize) {
						 node.enterUnstableState("BootstrapPingCommandHandler: different groupsize has been reported (" + reportedGroupSize + " & " + peerGroupSize) ;
					}
				}
				
				responded.add(msgHeader.getSourceAddress());
								
				synchronized (this) {
					if (isBootstrapFinished()) {
						notify();
					}
				}				
			}
		}

		int getRespondedSize() {
			return responded.size();
		}
		
		int getReportedSize() {
			return reportedGroupSize;
		}
		

		boolean isBootstrapFinished() {
			if (reportedGroupSize == null) {
				return false;
			} else {
				return responded.size() == reportedGroupSize;
			}
		}
	}


	private static final class LeaveCommandHandler implements CommandHandler {
		
		private GroupMember node = null;
		
		
		public LeaveCommandHandler(GroupMember node) {
			this.node = node;
		}
		
		void send() {
			try {
				UserDatagram packet = new UserDatagram(PACKET_SIZE);
				
				new MulticastMessageHeader(node.serviceAddress, LEAVE_CMD_KEY).writeTo(packet);

				node.broadcastEndpoint.send(packet);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + node.getId() + "] error occured by sending leave");
				}
			}
		}
	
			
		public void handle(MulticastMessageHeader msgHeader, IDataSource body) throws IOException {
			node.handlePeerLeave(msgHeader.getSourceAddress());
		}
	}
}
