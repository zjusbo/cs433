package org.xsocket.connection;





public final class MinaServer {

	
	public static void main(String... args) throws Exception {
		
		//System.setProperty(IServerIoProvider.PROVIDER_CLASSNAME_KEY, org.xsocket.stream.io.mina.MinaIoProvider.class.getName());
		
		
		if (args.length < 1) {
			System.out.println("usage org.xsocket.stream.MinaServer <port> <pause> <writeTofile> <syncFlush> <workers>");
			System.exit(-1);
		}
			
		new XSocketEchoServer().launch(args);
	}		
}
