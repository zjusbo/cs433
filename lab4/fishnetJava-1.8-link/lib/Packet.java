import java.math.BigInteger;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;

/**
 * <pre>   
 * Packet defines the Fishnet packet headers and some constants.
 * </pre>   
 */
public class Packet {

    public static final int BROADCAST_ADDRESS = 255;
    public static final int MAX_ADDRESS = 255;
    public static final int HEADER_SIZE = 9;
    public static final int MAX_PACKET_SIZE = 128;  // bytes
    public static final int MAX_PAYLOAD_SIZE = MAX_PACKET_SIZE - HEADER_SIZE;  // bytes
    public static final int MAX_TTL = 15;           // max hop count

    private int dest;
    private int src;
    private int ttl;
    private int protocol;
    private int seq;
    private byte[] payload;

    /**
     * Constructing a new packet.
     * @param dest The destination fishnet address.
     * @param src The source fishnet address.
     * @param ttl The time-to-live value for this packet.
     * @param protocol What type of packet this is.
     * @param seq The sequence number of the packet.
     * @param payload The payload of the packet.
     * @throws IllegalArgumentException If the given arguments are invalid
     */
    public Packet(int dest, int src, int ttl, int protocol, int seq, byte[] payload) throws IllegalArgumentException {
	
	if(!this.isValid(dest, src, ttl, protocol, payload.length + Packet.HEADER_SIZE)) {
	    throw new IllegalArgumentException("Arguments passed to constructor of Packet are invalid");
	}
	
	this.dest = dest;
	this.src = src;
	this.ttl = ttl;
	this.protocol = protocol;
	this.seq = seq;
	this.payload = payload;
    }

    /**
     * Provides a string representation of the packet.
     * @return A string representation of the packet.
     */
    public String toString() {
	return new String("Packet: " + this.src + "->" + this.dest + " protocol: " + this.protocol + " TTL: " + this.ttl + 
			  " seq: " + this.seq + " contents: " + Utility.byteArrayToString(this.payload));
    }

    /**
     * @return The address of the destination node
     */
    public int getDest() {
	return this.dest;
    }
    
    /**
     * @return The address of the src node
     */
    public int getSrc() {
	return this.src;
    }

    /**
     * @return The TTL of the packet
     */
    public int getTTL() {
	return this.ttl;
    }

    /**
     * Sets the TTL of this packet
     * @param ttl TTL to set
     */
    public void setTTL(int ttl) {
	this.ttl = ttl;
    }
    
    /**
     * @return The protocol used for this packet
     */
    public int getProtocol() {
	return this.protocol;
    }

    /**
     * @return The sequence number of this packet
     */
    public int getSeq() {
	return this.seq;
    }

    /**
     * @return The payload of this packet
     */
    public byte[] getPayload() {
	return this.payload;
    }

    /**
     * Convert the Packet object into a byte array for sending over the wire.
     * Format:
     *        destination address: 1 byte
     *        source address: 1 byte
     *        ttl (time to live): 1 byte
     *        protocol: 1 byte
     *        packet length: 1 byte
     *        packet sequence num: 4 bytes
     *        payload: <= MAX_PAYLOAD_SIZE bytes
     * @return A byte[] for transporting over the wire. Null if failed to pack for some reason
     */
    public byte[] pack() {	
	
	ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
	byteStream.write(this.dest);
	byteStream.write(this.src);
	byteStream.write(this.ttl);
	byteStream.write(this.protocol);
	byteStream.write(this.payload.length + Packet.HEADER_SIZE);

	byte[] seqByteArray = (BigInteger.valueOf(this.seq)).toByteArray();
	int paddingLength = 4 - seqByteArray.length;
	for(int i = 0; i < paddingLength; i++) {
	    byteStream.write(0);
	}

	byteStream.write(seqByteArray, 0, Math.min(seqByteArray.length, 4));
		
	byteStream.write(this.payload, 0, this.payload.length);

	return byteStream.toByteArray();
    }

    /**
     * Unpacks a byte array to create a Packet object
     * Assumes the array has been formatted using pack method in Packet
     * @param packedPacket String representation of the packet
     * @return Packet object created or null if the byte[] representation was corrupted
     */
    public static Packet unpack(byte[] packedPacket){
	
	ByteArrayInputStream byteStream = new ByteArrayInputStream(packedPacket);
	
	int dest = byteStream.read();
	int src = byteStream.read();
	int ttl = byteStream.read();
	int protocol = byteStream.read();
	int packetLength = byteStream.read();
	
	byte[] seqByteArray = new byte[4];
	if(byteStream.read(seqByteArray, 0, 4) != 4) {
	    return null;
	}

	int seq = (new BigInteger(seqByteArray)).intValue();

	byte[] payload = new byte[byteStream.available()];
	byteStream.read(payload, 0, payload.length);

	if((9 + payload.length) != packetLength) {
	    return null;
	}	
	
	try {
	    return new Packet(dest, src, ttl, protocol, seq, payload);
	}catch(IllegalArgumentException e) {
	    // will return null
	}
	return null;
    }
    
    /**
     * Tests if the address is a valid one
     * @param addr Address to check
     * @return True is address is valid, else false
     */
    public static boolean validAddress(int addr) {
	return (addr <= MAX_ADDRESS && addr >= 0);
    }

    /**
     * Tests if this Packet is valid or not
     * @return True if packet is valid, else false
     */
    public boolean isValid() {
	return this.isValid(this.dest, this.src, this.ttl, this.protocol, this.payload.length + HEADER_SIZE);
    }

    private boolean isValid(int dest, int src, int ttl, int protocol, int size) {
	return (dest <= MAX_ADDRESS && dest >= 0   &&
		Packet.validAddress(src)           &&
		Protocol.isProtocolValid(protocol) &&
		ttl <= MAX_TTL && ttl >= 0         &&
		size <= MAX_PACKET_SIZE);

    }

	/**
	 * Tests if this Packet is valid to send
	 * A valid packet may have TTL = 0, but a "valid to send" packet cannot have TTL = 0
	 * @return Trie if the packet is valid to send, else false
	 */
	public boolean isValidToSend()
	{
		return (isValid() && this.ttl > 0);
	}

}
