package org.xsocket.connection;




import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.nio.channels.ClosedChannelException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.concurrent.ThreadPoolExecutor;

import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IConnection.FlushMode;

 


public final class NotifyServer {

	static final int INIT_LOOPS = 100000;
	
	

	public static void main( String[] args ) throws Exception {
		
		if (args.length < 1) {
			System.out.println("usage org.xsocket.stream.NotifyServer <port>");
			System.exit(-1);
		}
		
		new NotifyServer().launch(args);
	}
		
		
	public void launch(String... args) throws Exception {
	
		boolean asynchFlush = true;
		
		int port = Integer.parseInt(args[0]);
	
		
		IServer server = null; 

		server = new Server(port, new NotifyHandler(), 1000);
		if (asynchFlush) {
			server.setFlushMode(FlushMode.ASYNC);
		}
		

		System.out.println("\r\n---------------------------------------");
		System.out.println("class: " + this.getClass().getName());
		System.out.println("mode threaded (pool size " + ((ThreadPoolExecutor) server.getWorkerpool()).getMaximumPoolSize() + ")");
		System.out.println("synch flush: " + !asynchFlush);
		System.out.println("-Dorg.xsocket.connection.dispatcher.handlesMaxCount=" + System.getProperty("org.xsocket.connection.dispatcher.handlesMaxCount"));		
		System.out.println("---------------------------------------\r\n");
		
		
		ConnectionUtils.registerMBean(server);
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
	
	
	private static final class NotifyHandler implements IDataHandler, IConnectHandler, IDisconnectHandler {

		private final ArrayList<INonBlockingConnection> notifyList = new ArrayList<INonBlockingConnection>();
		
		public NotifyHandler() {
			Thread t = new Thread(new Notifier());
			t.start();
			
			Thread t2 = new Thread() {
				@Override
				public void run() {
					
					int countClients = 0;
					
					while (true) {
						try {
							Thread.sleep(5000);
						} catch (InterruptedException ignore) { }
						
						if (getClientList().size() != countClients) {
							System.out.println(getClientList().size() + " clients connected");
							countClients = getClientList().size();
						}
					}
				}
			};
			t2.setDaemon(true);
			t2.setPriority(Thread.MIN_PRIORITY);
			t2.start();
			
		}

		
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			System.out.println("client added");
			
			return true;
		}
		
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			System.out.println("client removed");

			synchronized (notifyList) {
				notifyList.remove(connection);	
			}
			
			return true;
		}
		
	    
	    //@Execution(Execution.Mode.NONTHREADED)
	    public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
	    	
	    	String cmd = connection.readStringByDelimiter("\r\n");
	    	
	    	if (cmd.equals("SYN")) {
	    		System.out.println("SYNC called");
	    		for (int i = 0; i < INIT_LOOPS; i++) {
	    			connection.write(System.currentTimeMillis() + "\r\n");
	    		}
	    		
	    	} else if (cmd.equals("NOTIFY")) {
	    		System.out.println("NOTIFY called (suspend read)");
	    		synchronized (notifyList) {
	    			notifyList.add(connection);	
	    		}
	    		
	    		connection.suspendRead();
	    	}
	    	return true;
	    }
	    
	    
	    private ArrayList<INonBlockingConnection> getClientList() {
			ArrayList<INonBlockingConnection> notifyListCopy = null;
			synchronized (notifyList) {
				notifyListCopy = (ArrayList<INonBlockingConnection>) notifyList.clone();
			}

			return notifyListCopy;
		}
	    

	    private final class Notifier implements Runnable {
	    	
	    	@Override
	    	public void run() {
	    		
	    		while(true) {
	    			try {
	    				for (INonBlockingConnection nonBlockingConnection : getClientList()) {
	    					try {
	    						nonBlockingConnection.write(System.currentTimeMillis() + "\r\n");
	    					} catch (ClosedChannelException cce) {
	    						
	    						nonBlockingConnection.close();
	    						
	    						synchronized (notifyList) {
	    							notifyList.remove(nonBlockingConnection);
								}
	    						
	    					}
						}	    				
	    			} catch (Exception e) {
	    				e.printStackTrace();
	    			}
	    			
	    		}
	    	}
	    	
	    }
	}
}
