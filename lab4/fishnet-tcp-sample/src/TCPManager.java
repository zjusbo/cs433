/*
 * Decompiled with CFR 0_114.
 * 
 * Could not load the following classes:
 *  Callback
 *  Manager
 *  Node
 *  SimpleTCPSockSpace
 *  TCPSock$State
 *  Transport
 */
public class TCPManager {
    public static final int MAX_SOCK_NUM = 8;
    public static final int DEFAULT_SEND_BUF_SZ = 16384;
    public static final int DEFAULT_RECV_BUF_SZ = 16384;
    public static final int DEFAULT_MSS = 107;
    public static final int INITIAL_RTO = 1000;
    public static final int SYN_TIMEOUT = 1000;
    public static final int INITIAL_SND_WND = 10700;
    public static final double RTT_ESTIMATE_RATE = 0.125;
    public static final double RTTDEV_ESTIMATE_RATE = 0.25;
    public static final long RTO_MIN = 50;
    public static final long RTO_MAX = 30000;
    public static final int DUP_ACK_THRESHOLD = 3;
    public static final int FAST_RET_PER_RTO = 1;
    public static final float INITIAL_SND_CWND = 1.0f;
    public static final float INITIAL_SND_SSTHRESH = 16384.0f;
    public static final int EXP_BACKOFF_THRESHOLD = 1;
    private Node node;
    private int addr;
    private Manager manager;
    private SimpleTCPSockSpace socks;
    private static final byte[] dummy = new byte[0];

    public TCPManager(Node node, int addr, Manager manager) {
        this.node = node;
        this.addr = addr;
        this.manager = manager;
        this.socks = new SimpleTCPSockSpace(8);
    }

    public void start() {
    }

    public TCPSock socket() {
        TCPSock sock = this.socks.newSocket();
        if (sock != null) {
            sock.setManager(this);
            TCPSockID sid = sock.getID();
            sid.setLocalAddr(this.addr);
        }
        return sock;
    }

    public void release(TCPSock sock) {
        this.socks.release(sock);
    }

    public TCPSock getLocalSock(int localAddr, int localPort, TCPSock.State state) {
        return this.socks.getLocalSock(localAddr, localPort, state);
    }

    public TCPSock getLocalSock(int localAddr, int localPort) {
        return this.socks.getLocalSock(localAddr, localPort, TCPSock.State.ANY);
    }

    public TCPSock getSock(int localAddr, int localPort, int remoteAddr, int remotePort) {
        return this.socks.getSock(localAddr, localPort, remoteAddr, remotePort);
    }

    public void OnReceive(int srcAddr, int destAddr, Transport segment) {
        int srcPort = segment.getSrcPort();
        int destPort = segment.getDestPort();
        TCPSock sock = this.getSock(destAddr, destPort, srcAddr, srcPort);
        if (sock != null) {
            sock.OnReceive(srcAddr, destAddr, segment);
            return;
        }
        if (segment.getType() == 0 && (sock = this.getLocalSock(destAddr, destPort, TCPSock.State.LISTEN)) != null) {
            sock.OnReceive(srcAddr, destAddr, segment);
            return;
        }
    }

    public void send(TCPSockID sid, int type, int window, int seq, byte[] snd_buf, int len) {
        byte[] payload;
        if (len > 0) {
            payload = new byte[len];
            TCPSock.qread(snd_buf, seq, payload, 0, len);
        } else {
            payload = dummy;
        }
        Transport segment = new Transport(sid.getLocalPort(), sid.getRemotePort(), type, window, seq, payload);
        this.node.sendSegment(sid.getLocalAddr(), sid.getRemoteAddr(), 4, segment.pack());
    }

    public int initSeq(TCPSock sock) {
        return 0;
    }

    public void addTimer(long deltaT, Callback callback) {
        this.manager.addTimerAt(this.addr, this.manager.now() + deltaT, callback);
    }

    public long now() {
        return this.manager.now();
    }
}