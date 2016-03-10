package server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;

import utility.HTTPRequest;
import utility.HTTPResponse;

public class HTTPAsynchronousServerSelect implements HTTPServer{
	private Selector selector;
	private boolean DEBUG = true;
	private ServerConfig config;
	private void DEBUG(String s) {
		if (DEBUG) {
			System.out.println(s);
		}
	}

	public HTTPAsynchronousServerSelect(ServerConfig config) {
		this.config = config;
	}
	
	public void start(){
		DEBUG("Listening for connections on port " + config.port);
	
		// server socket channel and selector initialization
		try {
			// create selector
			selector = Selector.open();

			// open server socket for accept
			ServerSocketChannel serverChannel = openServerSocketChannel(config.port);

			// register the server channel to selector
			serverChannel.register(selector, SelectionKey.OP_ACCEPT);
		} catch (IOException ex) {
			ex.printStackTrace();
			System.exit(1);
		} // end of catch

		// event loop
		while (true) {

			DEBUG("Enter selection");
			try {
				// check to see if any events
				selector.select();
			} catch (IOException ex) {
				ex.printStackTrace();
				break;
			} // end of catch

			// readKeys is a set of ready events
			Set<SelectionKey> readyKeys = selector.selectedKeys();

			// create an iterator for the set
			Iterator<SelectionKey> iterator = readyKeys.iterator();

			// iterate over all events
			while (iterator.hasNext()) {

				SelectionKey key = (SelectionKey) iterator.next();
				iterator.remove();

				try {
					if (key.isAcceptable()) {
						// a new connection is ready to be accepted
						handleAccept(key);
					} // end of isAcceptable

					if (key.isReadable()) {
						handleRead(key);
					} // end of isReadable

					if (key.isWritable()) {
						handleWrite(key);
					} // end of if isWritable
				} catch (IOException ex) {
					key.cancel();
					try {
						key.channel().close();
					} catch (IOException cex) {
					}
				} // end of catch

			} // end of while (iterator.hasNext()) {

		} // end of while (true)

	} // end of main

	private  ServerSocketChannel openServerSocketChannel(int port) {
		ServerSocketChannel serverChannel = null;

		try {
			// create server channel
			serverChannel = ServerSocketChannel.open();

			// extract server socket of the server channel and bind the port
			// In Java 7 and later, you can bind directly
			// erverChannel.bind(new InetSocketAddress(port));
			ServerSocket ss = serverChannel.socket();
			InetSocketAddress address = new InetSocketAddress(port);
			ss.bind(address);

			// configure it to be non blocking
			serverChannel.configureBlocking(false);
		} catch (IOException ex) {
			ex.printStackTrace();
			System.exit(1);
		} // end of catch

		return serverChannel;

	} // end of openServerSocketChannel

	private void handleAccept(SelectionKey key) throws IOException {

		ServerSocketChannel server = (ServerSocketChannel) key.channel();

		// extract the ready connection
		SocketChannel client = server.accept();
		DEBUG("handleAccept: Accepted connection from " + client);

		// configure the connection to be non-blocking
		client.configureBlocking(false);

		// register the new connection with interests
		// v1:
		// SelectionKey clientKey = client.register(selector,
		// SelectionKey.OP_READ
		// | SelectionKey.OP_WRITE);
		/// **********************
		// v2: we set only READ
		SelectionKey clientKey = client.register(selector, SelectionKey.OP_READ);
		// *********************/

		// attach a buffer to the new connection
		// you may want to read up on ByteBuffer.allocateDirect on performance
		ByteBuffer buffer = ByteBuffer.allocate(1000);
		clientKey.attach(buffer);

	} // end of handleAccept

	private void handleRead(SelectionKey key) throws IOException {
		// a connection is ready to be read
		DEBUG("-->handleRead");
		SocketChannel client = (SocketChannel) key.channel();
		ByteBuffer output = (ByteBuffer) key.attachment();
		int readBytes = client.read(output);

		/// **********************
		// v2: update state
		SelectionKey sk = key.channel().keyFor(selector);
		int nextState = sk.interestOps();

		// turn off read if readBytes == -1
		if (readBytes == -1) {// no longer need to read, close read, open write
			nextState = nextState & ~SelectionKey.OP_READ; // close read
			nextState = nextState | SelectionKey.OP_WRITE; // add write
			DEBUG("   State change: client closed; turn off read.");
		}

		sk.interestOps(nextState);
		// **********************/

		DEBUG("   Read data from connection " + client + ": read " + readBytes + " byte(s); buffer becomes " + output);
/*		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
		}*/
		DEBUG("handleRead-->");

	} // end of handleRead

	private void handleWrite(SelectionKey key) throws IOException {
		DEBUG("-->handleWrite");
		SocketChannel client = (SocketChannel) key.channel();
		ByteBuffer output = (ByteBuffer) key.attachment();
		String s_request = new String(output.array(), StandardCharsets.US_ASCII);
		HTTPRequest request = HTTPRequest.parse(s_request);
		HTTPResponse response = RequestHandler.getResponse(request);
		output.clear();
		System.out.println(response);
		output.put(response.getBytes());
		output.flip();
		int writeBytes = client.write(output);

		/// ***********
		// v2: update write state; did not handle how to close after write all
		SelectionKey sk = key.channel().keyFor(selector);
		int nextState = sk.interestOps();
		if (!output.hasRemaining()) { // no response left
			nextState = nextState & ~SelectionKey.OP_WRITE; // close write
			client.socket().shutdownOutput();
			DEBUG("   State change: all data sent; turn off write");
			sk.interestOps(nextState);
		}
		// *************/
		output.compact();
		DEBUG("   Write data to connection " + client + ": write " + writeBytes + " byte(s); buffer becomes " + output);
//		try {
//			Thread.sleep(5000);
//		} catch (InterruptedException e) {
//		}
		DEBUG("handleWrite-->");
	} // end of handleWrite

} // end of class
