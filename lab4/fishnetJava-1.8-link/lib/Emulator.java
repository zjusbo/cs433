import java.net.Socket;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.SocketException;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.lang.NumberFormatException;
import java.lang.Integer;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Manages an emulated node
 */
public class Emulator extends Manager {
    private static int MAX_BYTE_RATE = 100000; // don't send more than 100KB/s

    private Socket trawler;
    private PrintWriter trawlerWriter;
    private BufferedReader trawlerReader;
    private DatagramSocket udpSocket;
    private int fishAddress;
    private Node node;
    private EmulatedNodeServer server;
    private IOThreadEmulator io;
    private MultiplexIO multiplexIO;
    private HashMap arp;  // Address resolution protocol. Maps fish addresses to [ip address, ip port]


    /**
     * Create a new emulator
     * @param trawlerName Name of the machine that the Trawler is on
     * @param trawlerPort The port that the Trawler is listening on
     * @param localUDPPort The UDP port that this node should use to talk to its neighbors
     * @throws UnknownHostException If the trawlerName cannot be resolved
     * @throws SocketException If there is an error in creating a TCP socket
     * @throws IOException If there is an error in writing to the TCP socket
     * @throws IllegalArgumentException If the local port given is already in use
     */
    public Emulator(String trawlerName, int trawlerPort, int localUDPPort) throws UnknownHostException, SocketException,
										  IOException, IllegalArgumentException {
	super(Utility.fishTime());
	super.setParser(new EmulationCommandsParser(this));
	this.trawler = new Socket(trawlerName, trawlerPort);
	this.trawlerWriter = new PrintWriter(trawler.getOutputStream(), true);
	this.trawlerReader = new BufferedReader(new InputStreamReader(trawler.getInputStream()));
	this.udpSocket = new DatagramSocket(localUDPPort);
	try {
	    this.fishAddress = this.getFishAddress();
	}catch(NumberFormatException e) {
	    System.err.println("Msg received from trawler is not an int, thus is not a fish address!!");
	    System.exit(1);
	}
	if(this.fishAddress == Packet.BROADCAST_ADDRESS) {
	    // Trawler returns broadcast address to signal there's already someone using the local port
	    System.err.println("Port " + localUDPPort + " is already in use. Pick another");
	    throw new IllegalArgumentException("Illegal local port " + localUDPPort);
	}
	this.node = new Node(this, this.fishAddress);
	this.arp = new HashMap();
	this.multiplexIO= new MultiplexIO();
	this.server = new EmulatedNodeServer(this.udpSocket, this.multiplexIO);
	this.io = new IOThreadEmulator(this.multiplexIO);
	this.server.start();
	this.io.start();
        /*
         * Apr. 1, 2006
         * Hao Wang
         *
         * Eliminate the use of a polling thread to improve performance
         */
        /*
         * this.multiplexIO.start();
         */
    }

    /**
     * <pre>
     * Starts the emulated node
     * do:
     *   Read commands from the fishnet file if there is one
     *   Process any defered events
     *   Process 1 pending incoming message. Timeout when next event is supposed to occur
     * loop
     * </pre>
     */
    public void start() {
	this.node.start();

	long deferParsingTill = 0;

	Event nextEvent = null;
	long waitTime; // time in microseconds
	while(true) {
	    try {
		long now = Utility.fishTime();

		deferParsingTill = this.readFishFile(deferParsingTill);

		// Run all due events
		while(!this.sortedEvents.isEmpty() &&
		      (nextEvent = this.sortedEvents.getNextEvent()).timeToOccur() <= now) {

		    this.sortedEvents.removeNextEvent();
		    try {
			nextEvent.callback().invoke();
		    }catch(Exception e) {
			System.err.println("Exception while trying to invoke method in Emulator. Error: " + e);
			e.printStackTrace();
		    }
		}

		/*
                 * Apr. 3, 2006
                 * Hao Wang
                 *
                 * Bug Fix: Emulator scheduling
                 * 1. busy waiting when no events or parsing pending
                 * 2. 'deferParsingTill' misused as delay
                 * 3. pending events may cause delayed parsing
                 */
                /*
                 * if(this.sortedEvents.isEmpty()) {
                 *     waitTime = Math.max(-1, deferParsingTill);
                 *     //waitTime = -1;
                 * }else {
                 *     waitTime = nextEvent.timeToOccur() - now;
                 * }
                 */
                /*
                 * Note: waitTime now is the time to wait TILL, not to wait for
                 */
                if (this.sortedEvents.isEmpty()) {
                    waitTime = deferParsingTill;
                } else {
                    if (deferParsingTill == -1) {
                        waitTime = nextEvent.timeToOccur();
                    } else {
                        waitTime =
                            Math.min(nextEvent.timeToOccur(), deferParsingTill);
                    }
                }

                /*
                 * if( waitTime == -1 || (Utility.fishTime() < (now + waitTime)) ) {
                 *     int channelID = this.getIOChannelID(waitTime, now + waitTime);
                 */
		if( waitTime == -1 || (Utility.fishTime() < (waitTime)) ) {
		    int channelID = this.getIOChannelID(waitTime);
		    if(channelID == EmulatedNodeServer.ID) {
			this.processPacket(this.server.getPacket());
		    }else if(channelID == IOThreadEmulator.ID) {
			this.parser.parseLine(this.io.readLine(), Utility.fishTime());
		    }
		}
	    }catch(Exception e) {
		System.err.println("Exception occured in Emulator. Stack trace: ");
		e.printStackTrace();
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
	this.refreshARP();
	EmulatorPacket emulatorPacket = new EmulatorPacket(to, from, pkt);
	byte[] payload = emulatorPacket.pack();
	if(payload == null) {
	    return false;
	}
	DatagramPacket physicalPacket = new DatagramPacket(payload, payload.length);
	try {
            /*
             * Mar. 12, 2006
             * Hao Wang
             *
             * Use physical link emulation if necessary
             */
	    if(to == Packet.BROADCAST_ADDRESS) {
                /*
                 * this.broadcastPacket(physicalPacket);
                 */
		this.broadcastPacket(physicalPacket, pkt.length);
	    }else if(this.arp.containsKey(new Integer(to))) {
                /*
                 * this.physicalSend(physicalPacket, to);
                 */
                this.schedulePkt(physicalPacket, to, pkt.length);
	    }else {
		System.err.println("Node " + to + " is not a neighbor of node " + from);
		return false;
	    }
	}catch(IOException e) {
	    System.err.println("IOException occured while trying to send to node: " + to + ". Exception: " + e);
	    e.printStackTrace();
	    return false;
	}
	return true;
    }

    /**
     * Sends the msg to the the specified node
     * @param nodeAddr Address of the node to whom the message should be sent
     * @param msg The msg to send to the node
     * @return True if msg sent, false if address is not valid
     */
    public boolean sendNodeMsg(int nodeAddr, String msg) {
	this.node.onCommand(msg);
	return true;
    }

    /**
     * Retrieve current time in milliseconds
     * @return Current time in milliseconds
     */
    public long now() {
	return Utility.fishTime() / 1000;
    }

    private int getFishAddress() throws NumberFormatException, IOException {
	this.trawlerWriter.println(this.udpSocket.getLocalPort());
	return Integer.parseInt(this.trawlerReader.readLine());
    }

    /*
     * Mar. 12, 2006
     * Hao Wang
     *
     * Support physical link emulation
     */
    /*
     * Apr. 14, 2006
     * Hao Wang
     *
     * Bug: Broadcast packet may be sent to wrong nodes, because UDP destination
     *      information is overwritten by later call to schedulePkt
     * Fix: Set UDP destination information individually
     */
    private void schedulePkt(DatagramPacket packet, int destAddr, int size) throws IOException {
        EmulatorARPData arpData = (EmulatorARPData) this.arp.get(new Integer(destAddr));
        EmulatedLink link = arpData.getEmulatedLink();
        if (link == null) {
            // no physical link emulation, send immediately
            packet.setAddress(arpData.getIPAddress());
            packet.setPort(arpData.getPort());
            this.udpSocket.send(packet);
        } else {
            // physical link emulation, schedule transmission
            long currentTime = Utility.fishTime();
            long timeToDeliver = link.schedulePkt(this, size, currentTime);

            if(timeToDeliver == -1) {
                return;  // packet dropped/lost
            }

            /*
             * Apr. 1, 2006
             * Hao Wang
             *
             * Bug: Emulator freezes due to timer not aligned on milliseconds
             * Fix: Align timeToDeliver to next milliseconds
             */
            long usecFraction = timeToDeliver % 1000;
            if (usecFraction != 0) {
                timeToDeliver += 1000 - usecFraction;
            }

            String[] paramTypes = {
                "java.net.DatagramPacket",
                "java.net.InetAddress",
                "java.lang.Integer"
            };
            Object[] params = {
                packet,
                arpData.getIPAddress(),
                Integer.valueOf(arpData.getPort())
            };
            this.addEvent(timeToDeliver, "physicalSend", this, paramTypes, params);
        }
    }

    /*
     * Apr. 14, 2006
     * Hao Wang
     *
     * Bug: Broadcast packet may be sent to wrong nodes, because UDP destination
     *      information is overwritten by later call to schedulePkt
     * Fix: Set UDP destination information individually
     */
    public void physicalSend(DatagramPacket packet,
                             InetAddress address,
                             Integer port) throws IOException {
        packet.setAddress(address);
        packet.setPort(port.intValue());
        this.udpSocket.send(packet);
    }

    public void physicalSend(DatagramPacket packet) throws IOException {
        this.udpSocket.send(packet);
    }

    /*
     * Mar. 12, 2006
     * Hao Wang
     *
     * Use physical link emulation if necessary
     */
    private void broadcastPacket(DatagramPacket packet, int size) throws IOException {
        Iterator iter = this.arp.keySet().iterator();
        while(iter.hasNext()) {
            Integer neighborAddr = (Integer)iter.next();
            this.schedulePkt(packet, neighborAddr.intValue(), size);
        }
    }

    /**
     * RefreshArp -- Make sure our arp cache is up to date, by checking to see
     * 	if the trawler has given us any updates
     */
    private void refreshARP() {
	try {
	    while(this.trawlerReader.ready()) {
		String trawlerCmd = trawlerReader.readLine();
		ArrayList addNeighborData = new ArrayList();
		int neighborToRemove;
		if(TrawlerNodeARPCommands.receiveReset(trawlerCmd)) {
		    // Clear ARP Cache
		    this.arp.clear();
		}else if( (neighborToRemove = TrawlerNodeARPCommands.receiveRemoveNeighbor(trawlerCmd)) >= 0 ) {
		    // Remove a neighbor
		    this.arp.remove(new Integer(neighborToRemove));
		}else if(TrawlerNodeARPCommands.receiveAddNeighbor(trawlerCmd, addNeighborData)) {
		    // Add a neighbor
		    Integer fishAddr = (Integer)addNeighborData.get(0);
		    EmulatorARPData arpData = (EmulatorARPData)addNeighborData.get(1);
		    if(Packet.validAddress(fishAddr.intValue())) {
			this.arp.put(fishAddr, arpData);
		    }
		}else {
		    System.err.println("Unrecognized command from trawler: " + trawlerCmd);
		}
	    }
	}catch(IOException e) {
	    System.err.println("Encountered IOException while trying to refresh ARP cache. Is Trawler dead?..\n Stack trace: ");
	    e.printStackTrace();
	}catch(Exception e) {
	    System.err.println("Encountered Exception while trying to refresh ARP cache. Stack trace: ");
	    e.printStackTrace();
	}
    }

    private void processPacket(DatagramPacket packet) {
	InetAddress ipAddress = packet.getAddress();
	int port = packet.getPort();
	EmulatorPacket emulatorPacket = EmulatorPacket.unpack(packet.getData());
	if(emulatorPacket == null) {
	    // Corrupt data.
	    System.err.println("Was unable to extract packet received from " + ipAddress + ":" + port);
	    return;
	}
	Integer srcAddr = new Integer(emulatorPacket.getSrc());
	int destAddr = emulatorPacket.getDest();
        /*
         * Mar. 11, 2006
         * Hao Wang
         *
         * Check for existing ARP data
         */
        /*
         * this.arp.put(srcAddr, new EmulatorARPData(ipAddress, port));
         */
        boolean newARPData = false;
        if (!this.arp.containsKey(srcAddr)) {
            newARPData = true;
        } else {
            EmulatorARPData arpData = (EmulatorARPData)this.arp.get(srcAddr);
            if (!arpData.getIPAddress().equals(ipAddress) ||
                arpData.getPort() != port) {
                newARPData = true;
            }
        }
        if (newARPData) {
            // we don't have edge options, defer emulation until
            // we learn ARP data from trwaler
            this.arp.put(srcAddr, new EmulatorARPData(ipAddress,port));
        }
	if(destAddr == this.fishAddress || destAddr == Packet.BROADCAST_ADDRESS) {
	    this.node.onReceive(srcAddr, emulatorPacket.getPayload());
	}
	// drop if not for me. This can happen if we took a port that was recently occupied by another node
    }

    /*
     * Apr. 3, 2006
     * Hao Wang
     *
     * Bug Fix: Emulator scheduling
     * 1. use of the 'synchronized' keyword may dead-lock
     *    in case of indefinite wait
     */

//  private int getIOChannelID(long waitTime, long endTime) {
//	waitTime = Math.max(waitTime, 0);
//	try {
//	    while(this.multiplexIO.isEmpty() &&
//		  (Utility.fishTime() < endTime)) {
//		synchronized(this.multiplexIO) {
//		    // divide by 1000 since waitTime is in microseconds
//		    this.multiplexIO.wait(waitTime / 1000);
//		}
//	    }
//	}catch(InterruptedException e) {
//	    // Do nothing. This should never happen, even if it does no harm done
//	}
//	return this.multiplexIO.read();
//  }

    private int getIOChannelID(long endTime) {
        if (endTime == -1) {
            // wait indefinitely (until some IO event occurs)
            try {
                synchronized (this.multiplexIO) {
                    while (this.multiplexIO.isEmpty()) {
                        this.multiplexIO.wait();
                    }
                }
            } catch (InterruptedException e) {}
        } else {
            // wait until endTime or some IO event occurs
            long waitTime;
            try {
                synchronized (this.multiplexIO) {
                    while (this.multiplexIO.isEmpty() &&
                           (waitTime = endTime - Utility.fishTime()) > 0) {
                        // divide by 1000 since waitTime is in microseconds
                        this.multiplexIO.wait(waitTime / 1000);
                    }
                }
            } catch (InterruptedException e) {}
        }

	return this.multiplexIO.read();
    }
}
