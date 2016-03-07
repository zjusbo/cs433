package org.xsocket.connection;





public final class GrizzlyServer {

	
	public static void main(String... args) throws Exception {
		
//		System.setProperty(IServerIoProvider.PROVIDER_CLASSNAME_KEY, org.xsocket.stream.io.grizzly.GrizzlyIoProvider.class.getName());
		
		if (args.length < 1) {
			System.out.println("usage org.xsocket.stream.GrizzlyServer <port> <pause> <writeTofile> <syncFlush> <workers>");
			System.exit(-1);
		}
		
		new XSocketEchoServer().launch(args);
	}	
}
