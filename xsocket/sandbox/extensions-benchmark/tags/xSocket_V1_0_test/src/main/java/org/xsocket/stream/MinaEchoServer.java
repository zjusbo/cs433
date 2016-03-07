package org.xsocket.stream;

import java.net.InetSocketAddress;

import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoAcceptorConfig;
import org.apache.mina.transport.socket.nio.SocketAcceptor;
import org.apache.mina.transport.socket.nio.SocketAcceptorConfig;

import edu.emory.mathcs.backport.java.util.concurrent.Executors;




public final class MinaEchoServer {

	public static void main( String[] args ) throws Exception {
		if (args.length != 3) {
			System.out.println("usage org.xsocket.stream.MinaEchoServer <port> <pause> <workers>");
			System.exit(-1);
		}
		
		int port = Integer.parseInt(args[0]);
		int pause = Integer.parseInt(args[1]);
		int workers = Integer.parseInt(args[2]);

		IoAcceptorConfig config = new SocketAcceptorConfig();
	        
		IoAcceptor acceptor = new SocketAcceptor(workers, Executors.newCachedThreadPool());
		
		System.out.println("running mina echo server with " + workers + " workers");
		acceptor.bind(new InetSocketAddress(port), new EchoHandler(pause), config);
    }
}
