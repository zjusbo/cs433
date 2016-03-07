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



import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;



/**
 * the member catalog   
 *  
 * @author grro@xsocket.org
 */
final class GroupMemberCatalog {
	
	
	private static final Logger LOG = Logger.getLogger(GroupMemberCatalog.class.getName());
	
	private final Map<String, Entry> members = new HashMap<String, Entry>();
	private final Map<String, Entry> removedMembers = new HashMap<String, Entry>();
		
	private String localAddress = null; 
	
	GroupMemberCatalog(String localAddress) {
		this.localAddress = localAddress;
	}
	
	
	public synchronized void reset() {
		 members.clear();
	}
	
	
		
	public synchronized boolean put(String address, Integer groupHash, Integer groupSize) {
		String hashCode = Integer.toString(address.hashCode());

		Entry entry = new Entry(address, groupHash, groupSize);
		
		if (members.containsKey(address)) {
			members.put(address, entry);
			return false;
				
		} else {
			
			int oldGrouphash = getGroupHashCode();
			members.put(address, entry);
			
			if (removedMembers.containsKey(hashCode)) {
				removedMembers.remove(hashCode);
			}
			
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + localAddress + "] member " + address + " has been added (old ghc " + oldGrouphash + ", new ghc " + getGroupHashCode() + ")");
			}
			return true;
		}
	}

	
	public synchronized boolean remove(String address) {
		int oldGrouphash = getGroupHashCode(); 
		Entry entry = members.remove(address);
		if (entry != null) {
			removedMembers.put(address, entry);

			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + localAddress + "] member " + address + " has been removed (old ghc " + oldGrouphash + ", new ghc " + getGroupHashCode() + ")");
			}			
			return true;
		}
		
		return false;
	}
	
	
	public synchronized boolean deleteRemove(String address) {
		Entry entry = removedMembers.remove(address);
		return (entry != null);
	}

	
	
		
	public int getSize() {
		return members.size() + 1;
	}

	
	public Set<String> getActiveMembers() {
		Set<String> result = new HashSet<String>();
		result.addAll(members.keySet());
		return result;
	}
	
	public String[] getMemberInfo() {
		List<String> result = new ArrayList<String>();
		result.add(localAddress + " (local)");
		
		for (Entry entry : members.values()) {
			result.add(entry.toString());
		}
		
		return result.toArray(new String[result.size()]);
	}

	
	public String[] getRemovedMemberInfo() {
		List<String> result = new ArrayList<String>();
		
		for (Entry entry : removedMembers.values()) {
			result.add(entry.toString());
		}
		
		return result.toArray(new String[result.size()]);
	}

	
	boolean hasMemberSameGroupHashcode() {
		boolean result = true;
		int hashcode = getGroupHashCode();
		
		for (Entry entry : members.values()) {
			if (entry.groupHash != hashcode) {
				result = false;
			}
		}
		
		return result;
	}


	public List<String> getActiveMembersWhereLastActivityOlderThan(long time) {
		List<String> result = new ArrayList<String>();

		for (Entry entry : members.values()) {
			if (entry.creationTime < time) {
				result.add(entry.address);
			}
		}
		
		return result;
	}

	
	public List<String> getRemovedMembersWhereLastActivityOlderThan(long time) {
		List<String> result = new ArrayList<String>();

		for (Entry entry : removedMembers.values()) {
			if (entry.creationTime < time) {
				result.add(entry.address);
			}
		}
		
		return result;
	}

	
	
/*	
	public void cleanRemovedPeers(long timeout) {
		long currentTime = System.currentTimeMillis();
	
		for (Peer peer : getRemovedPeers()) {
			if (currentTime > (peer.getLastConnect() + timeout)) {
				removedNeighbors.remove(peer);
				if (LOG.isLoggable(Level.INFO)) {
					LOG.info("deactive peer " + peer.getGUID().toString() + " has been removed for removedList (timeout "  + DataConverter.toFormatedDuration(timeout) + " reached)");
				}
			}
		}
	}
	
	public synchronized boolean containsActive(IEndpoint neighborsAddress) {
		return activeNeighbors.containsKey(neighborsAddress);
	}

	public synchronized Set<GUID> getGUIDsWhereLastAccessBefore(long time) {
		Set<GUID> result = new HashSet<GUID>();
		
		for (Peer entry : activeNeighbors.values()) {
			if (entry.lastConnect < time) {
				result.add(entry.nodeGUID);
			}
		}
			
		return result;
	}
*/
	
	public synchronized int getGroupHashCode() {
		int result = localAddress.hashCode();
		for (Entry entry : members.values()) {
			result = result ^ entry.getAddress().hashCode();
		}
		return result;
	}

	
	
	private static final class Entry {
		private String address = null;
		private Integer groupHash = null;
		private Integer groupSize = null;
		private long creationTime = 0;
			
		Entry(String address, Integer groupHash, Integer groupSize) {
			this.address = address;
			this.groupHash  = groupHash;
			this.groupSize = groupSize;
			creationTime = System.currentTimeMillis();			
		}
						
		public String getAddress() {
			return address;
		}

		public int getGroupHash() {
			return groupHash;
		}
		
		public int getGroupSize() {
			return groupSize;
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append(address 
					  + " lastData=" + DataConverter.toFormatedDuration(System.currentTimeMillis() - creationTime)
					  + ", grouphash=" + groupHash + ", groupsize=" + groupSize);
			
			return sb.toString();
		}


		public long creationTime() {
			return creationTime;
		}
	}

}