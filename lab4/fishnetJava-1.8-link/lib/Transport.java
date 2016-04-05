import java.math.BigInteger;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;

/**
 * <pre>   
 * This conveys the header for reliable message transfer.
 * This is carried in the payload of a Packet, and in turn the data being
 * transferred is carried in the payload of the Transport packet.
 * </pre>   
 */
public class Transport {
    
    public static final int MAX_PACKET_SIZE = Packet.MAX_PAYLOAD_SIZE;
    public static final int HEADER_SIZE = 12;
    public static final int MAX_PAYLOAD_SIZE = MAX_PACKET_SIZE - HEADER_SIZE;
    public static final int MAX_PORT_NUM = 255;  // port numbers range from 0 to 255

    public static final int SYN = 0;
    public static final int ACK = 1;
    public static final int FIN = 2;
    public static final int DATA = 3;

    private int srcPort;
    private int destPort;
    private int type;
    private int window;
    private int seqNum;
    private byte[] payload;

    /**
     * Constructing a new transport packet.
     * @param srcPort The source port
     * @param destPort The destination port
     * @param type The type of packet. Either SYN, ACK, FIN, or DATA
     * @param window The window size
     * @param seqNum The sequence number of the packet
     * @param payload The payload of the packet.
     */
    public Transport(int srcPort, int destPort, int type, int window, int seqNum, byte[] payload) throws IllegalArgumentException {
	if(srcPort < 0 || srcPort > MAX_PORT_NUM   ||
	   destPort < 0 || destPort > MAX_PORT_NUM ||
	   type < SYN || type > DATA               ||
	   payload.length > MAX_PAYLOAD_SIZE) {
	    throw new IllegalArgumentException("Illegal arguments given to Transport packet");
	}

	this.srcPort = srcPort;
	this.destPort = destPort;
	this.type = type;
	this.window = window;
	this.seqNum = seqNum;
	this.payload = payload;
    }

    /**
     * @return The source port
     */
    public int getSrcPort() {
	return this.srcPort;
    }

    /**
     * @return The destination port
     */
    public int getDestPort() {
	return this.destPort;
    }

    /**
     * @return The type of the packet
     */
    public int getType() {
	return this.type;
    }

    /**
     * @return The window size
     */
    public int getWindow() {
	return this.window;
    }

    /**
     * @return The sequence number
     */
    public int getSeqNum() {
	return this.seqNum;
    }

    /**
     * @return The payload
     */
    public byte[] getPayload() {
	return this.payload;
    }
    
    /**
     * Convert the Transport packet object into a byte array for sending over the wire.
     * Format:
     *        source port = 1 byte
     *        destination port = 1 byte
     *        type = 1 byte
     *        window size = 4 bytes
     *        sequence number = 4 bytes
     *        packet length = 1 byte
     *        payload <= MAX_PAYLOAD_SIZE bytes
     * @return A byte[] for transporting over the wire. Null if failed to pack for some reason
     */
    public byte[] pack() {
	
	ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
	byteStream.write(this.srcPort);
	byteStream.write(this.destPort);
	byteStream.write(this.type);
	
	// write 4 bytes for window size
	byte[] windowByteArray = (BigInteger.valueOf(this.window)).toByteArray();
	int paddingLength = 4 - windowByteArray.length;
	for(int i = 0; i < paddingLength; i++) {
	    byteStream.write(0);
	}
	byteStream.write(windowByteArray, 0, Math.min(windowByteArray.length, 4));

	// write 4 bytes for sequence number
	byte[] seqByteArray = (BigInteger.valueOf(this.seqNum)).toByteArray();
	paddingLength = 4 - seqByteArray.length;
	for(int i = 0; i < paddingLength; i++) {
	    byteStream.write(0);
	}
	byteStream.write(seqByteArray, 0, Math.min(seqByteArray.length, 4));

	byteStream.write(HEADER_SIZE + this.payload.length);	
	byteStream.write(this.payload, 0, this.payload.length);

	return byteStream.toByteArray();
    }

    /**
     * Unpacks a byte array to create a Transport object
     * Assumes the array has been formatted using pack method in Transport
     * @param packet String representation of the transport packet
     * @return Transport object created or null if the byte[] representation was corrupted
     */
    public static Transport unpack(byte[] packet) {
	ByteArrayInputStream byteStream = new ByteArrayInputStream(packet);

	int srcPort = byteStream.read();
	int destPort = byteStream.read();
	int type = byteStream.read();
	
	byte[] windowByteArray = new byte[4];
	if(byteStream.read(windowByteArray, 0, 4) != 4) {
	    return null;
	}
	int window = (new BigInteger(windowByteArray)).intValue();

	byte[] seqByteArray = new byte[4];
	if(byteStream.read(seqByteArray, 0, 4) != 4) {
	    return null;
	}
	int seqNum = (new BigInteger(seqByteArray)).intValue();

	int packetLength = byteStream.read();

	byte[] payload = new byte[packetLength - HEADER_SIZE];
	int bytesRead = Math.max(0, byteStream.read(payload, 0, payload.length));
	
	if((HEADER_SIZE + bytesRead) != packetLength) {
	    return null;
	}

	try {
	    return new Transport(srcPort, destPort, type, window, seqNum, payload);
	}catch(IllegalArgumentException e) {
	    // will return null
	}
	return null;
    }
}
