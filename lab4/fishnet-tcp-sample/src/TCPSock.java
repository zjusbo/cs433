/*
 * Decompiled with CFR 0_114.
 * 
 * Could not load the following classes:
 *  Callback
 *  TCPManager
 *  TCPSock$State
 *  TCPSockID
 *  Transport
 */
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.LinkedList;

public class TCPSock {
    private TCPManager tcpMan = null;
    private TCPSockID id = new TCPSockID();
    private State state = State.NEW;
    private int snd_MSS = 107;
    private int snd_initseq = 0;
    private int rcv_initseq = 0;
    private LinkedList<TCPSock> pendingConnections = null;
    private int backlog = 0;
    private byte[] snd_buf = null;
    private int snd_base = 0;
    private int snd_top = 0;
    private int snd_next = 0;
    private byte[] rcv_buf = null;
    private int rcv_base = 0;
    private int rcv_top = 0;
    private int rcv_next = 0;
    private long RTO = 1000;
    private int rt_seq = 0;
    private int rt_pending = 0;
    private int rt_expired = 0;
    private int dup_ack = 0;
    private int fast_ret = 0;
    private long RTT_estimate = this.RTO;
    private long RTTDev_estimate = 0;
    private int RTT_sample_seq = -1;
    private long RTT_sample_send_time = 0;
    private int snd_rcvWnd = 0;
    private float snd_cwnd = 1.0f;
    private float snd_ssthresh = 16384.0f;
    private int snd_wnd;

    public TCPSock() {
        this.update_snd_wnd();
    }

    public TCPManager getManager() {
        return this.tcpMan;
    }

    public void setManager(TCPManager tcpMan) {
        this.tcpMan = tcpMan;
    }

    public TCPSockID getID() {
        return this.id;
    }

    public void setID(TCPSockID id) {
        this.id = id;
    }

    public State getState() {
        return this.state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public int bind(int localPort) {
        if (this.id.getLocalPort() != 256) {
            return -1;
        }
        if (localPort == 256) {
            return -1;
        }
        int localAddr = this.id.getLocalAddr();
        TCPSock sock = this.tcpMan.getLocalSock(localAddr, localPort);
        if (sock != null) {
            return -1;
        }
        this.id.setLocalPort(localPort);
        return 0;
    }

    public int listen(int backlog) {
        if (this.state != State.NEW) {
            return -1;
        }
        if (this.id.getLocalPort() == 256) {
            return -1;
        }
        this.pendingConnections = new LinkedList();
        this.backlog = backlog;
        this.state = State.LISTEN;
        return 0;
    }

    public TCPSock accept() {
        if (this.state != State.LISTEN) {
            return null;
        }
        if (this.pendingConnections.isEmpty()) {
            return null;
        }
        return this.pendingConnections.removeFirst();
    }

    public boolean isConnectionPending() {
        return this.state == State.SYN_SENT;
    }

    public boolean isClosed() {
        return this.state == State.CLOSED;
    }

    public boolean isConnected() {
        return this.state == State.ESTABLISHED;
    }

    public boolean isClosurePending() {
        return this.state == State.SHUTDOWN;
    }

    public int connect(int destAddr, int destPort) {
        if (this.state != State.NEW) {
            return -1;
        }
        if (this.id.getLocalPort() == 256) {
            return -1;
        }
        this.id.setRemoteAddr(destAddr);
        this.id.setRemotePort(destPort);
        this.snd_buf = new byte[16385];
        this.snd_top = this.snd_next = this.tcpMan.initSeq(this);
        this.snd_base = this.snd_next;
        this.snd_initseq = this.snd_next;
        this.RTT_sample_seq = this.snd_base - 1;
        ++this.snd_top;
        this.state = State.SYN_SENT;
        this.sendSYN();
        return 0;
    }

    public void close() {
        if (this.state == State.LISTEN) {
            while (!this.pendingConnections.isEmpty()) {
                TCPSock conn = this.pendingConnections.removeFirst();
                this.tcpMan.release(conn);
            }
            this.state = State.CLOSED;
        } else if (this.state == State.ESTABLISHED) {
            if (this.snd_base == this.snd_next) {
                this.tcpMan.send(this.id, 2, 0, this.snd_base, this.snd_buf, 0);
                this.state = State.CLOSED;
            } else {
                this.state = State.SHUTDOWN;
            }
        } else if (this.state != State.SHUTDOWN) {
            this.state = State.CLOSED;
        }
    }

    public void release() {
        this.close();
        if (this.state == State.SHUTDOWN) {
            this.tcpMan.send(this.id, 2, 0, this.snd_base, this.snd_buf, 0);
        }
        this.tcpMan.release(this);
    }

    public int write(byte[] buf, int pos, int len) {
        if (this.state == State.CLOSED) {
            return -1;
        }
        if (this.state != State.ESTABLISHED) {
            return -1;
        }
        if (this.snd_buf == null) {
            return -1;
        }
        int avail = this.snd_buf.length - 1 - (this.snd_next - this.snd_base);
        int cnt = Math.min(avail, len);
        if (cnt == 0) {
            return 0;
        }
        TCPSock.qwrite(this.snd_buf, this.snd_next, buf, pos, cnt);
        this.snd_next += cnt;
        for (int seq = this.snd_next; seq < this.snd_base + this.snd_wnd && seq < this.snd_next; seq += this.sendDATA((int)seq)) {
        }
        return cnt;
    }

    public int read(byte[] buf, int pos, int len) {
        if (this.state == State.CLOSED) {
            return -1;
        }
        if (this.state != State.ESTABLISHED && this.state != State.SHUTDOWN) {
            return -1;
        }
        if (this.rcv_buf == null) {
            return -1;
        }
        int avail = this.rcv_base - this.rcv_next;
        int cnt = Math.min(avail, len);
        if (cnt == 0) {
            return 0;
        }
        TCPSock.qread(this.rcv_buf, this.rcv_next, buf, pos, cnt);
        this.rcv_next += cnt;
        this.rcv_top += cnt;
        if (this.state == State.SHUTDOWN && this.rcv_next == this.rcv_base) {
            this.state = State.CLOSED;
        }
        return cnt;
    }

    public void OnReceive(int srcAddr, int destAddr, Transport segment) {
        switch (segment.getType()) {
            case 0: {
                this.receiveSYN(srcAddr, destAddr, segment);
                break;
            }
            case 2: {
                this.receiveFIN(srcAddr, destAddr, segment);
                break;
            }
            case 1: {
                this.receiveACK(srcAddr, destAddr, segment);
                break;
            }
            case 3: {
                this.receiveDATA(srcAddr, destAddr, segment);
            }
        }
    }

    private void receiveSYN(int srcAddr, int destAddr, Transport syn) {
        System.out.print("S");
        if (this.state == State.LISTEN) {
            TCPSock conn = null;
            TCPSockID sid = new TCPSockID();
            sid.setLocalAddr(this.id.getLocalAddr());
            sid.setLocalPort(this.id.getLocalPort());
            sid.setRemoteAddr(srcAddr);
            sid.setRemotePort(syn.getSrcPort());
            int ackNum = syn.getSeqNum() + 1;
            if (this.pendingConnections.size() >= this.backlog || (conn = this.tcpMan.socket()) == null) {
                this.tcpMan.send(sid, 2, 0, ackNum, null, 0);
                return;
            }
            conn.setID(sid);
            conn.rcv_buf = new byte[16385];
            conn.rcv_base = conn.rcv_next = ackNum;
            conn.rcv_top = conn.rcv_base + 16384;
            conn.rcv_initseq = ackNum - 1;
            int rcv_wnd = conn.rcv_top - conn.rcv_base;
            this.tcpMan.send(sid, 1, rcv_wnd, conn.rcv_base, null, 0);
            conn.setState(State.ESTABLISHED);
            this.pendingConnections.addLast(conn);
        } else if (this.state == State.ESTABLISHED && syn.getSeqNum() == this.rcv_initseq) {
            int rcv_wnd = this.rcv_top - this.rcv_base;
            this.tcpMan.send(this.id, 1, rcv_wnd, this.rcv_base, null, 0);
        }
    }

    private void receiveFIN(int srcAddr, int destAddr, Transport fin) {
        System.out.print("F");
        if (this.state == State.ESTABLISHED) {
            this.state = this.rcv_next == this.rcv_base ? State.CLOSED : State.SHUTDOWN;
        }
    }

    private void receiveACK(int srcAddr, int destAddr, Transport ack) {
        int ackNum = ack.getSeqNum();
        if (this.state == State.SYN_SENT && ackNum == this.snd_base + 1) {
            System.out.print(":");
            this.state = State.ESTABLISHED;
            this.snd_base = ackNum;
            this.snd_next = ackNum;
            this.snd_rcvWnd = ack.getWindow();
            this.update_snd_wnd();
            return;
        }
        if (this.state != State.ESTABLISHED && this.state != State.SHUTDOWN) {
            System.out.print("?");
            return;
        }
        if (ackNum > this.snd_top) {
            System.out.print("?");
            return;
        }
        this.snd_rcvWnd = ack.getWindow();
        this.update_snd_wnd();
        if (ackNum > this.snd_base) {
            System.out.print(":");
            this.rt_pending = 0;
            this.rt_expired = 0;
            this.dup_ack = 0;
            this.fast_ret = 0;
            if (this.RTT_sample_seq >= this.snd_base && ackNum > this.RTT_sample_seq) {
                long RTT_sample = this.tcpMan.now() - this.RTT_sample_send_time;
                this.RTT_estimate = (long)((double)this.RTT_estimate * 0.875);
                this.RTT_estimate = (long)((double)this.RTT_estimate + (double)RTT_sample * 0.125);
                this.RTTDev_estimate = (long)((double)this.RTTDev_estimate * 0.75);
                this.RTTDev_estimate = (long)((double)this.RTTDev_estimate + (double)Math.abs(this.RTT_estimate - RTT_sample) * 0.25);
                this.RTO = this.RTT_estimate + 4 * this.RTTDev_estimate;
                this.RTO = Math.max(this.RTO, 50);
                this.RTO = Math.min(this.RTO, 30000);
            }
            int next_to_send = Math.min(this.snd_base + this.snd_wnd, this.snd_top);
            if (this.snd_cwnd < this.snd_ssthresh) {
                int acked = (ackNum - this.snd_base + this.snd_MSS - 1) / this.snd_MSS;
                this.snd_cwnd += (float)acked;
            } else {
                this.snd_cwnd += 1.0f / this.snd_cwnd;
            }
            this.update_snd_wnd();
            this.snd_base = ackNum;
            if (this.snd_base < this.snd_next) {
                if (this.snd_base < this.snd_top) {
                    this.start_rt_timer(this.snd_base);
                }
                for (int seq = Math.max((int)next_to_send, (int)this.snd_base); seq < this.snd_base + this.snd_wnd && seq < this.snd_next; seq += this.sendDATA((int)seq)) {
                }
            } else if (this.state == State.SHUTDOWN) {
                this.tcpMan.send(this.id, 2, 0, this.snd_base, this.snd_buf, 0);
                this.state = State.CLOSED;
            }
            return;
        }
        System.out.print("?");
        ++this.dup_ack;
        if (this.dup_ack == 3) {
            this.RTT_sample_seq = this.snd_base - 1;
            this.snd_cwnd = this.snd_ssthresh = Math.max(this.snd_cwnd / 2.0f, 1.0f);
            this.update_snd_wnd();
            if (this.fast_ret < 1) {
                for (int seq = this.snd_base; seq < this.snd_base + this.snd_wnd && seq < this.snd_next; seq += this.sendDATA((int)seq)) {
                }
                ++this.fast_ret;
            }
        }
    }

    private void receiveDATA(int srcAddr, int destAddr, Transport data) {
        int seqNum = data.getSeqNum();
        if (seqNum == this.rcv_base) {
            System.out.print(".");
            byte[] payload = data.getPayload();
            int len = payload.length;
            int avail = this.rcv_top - this.rcv_base;
            int cnt = Math.min(avail, len);
            TCPSock.qwrite(this.rcv_buf, this.rcv_base, payload, 0, cnt);
            this.rcv_base += cnt;
        } else {
            System.out.print("&");
        }
        int rcv_wnd = this.rcv_top - this.rcv_base;
        this.tcpMan.send(this.id, 1, rcv_wnd, this.rcv_base, null, 0);
    }

    public static void qread(byte[] src, int srcPos, byte[] dest, int destPos, int len) {
        if ((srcPos %= src.length) + len > src.length) {
            int cnt = src.length - srcPos;
            System.arraycopy(src, srcPos, dest, destPos, cnt);
            System.arraycopy(src, 0, dest, destPos += cnt, len - cnt);
        } else {
            System.arraycopy(src, srcPos, dest, destPos, len);
        }
    }

    public static void qwrite(byte[] dest, int destPos, byte[] src, int srcPos, int len) {
        if ((destPos %= dest.length) + len > dest.length) {
            int cnt = dest.length - destPos;
            System.arraycopy(src, srcPos, dest, destPos, cnt);
            System.arraycopy(src, srcPos += cnt, dest, 0, len - cnt);
        } else {
            System.arraycopy(src, srcPos, dest, destPos, len);
        }
    }

    public void sendSYN() {
        if (this.state != State.SYN_SENT) {
            return;
        }
        this.tcpMan.send(this.id, 0, 0, this.snd_base, this.snd_buf, 0);
        try {
            Method method = Callback.getMethod((String)"sendSYN", (Object)this, (String[])null);
            Callback cb = new Callback(method, (Object)this, null);
            this.tcpMan.addTimer(1000, cb);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void update_snd_wnd() {
        int rwnd = Math.max(this.snd_rcvWnd, 1);
        int cwnd = Math.round(this.snd_cwnd) * this.snd_MSS;
        this.snd_wnd = Math.min(rwnd, cwnd);
    }

    private int sendDATA(int seq) {
        int unsent = this.snd_next - seq;
        int inWnd = this.snd_base + this.snd_wnd - seq;
        int cnt = Math.min(unsent, inWnd);
        cnt = Math.min(cnt, this.snd_MSS);
        this.tcpMan.send(this.id, 3, 0, seq, this.snd_buf, cnt);
        int top = seq + cnt;
        if (this.snd_top < top) {
            this.snd_top = top;
        }
        if (seq == this.snd_base) {
            this.start_rt_timer(seq);
        }
        if (this.RTT_sample_seq < this.snd_base) {
            this.RTT_sample_seq = seq;
            this.RTT_sample_send_time = this.tcpMan.now();
        }
        return cnt;
    }

    private void start_rt_timer(int seq) {
        try {
            String[] paramTypes = new String[]{"java.lang.Integer"};
            Object[] params = new Object[]{seq};
            Method method = Callback.getMethod((String)"retransmit", (Object)this, (String[])paramTypes);
            Callback cb = new Callback(method, (Object)this, params);
            this.tcpMan.addTimer(this.RTO, cb);
            ++this.rt_pending;
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void retransmit(Integer seqNum) {
        int seq = seqNum;
        if (this.snd_base > seq) {
            return;
        }
        if (this.rt_pending-- > 1) {
            return;
        }
        ++this.rt_expired;
        this.dup_ack = 0;
        this.fast_ret = 0;
        this.RTT_sample_seq = this.snd_base - 1;
        this.snd_ssthresh = Math.max(this.snd_cwnd / 2.0f, 1.0f);
        this.snd_cwnd = 1.0f;
        this.update_snd_wnd();
        if (this.rt_expired == 1) {
            this.RTO *= 2;
            this.RTO = Math.min(this.RTO, 30000);
            this.rt_expired = 0;
        }
        System.out.print("!");
        while (seq < this.snd_base + this.snd_wnd && seq < this.snd_next) {
            seq += this.sendDATA(seq);
        }
    }
}