package distributedcache;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;
import org.xsocket.datagram.IDatagramHandler;
import org.xsocket.datagram.IEndpoint;
import org.xsocket.datagram.MulticastEndpoint;
import org.xsocket.datagram.UserDatagram;
import org.xsocket.group.Address;



public final class StoreServiceRegistry  {

	private static final Logger LOG = Logger.getLogger(StoreServiceRegistry.class.getName());
	
	private static final int PACKET_SIZE = 128;
	
	private static final byte MAGIC_BYTE = 78;
	private static final byte REFRESH_CMD = 0;
	private static final byte REGISTER_CMD = 1;
	private static final byte DEREGISTER_CMD = 2;
	
	private static final Map<Address, StoreServiceRegistry> INSTANCES = new HashMap<Address, StoreServiceRegistry>();

	private static final long PUPLISH_PERIOD = 10 * 1000;
	private static final Timer TIMER = new Timer(true);
	private TimerTask publishTimerTask = null;

	private final Set<Address> localAddresses = Collections.synchronizedSet(new HashSet<Address>());
	private final Set<Address> addresses = Collections.synchronizedSet(new HashSet<Address>());
	private IEndpoint endpoint = null;

	
	static {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				for (StoreServiceRegistry registry : INSTANCES.values()) {
					registry.close();
				}
			}
		});
		
		
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
			}
		};
		TIMER.schedule(task, 0);		 
	}
	
	
	StoreServiceRegistry(Address locatorGroupAddress) throws IOException {
		long start = System.currentTimeMillis();
		
		endpoint = new MulticastEndpoint(locatorGroupAddress.getAddress(), locatorGroupAddress.getPort(), PACKET_SIZE, new NotificationHandler());
		
		sendRefreshRequest(500);
		
		publishTimerTask = new TimerTask() {
			@Override
			public void run() {
				publishLocalRegistered();
			}
		};
		TIMER.schedule(publishTimerTask, PUPLISH_PERIOD, PUPLISH_PERIOD);

		
		if (LOG.isLoggable(Level.INFO)) {
			LOG.info("[" + endpoint.getId()+ "] StoreServiceRegistry (group " + endpoint.getLocalSocketAddress() + ") initializes (" + addresses.size() + " services registered, inittime=" + DataConverter.toFormatedDuration(System.currentTimeMillis() - start) + ")");
		}
	}
	

	private void sendRefreshRequest(long waittime) {
		try {
			UserDatagram datagram = new UserDatagram(PACKET_SIZE);
			datagram.write(MAGIC_BYTE);
			datagram.write(REFRESH_CMD);
			
			endpoint.send(datagram);	
	
			if (waittime > 0) {
				long start = System.currentTimeMillis();
				do {
					if (addresses.size() > 0) {
						return;
					} else {
						try {
							Thread.sleep(50);
						} catch (InterruptedException ignore) { }
					}
				} while (System.currentTimeMillis() < (start + waittime));
			}
			
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by sending refresh request. Reason " + ioe.toString());
			}
		}
	}
	
	
	
	@SuppressWarnings("unchecked")
	private void publishLocalRegistered() {
		Set<Address> copy = new HashSet<Address>();
		copy.addAll(localAddresses);
		for (Address address : copy) {
			sendRegistered(address);
		}
	}
	
	public static synchronized StoreServiceRegistry getInstance(Address locatorGroupAddress) throws IOException {
		StoreServiceRegistry registry = INSTANCES.get(locatorGroupAddress);
		if (registry == null) {
			registry = new StoreServiceRegistry(locatorGroupAddress);
			INSTANCES.put(locatorGroupAddress, registry);
		}
		
		return registry;
	}
	
	
	private void close() {
		if (publishTimerTask != null) {
			publishTimerTask.cancel();
		}
		
		try {
			endpoint.close();
		} catch (IOException ignore) { }
	}
	
	
	public void registerStoreServiceAddress(Address address)  {
		register(address);
		sendRegistered(address);		

		localAddresses.add(address);
	}
	
	
	private void register(Address address) {
		if (!addresses.contains(address)) {
			addresses.add(address);

			if (LOG.isLoggable(Level.INFO)) {
				LOG.info("[" + endpoint.getId()+ "](group " + endpoint.getLocalSocketAddress() + ") group service address " + address.toString() + " registered");
			}
		}
	}
	
	
	
	private void sendRegistered(Address address)  {
		try {
			UserDatagram datagram = new UserDatagram(PACKET_SIZE);
			datagram.write(MAGIC_BYTE);
			datagram.write(REGISTER_CMD);
			address.writeTo(datagram);
			
			endpoint.send(datagram);		
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + endpoint.getId()+ "] error occured by sending deregistered notification for address " + address);
			}
		}
	}
	

	public void deregisterStoreServiceAddress(Address address) {
		remove(address);
		sendDeregistered(address);
		
		localAddresses.remove(address);
	}
	
	
	@SuppressWarnings("unchecked")
	private void remove(Address address) {
		boolean isRemoved = addresses.remove(address);
		localAddresses.remove(address);

		if (isRemoved) {
			if (LOG.isLoggable(Level.INFO)) {
				LOG.info("[" + endpoint.getId()+ "] (group " + endpoint.getLocalSocketAddress() + ") group service address " + address.toString() + " deregistered");
			}
		}
	}
	
	
	private void sendDeregistered(Address address) {
		try {
			UserDatagram datagram = new UserDatagram(PACKET_SIZE);
			datagram.write(MAGIC_BYTE);
			datagram.write(DEREGISTER_CMD);
			address.writeTo(datagram);
			
			endpoint.send(datagram);
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + endpoint.getId()+ "] error occured by sending registered notification for address " + address);
			}
		}
	}
	
	
	@SuppressWarnings("unchecked")
	public Set<Address> getStoreServiceAddresses() {
		Set<Address> result = new HashSet<Address>();
		result.addAll(addresses);
		
		return result;
	}
	

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (Address address : addresses) {
			if (localAddresses.contains(address)) {
				sb.append(address.toString() + " (local)\r\n");
			} else {
				sb.append(address.toString() + " (remote)\r\n");
			}
		}
		
		return sb.toString();
	}

	private final class NotificationHandler implements IDatagramHandler {
		@SuppressWarnings("unchecked")
		public boolean onDatagram(IEndpoint localEndpoint) throws IOException {

			UserDatagram datagram = localEndpoint.receive();
			if (datagram != null) {
				byte magicByte = datagram.readByte();
				if (magicByte == MAGIC_BYTE) {
					byte cmd = datagram.readByte();
					
					switch (cmd) {
					
					case REFRESH_CMD:
						publishLocalRegistered();
						break;

					case REGISTER_CMD:
						register(Address.readFrom(datagram));
						break;

					case DEREGISTER_CMD:
						remove(Address.readFrom(datagram));
						break;

						
					default:
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("[" + endpoint.getId()+ "] receive datagram with unknown cmd " + cmd);
						}
						break;
					}
				}
			}
			
			return true;
		}
	}	
}
