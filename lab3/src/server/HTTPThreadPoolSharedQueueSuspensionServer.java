package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Vector;

public class HTTPThreadPoolSharedQueueSuspensionServer implements HTTPServer {
	private ServerConfig config;
	private ServerSocket welcomeSocket;
	private ServiceThread[] threads;
	private List<Socket> connSockPool;
	public HTTPThreadPoolSharedQueueSuspensionServer(ServerConfig config) throws IOException {
		this.config = config;
		this.welcomeSocket = new ServerSocket(this.config.port, 10);
		System.out.println("Server started");
		System.out.println(config);
		this.threads = new ServiceThread[config.threadPoolSize];
		this.connSockPool = new Vector<Socket>();
		for (int i = 0; i < threads.length; i++) {
			threads[i] = new ServiceThread(this.connSockPool);
			threads[i].start();
		}
	}

	@Override
	public void start() throws IOException {
	    while (true) {
	        try {
		        // accept connection from connection queue
		        Socket connSock = welcomeSocket.accept();
		        System.out.println("Main thread retrieve connection from " 
				                   + connSock);

		        // how to assign to an idle thread?
		        synchronized (connSockPool) {
		            connSockPool.add(connSock);
		            connSockPool.notifyAll();
		        } // end of sync
	        } catch (Exception e) {
	        	System.out.println("server run failed.");
	        } // end of catch
	    } // end of loop
		
	}
	class ServiceThread extends Thread {
		List<Socket> connSockPool;

		public ServiceThread(List<Socket> connSockPool) {
			// TODO Auto-generated constructor stub
			this.connSockPool = connSockPool;
		}

		@Override
		public void run() {
	    	
		    while (true) {
		        // get a new request connection
		        Socket s;
			    synchronized (connSockPool) {
		        	while (connSockPool.isEmpty()) {
			            try {
							connSockPool.wait();
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
			        }
				    // remove the first request
				    s = (Socket) connSockPool.remove(0); 
				    System.out.println("Thread " + this 
						       + " process request " + s);
			    } // end if
			     // end of sync
		         // end while
		        try {
					RequestHandler.HandleConnectionSocket(s);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}		
		    } // end while(true)

		}

	}

}
