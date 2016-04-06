package proj;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;

import lib.Callback;
import lib.Manager;
import lib.Packet;
import lib.Protocol;
import lib.Transport;
import lib.Utility;

/**
 * <pre>
 * Node -- Class defining the data structures and code for the
 * protocol stack for a node participating in the fishnet

 * This code is under student control; we provide a baseline
 * "hello world" example just to show how it interfaces with the FishNet classes.
 *
 * NOTE: per-node global variables are prohibited.
 *  In simulation mode, we execute many nodes in parallel, and thus
 *  they would share any globals.  Please put per-node state in class Node
 *  instead.
 *
 * In other words, all the code defined here should be generic -- it shouldn't
 * matter to this code whether it is executing in a simulation or emulation
 *
 * This code must be written as a state machine -- each upcall must do its work and return so that
 * other upcalls can be delivered
 * </pre>
 */
public class Node {
	private final long PingTimeout = 10000;  // Timeout pings in 10 seconds

	private Manager manager;
	private int addr;
	private ArrayList pings; // To store PingRequests.

	// Fishnet reliable data transfer
	// TCP manager
	private TCPManager tcpMan;

	/**
	 * Create a new node
	 * @param manager The manager that is managing Fishnet
	 * @param addr The address of this node
	 */
	public Node(Manager manager, int addr) {
		this.manager = manager;
		this.addr = addr;
		this.pings = new ArrayList();

		// Fishnet reliable data transfer
		this.tcpMan = new TCPManager(this, addr, manager);
	}

	/**
	 * Called by the manager to start this node up.
	 */
	public void start() {
		logOutput("started");
		this.addTimer(PingTimeout, "pingTimedOut");

		// Fishnet reliable data transfer
		// Start TCP manager
		tcpMan.start();
	}

	/**
	 * Called by the manager when there is a command for this node from the user.
	 * Command can be input either from keyboard or file.
	 * For now sending messages as ping to a neighbor.
	 * @param command The command for this node
	 */
	public void onCommand(String command) {
		if (this.matchTransferCommand(command)) {
			return;
		}

		if (this.matchServerCommand(command)) {
			return;
		}
		if(this.matchPingCommand(command)) {
			return;
		}

		logError("Unrecognized command: " + command);
	}

	/**
	 * Callback method given to manager to invoke when a timer fires.
	 */
	public void pingTimedOut() {
		Iterator iter = this.pings.iterator();
		while(iter.hasNext()) {
			PingRequest pingRequest = (PingRequest)iter.next();
			// should it be '>' ?
			if((pingRequest.getTimeSent() + PingTimeout) < this.manager.now()) {
				try {
					logOutput("Timing out ping: " + pingRequest);
					iter.remove();
				}catch(Exception e) {
					logError("Exception occured while trying to remove an element from ArrayList pings.  Exception: " + e);
					return;
				}
			}
		}
		this.addTimer(PingTimeout, "pingTimedOut");
	}

	/**
	 * Called by the manager when a packet has arrived for this node
	 * @param from The address of the node that has sent this packet
	 * @param msg The serialized form of the packet.
	 */
	public void onReceive(Integer from, byte[] msg) {
		Packet packet = Packet.unpack(msg);
		//logOutput("received packet from " + from);
		if(packet == null) {
			logError("Unable to unpack message: " + Utility.byteArrayToString(msg) + " Received from " + from);
			return;
		}
	
		this.receivePacket(from.intValue(), packet);
	}

	
	private boolean matchPingCommand(String command) {
		int index = command.indexOf(" ");
		if(index == -1) {
			return false;
		}
		try {
			int destAddr = Integer.parseInt(command.substring(0, index));
			String message = command.substring(index+1);
			Packet packet = new Packet(destAddr, this.addr, Packet.MAX_TTL, Protocol.PING_PKT, 0,
					Utility.stringToByteArray(message));

			this.send(destAddr, packet);
			this.pings.add(new PingRequest(destAddr, Utility.stringToByteArray(message), this.manager.now()));
			return true;
		}catch(Exception e) {
			logError("Exception: " + e);
		}

		return false;
	}

	private void receivePacket(int from, Packet packet) {
		switch(packet.getProtocol()) {

		case Protocol.PING_PKT:
			this.receivePing(packet);
			break;

		case Protocol.PING_REPLY_PKT:
			this.receivePingReply(packet);
			break;
		case Protocol.TRANSPORT_PKT:
			this.receiveTransport(packet);
			break;
		default:
			logError("Packet with unknown protocol received. Protocol: " + packet.getProtocol());
		}
	}

	// receive transport packet, send it to tcpman
	private void receiveTransport(Packet packet){
		logOutput("Received TCP packet from " + packet.getSrc() + " with message: " + Utility.byteArrayToString(packet.getPayload()));
		int destAddr = packet.getDest();
		int srcAddr = packet.getSrc();
		Transport segment = Transport.unpack(packet.getPayload());
		this.tcpMan.onReceive(srcAddr, destAddr, segment);
	}
	private void receivePing(Packet packet) {
		logOutput("Received Ping from " + packet.getSrc() + " with message: " + Utility.byteArrayToString(packet.getPayload()));

		try {
			Packet reply = new Packet(packet.getSrc(), this.addr, Packet.MAX_TTL, Protocol.PING_REPLY_PKT, 0, packet.getPayload());
			this.send(packet.getSrc(), reply);
		}catch(IllegalArgumentException e) {
			logError("Exception while trying to send a Ping Reply. Exception: " + e);
		}
	}

	// Check that ping reply matches what was sent
	private void receivePingReply(Packet packet) {
		Iterator iter = this.pings.iterator();
		String payload = Utility.byteArrayToString(packet.getPayload());
		while(iter.hasNext()) {
			PingRequest pingRequest = (PingRequest)iter.next();
			if( (pingRequest.getDestAddr() == packet.getSrc()) &&
					( Utility.byteArrayToString(pingRequest.getMsg()).equals(payload))) {

				logOutput("Got Ping Reply from " + packet.getSrc() + ": " + payload);
				try {
					iter.remove();
				}catch(Exception e) {
					logError("Exception occured while trying to remove an element from ArrayList pings while processing Ping Reply.  Exception: " + e);
				}
				return;
			}
		}
		logError("Unexpected Ping Reply from " + packet.getSrc() + ": " + payload);
	}

	private void send(int destAddr, Packet packet) {
		try {
			this.manager.sendPkt(this.addr, destAddr, packet.pack());
		}catch(IllegalArgumentException e) {
			logError("Exception: " + e);
		}
	}

	// Adds a timer, to fire in deltaT milliseconds, with a callback to a public function of this class that takes no parameters
	private void addTimer(long deltaT, String methodName) {
		try {
			Method method = Callback.getMethod(methodName, this, null);
			Callback cb = new Callback(method, this, null);
			this.manager.addTimer(this.addr, deltaT, cb);
		}catch(Exception e) {
			logError("Failed to add timer callback. Method Name: " + methodName +
					"\nException: " + e);
		}
	}

	// Fishnet reliable data transfer

	/**
	 * Send a transport segment to the specified node (network layer service
	 * entry point for the transport layer)
	 *
	 * @param srcAddr int Source node address
	 * @param destAddr int Sestination node address
	 * @param protocol int Transport layer protocol to use
	 * @param payload byte[] Payload to be sent
	 */
	public void sendSegment(int srcAddr, int destAddr, int protocol, byte[] payload) {
		Packet packet = new Packet(destAddr, srcAddr, Packet.MAX_TTL,
				protocol, 0, payload);
		this.send(destAddr, packet);
	}

	public int getAddr() {
		return this.addr;
	}

	public void logError(String output) {
		this.log(output, System.err);
	}

	public void logOutput(String output) {
		this.log(output, System.out);
	}

	private void log(String output, PrintStream stream) {
		stream.println("Node " + this.addr + ": " + output);
	}

	private boolean matchTransferCommand(String command) {
		// transfer command syntax:
		//     transfer dest port localPort amount [interval sz]
		// Synopsis:
		//     Connect to a transfer server listening on port <port> at node
		//     <dest>, using local port <localPort>, and transfer <amount> bytes.
		// Required arguments:
		//     dest: address of destination node
		//     port: destination port
		//     localPort: local port
		//     amount: number of bytes to transfer
		// Optional arguments:
		//     interval: execution interval of the transfer client, default 1 second
		//     sz: buffer size of the transfer client, default 65536
		String[] args = command.split(" ");
		if (args.length < 5 || args.length > 7 || !args[0].equals("transfer")) {
			return false;
		}

		try {
			int destAddr = Integer.parseInt(args[1]);
			int port = Integer.parseInt(args[2]);
			int localPort = Integer.parseInt(args[3]);
			int amount = Integer.parseInt(args[4]);
			long interval =
					args.length >= 6 ?
							Integer.parseInt(args[5]) :
								TransferClient.DEFAULT_CLIENT_INTERVAL;
							int sz =
									args.length == 7 ?
											Integer.parseInt(args[6]) :
												TransferClient.DEFAULT_BUFFER_SZ;

											TCPSock sock = this.tcpMan.socket();
											sock.bind(localPort);
											sock.connect(destAddr, port);
											TransferClient client = new
													TransferClient(manager, this, sock, amount, interval, sz);
											client.start();

											return true;
		} catch (Exception e) {
			logError("Exception: " + e);
		}

		return false;
	}

	private boolean matchServerCommand(String command) {
		// server command syntax:
		//     server port backlog [servint workint sz]
		// Synopsis:
		//     Start a transfer server at the local node, listening on port
		//     <port>.  The server has a maximum pending (incoming) connection
		//     queue of length <backlog>.
		// Required arguments:
		//     port: listening port
		//     backlog: maximum length of pending connection queue
		// Optional arguments:
		//     servint: execution interval of the transfer server, default 1 second
		//     workint: execution interval of the transfer worker, default 1 second
		//     sz: buffer size of the transfer worker, default 65536
		String[] args = command.split(" ");
		if (args.length < 3 || args.length > 6 || !args[0].equals("server")) {
			return false;
		}

		try {
			int port = Integer.parseInt(args[1]);
			int backlog = Integer.parseInt(args[2]);
			long servint =
					args.length >= 4 ?
							Integer.parseInt(args[3]) :
								TransferServer.DEFAULT_SERVER_INTERVAL;
							long workint =
									args.length >= 5 ?
											Integer.parseInt(args[4]) :
												TransferServer.DEFAULT_WORKER_INTERVAL;
											int sz =
													args.length == 6 ?
															Integer.parseInt(args[5]) :
																TransferServer.DEFAULT_BUFFER_SZ;
															TCPSock sock = this.tcpMan.socket();
															sock.bind(port);
															sock.listen(backlog);

															TransferServer server = new
																	TransferServer(manager, this, sock, servint, workint, sz);
															server.start();
															logOutput("server started, port = " + port);

															return true;
		} catch (Exception e) {
			logError("Exception: " + e);
		}

		return false;
	}

}
