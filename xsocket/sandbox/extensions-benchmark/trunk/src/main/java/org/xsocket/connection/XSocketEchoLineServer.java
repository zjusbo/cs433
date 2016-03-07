package org.xsocket.connection;




import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.xsocket.connection.IConnection.FlushMode;

 


public final class XSocketEchoLineServer {


	public static void main( String[] args ) throws Exception {
		
		if (args.length < 1) {
			System.out.println("usage org.xsocket.connection.XSocketEchoLineServer <port> <pause> <writeTofile> <syncFlush> <workers>");
			System.exit(-1);
		}
		
		new XSocketEchoLineServer().launch(args);
	}
		
		
	public void launch(String... args) throws Exception {
	
		int port = Integer.parseInt(args[0]);
		int pause = Integer.parseInt(args[1]);
		boolean writeToFile = Boolean.parseBoolean(args[2]);
		
		boolean sycnFlush = true;
		if (args.length > 3) {
			sycnFlush = Boolean.parseBoolean(args[3]);
		}
		
	
		
		System.out.println("running echo server");
		
		System.out.println("\r\n---------------------------------------");
		System.out.println(ConnectionUtils.getImplementationVersion() + "  (" + ConnectionUtils.getImplementationDate() + ")");

	
		
		IServer server = null; 

		if (args.length > 4) {
			int workers = Integer.parseInt(args[4]);
			server = new Server(port, new EchoLineHandler(pause, sycnFlush, writeToFile), 150);
			server.setWorkerpool(Executors.newFixedThreadPool(workers));

			System.out.println("mode threaded (pool size " + ((ThreadPoolExecutor) server.getWorkerpool()).getMaximumPoolSize() + ")");
			
		} else {
			server = new Server(port, new NonThreadedEchoLineHandler(pause, sycnFlush, writeToFile), 150);
			
			System.out.println("mode                      non threaded");
			System.out.println("synch flush               " + sycnFlush);

		}

//		server.setIdleTimeoutMillis(60 * 1000);
		server.setFlushMode(FlushMode.ASYNC);
		
			
		System.out.println("synch flush               " + sycnFlush);
		System.out.println("pause                     " + pause);	
		System.out.println("write to file             " + writeToFile);		
		System.out.println("---------------------------------------\r\n");
		
		

		ConnectionUtils.registerMBean(server);

		
		System.out.println("running xsocket echo server");
		server.run();		
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
