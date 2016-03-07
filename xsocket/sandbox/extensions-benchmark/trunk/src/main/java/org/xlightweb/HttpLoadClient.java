package org.xlightweb;


import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import org.xsocket.connection.Statistic;
import org.xsocket.connection.TestUtil;



public class HttpLoadClient {
	
	/*
	 * http://www.minq.se/products/pureload/tuning/connections.html
	 * 
	 * win xp
	 * http://support.microsoft.com/kb/314053/de
	 * http://support.microsoft.com/kb/196271/de
	 */

	private String host = null;
	private int port = 0;
	private int countWorkers = 0;
	private int countConnections = 0;
	private int loops = 0;
	private boolean openNew = false;
	private int dataSize = 0;
	
	private Statistic statistic = null;
	
	
	public static void main(String... args) throws Exception {
		if (args.length != 7) {
			System.out.println("usage org.xlightweb.HttpLoadClient <host> <port> <workers> <cons> <openNew?> <loops> <dataSize>");
			System.exit(-1);
		}
		
		HttpLoadClient loadClient = new HttpLoadClient();
		loadClient.host = args[0];
		loadClient.port = Integer.parseInt(args[1]);
		loadClient.countWorkers = Integer.parseInt(args[2]);
		loadClient.countConnections = Integer.parseInt(args[3]);
		loadClient.openNew = Boolean.parseBoolean(args[4]);
		loadClient.loops = Integer.parseInt(args[5]);
		loadClient.dataSize = Integer.parseInt(args[6]);
		
		loadClient.testLoad();
	}
	
	
	public void testLoad() throws Exception {
		
		
		System.out.println("running test client with (server="  + host + ":" + port + "; loops=" + loops + ")");
			
		System.out.println("\r\n---------------------------------------");
		System.out.println("Load-info:");
		System.out.println("data packet size                 " + dataSize + " bytes");
		System.out.println("concurrent client threads        " + countWorkers);
		System.out.println("concurrent connections           " + countConnections * countWorkers);
		System.out.println("new connection for each request? " + openNew);
		System.out.println("---------------------------------------\r\n");
		
		
		
		List<Worker> workers = new ArrayList<Worker>();

		
		System.out.println("preparing workers (init connctions)...");
		for (int i = 0; i < countWorkers; i++) {
			Worker worker = new Worker(i, host, port, countConnections, openNew, loops, dataSize);
			workers.add(worker);
			worker.prepare();
		}
		
		
		
		statistic = new Statistic(40 * 1000);
		
		System.out.println("run workers");		
		for (Worker worker : workers) {
			Thread t = new Thread(worker);
			t.start();
		}
		

		boolean allFinished;
		do {
			allFinished = true;
			TestUtil.sleep(1000);
			
			for (Worker worker : workers) {
				allFinished = allFinished & worker.isRunning();
			}
			
		} while (!allFinished); 
		
		
	}
	
	
	
	
	Socket createClientSocket(String host, int port) throws IOException {
		return new Socket(host, port);
	}
	
	
	
	private final class Worker implements Runnable {
		
		private boolean isRunning = true;
		
		private String host = null;
		private int port = 0;
		private int countConnections = 0;
		private boolean openNew = false; 
		private int loops = 0;
		private int dataSize = 0;
		private int instanceNum = 0;
		
		private ArrayList<ClientConnection> connections = new ArrayList<ClientConnection>();
		
		Worker(int instanceNum, String host, int port, int countConnections,  boolean openNew, int loops,int dataSize) throws IOException {
			this.instanceNum = instanceNum;
			this.host = host;
			this.port = port;
			this.countConnections = countConnections;
			this.openNew = openNew;
			this.loops = loops;
			this.dataSize =  dataSize;
		}
		
		
		public void prepare() {
			for (int i = 0; i < countConnections; i++) {
				newConnection();
				System.out.print(".");
			}			
		}
		
		@SuppressWarnings("unchecked")
		public void run() {
			


			for (int i = 0; i < loops; i++) {
				ArrayList<ClientConnection> copy = (ArrayList<ClientConnection>) connections.clone();
				for (ClientConnection connection : copy) {
					try {
						long elapsed = 0;
						
						if (openNew) {
							ClientConnection con = new ClientConnection(host, port, dataSize);
							elapsed = con.send(instanceNum + "_" + i, "HTTP/1.1 200 OK");
							con.close();
							
						} else {
							elapsed = connection.send(instanceNum + "_" + i, "HTTP/1.1 200 OK");
						}
						
						 
						statistic.addValue((int) elapsed);
						
					} catch (Exception e) {
						System.out.println("error " + e.toString() + ";");
						connection.close();
						connections.remove(connection);
						newConnection();
					}
				}
			}
			
			isRunning = false;
		}
		
		public boolean isRunning() {
			return isRunning;
		}
		
		private ClientConnection newConnection() {
			ClientConnection connection = null;
			do {
				try {
					connection = new ClientConnection(host, port, dataSize);
				} catch (Exception ex) { 
					try {
						Thread.sleep(100);
					} catch (InterruptedException ignore) { }
				}
			} while (connection == null);
			
			connections.add(connection);
			return connection;
		}
	}
	
	
	
	
	
	private static final class ClientConnection {
		private Socket socket = null; 
		private LineNumberReader lnr = null;
        private PrintWriter pw = null;
    
        private int dataSize = 0;
        private char[] body = null;
        
        ClientConnection(String host, int port, int dataSize) throws IOException {
        	socket = new Socket(host, port);
        	socket.setReuseAddress(true);
            lnr = new LineNumberReader(new InputStreamReader(socket.getInputStream()));
            pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            
            this.dataSize = dataSize;
            
            body = new char[dataSize];
        }
        
        public int send(String num, String expectedFirstLine) throws IOException {
        	
            String request =  "GET /test?dataLength=" + dataSize + "&num=" + num + " HTTP/1.1\r\n" +
 		   			          "Host: localhost\r\n" +
 		                      "User-Agent: me\r\n" +
 		                      "\r\n";
            
        	
        	long start = System.currentTimeMillis();
        	pw.println(request);
        	pw.flush();
      
        	
        	String line = null;
        	String first = null;
        	do {
        		line = lnr.readLine();
        		if (first == null) {
        			first = line;
        			if (!first.equals(expectedFirstLine)) {
        				throw new IOException("got " + first); 
        			}

        		}
        	} while (line.length() > 0);
        	

        	int read = 0;
        	if (dataSize > 0) {
        		do {
        			read += lnr.read(body);
        		} while (read < dataSize);
        	}        	
        	long stop = System.currentTimeMillis();
        	
        
        	return (int) (stop - start);
        }
        
        public void close() {
        	try {
        		lnr.close();
        		pw.close();
        		socket.close();
        	} catch (Exception ignore) { }
        }
	}
}
