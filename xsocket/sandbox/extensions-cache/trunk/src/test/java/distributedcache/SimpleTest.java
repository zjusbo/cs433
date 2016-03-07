package distributedcache;



import java.net.InetAddress;
import java.util.logging.Level;

import net.sf.ehcache.Element;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.stream.IServer;
import org.xsocket.stream.Server;

import distributedcache.management.MonitoredStoreService;




/**
*
* @author grro@xsocket.org
*/
public final class SimpleTest {
	
	
	@Test public void testSimple() throws Exception {
//		QAUtil.setLogLevel("org.xsocket.stream.IoSocketHandler", Level.FINE);
		/*
		JmxServer server = new JmxServer();
		server.start("test");
		
		InetAddress bAddress = InetAddress.getByName("224.0.0.1");
		int bPort = 8866;
		
		
		IServer server1 = new Server(new MonitoredStoreService(new StoreService(bAddress, bPort)));
		Thread t1 = new Thread(server1);
		t1.setDaemon(true);
		t1.start();
		
		IServer server2 = new Server(new MonitoredStoreService(new StoreService(bAddress, bPort)));
		Thread t2 = new Thread(server2);
		t2.setDaemon(true);
		t2.start();

		IServer server3 = new Server(new MonitoredStoreService(new StoreService(bAddress, bPort)));
		Thread t3 = new Thread(server3);
		t3.setDaemon(true);
		t3.start();
		
		try {
			Thread.sleep(200);
		} catch (InterruptedException ignore) { }
		
		PartitionedStore store1 = new PartitionedStore(bAddress, bPort);
		
		try {
			Thread.sleep(200);
		} catch (InterruptedException ignore) { }
				
		
		store1.put(new Element("key" + 0, "A long long long long ling lang lung long long long long long Value" + 0));
		for (int i = 0; i < 100; i++) {
			store1.put(new Element("key" + i, "Value"+ i));
			try {
				Thread.sleep(20);
			} catch (InterruptedException ignore) { }
		}	

		Assert.assertTrue(store1.get("key1").getObjectValue().equals("Value1"));
		Assert.assertTrue(store1.get("key2").getObjectValue().equals("Value2"));

	
		PartitionedStore store2 = new PartitionedStore(bAddress, bPort);
		Assert.assertTrue(store2.get("key1").getObjectValue().equals("Value1"));
		Assert.assertTrue(store2.get("key2").getObjectValue().equals("Value2"));
	
		
		Assert.assertTrue(store1.remove("key1") != null);
		Assert.assertTrue(store1.get("key1") == null);
		Assert.assertTrue(store2.get("key1") == null);
		Assert.assertTrue(store2.get("key2") != null);
		

		PartitionedStore store3 = new PartitionedStore(bAddress, bPort);
		Assert.assertTrue(store3.remove("key2") != null);
		Assert.assertTrue(store1.get("key2") == null);
		Assert.assertTrue(store2.get("key2") == null);
		Assert.assertTrue(store3.get("key2") == null);
		
		
		Assert.assertTrue(store3.remove("key2") == null);
		
		server1.close();
		server2.close();
		server3.close();
		
		server.stop();*/
	}
}
