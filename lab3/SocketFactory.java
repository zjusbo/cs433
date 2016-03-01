

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public class SocketFactory {
	private final InetAddress destIPAddress;
	private final int port;
	public SocketFactory(InetAddress destIPAddress, int port){
		this.destIPAddress = destIPAddress;
		this.port = port;
	}
	public Socket getSocket() throws IOException{
		return new Socket(this.destIPAddress, this.port);
	}
}
