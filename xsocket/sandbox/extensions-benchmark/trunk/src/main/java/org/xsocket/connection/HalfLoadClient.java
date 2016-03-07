package org.xsocket.connection;


import java.io.IOException;




public class HalfLoadClient {
	


	private String host = null;
	private int port = 0;
	
	
	public static void main(String... args) throws Exception {
		
		HalfLoadClient loadClient = new HalfLoadClient();
		loadClient.host = args[0];
		loadClient.port = Integer.parseInt(args[1]);
		
		loadClient.testLoad();
	}
	
	
	public void testLoad() throws Exception {
		
		for (int i = 0; i < 100; i++) {
			Thread t = new Thread(new PersistentWorker(host, port));
			t.setDaemon(true);
			t.start();
		}
		
		for (int i = 0; i < 50; i++) {
			Thread t = new Thread(new NonPersistentWorker(host, port));
			t.setDaemon(true);
			t.start();
		}
	
		Thread.sleep(10000000);
	}
	
	
	
	private static final class PersistentWorker implements Runnable {
		
		private IBlockingConnection con = null;
		
		public PersistentWorker(String host, int port) throws IOException {
			con = new BlockingConnection(host, port);
		}
		
		
		public void run() {
			
			try {
				while (true) {
					con.write("test1234567\r\n");
					String result = con.readStringByDelimiter("\r\n");
					if (!result.equals("test1234567")) {
						System.out.println("Error! got " + result);
					}
					//System.out.print(".");
					
					try {
						Thread.sleep(100);
					} catch (InterruptedException ignore) { }
				}
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
	}
	
	
	private static final class NonPersistentWorker implements Runnable {
		
		private String host;
		private int port;
		
		public NonPersistentWorker(String host, int port) {
			this.host = host;
			this.port = port;
		}
		
		
		public void run() {
			
			try {
				while (true) {
					IBlockingConnection con = new BlockingConnection(host, port);
					con.write("test1234567\r\n");
					String result = con.readStringByDelimiter("\r\n");
					if (!result.equals("test1234567")) {
						System.out.println("Error! got " + result);
					}
					con.close();
					
					//System.out.print("*");
					try {
						Thread.sleep(1000);
					} catch (InterruptedException ignore) { }
				}
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
	}
}
