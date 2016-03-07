package org.xsocket.connection;




 


public final class XSocketSyncEchoServer {


	public static void main( String[] args ) throws Exception {
		
		if (args.length < 1) {
			System.out.println("usage org.xsocket.connection.XSocketEchoServer <port>");
			System.exit(-1);
		}
		
		new XSocketSyncEchoServer().launch(args);
	}
		
		
	public void launch(String... args) throws Exception {
		
		
		
	
		int port = Integer.parseInt(args[0]);
		
		
		System.out.println("running echo server");

		
		SyncServer server = new SyncServer(port);
		

    }
}
