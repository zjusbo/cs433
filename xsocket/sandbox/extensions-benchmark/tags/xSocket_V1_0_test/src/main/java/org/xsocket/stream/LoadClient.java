package org.xsocket.stream;


import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import org.xsocket.DataConverter;


public class LoadClient {
	
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
	private int waitBetweenCalls = 0;
	
	private Statistic statistic = null;
	
	
	public static void main(String... args) throws Exception {
		if (args.length != 8) {
			System.out.println("usage org.xsocket.stream.LoadClient <host> <port> <workers> <cons> <openNew?> <loops> <dataSize> <waitTime>");
			System.exit(-1);
		}
		
		LoadClient loadClient = new LoadClient();
		loadClient.host = args[0];
		loadClient.port = Integer.parseInt(args[1]);
		loadClient.countWorkers = Integer.parseInt(args[2]);
		loadClient.countConnections = Integer.parseInt(args[3]);
		loadClient.openNew = Boolean.parseBoolean(args[4]);
		loadClient.loops = Integer.parseInt(args[5]);
		loadClient.dataSize = Integer.parseInt(args[6]);
		loadClient.waitBetweenCalls = Integer.parseInt(args[7]);
		
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
		System.out.println("wait time between client calls   " + DataConverter.toFormatedDuration(waitBetweenCalls));
		System.out.println("---------------------------------------\r\n");
		
		
		
		List<Worker> workers = new ArrayList<Worker>();

		
		System.out.println("preparing workers (init connctions)...");
		for (int i = 0; i < countWorkers; i++) {
			Worker worker = new Worker(host, port, countConnections, openNew, loops, dataSize, waitBetweenCalls);
			workers.add(worker);
			worker.prepare();
		}
		
		
		
		statistic = new Statistic();
		
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
		private int waitBetweenCalls = 0;
		
		private ArrayList<ClientConnection> connections = new ArrayList<ClientConnection>();
		
		Worker(String host, int port, int countConnections,  boolean openNew, int loops,int dataSize, int waitBetweenCalls) throws IOException {
			this.host = host;
			this.port = port;
			this.countConnections = countConnections;
			this.openNew = openNew;
			this.loops = loops;
			this.dataSize =  dataSize;
			this.waitBetweenCalls = waitBetweenCalls;
		}
		
		
		public void prepare() {
			for (int i = 0; i < countConnections; i++) {
				newConnection();
			}			
		}
		
		@SuppressWarnings("unchecked")
		public void run() {
			
			String data = new String(TestUtil.generatedByteArray(dataSize));

			for (int i = 0; i < loops; i++) {
				ArrayList<ClientConnection> copy = (ArrayList<ClientConnection>) connections.clone();
				for (ClientConnection connection : copy) {
					try {
						String request = data + i;

						String response = null;
						long elapsed = 0;
						
						if (openNew) {
							long start = System.currentTimeMillis();
							ClientConnection con = new ClientConnection(host, port);
							response = con.send(request);
							elapsed = System.currentTimeMillis() - start;
							con.close();
							
						} else {
							long start = System.currentTimeMillis();
							response = connection.send(request);
							elapsed = System.currentTimeMillis() - start;
						}
						
						if (response == null) {
							throw new Exception("null response");
						} 
						
						if (request.equals(response)) {
							statistic.addValue((int) elapsed);
						} else {
							throw new Exception("wrong response: " + response);
						}
						
					} catch (Exception e) {
						System.out.println("error " + e.toString() + ";");
						connection.close();
						connections.remove(connection);
						newConnection();
					}
					
					if (waitBetweenCalls > 0) {
						TestUtil.sleep(waitBetweenCalls);
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
					connection = new ClientConnection(host, port);
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
    
        
        ClientConnection(String host, int port) throws IOException {
        	socket = new Socket(host, port);
        	socket.setReuseAddress(true);
            lnr = new LineNumberReader(new InputStreamReader(socket.getInputStream()));
            pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
        }
        
        public String send(String data) throws IOException {
        	pw.println(data);
        	pw.flush();
        	return lnr.readLine();
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
