package client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import utility.Debug;
import utility.HTTPRequest;

public class SHTTPTestAsyncClient {
	public static void main(String[] args) throws Exception {

		ClientArgument config = ClientArgument.parse(args);
		if (config == null) {
			// argument incorrect
			return;
		}
		System.out.println(config);
		if (config.filename == null) {
			System.err.println("filename required");
			return;
		}
		ArrayList<String> filenameList = SHTTPTestClient.extractFiles(config.filename);
		if (filenameList.isEmpty()) {
			System.err.println("no content in " + config.filename);
			return;
		}
		// prepare for socket config
		ArrayList<HTTPRequest> requestList = new ArrayList<HTTPRequest>();

		for (String url : filenameList) {
			HTTPRequest request = new HTTPRequest(url, config.host);
			requestList.add(request);
		}

		AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
		SocketAddress serverAddr = new InetSocketAddress(config.server, config.port);
		
		Attachment attach = new Attachment();
		attach.serverAddr = serverAddr;
		attach.channel = channel;
		attach.requestList = requestList;
		attach.requestIndex = 0;
		attach.buffer = ByteBuffer.allocate(2048);
		attach.isRead = false;
		attach.mainThread = Thread.currentThread();
		channel.connect(serverAddr, attach, new ConnectedHandler());
		
		attach.mainThread.join();
	}
}

class Attachment {
	ArrayList<HTTPRequest> requestList;
	SocketAddress serverAddr;
	int requestIndex;
	AsynchronousSocketChannel channel;
	ByteBuffer buffer;
	Thread mainThread;
	boolean isRead;
}

class ConnectedHandler implements CompletionHandler<Void , Attachment>{

	@Override
	public void completed(Void result, Attachment attach) {
		// start next connection
		System.out.println("Connected");
		Attachment newAttach = new Attachment();
		newAttach.serverAddr = attach.serverAddr;
		newAttach.requestList = attach.requestList;
		newAttach.requestIndex = attach.requestIndex + 1 >= attach.requestList.size() ? 0 : attach.requestIndex + 1;
		newAttach.buffer = ByteBuffer.allocate(2048);
		newAttach.isRead = false;
		newAttach.mainThread = Thread.currentThread();
		try {
			newAttach.channel = AsynchronousSocketChannel.open(); // open a new channel
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		newAttach.channel.connect(newAttach.serverAddr, newAttach, this); // fire connections
		
		HTTPRequest request = attach.requestList.get(attach.requestIndex);
		attach.buffer.clear();
		attach.buffer.put(request.getBytes());
		attach.buffer.flip(); // ready to send

		ReadWriteHandler readWriteHandler = new ReadWriteHandler();
		attach.channel.write(attach.buffer, attach, readWriteHandler);
	}

	@Override
	public void failed(Throwable exc, Attachment attachment) {
		// TODO Auto-generated method stub
		exc.printStackTrace();
	}
	
}
class ReadWriteHandler implements CompletionHandler<Integer, Attachment> {
	@Override
	public void completed(Integer result, Attachment attach) {
		if (attach.isRead) {
			// read done
			// parse response
			attach.buffer.flip();
			int limits = attach.buffer.limit();
			byte bytes[] = new byte[limits];
			attach.buffer.get(bytes, 0, limits);
			String s_res = new String(bytes, StandardCharsets.US_ASCII);
			// output response
			Debug.DEBUG("Server Responded: " + s_res, 1);

			attach.buffer.clear();
			// close channel
			try {
				attach.channel.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			// write done
			// prepare to read response, 
			attach.isRead = true;
			attach.buffer.clear();
			attach.channel.read(attach.buffer, attach, this);
		}
	}

	@Override
	public void failed(Throwable e, Attachment attach) {
		e.printStackTrace();
	}
}