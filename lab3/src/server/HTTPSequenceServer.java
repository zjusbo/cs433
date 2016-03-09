package server;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class HTTPSequenceServer implements HTTPServer {
	private ServerConfig config;
	public HTTPSequenceServer(ServerConfig config) {
		this.config = config;
	}

	@SuppressWarnings("resource")
	public void start() throws IOException {
		ServerSocket welcomeSocket = new ServerSocket(config.port, 1);
		System.out.println("Server started");
		System.out.println(config);
		while (true) {
			// accept connection from connection queue
			Socket connectionSocket = welcomeSocket.accept();
			System.out.println("accepted connection from " + connectionSocket);
			RequestHandler.HandleConnectionSocket(connectionSocket);
		} // end of while (true)
		
	}
}