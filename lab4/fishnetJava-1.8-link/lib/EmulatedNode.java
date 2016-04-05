import java.net.Socket;
import java.net.InetAddress;
import java.io.PrintWriter;
import java.io.IOException;

/**
 * <pre>
 * Keeps track of information about an emulated node
 * Emulated node uses a TCP socket to talk to the Trawler, but send and receive messages directly
 * to other emulated nodes using UDP.
 * </pre>
 */
public class EmulatedNode {

    private Socket socket;               // Socket used to talk to node
    private PrintWriter out;             // Output stream to send data across socket
    private int fishAddr;                // Fish address assigned to node

    //This is the ip address and port that node uses to talk to other nodes via UDP
    private InetAddress ipAddress;       // The IP address of node.
    private int port;                  // The port that node is on.

    /**
     * Create a new EmulatedNode
     * @param socket The socket to use to talk to the emulated node
     * @param out An outputstream to use to send data across the socket
     * @param fishAddr The fishnet address of the emulated node
     * @param ipAddress The IP address of the machine that the node is on
     * @param port The port that the emulated node will use to talk to other nodes
     */
    public EmulatedNode(Socket socket, PrintWriter out, int fishAddr, InetAddress ipAddress, int port) {
	this.socket = socket;
	this.fishAddr = fishAddr;
	this.ipAddress = ipAddress;
	this.port = port;

	this.out = out;
    }


    // The next several routines tell an emulated node about an update to its topology.

    /**
     * Tell the emulated node about one of its neighbors
     * @param peerNode A neighbor of this node
     */
    public void putEdge(EmulatedNode peerNode) {
	if(!this.isAlive()) {
	    this.close();
	    Trawler.GetInstance().remove(this);
	    return;
	}

        /*
         * Feb. 27, 2006
         * Hao Wang
         *
         * include edge options in the ARP data sent to the node
         */
        /*
         * out.println(TrawlerNodeARPCommands.addNeighbor(peerNode.getFishAddr(), peerNode.getIPAddress(), peerNode.getPort()));
         */
        Edge e = Topology.GetInstance().getLiveEdge(this.getFishAddr(),
                                                    peerNode.getFishAddr());
        EdgeOptions options = e.getOptions();
        String cmd = TrawlerNodeARPCommands.addNeighborOptions(peerNode.getFishAddr(),
                                                               peerNode.getIPAddress(),
                                                               peerNode.getPort(),
                                                               options);
        out.println(cmd);
    }

    /**
     * Tell the emulated node that one of its neighbors is gone
     * @param peerFishAddr The fishnet address of the neighbor
     */
    public void removeEdge(int peerFishAddr) {
	if(!this.isAlive()) {
	    this.close();
	    Trawler.GetInstance().remove(this);
	    return;
	}
	out.println(TrawlerNodeARPCommands.removeNeighbor(peerFishAddr));
    }

    /**
     * Remove all edge's from this emulated node. Thus it loses all its neighbors
     */
    public void reset() {
	if(!this.isAlive()) {
	    this.close();
	    Trawler.GetInstance().remove(this);
	    return;
	}
	out.println(TrawlerNodeARPCommands.reset());
    }

    /**
     * Close the the connection to the emulated node
     */
    public void close() {
	try {
	    out.close();
	    socket.close();
	}catch(IOException e) {
	    System.err.println("Encountered IO Exception while trying to close socket in EmulatedNode: " + this.fishAddr +
			       "Exception Stack Trace:");
	    e.printStackTrace();
	}
    }

    /**
     * Get the fishnet address of this node
     * @return The fishnet address of this node
     */
    public int getFishAddr() {
	return this.fishAddr;
    }

    /**
     * Get the IP address of the machine that this emulated node is on
     * @return The IP address of the machine that this emulated node is on
     */
    public InetAddress getIPAddress() {
	return this.ipAddress;
    }

    /**
     * Get the port that this emulated node is using to talk to its neighbors
     * @return The port that this emulated node is using to talk to its neighbors
     */
    public int getPort() {
	return this.port;
    }

    /**
     * Return a string containing details of this emulated node
     * @return A string containing details of this emulated node
     */
    public String toString() {
	return new String("<TCP: " + this.socket.getInetAddress() + ":" + this.socket.getPort() + " Fish: " + this.fishAddr +
			  " UDP: " + this.ipAddress + ":" + this.port + ">");
    }

    /**
     * Check if the emulated node is still alive
     * @return True if the node is still alive
     */
    public boolean isAlive() {
	return (!socket.isClosed() &&
		!out.checkError()  &&
		!socket.isOutputShutdown());

    }
}
