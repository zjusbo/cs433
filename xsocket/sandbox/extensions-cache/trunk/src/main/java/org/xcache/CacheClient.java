package org.xcache;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.jsr107cache.Cache;
import net.sf.jsr107cache.CacheEntry;
import net.sf.jsr107cache.CacheException;
import net.sf.jsr107cache.CacheListener;
import net.sf.jsr107cache.CacheStatistics;

import org.xsocket.stream.IBlockingConnection;



final class CacheClient implements Cache {
	
	private static final Logger LOG = Logger.getLogger(CacheClient.class.getName());

	private ICacheServerLocator locator = null;
	
	
	public CacheClient(ICacheServerLocator locator) {
		this.locator = locator;
	}
	
	ICacheServerLocator getLocator() {
		return locator;
	}
	
	public Object put(Object key, Object value) {
		
		Object result = null;
		
		Item keyItem = new Item(key);
		Item valueItem = new Item(value);
		
		int segment = locator.computeSegement(key);
		
		IBlockingConnection connection = null;
		try {
			Address address = locator.getServiceAddress(segment);
			connection = ConnectionPool.getInstance().getConnection(address);
			
			connection.markWritePosition();
			connection.write((int) 0); // emtpy length field
			int length = 0;
			
			length += connection.write(CacheServer.CMD_PUT);
			length += connection.write(segment);
			length += keyItem.writeTo(connection);
			length += valueItem.writeTo(connection);
			
			connection.resetToWriteMark();
			connection.write(length);
			
			connection.flush();
			
			connection.readInt();  // first position is length
			byte response = connection.readByte();
			if (response == CacheServer.RESULT_OK_WITHOUT_RETURNVALUE) {
				result = null;
				
			} else if (response == CacheServer.RESULT_OK_WITH_RETURNVALUE) {
				Item oldValue = Item.readFrom(connection);
				result = oldValue.getValue();
				
			} else {
				throw new RuntimeException("unexpected result");
			}
			
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("item for key " + key + " (segment " + segment + ") has been inserted (cache server " + connection.getRemoteAddress() + "/" + connection.getRemotePort() + ")");
			}
			connection.close();
			
		} catch (Exception e) {
			if (connection != null) {
				try {
					ConnectionPool.getInstance().destroyConnection(connection);
				} catch (IOException ignore) { }; 
			}
			throw new RuntimeException("error occured by put " + key +  " into the cache. Reason: " + e.toString());
		}		
		return result;
	}
	
	public Object get(Object key) {
	Object result = null;
		
		Item keyItem = new Item(key);
		
		int segment = locator.computeSegement(key);
		
		IBlockingConnection connection = null;
		try {
			Address address = locator.getServiceAddress(segment);
			connection = ConnectionPool.getInstance().getConnection(address);

			connection.markWritePosition();
			connection.write((int) 0); // emtpy length field
			int length = 0;
			
			length += connection.write(CacheServer.CMD_GET);
			
			length += connection.write(segment);
			length += keyItem.writeTo(connection);
			
			connection.resetToWriteMark();
			connection.write(length);
			
			connection.flush();
			
			connection.readInt();  // first position is length
			byte response = connection.readByte();
			
			if (response == CacheServer.RESULT_OK_WITHOUT_RETURNVALUE) {
				result = null;

			} else if (response == CacheServer.RESULT_OK_WITH_RETURNVALUE) {
				Item value = Item.readFrom(connection);
				result = value.getValue();
				
			} else {
				throw new RuntimeException("unexpected result");
			}
			
			if (LOG.isLoggable(Level.FINE)) {
				if (result != null) {
					LOG.fine("item for key " + key + " (segment " + segment + ") has retrieved (cache server " + connection.getRemoteAddress() + "/" + connection.getRemotePort() + ")");
				} else {
					LOG.fine("no item for key " + key + " (segment " + segment + ") found on server (cache server " + connection.getRemoteAddress() + "/" + connection.getRemotePort() + ")");
				}
			}
			
			connection.close();
			
		} catch (Exception e) {
			if (connection != null) {
				try {
					ConnectionPool.getInstance().destroyConnection(connection);
				} catch (IOException ignore) { }; 
			}
			throw new RuntimeException("error occured by put " + key +  " into the cache. Reason: " + e.toString());
		}		
		return result;
	}
	
	

	public boolean containsKey(Object key) {
		// TODO Auto-generated method stub
		return false;
	}
	
	public boolean containsValue(Object value) {
		// TODO Auto-generated method stub
		return false;
	}
	
	public Set entrySet() {
		// TODO Auto-generated method stub
		return null;
	}
	
	
	public Map getAll(Collection keys) throws CacheException {
		// TODO Auto-generated method stub
		return null;
	}
	
	public CacheEntry getCacheEntry(Object key) {
		// TODO Auto-generated method stub
		return null;
	}
	
	public CacheStatistics getCacheStatistics() {
		// TODO Auto-generated method stub
		return null;
	}
	
	public boolean isEmpty() {
		// TODO Auto-generated method stub
		return false;
	}
	
	
	public Set keySet() {
		// TODO Auto-generated method stub
		return null;
	}
	
	public void load(Object key) throws CacheException {
		// TODO Auto-generated method stub
		
	}
	
	public void loadAll(Collection keys) throws CacheException {
		// TODO Auto-generated method stub
		
	}
	
	public Object peek(Object key) {
		// TODO Auto-generated method stub
		return null;
	}
	
	public void putAll(Map t) {
		// TODO Auto-generated method stub
		
	}
	
	public Object remove(Object key) {
		// TODO Auto-generated method stub
		return null;
	}
	
	public int size() {
		// TODO Auto-generated method stub
		return 0;
	}
	
	
	
	
	public void evict() {
		// TODO Auto-generated method stub
		
	}
	
	
	public void clear() {
		// TODO Auto-generated method stub
		
	}

	public Collection values() {
		// TODO Auto-generated method stub
		return null;
	}
	
	
	
	
	public void addListener(CacheListener listener) {
		// TODO Auto-generated method stub
		
	}
	
	public void removeListener(CacheListener listener) {
		// TODO Auto-generated method stub
		
	}
}
