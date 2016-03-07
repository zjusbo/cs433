package org.xsocket.connection;




import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;

import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.xsocket.MaxReadSizeExceededException;

 


public final class BulkUploadServer {

	public static final String TEMP_FILE_EXTENSION = "xtmp";
	
	public static final String UPLOAD_REQUEST = "up";
	public static final String OK = "OK";
	public static final String DELIMITER = "\r\n";


	public static void main( String[] args ) throws Exception {
		
		if (args.length < 2) {
			System.out.println("usage org.xsocket.stream.BulkUploadServer <port> <WriteToFile> <workers>");
			System.exit(-1);
		}
		
		new BulkUploadServer().launch(args);
	}
		
		
	public void launch(String... args) throws Exception {
		
		int port = Integer.parseInt(args[0]);
		boolean writeToFile = Boolean.parseBoolean(args[1]);
		Server server = null;

		if (args.length > 2) {
			int workers = Integer.parseInt(args[2]);
			server = new Server(InetAddress.getByName("xp-lp-grro2"), port, new Handler(writeToFile));
			server.setWorkerpool(Executors.newFixedThreadPool(workers));
			
			System.out.println("using worker pool with maxSize=" + workers +  " (writeToFile=" + writeToFile + ")");
			
		} else {
			server = new Server("localhost", port, new Handler(writeToFile));
			System.out.println("using default worker pool (writeToFile=" + writeToFile + ")");
		}
		
		Thread t = new Thread(server);
		t.setName("xAcceptor");


		startJmxServer("test", 1599);
		ConnectionUtils.registerMBean(server);
		
		System.out.println("running BulkUploadServer");
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
		
		
		
		// start cleaner
		if (writeToFile) {
			new TempCleaner().launch();
		}
		
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
		
		private boolean writeToFile = true;
		
		
		public Handler(boolean writeToFile) {
			this.writeToFile = writeToFile;
		}
		
		
		public boolean getWriteToFile() {
			return writeToFile; 
		}
		
		public void setWriteToFile(boolean b) {
			this.writeToFile = b;
		}
		
		
		@Override
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

			IDataHandler delegee = (IDataHandler) connection.getAttachment(); 
			
			if (delegee != null) {
				boolean handled = delegee.onData(connection);
				if (handled) {
					connection.setAttachment(null);
				}
				
			} else {
				String cmd = connection.readStringByDelimiter(DELIMITER);
				
				if (cmd.startsWith(UPLOAD_REQUEST)) {
					int size = Integer.parseInt(cmd.substring(UPLOAD_REQUEST.length(), cmd.length()));
					delegee = new UploadHandler(size, writeToFile);
					connection.setAttachment(delegee);
				}
			}
			
			return true;
		}	
	}
	
	
	private static final class UploadHandler implements IDataHandler {
		
		private int totalSize = 0;
		private int loaded = 0;

		private boolean writeToFile = false;
		private File file = null;
		private FileChannel fc = null;
		
		public UploadHandler(int totalSize, boolean writeToFile) throws IOException {
			this.writeToFile = writeToFile;
			
			file = File.createTempFile("testfile", "tmp");
			fc = new RandomAccessFile(file, "rw").getChannel();
			
			this.totalSize = totalSize;
		}
		
		@Override
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			int remaining = totalSize - loaded;
			
			if (writeToFile) {
				loaded += connection.transferTo(fc, remaining);	

			} else {
				ByteBuffer[] bufs = connection.readByteBufferByLength(connection.available());
				for (ByteBuffer buffer : bufs) {
					loaded += buffer.remaining();
				}
			}
			
			
			if (loaded == totalSize) {
				connection.write(OK + DELIMITER);
				
				fc.close();
				file.renameTo(new File(file.getAbsolutePath().substring(0, (file.getAbsolutePath().length() - "tmp".length())) + TEMP_FILE_EXTENSION));
				return true;
				
			} else {
				return false;
			}
		}
		
	}
	
	
	private static final class TempCleaner  {
		
		private Timer timer = new Timer(true);

		public void launch() {
			try {
				File file = File.createTempFile("testfile", TEMP_FILE_EXTENSION);
				final File dir = file.getParentFile();
				file.delete();
				
				final FileFilter filter = new FileFilter() {
					@Override
					public boolean accept(File file) {
						return file.getName().endsWith(TEMP_FILE_EXTENSION);
					}
				};
				
				TimerTask cleaner = new TimerTask() {
					@Override
					public void run() {
						int deleted = 0;
						
						File[] files = dir.listFiles(filter);
						for (File file : files) {
							try {
								boolean del = file.delete();
								if (del) {
									deleted++;
								}
							} catch (Exception ignore) { }
						}
						
						//System.out.println(deleted + " files deleted");
					}
				};
				timer.schedule(cleaner, 5000, 5000);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}		
	}
	
}
