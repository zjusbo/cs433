import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileNotFoundException;
import java.lang.NumberFormatException;
import java.lang.Integer;

/**
 * <pre>   
 * Manages the emulated nodes. 
 * It works by listening for new TCP connections. Each new connecting node sends the UDP port
 * it is using for listening to peers. Also multiple emulated nodes might be running on the same machine
 * so this lets us disambiguate them.
 * The Trawler replies with the fishnet address that the emulated node should use, as well as the current 
 * neighbor list for that node as <fishnetAddress ipAddress udpPort> pairs.
 * The trawler updates this list as it changes.
 *
 * Usage: java Trawler <port to listen on> [topo file]
 *       
 *        Topo file is the topology file. It is an optional argument. By default all nodes will be neighbors.
 * </pre>   
 */
public class Trawler {

    private static Trawler trawler = null;
    private ServerSocket socket;
    private HashMap emulatedNodes;
    private TrawlerCommandsParser parser;

    /**
     * Static method to get an instance of trawler. 
     * It will return null if the trawler has not been initialized with the port and topofile first
     * @return An instance of this Trawler class
     */
    public static Trawler GetInstance() {
	return trawler;
    }

    /**
     * Static method to get an instance of trawler. 
     * @param port The port the trawler should listen for incoming connections on
     * @return An instance of this Trawler class
     * @throws IOException If an I/O error occurs when opening the socket
     */
    public static Trawler GetInstance(int port) throws IOException {
	if(trawler == null) {
	    trawler = new Trawler(port);	    
	}
	return trawler;
    }

    /*
     * the next several routines track changes to the topology, and update
     * the emulated nodes to reflect those changes.  Note these are changes
     * to the *topology*, not the set of live emulated nodes.
     */

    /**
     * An edge was removed. Notify both ends if they are alive other wise ignore. They will find out themselves anyway
     * @param fishAddrA Address of one node of the edge
     * @param fishAddrB Address of the other node of the edge
     */
    public void failEdge(int fishAddrA, int fishAddrB) {	
	EmulatedNode nodeA = this.getEmulatedNode(fishAddrA);
	EmulatedNode nodeB = this.getEmulatedNode(fishAddrB);
	
	if(nodeA != null && nodeB != null) {
	    nodeA.removeEdge(fishAddrB);
	    nodeB.removeEdge(fishAddrA);
	}					  
    }

    public void startEdge(int fishAddrA, int fishAddrB) {
	EmulatedNode nodeA = this.getEmulatedNode(fishAddrA);
	EmulatedNode nodeB = this.getEmulatedNode(fishAddrB);
	
	if(nodeA != null && nodeB != null) {
	    nodeA.putEdge(nodeB);
	    nodeB.putEdge(nodeA);
	}					  
    }

    /**
     * A node was removed from the topology.  We just remove the node's edges;
     * the user has to kill the emulated node directly.
     * NOTE: this must be called *BEFORE* the change to the topology!
     * @param fishAddr Address of the node to be failed
     */
    public void failNode(int fishAddr) {
	EmulatedNode node = this.getEmulatedNode(fishAddr);
	if(node != null) {
	    node.reset();
	    this.removeAsNeighbor(node);
	}
    }

    /**
     * A node rejoined the topology, so we add back in all of its edges.
     * @param fishAddr Address of node that is restarting
     */
    public void restartNode(int fishAddr) {
	this.updateNeighbors(fishAddr);
    }

    /**
     * <pre>          
     * Wait for a node to connect, then:
     *	find out if any nodes have left while we were waiting
     *		(and if so, update their neighbors so they stop sending them packets)
     *	find a free fishnet Address to assign to the new node, and send it to them
     *		(by writing to the node's TCP socket)
     *	find out which UDP port they are listening to
     *		(by reading from the node's TCP socket)
     *	tell the node about all its neighbors' IP addresses and port #'s
     *	tell all their neighbors with their IP address and port #
     *	loop
     * </pre>   
     * @param topofile Name of the topology filename. If it is null then all nodes are neighbors by default
     * @throws FileNotFoundException If the filename could not be found
     * @throws Exception Any other exception that might occur while the Trawler is running
     */
    public void start(String topofile) throws FileNotFoundException, Exception {
	
	long deferParsingTill = -1;

	if(topofile == null) {
	    Topology.GetInstance(true);
	}else {
	    deferParsingTill = this.parser.parseFile(topofile, Utility.fishTime());
	}
	
	System.out.println("Trawler awaiting fish...");
	Socket nodeSocket;
	PrintWriter out;
	while((nodeSocket = this.socket.accept()) != null) {
	    try {
		this.checkNodesQuit();
		
		long now = Utility.fishTime();
		if(deferParsingTill > -1 && deferParsingTill < now) {
		    // Need to complete parsing topology file
		    deferParsingTill = this.parser.parseRemainder(now);
		}
		
		BufferedReader in = new BufferedReader( new InputStreamReader( nodeSocket.getInputStream() ));
		out = new PrintWriter(nodeSocket.getOutputStream(), true);
		
		InetAddress ipAddress = nodeSocket.getInetAddress();
		int port = Integer.parseInt(in.readLine());
		
		if(port < 1024 || this.portConflict(ipAddress, port)) {
		    System.err.println("Trawler: Illegal port: " + port);
		    out.println(Packet.BROADCAST_ADDRESS);
		    out.close();
		    nodeSocket.close();
		}else {
		    // find a fishnet address to assign to the new node
		    int fishAddr = this.freeFishAddr();
		    
		    if(fishAddr == -1) {
			System.err.println("Trawler: out of addresses");
			out.println(Packet.BROADCAST_ADDRESS);
			out.close();
			nodeSocket.close();		    
		    }else {
			//Disable Nagle
			nodeSocket.setTcpNoDelay(true);

			System.out.println("Got port " + port + ": assigning addr: " + fishAddr);
			out.println(fishAddr);
			this.emulatedNodes.put(new Integer(fishAddr), 
					       new EmulatedNode(nodeSocket, out, fishAddr, ipAddress, port));
			this.updateNeighbors(fishAddr);
		    }
		}
	    }catch(NumberFormatException e) {
		System.err.println("Msg received from node is not a port number. Socket: " + nodeSocket);
	    }catch(IOException e) {
		System.err.println("IOException occured while trying to creade new node. Exception: " + e);
	    }catch(Exception e) {
		System.err.println("Exception occured while trying to creade new node. Exception Stack Trace: ");
		e.printStackTrace();		
	    }
	}	    
    }


    /**
     * An emulated node has quit so notify its neighbors
     * @param dyingNode The node that has quit
     */
    public void remove(EmulatedNode dyingNode) {
	System.err.println("Removing node " + dyingNode.getFishAddr());
	try {
	    Iterator iter = this.emulatedNodes.values().iterator();
	    while(iter.hasNext()) {
		EmulatedNode node = (EmulatedNode)iter.next();
		if(node.getFishAddr() == dyingNode.getFishAddr()) {
		    iter.remove();
		}else { 
		    removeNeighbors(dyingNode, node);
		}
	    }
	}catch(Exception e) {
	    System.err.println("Exception occured while to remove emulated node: " + dyingNode.getFishAddr() + 
			       " Exception: " + e);
	}
    }

    /**
     * Stop the Trawler
     */
    public void exit() {
	System.out.println("Trawler exiting...");
	System.exit(0);
    }
    

    private void removeAsNeighbor(EmulatedNode dyingNode) {
	Iterator iter = this.emulatedNodes.values().iterator();
	while(iter.hasNext()) {
	    EmulatedNode node = (EmulatedNode)iter.next();
	    removeNeighbors(dyingNode, node);
	}
    }

    private void removeNeighbors(EmulatedNode dyingNode, EmulatedNode node) {
	if( (Topology.GetInstance().getLiveEdge(node.getFishAddr(), dyingNode.getFishAddr()) != null) &&
	    node.isAlive() ) {
	    
	    node.removeEdge(dyingNode.getFishAddr());
	}
    }

    private void updateNeighbors(int fishAddr) {
	EmulatedNode startingNode = this.getEmulatedNode(fishAddr);
	if(startingNode != null) {
	    Iterator iter = this.emulatedNodes.values().iterator();
	    while(iter.hasNext()) {
		EmulatedNode node = (EmulatedNode)iter.next();
		
		if( node.getFishAddr() != fishAddr  && 
		    (Topology.GetInstance().getLiveEdge(node.getFishAddr(), startingNode.getFishAddr()) != null) &&
		    node.isAlive() ) {
		    
		    node.putEdge(startingNode);
		    startingNode.putEdge(node);
		}
	    }
	}
    }

    
    private void checkNodesQuit() {
	try {
	    Iterator iter = this.emulatedNodes.values().iterator();

	    while(iter.hasNext()) {
		EmulatedNode node = (EmulatedNode)iter.next();
		if(!node.isAlive()) {
		    node.close();
		    iter.remove();
		    this.removeAsNeighbor(node);
		}
	    }
	}catch(Exception e) {
	    System.err.println("Exception occured while to remove emulated node. Exception: " + e);
	}
    } 
    

    // returns -1 if no fish address is available
    private int freeFishAddr() {
	for(int i = 0; i < Packet.BROADCAST_ADDRESS; i++) {
	    if(!this.emulatedNodes.containsKey(new Integer(i))) {
		return i;
	    }
	}
	return -1;
    }

    private boolean portConflict(InetAddress ipAddress, int port) {
	Iterator iter = this.emulatedNodes.values().iterator();
	while(iter.hasNext()) {
	    EmulatedNode node = (EmulatedNode)iter.next();
	    if(ipAddress.equals(node.getIPAddress()) && port == node.getPort()) {
		return true;
	    }
	}
	return false;
    }

    // return null if addr not in hash
    private EmulatedNode getEmulatedNode(int fishAddr) {
	Integer addr = new Integer(fishAddr);
	EmulatedNode node = null;
	if(this.emulatedNodes.containsKey(addr)) {
	    node = (EmulatedNode)this.emulatedNodes.get(addr);
	}
	return node;
    }

    private Trawler(int port) throws IOException {
	this.socket = new ServerSocket(port);
	this.emulatedNodes = new HashMap();
	this.parser = new TrawlerCommandsParser();
    }

    /**
     * Entry point to start Trawler
     */
    public static void main(String[] args) {
	if(args.length < 1) {
	    System.err.println("Missing arguments");
	    usage();
	    return;
	}
	
	try {
	    int port = Integer.parseInt(args[0]);
	    String topofile = null;
	    
	    if(args.length > 1) {
		topofile = args[1];	  
	    }

	    Trawler.GetInstance(port);
	    Trawler.GetInstance().start(topofile);	    
	}catch(FileNotFoundException e) {
	    System.err.println("Incorrect topo file name given to Trawler. Exception: " + e);
	}catch(IOException e) {
	    System.err.println("Invalid port given to Trawler. Exception: " + e);	
	}catch(NumberFormatException e) {
	    System.err.println("First argument must be the port number, an integer. Exception: " + e);
	}catch(Exception e) {
	    System.err.println("Exception occured in Trawler!! Exception: " + e);
	}
    }

    private static void usage() {
	System.out.println("Usage: java Trawler <port to listen on> [topo file]\n\n" +        
			   "Topo file is the topology file. It is an optional argument.\n" +
			   "By default all nodes will be neighbors.");
    }
}
