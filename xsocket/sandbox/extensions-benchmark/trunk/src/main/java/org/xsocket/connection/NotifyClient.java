package org.xsocket.connection;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ThreadPoolExecutor;




public class NotifyClient {
	
	
	private Statistic statistic = null;
	private long diff = Long.MAX_VALUE;
	
	
	
	
	public static void main(final String... args) throws Exception {
		
		if (args.length == 0) {
			System.out.println("usage org.xsocket.connection.NotifyClient <host> <port> <numThreads> <isPrintStatistics>");
		}
		
		int count = Integer.parseInt(args[2]);  
		final boolean isPrintStatistics = Boolean.parseBoolean(args[3]);
		
		System.out.println("\r\n---------------------------------------");
		System.out.println("class: " + NotifyClient.class.getName());
		System.out.println("number of threads: " + count);
		System.out.println("print statistic: " + isPrintStatistics);
		System.out.println("-Dorg.xsocket.connection.dispatcher.handlesMaxCount=" + System.getProperty("org.xsocket.connection.dispatcher.handlesMaxCount"));
		System.out.println("---------------------------------------\r\n");
		
		for (int i = 0; i < count; i++) {
			new Thread() { 
				@Override
				public void run() {
					try {
						new NotifyClient().launch(args[0], Integer.parseInt(args[1]), isPrintStatistics);
						Thread.sleep(500);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}.start();	
		}
		
		
		
		try {
			Thread.sleep(1000000);
		} catch (InterruptedException ignore) { }
			
	}
	
	
	public void launch(String host, int port, boolean writeStatistics) throws Exception {
		

		ClientConnection con = new ClientConnection(host, port);

		
		if (writeStatistics) {
			
			con.send("SYN\r\n");
			
			long df = Long.MAX_VALUE;
			for (int i = 0; i < NotifyServer.INIT_LOOPS; i++) {
				long serverTime = Long.parseLong(con.readLine());
				long l = System.currentTimeMillis() - serverTime;
				if (Math.abs(l) < diff) {
					df = Math.abs(l);
					diff = l;
				}  
			}
			
			System.out.println("LocalTime - ServerTime: " + diff);
			statistic = new Statistic(20 * 1000);
		}

		con.send("NOTIFY\r\n");

		
		while(true) {
			String s = con.readLine();
			if (writeStatistics) {
				statistic.addValue((int) getAdjustedTime(System.currentTimeMillis() - Long.parseLong(s)));
			}
		}
	}
	
	private long getAdjustedTime(long servertime) {
		return servertime - diff;
	}
	
	
	
	private static final class ClientConnection {
		private Socket socket = null; 
		private LineNumberReader lnr = null;
        private PrintWriter pw = null;
    
        private BlockingConnection con = null;
        
        ClientConnection(String host, int port) throws IOException {
        //	socket = new Socket(host, port);
        //	socket.setReuseAddress(true);
         //   lnr = new LineNumberReader(new InputStreamReader(socket.getInputStream()));
         //   pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
        	
        	con = new BlockingConnection(host, port);
        }
        
        public String readLine() throws IOException {
        //	return lnr.readLine();
        	return con.readStringByDelimiter("\r\n");
        }
        
        public void send(String data) throws IOException {
        	//pw.write(data);
        	//pw.flush();
        	
        	con.write(data);
        }
        
        public void close() {
        	try {
        		//lnr.close();
        		//pw.close();
        		//socket.close();
        		con.close();
        	} catch (Exception ignore) { }
        }
	}
	
}
