 /*
 * Decompiled with CFR 0_114.
 * 
 * Could not load the following classes:
 *  TCPSock
 *  TCPSock$State
 *  TCPSockID
 */
public class SimpleTCPSockSpace {
    int sz;
    TCPSock[] socks;

    public SimpleTCPSockSpace() {
        this(8);
    }

    public SimpleTCPSockSpace(int sz) {
        this.sz = sz;
        this.socks = new TCPSock[sz];
        for (int i = 0; i < sz; ++i) {
            this.socks[i] = null;
        }
    }

    public TCPSock newSocket() {
        for (int i = 0; i < this.sz; ++i) {
            if (this.socks[i] != null) continue;
            this.socks[i] = new TCPSock();
            return this.socks[i];
        }
        return null;
    }

    public void release(TCPSock sock) {
        for (int i = 0; i < this.sz; ++i) {
            if (this.socks[i] != sock) continue;
            this.socks[i] = null;
            return;
        }
    }

    public TCPSock getLocalSock(int localAddr, int localPort, TCPSock.State state) {
        for (int i = 0; i < this.sz; ++i) {
            TCPSockID id;
            TCPSock sock = this.socks[i];
            if (sock == null || (id = sock.getID()).getLocalAddr() != localAddr || id.getLocalPort() != localPort || state != TCPSock.State.ANY && state != sock.getState()) continue;
            return sock;
        }
        return null;
    }

    public TCPSock getSock(int localAddr, int localPort, int remoteAddr, int remotePort) {
        for (int i = 0; i < this.sz; ++i) {
            TCPSockID id;
            TCPSock sock = this.socks[i];
            if (sock == null || (id = sock.getID()).getLocalAddr() != localAddr || id.getLocalPort() != localPort || id.getRemoteAddr() != remoteAddr || id.getRemotePort() != remotePort) continue;
            return sock;
        }
        return null;
    }
}
