package org.xcache;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import org.xsocket.stream.IBlockingConnection;

import net.sf.jsr107cache.Cache;




public final class StaticCacheClientManager implements ICacheClientManager {

	private final ICacheServerLocator locator = new CacheServerLocator();
	private final List<Address> addresses = new ArrayList<Address>();
	

	
	public void addCacheServer(InetAddress addr, int port) throws IOException {
		Address address = new Address(addr, port);
		addresses.add(address);
		
		addSegment(new Address(addr, port), addresses.size() - 1);
	}
	
	
	private void addSegment(Address serviceAddress, int segment) throws IOException {
		
		IBlockingConnection connection = null;
		try {
			connection = ConnectionPool.getInstance().getConnection(serviceAddress);
			connection.markWritePosition();
			connection.write((int) 0); // emtpy length field
			int length = 0;
		
			length += connection.write(CacheServer.CMD_ADD_SEGMENT);
			length += connection.write(segment);

			connection.resetToWriteMark();
			connection.write(length);
		
			connection.flush();
		
			connection.readInt();  // first position is length
			byte response = connection.readByte();
			if (response != CacheServer.RESULT_OK_WITHOUT_RETURNVALUE) {
				throw new IOException("couldn't add segment");
			}
		
			connection.close();
		} catch (IOException e) {
			if (connection != null) {
				try {
					ConnectionPool.getInstance().destroyConnection(connection);
				} catch (Exception ignore) { }
			}
		
			throw e;
		}
	}
	
	public Cache getCache() {
		return new CacheClient(locator);
	}

	
	
	private final class CacheServerLocator implements ICacheServerLocator {
		
		public int computeSegement(Object key) {
			int hc = key.hashCode();
			if (hc < 0) {
				hc = 0 - hc;
			}
			
			int segement = hc % addresses.size();
			return segement;
		}

		public Address getServiceAddress(int segment) throws IOException {
			return  addresses.get(segment);
		}
	}
}
