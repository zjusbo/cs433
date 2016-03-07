package org.xsocket.server;

import java.io.IOException;

import org.xsocket.BlockingConnection;
import org.xsocket.IBlockingConnection;

public class RunnableEchoClient {
	
	private static final String DATA = "E"; 
	private static final String DELIMITER = System.getProperty("line.separator");

	
	
	public static void main(String... args) {
		new RunnableEchoClient().launch(args[0], Integer.parseInt(args[1]));
	}
	
	
	
	public void launch(final String host, final int port) {

		long elapsed = 0;
		
		
		// warm up
		for (int i = 0; i < 5; i++) {
			call(host, port);
		}

		// start 
		for (int i = 0; i < 10; i++) {
			elapsed += call(host, port);
		}
		 
		System.out.println(elapsed);
	}
	
	
	public long call(String host, int port)  {
		try {
			IBlockingConnection connection = new BlockingConnection(host, port);
			
			long start = System.nanoTime();
			connection.write(DATA + DELIMITER);
			connection.receiveStringByDelimiter(DELIMITER);
			long elapsed = System.nanoTime() - start;
			
			connection.close();
			
			return elapsed;
		} catch (IOException ioe) {
			ioe.printStackTrace();
			return 0;
		}
	}
	
}
