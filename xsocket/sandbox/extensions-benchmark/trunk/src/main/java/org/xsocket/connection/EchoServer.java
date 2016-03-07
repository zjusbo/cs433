package org.xsocket.connection;




import java.io.IOException;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;

import org.xsocket.MaxReadSizeExceededException;




public final class EchoServer {


	public static void main( String[] args ) throws Exception {
		
		new EchoServer().launch(args);
	}
		
		
	public void launch(String... args) throws Exception {
	
		int port = Integer.parseInt(args[0]);
		int rcvBufferSize = Integer.parseInt(args[1]);
		
		IServer server = new Server(InetAddress.getLocalHost(), port, new Handler(rcvBufferSize));
	    ConnectionUtils.start(server);
		
		while (true) {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException ignore) { }
		}
    }
	
	private static final class Handler implements IConnectHandler, IDataHandler {
		
		private int rcvBufferSize = 0;
		
		Handler(int rcvBufferSize) {
			this.rcvBufferSize = rcvBufferSize;
		}
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setOption(IConnection.SO_RCVBUF, rcvBufferSize);
			if (((Integer) connection.getOption(IConnection.SO_RCVBUF)) != rcvBufferSize) {
				System.out.println("not set!");
			}
			
			connection.setOption(IConnection.SO_SNDBUF, rcvBufferSize);
			if (((Integer) connection.getOption(IConnection.SO_SNDBUF)) != rcvBufferSize) {
				System.out.println("not set!");
			}
			connection.suspendRead();
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

			System.out.println("do nothing");
			
			return true;
		}
	}
	
}
