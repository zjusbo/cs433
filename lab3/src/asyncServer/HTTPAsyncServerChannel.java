package asyncServer;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;

import server.HTTPServer;
import server.RequestHandler;
import server.ServerConfig;
import utility.Debug;
import utility.HTTPRequest;
import utility.HTTPResponse;
public class HTTPAsyncServerChannel implements HTTPServer{
	ServerConfig config;
	public static void main(String[] args) throws Exception {
    
  }
  public HTTPAsyncServerChannel(ServerConfig config){
	  this.config = config;
  }
  
  @Override
  public void start() throws IOException{
	  AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open();
		    InetSocketAddress sAddr = new InetSocketAddress(config.servername, config.port);
		    server.bind(sAddr);
		    System.out.format("Server is listening at %s%n", sAddr);
		    Attachment attach = new Attachment();
		    attach.server = server;
		    server.accept(attach, new ConnectionHandler());
		    try{
		    	Thread.currentThread().join();
		    }catch(InterruptedException e){
		    	e.printStackTrace();
		    	
		    }
		      
  }

}
class Attachment {
  AsynchronousServerSocketChannel server;
  AsynchronousSocketChannel client;
  ByteBuffer buffer;
  SocketAddress clientAddr;
  boolean isRead;
}

// accept handler
class ConnectionHandler implements
    CompletionHandler<AsynchronousSocketChannel, Attachment> {
  @Override
  public void completed(AsynchronousSocketChannel client, Attachment attach) {
    try {
      SocketAddress clientAddr = client.getRemoteAddress();
      Debug.DEBUG(String.format("Accepted a  connection from  %s%n", clientAddr));
      attach.server.accept(attach, this); // accept for next connection
      ReadWriteHandler rwHandler = new ReadWriteHandler();
      Attachment newAttach = new Attachment();
      newAttach.server = attach.server;
      newAttach.client = client;
      newAttach.buffer = ByteBuffer.allocate(2048);
      newAttach.isRead = true;
      newAttach.clientAddr = clientAddr;
      client.read(newAttach.buffer, newAttach, rwHandler); 
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void failed(Throwable e, Attachment attach) {
    System.out.println("Failed to accept a  connection.");
    e.printStackTrace();
  }
}

class ReadWriteHandler implements CompletionHandler<Integer, Attachment> {
  @Override
  public void completed(Integer result, Attachment attach) {
    if (result == -1) {
      try {
        attach.client.close();
        Debug.DEBUG(String.format("Stopped   listening to the   client %s%n",
            attach.clientAddr), 3);
      } catch (IOException ex) {
        ex.printStackTrace();
      }
      return;
    }

    if (attach.isRead) {
      attach.buffer.flip();
      int limits = attach.buffer.limit();
      byte bytes[] = new byte[limits];
      attach.buffer.get(bytes, 0, limits);
      String msg = new String(bytes, StandardCharsets.US_ASCII);
      HTTPRequest request = HTTPRequest.parse(msg);
      HTTPResponse response = RequestHandler.getResponse(request);
      
      attach.isRead = false; // It is a write
      //attach.buffer.rewind();
      attach.buffer.clear();
      attach.buffer.put(response.getBytes());
      attach.buffer.flip();
      Debug.DEBUG("Ready to write\n" + response, 3);
      attach.client.write(attach.buffer, attach, this);
    } else {
      // Write to the client, completed
      // recycle resources
    	try {
			attach.client.close();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
//      attach.client.write(attach.buffer, attach, this);
//      attach.isRead = true;
//      attach.buffer.clear();
//      attach.client.read(attach.buffer, attach, this);
    }
  }

  @Override
  public void failed(Throwable e, Attachment attach) {
    e.printStackTrace();
  }
}