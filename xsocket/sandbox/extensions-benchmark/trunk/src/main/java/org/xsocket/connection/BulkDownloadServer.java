package org.xsocket.connection;




import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.xsocket.MaxReadSizeExceededException;

 


public final class BulkDownloadServer {

	public static final String TEMP_FILE_EXTENSION = "xtmp";
	
	public static final String DOWNLOAD_REQUEST = "down";
	public static final String PREPARE_DOWNLOAD = "prepareDown";
	public static final String OK = "OK";
	public static final String DELIMITER = "\r\n";


	public static void main( String[] args ) throws Exception {
		
		if (args.length < 2) {
			System.out.println("usage org.xsocket.stream.BulkDownloadServer <port> <ReadFromFile> <workers>");
			System.exit(-1);
		}
		
		new BulkDownloadServer().launch(args);
	}
		
		
	public void launch(String... args) throws Exception {
		
		int port = Integer.parseInt(args[0]);
		boolean readFromFile = Boolean.parseBoolean(args[1]);
		Server server = null;

		if (args.length > 2) {
			int workers = Integer.parseInt(args[2]);
			server = new Server(InetAddress.getByName("xp-lp-grro2"), port, new Handler(readFromFile));
			server.setWorkerpool(Executors.newFixedThreadPool(workers));
			
			System.out.println("using worker pool with maxSize=" + workers +  " (readFromFile=" + readFromFile + ")");
			
		} else {
			server = new Server("localhost", port, new Handler(readFromFile));
			System.out.println("using default worker pool (readFromFile=" + readFromFile + ")");
		}
		
		Thread t = new Thread(server);
		t.setName("xAcceptor");


		startJmxServer("test", 1899);
		ConnectionUtils.registerMBean(server);
		
		System.out.println("running BulkDownloadServer");
		t.start();
		
		
		final IServer srv = server;
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				try {
					srv.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
				
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
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	

	
	private static final class Handler implements IDataHandler {
		
		private boolean readFromFile = true;
		
		public Handler(boolean readFromFile) {
			this.readFromFile = readFromFile;
		}
		
		
		public boolean getReadFromFile() {
			return readFromFile; 
		}
		
		public void setReadFromFile(boolean b) {
			this.readFromFile = b;
		}
		
		
		@Override
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			
			String cmd = connection.readStringByDelimiter(DELIMITER);
				
			if (cmd.startsWith(PREPARE_DOWNLOAD)) {
				Integer size = Integer.parseInt(cmd.substring(PREPARE_DOWNLOAD.length(), cmd.length()));
					
				if (readFromFile) {
					File file = File.createTempFile("test", TEMP_FILE_EXTENSION);
					file.deleteOnExit();
					
					FileChannel fc = new RandomAccessFile(file, "rw").getChannel();
					fc.write(QAUtil.generateDirectByteBuffer(size));
					fc.close();
					
					connection.setAttachment(file);
				} else {
					connection.setAttachment(size);
				}
				connection.write(OK + DELIMITER);
					
			} else if (cmd.startsWith(DOWNLOAD_REQUEST)) {
				if (readFromFile) {
					try {
						File file = (File) connection.getAttachment();
						FileChannel fc = new RandomAccessFile(file, "r").getChannel();
						connection.transferFrom(fc);
						fc.close();
						connection.write(DELIMITER);
						
					} catch (Exception e) {
						e.printStackTrace();
					}
					
				} else {
					Integer size = (Integer) connection.getAttachment();
					connection.write(QAUtil.generateDirectByteBuffer(size));
					connection.write(DELIMITER);
				}
			}
			
			return true;
		}	
	}
}
