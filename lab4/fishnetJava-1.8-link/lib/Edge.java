/**
 * <pre>
 * Edge stores the specifics about each edge in the topology.
 * Edges can be temporarily disabled and they record when the next packet can be sent along the edge
 * </pre>
 */
public class Edge {
    private int a;
    private int b;
    private boolean live;
    private long[] nextPktSendTime;  // When can the next packet be put onto the wire (in microseconds)
    private EdgeOptions options;

    /**
     * Create a live edge between nodes a and b
     * @param a Int specifying a node
     * @param b Int specifying a node
     * @param options The edge options. That is, the delay, the loss rate and the bandwidth
     */
    public Edge(int a, int b, EdgeOptions options) {
	this.a = a;
	this.b = b;
	this.live = true;
	this.nextPktSendTime = new long[2];
	this.insertSendTime(a, 0);
	this.insertSendTime(b, 0);
	this.options = options;
    }


    /**
     * Figure out when, in microseconds, a packet will arrive at the destination, given a link's
     * bandwidth propogation delay characteristics.
     * @param src The src node that wants to send the packet
     * @param size The size of the packet in bytes
     * @param now The current time in microseconds
     * @return The time (in microseconds) when the next packet will arrive at the destination. Returns -1 if the packet is dropped
     * @throws IllegalArgumentException Thrown if size is greater than Packet.MAX_PACKET_SIZE
     */
    public long schedulePkt(int src, int size, long now) throws IllegalArgumentException {
	if (size > Packet.MAX_PACKET_SIZE) {
	    throw new IllegalArgumentException("Packet size must be less than Packet.MAX_PACKET_SIZE. Size = " +
					       String.valueOf(size));
	}

	if (src != this.a && src != this.b) {
	    throw new IllegalArgumentException("Src specified is not part of this edge. This edge has a: " +
					       String.valueOf(this.a) + " and b: " + String.valueOf(this.b) +
					       ". Src specified is: " + String.valueOf(src));
	}

	long result = Math.max(now, this.getSendTime(src));
        /*
         * Mar. 13, 2006
         * Hao Wang
         *
         * unit of bandwidth now B/s
         */
	this.insertSendTime(src, result + size * 1000000 / this.options.getBW());

	if(!this.live || Math.random() < this.options.getLossRate()) {
	    return -1; // pkt was dropped
	}

	return this.getSendTime(src) + (this.options.getDelay() * 1000);
    }

    /*
     * Mar. 12, 2006
     * Hao Wang
     *
     * Support determination of buffer overflow and collection of statistics
     */

    /**
     * Figure out when, in microseconds, a packet will arrive at the destination, given a link's
     * bandwidth propogation delay buffer characteristics.
     * @param manager The manager that is scheduling the packet
     * @param src The src node that wants to send the packet
     * @param size The size of the packet in bytes
     * @param now The current time in microseconds
     * @return The time (in microseconds) when the next packet will arrive at the destination. Returns -1 if the packet is dropped/lost
     * @throws IllegalArgumentException Thrown if size is greater than Packet.MAX_PACKET_SIZE
     */
    public long schedulePkt(Manager manager, int src, int size, long now) throws IllegalArgumentException {
        if (size > Packet.MAX_PACKET_SIZE) {
            throw new IllegalArgumentException("Packet size must be less than Packet.MAX_PACKET_SIZE. Size = " +
                                               String.valueOf(size));
        }

        if (src != this.a && src != this.b) {
            throw new IllegalArgumentException("Src specified is not part of this edge. This edge has a: " +
                                               String.valueOf(this.a) + " and b: " + String.valueOf(this.b) +
                                               ". Src specified is: " + String.valueOf(src));
        }

        long currentPktSendTime = Math.max(now, this.getSendTime(src));
        /*
         * Mar. 13, 2006
         * Hao Wang
         *
         * unit of bandwidth now B/s
         */
        long finishTime = currentPktSendTime + size * 1000000 / this.options.getBW();
        if (finishTime - now > this.options.getBT() * 1000) {
            // buffer overflow, drop packet
            manager.packetDropped();
            return -1;
        }
        this.insertSendTime(src, finishTime);

        if(!this.live || Math.random() < this.options.getLossRate()) {
            // packet lost due to dead link or transmission error
            manager.packetLost();
            return -1;
        }

        return  finishTime + (this.options.getDelay() * 1000);
    }

    /**
     * @param a Int specifying a node
     * @param b Int specifying a node
     * @return Returns true if this is an edge between a and b
     */
    public boolean isEdge(int a, int b) {
	return ((this.a == a && this.b == b) || (this.a == b && this.b == a));
    }

    /**
     * Returns node a
     * @return The address of node a
     */
    public int getNodeA() {
	return this.a;
    }

    /**
     * Returns node b
     * @return The address of node b
     */
    public int getNodeB() {
	return this.b;
    }

    /**
     * Sets the state of the edge, either live or not live
     * @param state The state of the edge. True means live
     */
    public void setState(boolean state) {
	this.live = state;
    }

    /**
     * @return True if edge is live
     */
    public boolean isLive() {
	return this.live;
    }

    /**
     * @return The options of the edge. That is, the loss rate, delay and bandwidth
     */
    public EdgeOptions getOptions() {
	return this.options;
    }

    /**
     * Sets the options for the edge
     * @param options The edge options
     */
    public void setOptions(EdgeOptions options) {
	this.options = options;
    }


    private void insertSendTime(int node, long time) {
	this.nextPktSendTime[getIndex(node)] = time;
    }

    private long getSendTime(int node) {
	return this.nextPktSendTime[getIndex(node)];
    }

    private int getIndex(int node) {
	if (node == this.a) {
	    return 0;
	}
	return 1;
    }
}
