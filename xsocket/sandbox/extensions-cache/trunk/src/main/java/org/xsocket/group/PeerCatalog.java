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



import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;
import org.xsocket.datagram.IEndpoint;



/**
 *   
 *  
 * @author grro@xsocket.org
 */
final class PeerCatalog {
	
	
	private static final Logger LOG = Logger.getLogger(PeerCatalog.class.getName());
	
	private final HashMap<Address, Peer> activeNeighbors = new HashMap<Address, Peer>();
	private final Set<Peer> removedNeighbors = new HashSet<Peer>();
		
	private Address ownerAddress = null;
	
	PeerCatalog(Address ownerAddress) {
		this.ownerAddress = ownerAddress;
	}
	
	
	public synchronized void reset() {
		 activeNeighbors.clear();
		 removedNeighbors.clear();
	}
	
	
	
		
	public synchronized boolean add(Address neighborsAddress) {
		if (!neighborsAddress.equals(this.ownerAddress)) {

			
			
			Peer neighbor = null;
			if (activeNeighbors.containsKey(neighborsAddress)) {
				activeNeighbors.get(neighborsAddress).setLastConnect(System.currentTimeMillis());
				
			} else {
				neighbor = new Peer(neighborsAddress);
				activeNeighbors.put(neighborsAddress, neighbor);

				if (removedNeighbors.contains(neighbor)) {
					removedNeighbors.remove(neighbor);
				}
				
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("neighbor " + neighborsAddress.toString() + " has been added");
				}
				return true;
			}
		}
		
		return false;
	}
	
	
	
	public synchronized boolean remove(Address neighborsAddress) {
		Peer neighbor = activeNeighbors.remove(neighborsAddress);
		if (neighbor != null) {
			removedNeighbors.add(neighbor);

			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("neighbor " + neighborsAddress.toString() + " has been removed");
			}			
			return true;
		}
		
		return false;
	}
	
	
	public synchronized Peer[] getNeighborPeers() {
		Collection<Peer> neighbors = activeNeighbors.values();
		return neighbors.toArray(new Peer[neighbors.size()]);
	}
	
		
	public synchronized Peer[] getActivePeers() {
		Collection<Peer> neighbors = activeNeighbors.values();
		Set<Peer> peers = new HashSet<Peer>();
		peers.addAll(neighbors);
		peers.add(new Peer(ownerAddress));
		return peers.toArray(new Peer[peers.size()]);
	}
	
	public synchronized Peer[] getRemovedPeers() {
		return removedNeighbors.toArray(new Peer[removedNeighbors.size()]);
	}

	public void cleanRemovedPeers(long timeout) {
		long currentTime = System.currentTimeMillis();
	
		for (Peer peer : getRemovedPeers()) {
			if (currentTime > (peer.getLastConnect() + timeout)) {
				removedNeighbors.remove(peer);
				if (LOG.isLoggable(Level.INFO)) {
					LOG.info("deactive peer " + peer.getAddress() + " has been removed for removedList (timeout "  + DataConverter.toFormatedDuration(timeout) + " reached)");
				}
			}
		}
	}
	
	public synchronized boolean containsActive(IEndpoint neighborsAddress) {
		return activeNeighbors.containsKey(neighborsAddress);
	}

	public synchronized Set<Address> getAdressesWhereLastAccessBefore(long time) {
		Set<Address> result = new HashSet<Address>();
		
		for (Peer entry : activeNeighbors.values()) {
			if (entry.lastConnect < time) {
				result.add(entry.nodeAddress);
			}
		}
			
		return result;
	}

	
	public synchronized int getViewId() {
		int result = ownerAddress.hashCode();
		for (Peer entry : activeNeighbors.values()) {
			result = result ^ entry.nodeAddress.hashCode();
		}
		return result;
	}

	
	static final class Peer {
		private Address nodeAddress = null;
		private long lastConnect = -1;
			
		Peer(Address nodeAddress) {
			this.nodeAddress = nodeAddress;
			this.lastConnect = System.currentTimeMillis();			
		}
			
			
		Address getAddress() {
			return nodeAddress;
		}
			
		@Override
		public int hashCode() {
			return nodeAddress.hashCode();
		}
			
		@Override
		public boolean equals(Object other) {
			if (other instanceof Peer) {
				return ((Peer) other).nodeAddress.equals(this.nodeAddress);
			} else {
				return false;
			}
		}
			
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("address=" + nodeAddress.toString() 
					+ ", lastConnect=" + DataConverter.toFormatedDuration(System.currentTimeMillis() - lastConnect));
			
			return sb.toString();
		}



		public void setLastConnect(long lastConnect) {
			this.lastConnect = lastConnect;
		}
		
		public long getLastConnect() {
			return lastConnect;
		}
	}
}