package org.xsocket.stream;



import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.xsocket.DynamicWorkerPool;
import org.xsocket.stream.management.MultithreadedServerMBeanProxyFactory;



public final class XSocketEchoServer {


	public static void main( String[] args ) throws Exception {
		
		
		if (args.length < 1) {
			System.out.println("usage org.xsocket.stream.XSocketEchoServer <port> <pause> <workers>");
			System.exit(-1);
		}
		
		
	
		int port = Integer.parseInt(args[0]);
		int pause = Integer.parseInt(args[1]);
		
		final MultithreadedServer server = new MultithreadedServer(port, new EchoHandler(pause));

		if (args.length > 2) {
			int workers = Integer.parseInt(args[2]);
			DynamicWorkerPool pool = new DynamicWorkerPool(workers, workers);
			pool.setAdjustPeriod(Integer.MAX_VALUE);
			server.setWorkerPool(pool);
		}
		
		Thread t = new Thread(server);
		t.setName("Server");

		startJmxServer("test", 1099);
		MultithreadedServerMBeanProxyFactory.createAndRegister(server, "MyTestServer");

		
		
		System.out.println("running xsocket echo server");
		t.start();
		
		
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				server.close();
			}
		});
		
		while (true) {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException ignore) { }
		}
    }
	
	
	private static void startJmxServer(String name, int port) {
		try {
			Registry registry = LocateRegistry.createRegistry(port);
			registry.unbind(name);
		} catch (Exception ignore) {  }

		try {
		    JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + InetAddress.getLocalHost().getHostName() + ":" + port + "/" + name);

		    MBeanServer mbeanSrv = ManagementFactory.getPlatformMBeanServer();
		    JMXConnectorServer server = JMXConnectorServerFactory.newJMXConnectorServer(url, null, mbeanSrv);
		    server.start();
		    System.out.println("JMX RMI Agent has been bound on address");
		    System.out.println(url);
		} catch (Exception e) { }
	}
	
}
