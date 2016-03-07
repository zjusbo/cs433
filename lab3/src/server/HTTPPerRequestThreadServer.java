package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class HTTPPerRequestThreadServer implements HTTPServer {
	private ServerConfig config;

	public HTTPPerRequestThreadServer(ServerConfig config) {
		// TODO Auto-generated constructor stub
		this.config = config;
	}

	@SuppressWarnings("resource")
	@Override
	public void start() throws IOException {
		// TODO Auto-generated method stub
		ServerSocket welcomeSocket = new ServerSocket(config.port, 10);
		System.out.println("Server started");
		System.out.println(config);
		while (true) {
			// accept connection from connection queue
			Socket connectionSocket = welcomeSocket.accept();
			System.out.println("accepted connection from " + connectionSocket);
			Thread t = new Thread(new ConnectionSocketHandler(connectionSocket));
			t.start();
		} // end of while (true)
	}
	class ConnectionSocketHandler implements Runnable {
		Socket connectionSocket;

		public ConnectionSocketHandler(Socket connectionSocket) {
			this.connectionSocket = connectionSocket;
		}

		@Override
		public void run() {
			try {
				RequestHandler.HandleConnectionSocket(connectionSocket);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

}

