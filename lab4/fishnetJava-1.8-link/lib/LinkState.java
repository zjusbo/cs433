import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;

/**
 * <pre>   
 * This manages a link state packet, to convey a list of neighbors around the
 * network for building routing tables.
 *
 * The format of the link state packet is very simple:
 *	a variable length array of <fishnet address>
 *
 *  Note that this header is assumed to be the "payload" contents of a class Packet,
 *  so it does not need source, destination, TTL, sequence #, etc., information.
 * </pre>   
 */
public class LinkState {
    
    public static int MAX_NEIGHBORS = Packet.MAX_PAYLOAD_SIZE;  

    private byte[] neighbors;   // each neighbor's address fits in 1 byte

    /**
     * Creates a new LinkState packet
     * @param neighbors An array of ints containing the neighbors' addresses
     */
    public LinkState(int[] neighbors) throws IllegalArgumentException {
	if(neighbors.length > MAX_NEIGHBORS) {
	    throw new IllegalArgumentException("Number of neighbors is greater than max allowed neighbors. Neighbors given: " +
					       neighbors.length + " Max allowed: " + MAX_NEIGHBORS);
	}
	// convert int[] to byte[]
	ByteArrayOutputStream stream = new ByteArrayOutputStream(neighbors.length);
	for(int i = 0; i < neighbors.length; i++) { 
	    stream.write(neighbors[i]);
	} 
	this.neighbors = stream.toByteArray();
    }

    private LinkState(byte[] neighbors) throws IllegalArgumentException {
	if(neighbors.length > MAX_NEIGHBORS) {
	    throw new IllegalArgumentException("Number of neighbors is greater than max allowed neighbors. Neighbors given: " +
					       neighbors.length + " Max allowed: " + MAX_NEIGHBORS);
	}
	this.neighbors = neighbors;
    }

    /**
     * @return An int[] containg the neighbors' addresses
     */
    public int[] getNeighbors() {
	ByteArrayInputStream stream = new ByteArrayInputStream(this.neighbors);
	int[] neighbors = new int[this.neighbors.length];
	for(int i = 0; i < neighbors.length; i++) {
	    neighbors[i] = stream.read();
	}
	return neighbors;
    }

    /**
     * Packs the LinkState packet
     * @return A byte[] containing a packed representaion of this packet
     */
    public byte[] pack() {
	return this.neighbors;
    }

    /**
     * Unpacks a packed representation of a LinkState packet
     * @param linkState A packed representation of a LinkState packet
     * @return A LinkState object
     */
    public static LinkState unpack(byte[] linkState) {
	try {
	    return new LinkState(linkState);
	}catch(IllegalArgumentException e) {
	    System.err.println("Could not unpack LinkState packet. Exception: " + e);
	}
	return null;
    }   
}
