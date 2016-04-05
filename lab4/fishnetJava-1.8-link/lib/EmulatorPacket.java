import java.net.DatagramPacket;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;

/**
 * Packet used by emulated nodes to send data to each other via UDP
 */
public class EmulatorPacket {

    public static int HEADER_SIZE = 3; // bytes
    public static int MAX_PACKET_SIZE = Packet.MAX_PACKET_SIZE + HEADER_SIZE;

    private int destAddr;
    private int srcAddr;
    private byte[] payload;

    /**
     * Constructing a new packet
     * @param destAddr The fishnet address of the destination node
     * @param srcAddr The fishnet address of the source node
     * @param payload The payload of the packet
     * @throws IllegalArgumentException If the size of the payload is too big
     */
    public EmulatorPacket(int destAddr, int srcAddr, byte[] payload) throws IllegalArgumentException {
	if((HEADER_SIZE + payload.length) > MAX_PACKET_SIZE) {
	    throw new IllegalArgumentException("Payload is too big");
	}
	this.destAddr = destAddr;
	this.srcAddr = srcAddr;
	this.payload = payload;
    }

    /**
     * Get the address of the destination node
     * @return The address of the destination node
     */
    public int getDest() {
	return this.destAddr;
    }

    /**
     * Get the address of the source node
     * @return The address of the source node
     */
    public int getSrc() {
	return this.srcAddr;
    }

    /**
     * Get the payload
     * @return The payload
     */
    public byte[] getPayload() {
	return this.payload;
    }

    /**
     * Return a string representation of this packet
     * @return A string representation of this packet
     */
    public String toString() {
	return new String("EmulatedPacket: " + this.srcAddr + "->" + this.destAddr + 
			  " payload: " + Utility.byteArrayToString(this.payload));
    }

    /**
     * Convert packet into byte[] for sending over UDP Socket
     * Format:
     *    destination address: 8 bits
     *    source address: 8 bits
     *    packet length: 8 bits
     *    payload: <= (MAX_PACKET_SIZE - HEADER_SIZE) bytes
     * @return A byte[] for sending over UDP
     */
    public byte[] pack() {
	ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
	byteStream.write(this.destAddr);
	byteStream.write(this.srcAddr);
	byteStream.write(HEADER_SIZE + this.payload.length);
	byteStream.write(this.payload, 0, this.payload.length);
	
	return byteStream.toByteArray();
    }
    
    /**
     * Unpacks a byte array to create a EmulatorPacket object
     * Assumes the array has been formatted using pack method
     * @param packet String representation of the packet
     * @return EmulatorPacket object created or null if the byte[] representation was corrupted
     */
    public static EmulatorPacket unpack(byte[] packet) {
	ByteArrayInputStream byteStream = new ByteArrayInputStream(packet);
	
	int destAddr = byteStream.read();
	int srcAddr = byteStream.read();
	int packetLength = byteStream.read();

	byte[] payload = new byte[packetLength - HEADER_SIZE];
	int numBytesRead = byteStream.read(payload, 0, payload.length);	
	
	if((HEADER_SIZE + numBytesRead) != packetLength) {
	   return null;
	}
	return new EmulatorPacket(destAddr, srcAddr, payload);	
    }
}
