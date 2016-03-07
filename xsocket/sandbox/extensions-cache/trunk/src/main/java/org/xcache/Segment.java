// $Id: DataConverter.java 1546 2007-07-23 06:07:56Z grro $

/*
 *  Copyright (c) xcache.org, 2007. All rights reserved.
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
 * The latest copy of this software may be found on http://www.xcache.org/
 */
package org.xcache;


import java.io.IOException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IConnection;

import net.sf.jsr107cache.Cache;




/**
 * 
 * 
 * @author grro@xcache.org
 */
final class Segment  {
	
	private static final Logger LOG = Logger.getLogger(Segment.class.getName());

	private Cache cache = null;
	private int segmentId = 0;
	private ICacheFactory cacheFactory = null;
	
	
	
	Segment(int segmentId, ICacheFactory cacheFactory) {
		this.segmentId = segmentId;
		this.cacheFactory = cacheFactory;
		
		setCache(cacheFactory.newCache());
	}
	
	
	synchronized Cache getCache() {
		return cache;
	}
	
	boolean isForwardSegment() {
		return (cache instanceof CacheClient);
	}

	Address getForwardAddress() {
		if (isForwardSegment()) {
			return ((Forwarder) ((CacheClient) cache).getLocator()).getTargetService();
		} else {
			return null;
		}
	}
	
	private void setCache(Cache cache) {
		this.cache = cache;
	}

	
	
	static final void callTransferSegment(Address sourceService, Address targetService, int segmentId) throws IOException {
		IBlockingConnection connection = null;
		try {
			connection = ConnectionPool.getInstance().getConnection(sourceService);

			connection.markWritePosition();
			connection.write((int) 0); // emtpy length field
			int length = 0;
			
			length += connection.write(CacheServer.CMD_TRANSFER_SEGMENT);
			length += connection.write(segmentId);
			length += targetService.writeTo(connection);
			
			connection.resetToWriteMark();
			connection.write(length);
			
			connection.flush();
			
			connection.readInt();  // first position is length
			byte response = connection.readByte();
			if (response != CacheServer.RESULT_OK_WITHOUT_RETURNVALUE) {
				throw new IOException("couldn't initiate transfer. reason");
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
	
	
	@SuppressWarnings("unchecked")
	synchronized void transferTo(Address address) throws IOException {

		long start = System.currentTimeMillis();
		
		
		if (isForwardSegment()) {
			callTransferSegment(getForwardAddress(), address, segmentId);
			
			if (LOG.isLoggable(Level.FINE)) {
				long duration = System.currentTimeMillis() - start;
				LOG.fine("forward segment " + segmentId + " (forward address " + getForwardAddress() + " has been forwarded to " + address + " (duration=" + DataConverter.toFormatedDuration(duration) + ")"); 
			}

			
		} else {
			IBlockingConnection con = null;
			try {
				con = ConnectionPool.getInstance().getConnection(address);
							
				con.markWritePosition();
				con.write((int) 0); // emtpy length field
				int length = 0;
							
				length += con.write(CacheServer.CMD_RECEIVE_SEGMENT);
				length += con.write(segmentId);
	
				Set<Object> keys = cache.keySet();
				length += con.write(keys.size());
							
				for (Object key : keys) {
					Item keyItem = new Item(key);
					length += keyItem.writeTo(con);
					
					Item valueItem = (Item) cache.get(key);
					if (valueItem == null) {
						valueItem = new Item(null);
					}
					length += valueItem.writeTo(con);
				}
							
				con.resetToWriteMark();
				con.write(length);
							
				con.flush();
	
							
				// read response
				con.setReceiveTimeoutMillis(10L * 1000L);
				con.readInt();  // first position is length
				byte response = con.readByte();
				if (response != CacheServer.RESULT_OK_WITHOUT_RETURNVALUE) {
					throw new RuntimeException("unexpected result");
				}
				
				con.close();
				
				if (LOG.isLoggable(Level.FINE)) {
					long duration = System.currentTimeMillis() - start;
					LOG.fine("segment " + segmentId + " (size= " + cache.size() + ") has been transfered to " + address + " update ref (duration=" + DataConverter.toFormatedDuration(duration) + ")"); 
				}
				
			} catch (Exception e) {
				if (con != null) {
					try {
						ConnectionPool.getInstance().destroyConnection(con);
					} catch (IOException ignore) { }; 
				}
				throw new IOException("error occured by transfering segment " + segmentId +  ". Reason: " + e.toString());
			}
		}
		

		// transfer operation succeeds -> update cache with forward cache
		setCache(new CacheClient(new Forwarder(segmentId, address))); 
	}
	
	
	synchronized void readFrom(IConnection connection) throws IOException {
		
		Cache cache = cacheFactory.newCache();

		int countElements = connection.readInt();
		for (int i = 0; i < countElements; i++) {
			Item keyItem = Item.readFrom(connection);
			Item valueItem = Item.readFrom(connection);
				
			if (valueItem.getValue() != null) {
				cache.put(keyItem.getValue(), valueItem);
			}
		}

		setCache(cache);
	}
		
	

	private static final class Forwarder implements ICacheServerLocator {
		
		private int segment = 0;
		private Address targetService = null;
		
		
		Forwarder(int segment, Address targetService) {
			this.segment = segment;
			this.targetService = targetService;
		}

		Address getTargetService() {
			return targetService;
		}
		
		public int computeSegement(Object key) {
			return segment;
		}
		
		public Address getServiceAddress(int segment) throws IOException {
			return targetService;
		}
	};
}
