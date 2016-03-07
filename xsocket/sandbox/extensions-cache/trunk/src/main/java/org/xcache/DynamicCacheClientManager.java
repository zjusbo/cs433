package org.xcache;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.stream.IBlockingConnection;

import net.sf.jsr107cache.Cache;



public final class DynamicCacheClientManager implements ICacheClientManager {

	private static final Logger LOG = Logger.getLogger(DynamicCacheClientManager.class.getName());
	
	
	private static final int DEFAULT_SEGMENT_NUMBERS = 5000;
	
	
	private final ICacheServerLocator locator = new CacheServerLocator();

	private int segments = 0;
	private Service[] services = null;
	
	
	public DynamicCacheClientManager() {
		this(DEFAULT_SEGMENT_NUMBERS);
	}
	
	public DynamicCacheClientManager(int sgm) {
		this.segments = sgm + 1;
		services = new Service[this.segments];
		
		NullService service = new NullService();
		for (int j = 0; j < segments; j++) {
			services[j] = service;
		}
	}

	
	public Cache getCache() {
		return new CacheClient(locator);
	}
	
	
	
	void addCacheServer(InetAddress address, int port) throws IOException {
		addCacheServer(new Address(address, port));
	}
	
	
	synchronized void addCacheServer(Address address) throws IOException {
		
		int i = new Random().nextInt();
		if (i < 0) {
			i = 0 - i;
		}

		int randomSegment = i % (segments - 1);  // ignore highest segment (this is a system segment)
		Service srv = services[randomSegment];

		
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("splitting service " + srv.toString());
		}
		srv.split(address);
	}
	

	
	
	private final class CacheServerLocator implements ICacheServerLocator {
		
		public int computeSegement(Object key) {
			int hc = key.hashCode();
			if (hc < 0) {
				hc = 0 - hc;
			}
			
			
			int segment = hc % (segments - 1);  // ignore highest slot (this is a system slot)
			return segment;
		}

		public Address getServiceAddress(int segment) throws IOException {
			return services[segment].address;
		}

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

	
	private class Service {
		private int lowSegment = 0;
		private int highSegment = 0;
		private Address address = null;
		
		
		Service(int lowSegment, int highSegment, Address address) {
			this.lowSegment = lowSegment;
			this.highSegment = highSegment;
			this.address = address;
		}
		
		Address getAddress() {
			return address;
		}
		
		
		synchronized void split(Address addressService) throws IOException {
			if (lowSegment < highSegment) {
				int newRange = (highSegment - lowSegment) / 2;
				
				Service newService = new Service(lowSegment, lowSegment + newRange, addressService);
				
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("initiate transfering segements to " + addressService);
				}
				for (int i = newService.lowSegment; i < newService.highSegment; i++) {
					CacheServer.callTransferSegment(address, newService.address, i);
					services[i] = newService;
				}
				
				lowSegment = lowSegment + newRange + 1;

				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("existing service splitted " + this.toString());
					LOG.fine("new service " + newService.toString());
				}

				
			} else {
				throw new RuntimeException("service is responsible for one slot. couldn't split");
			}
		}
		
		@Override
		public String toString() {
			return address.toString() + " [segments: " + lowSegment + "-" + highSegment + "]";
		}
	}
	
	
	private final class NullService extends Service {
		
		public NullService() {
			super(0, segments, null);
		}

		
		void split(Address address) throws IOException {
			Service service = new Service(0, segments - 1, address);
			for (int i = 0; i < (segments - 1); i++) {
				addSegment(address, i);
				services[i] = service;
			}
		}
		
		@Override
		public String toString() {
			return "NullService";
		}

	}
	
	
	
	
	private final class TransferJob implements Runnable {
		
		private int slot = 0;
		private InetSocketAddress origin = null;
		private InetSocketAddress target = null;
		
		TransferJob(int slot, InetSocketAddress origin, InetSocketAddress target) {
			this.slot = slot;
			this.origin = origin;
			this.target = target;
		}
		
		
		public void run() {


		}
		
	}
}
