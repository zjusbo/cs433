 /*
 * Decompiled with CFR 0_114.
 * 
 * Could not load the following classes:
 *  Callback
 *  Manager
 *  Packet
 *  PingRequest
 *  TCPManager
 *  TCPSock
 *  TCPSock$State
 *  TransferClient
 *  TransferServer
 *  Transport
 *  Utility
 */
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;

public class Node {
    private final long PingTimeout = 10000;
    private Manager manager;
    private int addr;
    private ArrayList pings;
    private TCPManager tcpMan;

    public Node(Manager manager, int addr) {
        this.manager = manager;
        this.addr = addr;
        this.pings = new ArrayList();
        this.tcpMan = new TCPManager(this, addr, manager);
    }

    public void start() {
        this.logOutput("started");
        this.addTimer(10000, "pingTimedOut");
        this.tcpMan.start();
    }

    public void onReceive(Integer from, byte[] msg) {
        Packet packet = Packet.unpack((byte[])msg);
        if (packet == null) {
            this.logError("Unable to unpack message: " + Utility.byteArrayToString((byte[])msg) + " Received from " + from);
            return;
        }
        this.receivePacket(from, packet);
    }

    public void onCommand(String command) {
        if (this.matchTransferCommand(command)) {
            return;
        }
        if (this.matchServerCommand(command)) {
            return;
        }
        if (this.matchShutdownCommand(command)) {
            return;
        }
        if (this.matchPingCommand(command)) {
            return;
        }
        this.logError("Unrecognized command: " + command);
    }

    public void pingTimedOut() {
        Iterator iter = this.pings.iterator();
        while (iter.hasNext()) {
            PingRequest pingRequest = (PingRequest)iter.next();
            if (pingRequest.getTimeSent() + 10000 >= this.manager.now()) continue;
            try {
                this.logOutput("Timing out ping: " + (Object)pingRequest);
                iter.remove();
                continue;
            }
            catch (Exception e) {
                this.logError("Exception occured while trying to remove an element from ArrayList pings.  Exception: " + e);
                return;
            }
        }
        this.addTimer(10000, "pingTimedOut");
    }

    private boolean matchPingCommand(String command) {
        int index = command.indexOf(" ");
        if (index == -1) {
            return false;
        }
        try {
            int destAddr = Integer.parseInt(command.substring(0, index));
            String message = command.substring(index + 1);
            Packet packet = new Packet(destAddr, this.addr, 15, 0, 0, Utility.stringToByteArray((String)message));
            this.send(destAddr, packet);
            this.pings.add(new PingRequest(destAddr, Utility.stringToByteArray((String)message), this.manager.now()));
            return true;
        }
        catch (Exception e) {
            this.logError("Exception: " + e);
            return false;
        }
    }

    private void receivePacket(int from, Packet packet) {
        switch (packet.getProtocol()) {
            case 0: {
                this.receivePing(packet);
                break;
            }
            case 1: {
                this.receivePingReply(packet);
                break;
            }
            case 4: {
                int to = packet.getDest();
                Transport segment = Transport.unpack((byte[])packet.getPayload());
                if (segment == null) {
                    this.logError("Corrupted transport segment received from " + from);
                    break;
                }
                this.tcpMan.OnReceive(from, to, segment);
                break;
            }
            default: {
                this.logError("Packet with unknown protocol received. Protocol: " + packet.getProtocol());
            }
        }
    }

    private void receivePing(Packet packet) {
        this.logOutput("Received Ping from " + packet.getSrc() + " with message: " + Utility.byteArrayToString((byte[])packet.getPayload()));
        try {
            Packet reply = new Packet(packet.getSrc(), this.addr, 15, 1, 0, packet.getPayload());
            this.send(packet.getSrc(), reply);
        }
        catch (IllegalArgumentException e) {
            this.logError("Exception while trying to send a Ping Reply. Exception: " + e);
        }
    }

    private void receivePingReply(Packet packet) {
        Iterator iter = this.pings.iterator();
        String payload = Utility.byteArrayToString((byte[])packet.getPayload());
        while (iter.hasNext()) {
            PingRequest pingRequest = (PingRequest)iter.next();
            if (pingRequest.getDestAddr() != packet.getSrc() || !Utility.byteArrayToString((byte[])pingRequest.getMsg()).equals(payload)) continue;
            this.logOutput("Got Ping Reply from " + packet.getSrc() + ": " + payload);
            try {
                iter.remove();
            }
            catch (Exception e) {
                this.logError("Exception occured while trying to remove an element from ArrayList pings while processing Ping Reply.  Exception: " + e);
            }
            return;
        }
        this.logError("Unexpected Ping Reply from " + packet.getSrc() + ": " + payload);
    }

    private void send(int destAddr, Packet packet) {
        try {
            this.manager.sendPkt(this.addr, destAddr, packet.pack());
        }
        catch (IllegalArgumentException e) {
            this.logError("Exception: " + e);
        }
    }

    private void addTimer(long deltaT, String methodName) {
        try {
            Method method = Callback.getMethod((String)methodName, (Object)this, (String[])null);
            Callback cb = new Callback(method, (Object)this, null);
            this.manager.addTimer(this.addr, deltaT, cb);
        }
        catch (Exception e) {
            this.logError("Failed to add timer callback. Method Name: " + methodName + "\nException: " + e);
        }
    }

    public void sendSegment(int srcAddr, int destAddr, int protocol, byte[] payload) {
        Packet packet = new Packet(destAddr, srcAddr, 15, protocol, 0, payload);
        this.send(destAddr, packet);
    }

    public int getAddr() {
        return this.addr;
    }

    public void logError(String output) {
        this.log(output, System.err);
    }

    public void logOutput(String output) {
        this.log(output, System.out);
    }

    private void log(String output, PrintStream stream) {
        stream.println("Node " + this.addr + ": " + output);
    }

    private boolean matchTransferCommand(String command) {
        String[] args = command.split(" ");
        if (args.length < 5 || args.length > 7 || !args[0].equals("transfer")) {
            return false;
        }
        try {
            int destAddr = Integer.parseInt(args[1]);
            int port = Integer.parseInt(args[2]);
            int localPort = Integer.parseInt(args[3]);
            int amount = Integer.parseInt(args[4]);
            long interval = args.length >= 6 ? (long)Integer.parseInt(args[5]) : 1000;
            int sz = args.length == 7 ? Integer.parseInt(args[6]) : 65536;
            TCPSock sock = this.tcpMan.socket();
            sock.bind(localPort);
            sock.connect(destAddr, port);
            TransferClient client = new TransferClient(this.manager, this, sock, amount, interval, sz);
            client.start();
            return true;
        }
        catch (Exception e) {
            this.logError("Exception: " + e);
            return false;
        }
    }

    private boolean matchServerCommand(String command) {
        String[] args = command.split(" ");
        if (args.length < 3 || args.length > 6 || !args[0].equals("server")) {
            return false;
        }
        try {
            int port = Integer.parseInt(args[1]);
            int backlog = Integer.parseInt(args[2]);
            long servint = args.length >= 4 ? (long)Integer.parseInt(args[3]) : 1000;
            long workint = args.length >= 5 ? (long)Integer.parseInt(args[4]) : 1000;
            int sz = args.length == 6 ? Integer.parseInt(args[5]) : 65536;
            TCPSock sock = this.tcpMan.socket();
            sock.bind(port);
            sock.listen(backlog);
            TransferServer server = new TransferServer(this.manager, this, sock, servint, workint, sz);
            server.start();
            this.logOutput("server started, port = " + port);
            return true;
        }
        catch (Exception e) {
            this.logError("Exception: " + e);
            return false;
        }
    }

    private boolean matchShutdownCommand(String command) {
        String[] args = command.split(" ");
        if (args.length != 2 || !args[0].equals("shutdown")) {
            return false;
        }
        try {
            int port = Integer.parseInt(args[1]);
            TCPSock server = this.tcpMan.getLocalSock(this.addr, port, TCPSock.State.LISTEN);
            if (server != null) {
                this.logOutput("shuting down server running at port = " + port);
                server.close();
            } else {
                this.logError("no server running at port = " + port);
            }
            return true;
        }
        catch (Exception e) {
            this.logError("Exception: " + e);
            return false;
        }
    }
}