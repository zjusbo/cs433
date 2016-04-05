/*
 * Decompiled with CFR 0_114.
 */
public class TCPSockID {
    public static final int ANY_PORT = 256;
    public static final int ANY_ADDRESS = 256;
    private int localAddr = 256;
    private int localPort = 256;
    private int remoteAddr = 256;
    private int remotePort = 256;

    public int getLocalAddr() {
        return this.localAddr;
    }

    public void setLocalAddr(int localAddr) {
        this.localAddr = localAddr;
    }

    public int getLocalPort() {
        return this.localPort;
    }

    public void setLocalPort(int localPort) {
        this.localPort = localPort;
    }

    public int getRemoteAddr() {
        return this.remoteAddr;
    }

    public void setRemoteAddr(int remoteAddr) {
        this.remoteAddr = remoteAddr;
    }

    public int getRemotePort() {
        return this.remotePort;
    }

    public void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }
}