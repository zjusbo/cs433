package org.xsocket.connection;


import java.io.IOException;




public class LatencyCheckClient {
	


	private String host = null;
	private int port = 0;
	boolean isNewCon = true;
	
	
	public static void main(String... args) throws Exception {
		
		if (args.length != 3) {
			System.out.println("usage java org.xsocket.connection LatencyCheckClient <host> <port> isNewCon>");
			System.exit(-1);
			return;
		}
		
		LatencyCheckClient loadClient = new LatencyCheckClient();
		loadClient.host = args[0];
		loadClient.port = Integer.parseInt(args[1]);
		
		loadClient.isNewCon = Boolean.parseBoolean(args[2]); 
		
		loadClient.test();
	}
	
	
	public void test() throws Exception {
		
		IBlockingConnection con = new BlockingConnection(host, port);
		
		try {
			while (true) {
				
				long start = System.nanoTime();
				con.write("test1234567\r\n");
				String result = con.readStringByDelimiter("\r\n");
				long end = System.nanoTime();
				
				if (!result.equals("test1234567")) {
					System.out.println("Error! got " + result);
				}
				//System.out.print(".");
				
				double mikro = (end - start) / 1000;
				System.out.println(mikro / 1000 + " millis");
				
				
				
				try {
					Thread.sleep(100);
				} catch (InterruptedException ignore) { }
				
				if (isNewCon) {
					con.close();
					con = new BlockingConnection(host, port);
				}
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}
}
