package org.xsocket.connection;

import java.net.InetSocketAddress;

import org.apache.mina.common.DefaultIoFilterChainBuilder;
import org.apache.mina.transport.socket.SocketAcceptor;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;





public final class MinaNativeServer {

	public static void main( String[] args ) throws Exception {
		if (args.length != 4) {
			System.out.println("usage org.xsocket.stream.OtherEchoServer <port> <pause> <writeToFile> <workers>");
			System.exit(-1);
		}
		
		int port = Integer.parseInt(args[0]);
		int pause = Integer.parseInt(args[1]);
		boolean writeTofile = Boolean.parseBoolean(args[2]);
		int workers = Integer.parseInt(args[3]);


		SocketAcceptor acceptor = new NioSocketAcceptor();
		DefaultIoFilterChainBuilder chain = acceptor.getFilterChain();
	        

		// Bind
		acceptor.setHandler(new MinaEchoProtocolHandler());
		acceptor.bind(new InetSocketAddress(port));

		System.out.println("Listening on port " + port);
	        
		for (;;) {
			System.out.println("R: " + acceptor.getReadBytesThroughput() + ", W: " + acceptor.getWrittenBytesThroughput());
			Thread.sleep(3000);
		}
    }
}
