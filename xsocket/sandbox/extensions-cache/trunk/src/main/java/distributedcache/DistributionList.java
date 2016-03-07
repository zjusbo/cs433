package distributedcache;


import java.util.HashMap;
import java.util.Map;

import org.xsocket.group.Address;

final class DistributionList {

	private static final int DEFAULT_SLOT_COUNT = 1000;
	
	private int slotCount= DEFAULT_SLOT_COUNT;
	
	private final Map<Integer, Slot> slots = new HashMap<Integer, Slot>();
	
	public synchronized boolean addAddress(Address address) {
		if (slots.isEmpty()) {
			for (int i = 0; i < slotCount; i++) {
				slots.put(i, new Slot(address, address));
			}
			return true;
			
		} else {
			Map<Address,Integer> primaries = new HashMap<Address, Integer>();
			Map<Address,Integer> secondaries = new HashMap<Address, Integer>();
			for (Slot slot : slots.values()) {
				if (primaries.containsKey(slot.getPrimary())) {
					int i = primaries.get(slot.getPrimary());
					i++;
					primaries.put(slot.getPrimary(), i);
				} else {
					primaries.put(slot.getPrimary(), 1);
				}
			}
			
			for (Slot slot : slots.values()) {
				if (secondaries.containsKey(slot.getSecondary())) {
					int i = secondaries.get(slot.getSecondary());
					i++;
					secondaries.put(slot.getSecondary(), i);
				} else {
					secondaries.put(slot.getSecondary(), 1);
				}
			}
			
			if (primaries.containsKey(address)) {
				return false;
			}
			
			
			
			
			return true;
		}
	}

	
	
	public synchronized Address getPrimary(Object key) {
		Slot slot = slots.get(computeSlotNumber(key));
		
		if (slot == null) {
			return null;
		} else {
			return slot.primary;
		}
	}

	
	public synchronized Address getSecondaryPrimary(Object key) {
		Slot slot = slots.get(computeSlotNumber(key));
		
		if (slot == null) {
			return null;
		} else {
			return slot.secondary;
		}
	}
	
	
	private int computeSlotNumber(Object key) {
		int hashCode = key.hashCode();
		if (hashCode < 0) {
			hashCode *= -1;
		}
		
		return hashCode % slotCount; 
	}
	
	
	private static final class Slot {
		private Address primary = null;
		private Address secondary = null;
		
		Slot(Address primary, Address secondary) {
			this.primary = primary;
			this.secondary = secondary;
		}
		
		public Address getPrimary() {
			return primary;
		}
		
		public Address getSecondary() {
			return secondary;
		}
	}
}
