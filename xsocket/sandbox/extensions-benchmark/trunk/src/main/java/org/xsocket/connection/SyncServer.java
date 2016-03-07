package org.xsocket.connection;




import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.xsocket.connection.IConnection.FlushMode;

 


public final class SyncServer {

	private static final int PRINT_PERIOD = 5000;
	
	
	private int count = 0;
	private long time = System.currentTimeMillis(); 

	private Timer timer = new Timer(true);

	
	private ServerSocket ss = null;
	
	SyncServer(int port) throws IOException {
		
		
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				try {
					String rate = printRate();
					System.out.println(rate);
				} catch (Exception ignore) { }
			}
		};
		timer.schedule(task, PRINT_PERIOD, PRINT_PERIOD);
		
		
		
		
		ss = new ServerSocket(port); 
		
		while (true) {
			Socket s = ss.accept();
			new Thread(new Worker(s)).start();
		}
	}
	

	public String printRate() {
		long current = System.currentTimeMillis();
		
		long rate = 0;
		
		if (count == 0) {
			rate = 0;
		} else {
			rate = (count * 1000) / (current - time);
		}

		reset();
		
		return Long.toString(rate);
	}
	
	private void reset() {
		time = System.currentTimeMillis(); 
		count = 0;
	}

	
	private final class Worker implements Runnable {
		
		private InputStream is = null;
		private OutputStream os = null;
		
		
		public Worker(Socket s) throws IOException {
			is = s.getInputStream();
			os = s.getOutputStream();
		}

		
		@Override
		public void run() {
			
			try {
				
				byte[] buffer = new byte[8192];
				
				while (true) { 
					int length = is.read(buffer);
					
					os.write(buffer, 0, length);
					
					count++;
				}				
				
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
	}
	
}
