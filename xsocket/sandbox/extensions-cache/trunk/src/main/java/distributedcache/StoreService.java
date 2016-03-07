package distributedcache;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

import org.xsocket.IDataSink;
import org.xsocket.IDataSource;
import org.xsocket.ILifeCycle;
import org.xsocket.Resource;
import org.xsocket.group.Address;
import org.xsocket.group.GroupEndpoint;
import org.xsocket.group.IGroupEndpoint;
import org.xsocket.group.IGroupEndpointHandler;
import org.xsocket.group.ObjectMessage;
import org.xsocket.stream.BlockingConnectionPool;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.IServerContext;
import org.xsocket.stream.StreamUtils;


import distributedcache.MultiRpcCaller.Request;
import distributedcache.MultiRpcCaller.Response;

public final class StoreService implements IDataHandler, ILifeCycle {
	
	private static final Logger LOG = Logger.getLogger(StoreService.class.getName());

	private static final byte FALSE = 00;
	private static final byte TRUE = 01;
	
	private static final byte CMD_PUT = 01;
	private static final byte CMD_GET = 02;
	private static final byte CMD_REMOVE = 03;
	
	private static final String ENCODING = "Cp1252";
	private static final Serializer SERIALIZER = new Serializer();
	
	private Cache cache = null;
	
	private Address serviceAddress = null;
		
	private GroupEndpoint<ObjectMessage> groupEndpoint = null;
	
	@Resource
	private IServerContext ctx = null;
	
	private StoreServiceRegistry registry = null;
	private Set<Integer> localSupported = new HashSet<Integer>();
	private Map<Integer, Address> remoteSupported = new HashMap<Integer, Address>();
	
	private Map<Address, Integer> load = Collections.synchronizedMap(new HashMap<Address, Integer>());
	
	
	private static BlockingConnectionPool connectionPool = new BlockingConnectionPool(3L * 60L * 1000L);
	
	private MultiRpcCaller multiRpcCaller = null;

	private static final Timer TIMER = new Timer(true); 
	private final LoadNotifierTimerTask loadNotifierTimerTask = new LoadNotifierTimerTask();
	
	public StoreService(InetAddress groupAddress, int groupPort) throws IOException {
		registry = StoreServiceRegistry.getInstance(new Address(groupAddress, groupPort));
		groupEndpoint = new GroupEndpoint<ObjectMessage>(groupAddress, groupPort, new GroupMessageHandler());
		multiRpcCaller = new MultiRpcCaller(groupEndpoint);
	}

	
	public void onInit() {
		cache = new Cache(ctx.getLocaleAddress().getAddress() + ":" + ctx.getLocalePort(), 100, true, true, 0, 0);
		CacheManager.getInstance().addCache(cache);
		
		serviceAddress = new Address(ctx.getLocaleAddress(), ctx.getLocalePort());
		registry.registerStoreServiceAddress(serviceAddress);
		
		//load.put(serviceAddress, 0);
		load.put(serviceAddress, 30);
		TIMER.schedule(loadNotifierTimerTask, 100, 500);
	}
	
	public void onDestroy() {
		loadNotifierTimerTask.cancel();
		
		registry.deregisterStoreServiceAddress(serviceAddress);
		
		CacheManager.getInstance().removeCache(ctx.getLocaleAddress().getAddress() + ":" + ctx.getLocalePort());
		cache.dispose();
		
		try {
			groupEndpoint.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	

	
	synchronized Address assignService(int keyHashCode) throws IOException {
		Address address = serviceAddress;
		
		if (!load.isEmpty()) {
			for (Address addr : load.keySet()) {
				if (load.get(addr) < load.get(address)) {
					address = addr;
				}
			}
		}
		
		address = sendAssignNotification(address, keyHashCode);
		LOG.info("key " + keyHashCode + " has been assigned to service " + address);

		registerAddressToKeyHashCode(address, keyHashCode);
		
		return address;
	}
	
	
	private Address sendAssignNotification(Address address, int keyHashCode) throws IOException {
		List<Response> responses = multiRpcCaller.call(new AssignMsg(keyHashCode, address));
		for (Response response : responses) {
			if (response instanceof AlreadyAssignedMsg) {
				AlreadyAssignedMsg alreadyAssignedMsg = (AlreadyAssignedMsg) response;
				remoteSupported.put(keyHashCode, alreadyAssignedMsg.getAddress());
				return alreadyAssignedMsg.getAddress();
			}
		}
		
		return address;
	}

	
	private void registerAddressToKeyHashCode(Address address, int keyHashCode) {
		if (address.equals(serviceAddress)) {
			localSupported.add(keyHashCode);
		} else {
			remoteSupported.put(keyHashCode, address);
		}
	}
	
	String getServiceAddress() {
		return serviceAddress.toString();
	} 
	
	List<String> getLocalSupported() {
		ArrayList<String> result = new ArrayList<String>();
		for (Integer hashCode : localSupported) {
			result.add(Integer.toString(hashCode));
		}
		
		return result;
	}

	List<String> getRemoteSupported() {
		ArrayList<String> result = new ArrayList<String>();
		for (Integer hashCode : remoteSupported.keySet()) {
			result.add(Integer.toString(hashCode) + " (" + remoteSupported.get(hashCode) + ")");
		}
		
		return result;
	}


	List<String> getLoadList() {
		List<String> result = new ArrayList<String>();
		for (Address address : load.keySet()) {
			result.add(address.toString() + "=" + load.get(address));
		}
		
		return result;
	}
	
	int getLoad() {
		int load = 0;
		if (cache.getSize() != 0) {
			load = (int) ((cache.getSize() *  100) / cache.getMaxElementsInMemory());	
		}
		return load;
	}
	
	
	synchronized Address getServiceAddress(int keyHashcode) throws IOException {
		if (localSupported.contains(keyHashcode)) {
			return serviceAddress;
		
		} else if (remoteSupported.containsKey(keyHashcode)) {
			return remoteSupported.get(keyHashcode);
			
		} else {
			LOG.info("non assignment for key " + keyHashcode + " assign service");
			return assignService(keyHashcode);
		}
	}
	
	
	public static Address callPut(Address address, int keyHashCode, String key, Serializable value) throws IOException {
		
		IBlockingConnection connection = connectionPool.getBlockingConnection(address.getAddress().getHostName(), address.getPort());
		
		
		connection.markWritePosition();  // mark current position
		connection.write((int) 0);       // write "emtpy" length field
		
		int written = connection.write(CMD_PUT);
		
		written += connection.write(keyHashCode);
		
		byte[] serializedKey = key.getBytes(ENCODING);
		written += connection.write(serializedKey.length);
		written += connection.write(serializedKey);
		
		byte[] serializedValue = SERIALIZER.serialize(value);
		written += new Record(serializedValue).writeTo(connection);

		connection.resetToWriteMark();  // return to length field position
		connection.write(written);      // and update it
		
		connection.flush(); // flush (marker will be removed implicit) 
		
		
		connection.readInt();
		Address srvAddress = Address.readFrom(connection);
		connection.close();
		
		return srvAddress;
	}


	private void put(INonBlockingConnection connection) throws IOException {
		int keylength = connection.readInt();
		byte[] serializedKey =connection.readBytesByLength(keylength);
		String key = new String(serializedKey, ENCODING);
			
		byte[] data = Record.readFrom(connection).getData();
		Serializable value = SERIALIZER.deserialize(data);
			
		cache.put(new Element(key, value));
			
		if (LOG.isLoggable(Level.INFO)) {
			LOG.info("[" + serviceAddress + "] element " + key + "=" + value + " inserted");
		}

		connection.markWritePosition();
		connection.write((int) 0);
		
		int written = serviceAddress.writeTo(connection);
		
		connection.resetToWriteMark();
		connection.write(written);
	}
	
	
	
	public static IGetResult callGet(Address address, int keyHashCode, String key) throws IOException {
		
		IBlockingConnection connection = connectionPool.getBlockingConnection(address.getAddress().getHostName(), address.getPort());
		
		connection.markWritePosition();  // mark current position
		connection.write((int) 0);       // write "emtpy" length field
		
		int written = connection.write(CMD_GET);

		written += connection.write(keyHashCode);
		
		byte[] serializedKey = key.getBytes(ENCODING);
		written += connection.write(serializedKey.length);
		written += connection.write(serializedKey);
		
		connection.resetToWriteMark();  // return to length field position
		connection.write(written);      // and update it
		
		connection.flush(); // flush (marker will be removed implicit) 


		connection.readInt();
		
		final Address srvAddress = Address.readFrom(connection);
		

		Record record = Record.readFrom(connection);
		connection.close();

		Serializable ser = null;
		if (record.getData() != null) {
			ser = SERIALIZER.deserialize(record.getData());
		}
		final Serializable value = ser; 

		
		return new IGetResult() {
			public Serializable getValue() {
				return value;
			}
			
			public Address getServiceAddress() {
				return srvAddress;
			}
		};
	}

	
	public interface IGetResult {
		public Serializable getValue();
		
		public Address getServiceAddress();
	}


	
	private void get(INonBlockingConnection connection) throws IOException {
		int keylength = connection.readInt();
		byte[] serializedKey = connection.readBytesByLength(keylength);
		String key = new String(serializedKey, ENCODING);
	
		Element element = cache.get(key);
		if (LOG.isLoggable(Level.INFO)) {
			LOG.info("element " + element + " requested by key " + key);
		}
			
		connection.markWritePosition();
		connection.write((int) 0);
		
		int written = serviceAddress.writeTo(connection);

		byte[] serializedData = null;
		if (element != null) {
			serializedData = SERIALIZER.serialize((Serializable) element.getObjectValue());
		}
		
		written += new Record(serializedData).writeTo(connection);
		
		connection.resetToWriteMark();
		connection.write(written);
	}

	
	public static IRemoveResult callRemove(Address address, int keyHashCode, String key) throws IOException {
		
		IBlockingConnection connection = connectionPool.getBlockingConnection(address.getAddress().getHostName(), address.getPort());
		
		connection.markWritePosition();  // mark current position
		connection.write((int) 0);       // write "emtpy" length field
		
		int written = connection.write(CMD_REMOVE);

		written += connection.write(keyHashCode);
		
		byte[] serializedKey = key.getBytes(ENCODING);
		written += connection.write(serializedKey.length);
		written += connection.write(serializedKey);
		
		connection.resetToWriteMark();  // return to length field position
		connection.write(written);      // and update it
		
		connection.flush(); // flush (marker will be removed implicit) 


		connection.readInt();
		
		final Address srvAddress = Address.readFrom(connection);
		
		Record record = Record.readFrom(connection);
		connection.close();
		
		Serializable ser = null;
		if (record.getData() != null) {
			ser = SERIALIZER.deserialize(record.getData());
		} 

		final Serializable value = ser; 

		return new IRemoveResult() {
			public Serializable getValue() {
				return value;
			}
			
			public Address getServiceAddress() {
				return srvAddress;
			}
		};
	}

	public interface IRemoveResult {
		public Serializable getValue();
		
		public Address getServiceAddress();
	}

	
	
	private void remove(INonBlockingConnection connection) throws IOException {
		int keylength = connection.readInt();
		byte[] serializedKey = connection.readBytesByLength(keylength);
		String key = new String(serializedKey, ENCODING);
	
		Element element= cache.get(key);
		boolean isRemoved = cache.remove(key);
		if (LOG.isLoggable(Level.INFO)) {
			LOG.info("element removed is " + isRemoved);
		}
			
		connection.markWritePosition();
		connection.write((int) 0);
		
		int written = serviceAddress.writeTo(connection);

		byte[] serializedData = null;
		if (isRemoved) {
			serializedData = SERIALIZER.serialize((Serializable) element.getObjectValue());
		}
		
		written += new Record(serializedData).writeTo(connection);
				
		connection.resetToWriteMark();
		connection.write(written);
	}


	

	
	public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {

		int length = StreamUtils.validateSufficientDatasizeByIntLengthField(connection);
		
		byte cmd = connection.readByte();
		int keyHashCode = connection.readInt(); 
		Address targetAddress = getServiceAddress(keyHashCode);

		if (targetAddress.equals(serviceAddress)) {
			switch (cmd) {
				case CMD_PUT:
					put(connection);
					break;
		
		
				case CMD_GET:
					get(connection);
					break;

					
				case CMD_REMOVE:
					remove(connection);
					break;

						
				default:
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("receive datagram with unknown cmd " + cmd);
					}
		
					break;
			}
			
		} else {
			forwardRequest(connection, targetAddress, length, cmd, keyHashCode);
		}
	
		return true;
	}

	
	
	private void forwardRequest(INonBlockingConnection connection, Address address, int length, byte cmd, int keyHashCode) throws IOException {
		LOG.info("forwarding request (cmd=" + cmd + ", hashkey=" + keyHashCode + ") to " + address);
		
		// forward request 
		IBlockingConnection con = connectionPool.getBlockingConnection(address.getAddress().getHostName(), address.getPort());
		con.write(length);
		con.write(cmd);
		con.write(keyHashCode); 
		con.write(connection.readByteBufferByLength(length - 1 - 4)); 
		con.flush();
		
		// return response
		int lengthResponse = con.readInt();
		connection.write(lengthResponse);
		connection.write(con.readByteBufferByLength(lengthResponse));
		con.close();
	}
	
	
	private final class LoadNotifierTimerTask extends TimerTask {
		@Override
		public void run() {
			try {
				int currentLoad = getLoad();
				load.put(serviceAddress, currentLoad);
				
				ObjectMessage msg = groupEndpoint.createObjectMessage((Serializable) new LoadNotificationMsg(currentLoad, serviceAddress));
				groupEndpoint.send(msg);
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	
	private final class GroupMessageHandler implements IGroupEndpointHandler<ObjectMessage> {
		public boolean onMessage(IGroupEndpoint<ObjectMessage> endpoint) throws IOException {
			ObjectMessage message = endpoint.receiveMessage();
			boolean handled = multiRpcCaller.handleResponse(message);
			
			if (!handled) {
				if (message.getObject() instanceof AssignMsg) {
					AssignMsg assignMsg = (AssignMsg) message.getObject();
					
					if (localSupported.contains(assignMsg.getHashCode())) {
						LOG.info("hashCode=" +  assignMsg.getHashCode() + " is already supported by local address (" + serviceAddress + ") sending NOK");
						ObjectMessage response = endpoint.createObjectMessage(new AlreadyAssignedMsg(message.getId(), serviceAddress));
						response.addDestinationAddress(message.getSourceAddress());
						endpoint.send(response);
						
					} else {
						LOG.info("register service " + assignMsg.getAddress() + " for keyHash " + assignMsg.getHashCode());
						
						registerAddressToKeyHashCode(assignMsg.getAddress(), assignMsg.getHashCode());
						ObjectMessage response = endpoint.createObjectMessage(new OkMsg(message.getId()));
						response.addDestinationAddress(message.getSourceAddress());
						endpoint.send(response);
					}
					
					
				} else if (message.getObject() instanceof LoadNotificationMsg) {
					LoadNotificationMsg loadNotificationMessage = (LoadNotificationMsg) message.getObject();
					load.put(loadNotificationMessage.getAddress(), loadNotificationMessage.getLoad());
				}
			}
			
			return true;
		}
	}
	
	
	private static final class Serializer {
		
		byte[] serialize(Serializable object) {
			try {
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				new ObjectOutputStream(os).writeObject(object);
				
				return os.toByteArray();
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}
		
		
		Serializable deserialize(byte[] bytes) {
			try {
				ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
				return (Serializable) ois.readObject();
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}
	}
	
	

	
	private static final class AssignMsg implements Request {
		private int hashCode = 0;
		private Address address = null;
		
		AssignMsg(int hashCode, Address address) {
			this.hashCode = hashCode;
			this.address = address;
		}
		
		public int getHashCode() {
			return hashCode; 
		}
		
		public Address getAddress() {
			return address;
		}
	}
	
	
	private static final class LoadNotificationMsg implements Serializable {
		private Address address = null;
		private int load = 0;

		
		public LoadNotificationMsg(int load, Address address) {
			this.load = load;
			this.address = address;
		}
		
		public int getLoad() {
			return load;
		}
		
		public Address getAddress() {
			return address;
		}
	}


	private static final class AlreadyAssignedMsg implements Response {
		private long correlatedMsgId = 0;
		private Address address = null;
		
		public AlreadyAssignedMsg(long correlatedMsgId, Address address) {
			this.correlatedMsgId = correlatedMsgId;
			this.address = address;
		}
		
		public long getRequestMsgId() {
			return correlatedMsgId;
		}
		
		public Address getAddress() {
			return address;
		}
	}

	
	private static final class OkMsg implements Response {
		private long correlatedMsgId = 0;
		
		OkMsg(long correlatedMsgId) {
			this.correlatedMsgId = correlatedMsgId;
		}
		
		public long getRequestMsgId() {
			return correlatedMsgId;
		}
	}
	
	
	private static final class Record {
		
		private static final int COMPRESS_THRESHOLD = 50;
		
		private byte[] data = null;
		
		Record(byte[] data) {
			this.data = data;
		}
		
		public byte[] getData() {
			return data;
		}
		
		public static Record readFrom(IDataSource dataSource) throws IOException {

			boolean isCompressed = (dataSource.readByte() == TRUE);

			byte[] data = null;
			int length = dataSource.readInt();
			if (length > 0) {
				data = dataSource.readBytesByLength(length);
			} 
			
			if (isCompressed) {
				data = decompress(data);
			}
			
			return new Record(data);
		}
		
		
		public int writeTo(IDataSink dataSink) throws IOException {
			int written = 0;
			
			if (data != null) {
				byte[] dataToWrite = data;
				
				boolean isCompressed = false;
				if (dataToWrite.length >  COMPRESS_THRESHOLD) {
					int originalLength = dataToWrite.length;
					dataToWrite = compress(dataToWrite);
					isCompressed = true;
					
					LOG.info("data has been compressed (" +  (100 - ((dataToWrite.length * 100) / originalLength)) + "%)");
				}
				
				
				if (isCompressed) {
					written += dataSink.write(TRUE);
				} else {
					written += dataSink.write(FALSE);
				}
				written += dataSink.write(dataToWrite.length);
				written += dataSink.write(dataToWrite);
				
			} else {
				written += dataSink.write(FALSE);
				written += dataSink.write(0);
			}
			
			return written;
		}
		
		
		private static byte[] compress(byte[] data) {
		    Deflater compressor = new Deflater();
		    compressor.setLevel(Deflater.BEST_COMPRESSION);
		    
		    compressor.setInput(data);
		    compressor.finish();
		    
		    ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
		    
		    byte[] buf = new byte[1024];
		    while (!compressor.finished()) {
		        int count = compressor.deflate(buf);
		        bos.write(buf, 0, count);
		    }
		    try {
		        bos.close();
		    } catch (IOException e) { }
		    
		    return bos.toByteArray();
		}

		
		private static byte[] decompress(byte[] data) {
			Inflater decompressor = new Inflater();
		    decompressor.setInput(data);
			    
		    ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
			    
		    byte[] buf = new byte[1024];
		    while (!decompressor.finished()) {
		    	try {
		    		int count = decompressor.inflate(buf);
		    		bos.write(buf, 0, count);
		    	} catch (DataFormatException e) {  }
		    }
		    try {
		    	bos.close();
		    } catch (IOException e) { }
			    
		    return bos.toByteArray();
		}
	}
}
