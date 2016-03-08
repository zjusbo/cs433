package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import asyncServer.Debug;

public class HTTPThreadPoolCompetingWelcomSocketServer implements HTTPServer {
	ServerConfig config;
	ServerSocket welcomeSocket;
	Thread threads[];

	public HTTPThreadPoolCompetingWelcomSocketServer(ServerConfig config) {
		// TODO Auto-generated constructor stub
		this.config = config;
	}

	@Override
	public void start() throws IOException {
		// TODO Auto-generated method stub
		this.welcomeSocket = new ServerSocket(config.port, 10);
		System.out.println("Server started");
		System.out.println(config);
		threads = new ServiceThread[config.threadPoolSize];
		for (int i = 0; i < threads.length; i++) {
			threads[i] = new ServiceThread(this.welcomeSocket);
			threads[i].start();
		}
	}

	class ServiceThread extends Thread {
		ServerSocket welcomeSocket;

		public ServiceThread(ServerSocket welcomeSocket) {
			// TODO Auto-generated constructor stub
			this.welcomeSocket = welcomeSocket;
		}

		@Override
		public void run() {
			// TODO Auto-generated method stub
			// create read stream to get input
			// BufferedReader inFromClient = new BufferedReader(new
			// InputStreamReader(connectionSocket.getInputStream()));
			while (true) {
				synchronized (welcomeSocket) {
					try {
						Debug.DEBUG("waiting for new connection");
						Socket connectionSocket = welcomeSocket.accept();
						RequestHandler.HandleConnectionSocket(connectionSocket);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}

		}

	}

}
