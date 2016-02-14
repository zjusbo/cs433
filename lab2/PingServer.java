
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;
import java.util.zip.DataFormatException;

    /* 
     * Server to process ping requests over UDP.
     */
        
    public class PingServer
    {
       private static double LOSS_RATE = 0.3;
       private static int AVERAGE_DELAY = 100; // milliseconds
       private static String passwd = "";
       //java PingServer port passwd [-delay delay] [-loss loss]
       public static void main(String[] args) throws Exception
       {
          // Get command line argument.
          if (args.length < 2) {
             System.out.println("Required arguments: <port> <passwd> [-delay delay] [-loss loss]");
             return;
          }

          int port = Integer.parseInt(args[0]);
          passwd = args[1];
          for(int i = 2; i < args.length - 1; i += 2){
        	  String k = args[i];
        	  String v = args[i + 1];
        	  if(k.equals("-delay")){
        		  AVERAGE_DELAY = Integer.parseInt(v);
        	  }else if(k.equals("-loss")){
        		  LOSS_RATE = Double.parseDouble(v);
        	  }else{
        		  // ignore it
        		  System.out.println("ignore unknown argument: " + k);
        		  i--;
        	  }
          }
          // Create random number generator for use in simulating
          // packet loss and network delay.
          Random random = new Random();

          // Create a datagram socket for receiving and sending
          // UDP packets through the port specified on the
          // command line.
          DatagramSocket socket = new DatagramSocket(port);

          // Processing loop.
          System.out.println("Server port = " + port + ", Avg delay = " + AVERAGE_DELAY + "ms, Loss rate = " + LOSS_RATE);
          while (true) {

             // Create a datagram packet to hold incomming UDP packet.
             DatagramPacket
                request = new DatagramPacket(new byte[1024], 1024);
      
             // Block until receives a UDP packet.
             socket.receive(request);
         
             // Print the received data, for debugging
             //printData(request);

             // Decide whether to reply, or simulate packet loss.
             if (random.nextDouble() < LOSS_RATE) {
                System.out.println(" Reply not sent.");
                continue;
             }

             // Simulate prorogation delay.
             Thread.sleep((int) (random.nextDouble() * 2 * AVERAGE_DELAY));

             // Send reply.
             InetAddress clientHost = request.getAddress();
             int clientPort = request.getPort();
             byte[] buf = request.getData();
             PingPackage pingPackage = new PingPackage();
             try{
            	 pingPackage.parse(buf);
            	 System.out.println(pingPackage);
            	 if(!pingPackage.isRequest()){
            		 throw new DataFormatException("Not a valid request ping package");
            	 }
            	 // password wrong
            	 if(!pingPackage.passwd.equals(passwd)){
            		 throw new DataFormatException("Password incorrect: " + pingPackage.passwd);
            	 }
            	 pingPackage.toResponse();
            	 buf = pingPackage.getBytes();
             }catch(Exception e){
            	 // error
            	 System.out.println(e + ": " + e.getMessage());
            	 continue;
             }
             
             DatagramPacket
             reply = new DatagramPacket(buf, buf.length, 
                                        clientHost, clientPort);
        
             socket.send(reply);
        
             System.out.println(" Reply sent.");
         } // end of while
       } // end of main

       /* 
        * Print ping data to the standard output stream.
        */
       private static void printData(DatagramPacket request) 
               throws Exception

       {
          // Obtain references to the packet's array of bytes.
          byte[] buf = request.getData();

          // Wrap the bytes in a byte array input stream,
          // so that you can read the data as a stream of bytes.
          ByteArrayInputStream bais 
              = new ByteArrayInputStream(buf);

          // Wrap the byte array output stream in an input 
          // stream reader, so you can read the data as a
          // stream of **characters**: reader/writer handles 
          // characters
          InputStreamReader isr 
              = new InputStreamReader(bais);

          // Wrap the input stream reader in a bufferred reader,
          // so you can read the character data a line at a time.
          // (A line is a sequence of chars terminated by any 
          // combination of \r and \n.)
          BufferedReader br 
              = new BufferedReader(isr);

          // The message data is contained in a single line, 
          // so read this line.
          String line = br.readLine();

          // Print host address and data received from it.
          System.out.println("Received from " +         
            request.getAddress().getHostAddress() +
            ": " +
            new String(line) );
         } // end of printData
       } // end of class

