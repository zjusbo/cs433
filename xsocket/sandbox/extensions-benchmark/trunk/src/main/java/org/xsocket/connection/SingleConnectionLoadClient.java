package org.xsocket.connection;




public class SingleConnectionLoadClient {
	

	
	public static void main(String... args) throws Exception {
		if (args.length != 2) {
			System.out.println("usage org.xsocket.stream.LoadClient <host> <port>");
			System.exit(-1);
		}
		
		String host = args[0];
		int port = Integer.parseInt(args[1]);
		
	
		INonBlockingConnection con = new NonBlockingConnection(host, port);
		
		for (int i = 0; i < 9999999; i++) {
			con.write("test" + i);
			System.out.print(".");
			try {
				Thread.sleep(50);
			} catch (Exception e) { };
		}		
	}

}
