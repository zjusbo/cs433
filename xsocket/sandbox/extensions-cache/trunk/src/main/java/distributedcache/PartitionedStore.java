package distributedcache;



import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import org.xsocket.group.Address;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.Element;
import net.sf.ehcache.Status;
import net.sf.ehcache.store.Store;


public class PartitionedStore implements Store {
		

	private Map<Integer, Address> storeAddresses = new HashMap<Integer, Address>();
	private Address groupAddress = null;
	
	public PartitionedStore(InetAddress broadcastAddress, int broadcastPort) throws IOException {
		groupAddress = new Address(broadcastAddress, broadcastPort);
	}
	
	
	public boolean containsKey(Object obj) {
		
		// TODO Auto-generated method stub
		return false;
	}
	
	public Element get(Object key) {
		try {
			int keyHashCode = computeHash(key);
			Address address = getServiceAddress(keyHashCode);
	
			StoreService.IGetResult result = StoreService.callGet(address, keyHashCode, key.toString());
			storeAddresses.put(keyHashCode, result.getServiceAddress());
			
			if (result.getValue() != null) {
				return new Element(key, result.getValue());
			} else {
				return null;
			}
		} catch (IOException ioe) {
			throw new CacheException(ioe);
		}
	}

	
	public void put(Element element) throws CacheException {
		try {
			int keyHashCode = computeHash(element.getObjectKey());
			Address address = getServiceAddress(keyHashCode);
	
			Address addr = StoreService.callPut(address, keyHashCode, element.getObjectKey().toString(), (Serializable) element.getObjectValue());
			storeAddresses.put(keyHashCode, addr);
			
		} catch (IOException ioe) {
			throw new CacheException(ioe);
		}
	}
	
	public boolean backedUp() {
		// TODO Auto-generated method stub
		return false;
	}
	
	public Object[] getKeyArray() {
		// TODO Auto-generated method stub
		return null;
	}
	
	public void expireElements() {
		// TODO Auto-generated method stub
		
	}
	
	public void flush() throws IOException {
		// TODO Auto-generated method stub
		
	}
	
	
	public Element remove(Object key) {
		try {
			int keyHashCode = computeHash(key);
			Address address = getServiceAddress(keyHashCode);
	
			StoreService.IRemoveResult result = StoreService.callRemove(address, keyHashCode, key.toString());
			storeAddresses.put(keyHashCode, result.getServiceAddress());
			
			if (result.getValue() != null) {
				return new Element(key, result.getValue());
			} else {
				return null;
			}
		} catch (IOException ioe) {
			throw new CacheException(ioe);
		}
	}
	
	public void removeAll() throws CacheException {
		// TODO Auto-generated method stub
		
	}
	
	
	private Address getServiceAddress(int keyHashCode) throws IOException {
		Address address = storeAddresses.get(keyHashCode);
		
		if (address == null) {
			address = StoreServiceRegistry.getInstance(groupAddress).getStoreServiceAddresses().iterator().next();
		}

		return address;
	}

	
	public Element getQuiet(Object arg0) {
		// TODO Auto-generated method stub
		return null;
	}
	
	
	public int getSize() {
		// TODO Auto-generated method stub
		return 0;
	}
	
	public Status getStatus() {
		// TODO Auto-generated method stub
		return null;
	}
	
	
	
	
	public void dispose() {
		// TODO Auto-generated method stub
		
	}
	
	
	
	void setCompress(boolean b) {
		
	}
	
	void setCompressThreshold(int thresholdBytes) {
		
	}
	
	
	private int computeHash(Object obj) {
		return (obj.hashCode() % 10); 
	}
}
