package org.xsocket.connection;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;





public final class ClassicProxy implements Runnable {

	private Server server = null;

	
	
	public static void main(String... args) throws IOException {
		
		if (args.length != 3) {
			System.out.println("usage java org.xsocket.stream.ClassicProxy <listenPort> <forwardAddress> <forwardPort>");
			System.exit(-1);
		}
		
		int listenPort = Integer.parseInt(args[0]);
		InetAddress forwardAddress = InetAddress.getByName(args[1]);
		int forwardPort = Integer.parseInt(args[2]);
		new ClassicProxy(listenPort, forwardAddress, forwardPort).run();
		
	}
	
	
	public ClassicProxy(int listenPort, InetAddress forwardAddress, int forwardPort) throws IOException {
		server = new Server(listenPort, forwardAddress, forwardPort);
		
		System.out.println("classic proxy: listenport=" + listenPort + " forwardAddress=" + forwardAddress + ":" + forwardPort);
	}
	
	
	public void run() {
		server.run();
	}
	
	
	public void close() throws IOException {
		if (server != null) {
			server.close();
		}
	}


	private static final class Server {
		
		private ExecutorService executor = Executors.newCachedThreadPool();
		
		private ServerSocket ssocket = null;
		private boolean isRunning = true;
		
		private InetAddress forwardAddress = null;
		private int forwardPort = 0;

		private Server(int port, InetAddress forwardAddress, int forwardPort) throws IOException {
        	this.forwardAddress = forwardAddress;
        	this.forwardPort = forwardPort;
        	ssocket = new ServerSocket(port);
        }   
    		
        
        int getLocalPort() {
        	return ssocket.getLocalPort();
        }
		
        void run() {
        	while (isRunning) {
        		try {
        			Socket socket = ssocket.accept();
        			if (socket != null) {
       					executor.execute(new ForwardHandler(socket, forwardAddress, forwardPort, executor));
        			}
        		} catch (Exception ignore) { }
        	}
        }
        
        boolean isOpen() {
        	return isRunning;
        }

        
        void close() {
        	isRunning = false;
        	
        	try {
        		ssocket.close();
        	} catch (Exception ignore) { }
        }
	}
	
	
	
	
	private static class Handler implements Runnable {
		
		private static final boolean DEBUG = false;

		private boolean isRunning = true;
		
		private BufferedInputStream in = null;
		private BufferedOutputStream out = null;
		private int preallocSize = 0;
		private String info = null;

		private Handler peer = null;
		
		void init(InputStream in, OutputStream out, int preallocSize, String info, Handler peer) {
			this.in = new BufferedInputStream(in);
			this.out = new BufferedOutputStream(out);
			this.preallocSize = preallocSize;
			this.info = info;
			this.peer = peer;
		}
		
		void setPeer(Handler peer) {
			this.peer = peer;
		}

		Handler getPeer() {
			return peer;
		}
		
		public final void run() {
			try {
				byte[] buf = new byte[preallocSize];
				int i = 0;
				while((i = in.read(buf))!= -1) {
				    	
					if (DEBUG ) {
						System.out.println(info + " size=" + i);
					}
					out.write(buf, 0, i);
					out.flush();
				}
			    	
			} catch (Exception ignore) { }

			close();
		}
		
		boolean isOpen() {
			return isRunning;
		}

	        
		void close() {
			isRunning = false;
	        	
			try {
				in.close();
				out.close();
			} catch (Exception ignore) { }
			
			if (peer != null) {
				if (peer.isOpen()) {
					peer.close();
				}
			}
		}
	}
	
	
	
	
	private static final class ForwardHandler extends Handler implements Runnable {
		
		private Socket serviceSocket = null;
        private Socket forwardSocket = null;
        

		public ForwardHandler(Socket serviceSocket, InetAddress forwardAddress, int forwardPort, ExecutorService executor) throws IOException {
			this.serviceSocket = serviceSocket;
			
			try {
				forwardSocket = new Socket(forwardAddress, forwardPort);
			
				init(serviceSocket.getInputStream(), forwardSocket.getOutputStream(), serviceSocket.getReceiveBufferSize(), serviceSocket.getInetAddress().getHostName() + " -> " + forwardSocket.getInetAddress().getHostName(), null);
			
				Handler reverser = new Handler();
				reverser.init(forwardSocket.getInputStream(), serviceSocket.getOutputStream(), forwardSocket.getReceiveBufferSize(), forwardSocket.getInetAddress().getHostName() + " -> " + serviceSocket.getInetAddress().getHostName(), this);
				
				setPeer(reverser);
				
				executor.execute(reverser);
				
			} catch (IOException ioe) {
				System.out.println("couldn't connect " + forwardAddress + ":" + forwardPort + " " + ioe.toString());
				close();
				
				throw ioe;
			}
		}
		


	   	public void close()  {
	   		try {
			   forwardSocket.close();
			   serviceSocket.close();
	   		} catch (Exception ignore) { }
	   	}
	}	
}
