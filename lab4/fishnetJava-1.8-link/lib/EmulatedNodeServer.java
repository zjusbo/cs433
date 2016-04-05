import java.lang.Thread;
import java.util.ArrayList;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.io.IOException;

/**
 * <pre>
 * In a seperate thread, this class listens to incoming messages from neighbors and stores the data received
 * </pre>
 */
public class EmulatedNodeServer extends Thread {

    public static final int ID = 1;

    private DatagramSocket socket;
    private ArrayList packetsReceived;
    private static int MAX_PACKET_LENGTH = EmulatorPacket.MAX_PACKET_SIZE;
    private MultiplexIO multiplexIO;

    /**
     * Creates a new EmulatedNodeServer
     * @param socket The UDP socket to listen on
     */
    public EmulatedNodeServer(DatagramSocket socket, MultiplexIO multiplexIO) {
	this.socket = socket;
	this.packetsReceived = new ArrayList();
	this.multiplexIO = multiplexIO;
    }

    /**
     * This starts the server
     */
    public void run() {
	while(true) {
	    byte[] buf = new byte[MAX_PACKET_LENGTH];

	    // receive request
	    DatagramPacket packet = new DatagramPacket(buf, buf.length);
	    try {
		socket.receive(packet);
	    }catch(IOException e) {
		System.err.println("Encountered IOException when to recceive packet. User should kill explicitly. \nStack Trace:");
		e.printStackTrace();
		continue;
	    }
	    this.storePacket(packet);
            /*
             * Apr. 1, 2006
             * Hao Wang
             *
             * Eliminate the use of a polling thread to improve performance
             */
            /*
             * try {
             *     this.multiplexIO.write(ID);
             * }catch(IOException e) {
             *     System.out.println("IOException occured in EmulatedNodeServer while writing to MultiplexIO. Exception: " + e);
             * }
             */
            this.multiplexIO.write(ID);
	}
    }

    /**
     * Tests if there are more packets stored
     * @return True if there are more packets stored in memory
     */
    public synchronized boolean hasPackets() {
	return !this.packetsReceived.isEmpty();
    }

    /**
     * Gets the first packet stored
     * @return The first packet stored
     */
    public synchronized DatagramPacket getPacket() {
	if(this.packetsReceived.isEmpty()) {
	    return null;
	}
	return (DatagramPacket)this.packetsReceived.remove(0);
    }

    private synchronized void storePacket(DatagramPacket packet) {
	this.packetsReceived.add(packet);
    }
}
