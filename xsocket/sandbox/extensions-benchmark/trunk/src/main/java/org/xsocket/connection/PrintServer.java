package org.xsocket.connection;



import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.xsocket.DataConverter;
import org.xsocket.MaxReadSizeExceededException;



public final class PrintServer {


	public static void main( String[] args ) throws Exception {
		
		
		if (args.length < 1) {
			System.out.println("usage org.xsocket.stream.PrintServer <port>");
			System.exit(-1);
		}
		
		
		IServer server = new Server(Integer.parseInt(args[0]), new Handler());
		ConnectionUtils.start(server);
	}
	
	
	
	private static final class Handler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			ByteBuffer[] buffers = connection.readByteBufferByLength(connection.available());
			System.out.println(DataConverter.toHexString(buffers, Integer.MAX_VALUE));
			System.out.println("");
			return true;
		}
	}
		
}
