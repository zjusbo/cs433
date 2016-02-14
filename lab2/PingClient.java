

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.zip.DataFormatException;

public class PingClient {
	private static int repeat = 10;
	private static int timeout = 1000;// 1000 milliseconds
	//java PingClient host port passwd
	public static void main(String[] args) throws Exception{
    	
        // Create a datagram socket for receiving and sending
        // UDP packets through the port specified on the
        // command line.
		if(args.length < 3){
			System.out.println("Usage: PingClient <host> <port> <passwd>");
			return ;
		}
		String host = args[0];
		int port = Integer.parseInt(args[1]);
		String passwd = args[2];
        DatagramSocket socket = new DatagramSocket();
        InetAddress serverHost = InetAddress.getByName(host);
    	socket.setSoTimeout(timeout);
        int num_loss = 0;
        long total_delay, min_delay, max_delay;
        double avg_delay, loss_rate;
        total_delay = 0;
        min_delay = Long.MAX_VALUE;
        max_delay = Long.MIN_VALUE;
    	for(int i = 0; i < repeat; i++){
        	PingPackage pingPackage = new PingPackage();
        	pingPackage.setSequence_number((short)i);
        	pingPackage.setPasswd(passwd);
        	byte [] data = pingPackage.getBytes();
        	DatagramPacket request = new DatagramPacket(data, data.length, 
                    serverHost, port);
        	// send ping package
        	socket.send(request);
        	DatagramPacket
            	res = new DatagramPacket(new byte[1024], 1024);
        	//recv response
        	try{
        		socket.receive(res);
        	}
        	// time out
        	catch(SocketTimeoutException e){
        		System.out.println(i + " RTT: Loss");
        		num_loss++;
        		continue;
        	}
        	// res received
        	PingPackage recv = new PingPackage();
        	try{
        		recv.parse(res.getData());
        		if(!recv.isResponse()){
        			throw new DataFormatException("Not a valid response.");
        		}
        		
        	}catch(Exception e){
        		System.out.println(e.getMessage());
        	}
        	long delay = System.currentTimeMillis() - recv.getTimestamp();
        	min_delay = Math.min(delay, min_delay);
        	max_delay = Math.max(delay, max_delay);
        	System.out.println(i + " RTT: " + delay + "ms");
        	total_delay += delay;
        }
    	socket.close();
    	avg_delay = (double)total_delay / (repeat - num_loss);
    	loss_rate = (double)num_loss / repeat;
    	if(num_loss == repeat){
    		System.out.println("Loss Rate: 100%");
    	}
    	else{
    		System.out.println("Avg RTT: " + avg_delay + "ms, Max RTT: " + max_delay + "ms, Min RTT: " + min_delay + "ms, Loss Rate: " + loss_rate);
    	}
    }
}
