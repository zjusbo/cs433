import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.lang.reflect.Method;

/**
 * <pre>
 * Manages a simulation. All nodes are instantiated in process.
 * </pre>
 */
public class Simulator extends Manager {

    public static final int MAX_NODES_TO_SIMULATE = Packet.MAX_ADDRESS - 1;
    private long now;  // simulated time in microseconds
    private double timescale;
    private Node[] nodes;
    private SimulationCommandsParser topoFileParser;
    private IOThread ioThread;

    /**
     * Creates a new simulation
     * @param numNodes The number of nodes to simulate
     * @param topoFile The name of the topology file to use
     * @throws IllegalArgumentException If the number of nodes to simulate is < 0 or > MAX_NODES_TO_SIMULATE
     * @throws FileNotFoundException If the given topology file cannot be found
     */
    public Simulator(int numNodes, String topoFile) throws IllegalArgumentException, FileNotFoundException {
	super(0);
	super.setParser(new SimulationCommandsParser(this));
	if(numNodes <= 0 || numNodes > MAX_NODES_TO_SIMULATE) {
	    throw new IllegalArgumentException("Invalid number of nodes given to simulate. Nodes given: " + numNodes);
	}

	this.now = 0;

	this.timescale = 1.0;

	this.nodes = new Node[numNodes];
	for(int i = 0; i < numNodes; i++) {
	    this.nodes[i] = new Node(this, i);
	}

	this.topoFileParser = new SimulationCommandsParser(this);

	long deferTill = this.topoFileParser.parseFile(topoFile, this.now);

	this.addEvent(deferTill, "parseRestOfTopoFile", this, null, null);
	this.ioThread = new IOThread();
	this.ioThread.start();
    }

    /**
     * Starts the simulation
     */
    public void start() {
	// Start all the nodes
	for(int i = 0; i < this.nodes.length; i++) {
	    this.nodes[i].start();
	}

	this.now = 1;

	Event nextEvent;
	long waitTime; // time in microseconds
	long deferParsingTill = 0;
	while(true) {
	    long deltaTime = 0;
	    deferParsingTill = this.readFishFile(deferParsingTill);

	    nextEvent = null;
	    waitTime = -1;  // wait indefinitely
	    if(!this.sortedEvents.isEmpty()) {
		nextEvent = this.sortedEvents.getNextEvent();
		deltaTime = nextEvent.timeToOccur() - this.now;

		waitTime = 0; // Don't wait for user input. If delta time > 0 then will get reset below

	    }else if(deferParsingTill >= 0) {
		deltaTime = deferParsingTill - this.now;
		waitTime = 0;
	    }

	    if(deltaTime > 0 && this.timescale > 0) {
		waitTime = (long)(((double)deltaTime) / this.timescale);
	    }


	    long beforeInputTime = Utility.fishTime();
	    String userInput = this.getUserInput(waitTime);

	    // Process user input if there is any
	    if(userInput != null) {
		// Increment now
		this.now += (long)((Utility.fishTime() - beforeInputTime) * this.timescale);

		this.parser.parseLine(userInput, this.now);
	    }else {
		// Have waited appropriate amount of real time, so can fast-forward now
		this.now = Math.max(this.now, deltaTime + this.now);

		// Run all pending events
		while((nextEvent != null) && (nextEvent.timeToOccur() <= this.now)) {
		    this.sortedEvents.removeNextEvent();
		    try {
			nextEvent.callback().invoke();
		    }catch(Exception e) {
			System.err.println("Exception while trying to invoke method in Simulator. Error: " + e);
			e.printStackTrace();
		    }
		    nextEvent = this.sortedEvents.getNextEvent();
		}
	    }
	}
    }

    /**
     * Send the pkt to the specified node
     * @param from The node that is sending the packet
     * @param to Int spefying the destination node
     * @param pkt The packet to be sent, serialized to a byte array
     * @return True if the packet was sent, false otherwise
     * @throws IllegalArgumentException If the arguments are invalid
     */
    public boolean sendPkt(int from, int to, byte[] pkt) throws IllegalArgumentException {
	super.sendPkt(from, to, pkt);  // check arguments
	Edge edge;
	if(to == Packet.BROADCAST_ADDRESS) {
	    for(int i = 0; i < this.nodes.length; i++) {
		edge = Topology.GetInstance().getLiveEdge(from, i);
		if(edge != null) {
		    this.deliverPkt(i, this.nodes[i], from, pkt, edge);
		}

		//this.nodes[from].onReceive(from, pkt);  // Should the node that broadcast also receive the pkt?
	    }
	}else if((edge = Topology.GetInstance().getLiveEdge(from, to)) != null) {
	    this.deliverPkt(to, this.nodes[to], from, pkt, edge);
	}else {
	    System.err.println("Failed to send pkt from: " + from + " to: " + to);
	    return false;
	}
	return true;
    }

    /**
     * Retrieve current time in milliseconds
     * @return Current time in milliseconds
     */
    public long now() {
	return this.now / 1000;
    }

    /**
     * Adds a timer to be fired at time t
     * @param nodeAddr Addr of node that is registering this timer
     * @param t The time when the timer should fire. Its in milliseconds
     * @param callback The callback to be invoked when the timer fires
     */
    public void addTimerAt(int nodeAddr, long t, Callback callback) {
	if( (!this.isNodeAddrValid(nodeAddr)) ) {
	    return;
	}

	super.addTimerAt(nodeAddr, t, callback);

    }

    /**
     * Sends the msg to the the specified node
     * @param nodeAddr Address of the node to whom the message should be sent
     * @param msg The msg to send to the node
     * @return True if msg sent, false if address is not valid
     */
    public boolean sendNodeMsg(int nodeAddr, String msg) {
	if(!this.isNodeAddrValid(nodeAddr)) {
	    return false;
	}
	this.nodes[nodeAddr].onCommand(msg);
	return true;
    }

    /**
     * Sets the amount to scale real time by.
     * @param timescale The amount to scale real time by
     */
    public void setTimescale(double timescale) {
	this.timescale = timescale;
    }

    /**
     * Parses rest of topology file. Has public accesibility since used as a callback
     */
    public void parseRestOfTopoFile() {
	long deferTill = this.topoFileParser.parseRemainder(this.now);
	this.addEvent(deferTill, "parseRestOfTopoFile", this, null, null);
    }

    /******************** Private Functions ********************/

    private boolean isNodeAddrValid(int nodeAddr) {
	return ((nodeAddr >= 0) && (nodeAddr < this.nodes.length));
    }

    private void deliverPkt(int destAddr, Node destNode, int srcAddr, byte[] pkt, Edge edge) {
        /*
         * Mar. 12, 2006
         * Hao Wang
         *
         * Support determination of buffer overflow and collection of statistics
         * about dropped/lost packets
         */
	/*
         * long timeToDeliver = edge.schedulePkt(srcAddr, pkt.length, this.now);
         */
        long timeToDeliver = edge.schedulePkt(this,srcAddr, pkt.length, this.now);
	if(timeToDeliver == -1) {
	    return;  // pkt dropped
	}

	String[] paramTypes = {"java.lang.Integer", "[B"};
	Object[] params = {new Integer(srcAddr), pkt};
	this.addEvent(timeToDeliver, "onReceive", destNode, paramTypes, params);
    }

    private String getUserInput(long timeout) {
	if (timeout == 0) {
	    return null;
	}
	timeout = Math.max(timeout, 0);
	long endTime = (this.now() * 1000) + timeout;
	try {
	    // Test for condition in while loop in case of spurious wakeup
	    while(this.ioThread.isEmpty() && Utility.fishTime() < endTime) {
		synchronized(this.ioThread) {
		    this.ioThread.wait(timeout / 1000);
		}
	    }
	 }catch(InterruptedException e) {
	    // Do nothing. This should never happen, even if it does no harm done
	}
	return this.ioThread.readLine();
    }
}
