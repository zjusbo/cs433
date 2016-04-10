package proj;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import lib.Callback;
import lib.Transport;

/**
 * <p>Title: CPSC 433/533 Programming Assignment</p>
 *
 * <p>Description: Fishnet socket implementation</p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: Yale University</p>
 *
 * @author Hao Wang
 * @version 1.0
 */



/**
 * FSM:      CLOSED  ->  INIT  -> BIND ->  LISTEN
 *                                    \
 *                                     -> SYN_SENT -> ESTABLISHED
 *                                     
 *            Every state can go to SHUTDOWN state then go to CLOSED state or can go to CLOSED state immediately.
 **/
public class TCPSock {
	// TCP socket states
	enum State {
		// protocol states
		CLOSED, // closed, not used, ready to be reused
		INIT, // newly created
		BIND, // bound to a port
		LISTEN,
		SYN_SENT,
		ESTABLISHED,
		SHUTDOWN // close requested, FIN not sent (due to unsent data in queue)
	}
	private State state = State.CLOSED; // init sock state as closed
	private TCPManager tcpMan;
	private int localAddr = -1;
	private int localPort = -1;
	private int remoteAddr = -1;
	private int remotePort = -1;
	private int DELTA_T = 1000; // timeout for retransmit, milliseconds
	private int send_nextseqnum; // first unsent seq number
	private int send_base; // start seq num of window
	private int send_wnd_size = 1024; // 1024 bytes
	private int recv_base; // expected data seq num
	private int remote_wnd_size = 0;

	private int backlog = 0;
	private RingBuffer recv_buf, send_buf;

	private Random rand = new Random();
	private List<TCPSock> pendingConnections = null;
	public TCPSock(TCPManager manager) {
		this.tcpMan = manager;

	}

	public void init(){
		localPort = remoteAddr = remotePort = -1;
		this.localAddr = this.tcpMan.getAddr();
		this.state = State.INIT;
		recv_buf = new RingBuffer(4096); // one page
		send_buf = new RingBuffer(4096); // one page
	}

	// recycle resources immediately
	public void clean(){
		localAddr = localPort = remoteAddr = remotePort = -1;
		state = State.CLOSED;
		recv_buf = null;
		send_buf = null;
		if(pendingConnections != null){
			for(TCPSock sock : pendingConnections){
				sock.clean();	
			}
		}

		pendingConnections = null;
	}

	/*
	 * The following are the socket APIs of TCP transport service.
	 * All APIs are NON-BLOCKING.
	 */

	/**
	 * Bind a socket to a local port
	 *
	 * @param localPort int local port number to bind the socket to
	 * @return int 0 on success, -1 otherwise
	 */

	public int bind(int localPort) {
		// sock is initialized and localPort is not occupied.
		if(this.state == State.INIT && this.tcpMan.getSock(this.localAddr, localPort) == null){
			this.localPort = localPort;
			this.state = State.BIND;
			Debug.println(String.format("%s: Binded to port %d", this.toString(), this.localPort));
			return 0;
		}
		return -1;
	}

	/**
	 * Listen for connections on a socket
	 * @param backlog int Maximum number of pending connections
	 * @return int 0 on success, -1 otherwise
	 */
	public int listen(int backlog) {
		// sock is binded to a port
		if(this.state == State.BIND){
			Debug.println(String.format("%s: Listening at port %d", this.toString(), this.localPort));
			this.pendingConnections = new LinkedList<TCPSock>();
			this.backlog = backlog;
			this.state = State.LISTEN;
			return 0;
		}
		return -1;

	}

	/**
	 * Initiate connection to a remote socket
	 * @param destAddr int Destination node address
	 * @param destPort int Destination port
	 * @return int 0 on success, -1 otherwise
	 * 
	 * FSM:
	 * 		BIND -> SYN_SENT
	 */
	public int connect(int destAddr, int destPort) {

		if(this.state == State.BIND){
			this.remoteAddr = destAddr;
			this.remotePort = destPort;

			this.state = State.SYN_SENT;
			this.send_base = rand.nextInt(1024);

			Debug.println(String.format("Socket (%d:%d) connecting to (%d:%d)", this.localAddr, this.localPort, this.remoteAddr, this.remotePort));
			sendSYN();
			return 0;
		}
		return -1;
	}

	/**
	 * Initiate closure of a connection (graceful shutdown)
	 * 
	 * FSM:
	 *     LISTEN, INIT, BIND -> CLOSED
	 *     SYN_SENT -> CLOSED
	 *     ESTABLISHED -> SHUTDOWN
	 *
	 */
	public void close() {
		if(this.state == State.LISTEN || 
				this.state == State.INIT ||
				this.state == State.BIND){
			this.state = State.CLOSED;
			this.clean();
		}else if(this.state == State.SYN_SENT){
			this.state = State.CLOSED;
			this.sendFIN(this.send_base);
			this.clean();
		}
		else if(this.state == State.ESTABLISHED){
			this.state = State.SHUTDOWN;
			sendData(); // sendData() also send FIN
		}else{
			// CLOSED, SHUTDOWN
			// IGNORE
		}
	}

	/**
	 * Release a connection immediately (abortive shutdown)
	 */
	public void release() {
		if(this.state == State.ESTABLISHED || this.state == State.SHUTDOWN){
			this.sendFIN(this.send_base);
		}
		this.state = State.CLOSED;
		this.clean();
	}

	public State getState() {
		return state;
	}

	public void setState(State state) {
		this.state = state;
	}

	public void setRecv_base(int recv_base) {
		this.recv_base = recv_base;
	}

	/**
	 * Accept a connection on a socket
	 *
	 * @return TCPSock The first established connection on the request queue
	 */
	public TCPSock accept() {
		if(this.state == State.LISTEN && !pendingConnections.isEmpty()){
			Debug.println(String.format("%s: Accepting", this.toString()));
			TCPSock sock = this.pendingConnections.remove(0);
			return sock;
		}
		return null;
	}

	public boolean isConnectionPending() {
		return (state == State.SYN_SENT);
	}

	public boolean isClosed() {
		return (state == State.CLOSED);
	}

	public boolean isConnected() {
		return (state == State.ESTABLISHED);
	}

	public boolean isClosurePending() {
		return (state == State.SHUTDOWN);
	}

	/**
	 * Write to the socket up to len bytes from the buffer buf starting at
	 * position pos.
	 *
	 * @param buf byte[] the buffer to write from
	 * @param pos int starting position in buffer
	 * @param len int number of bytes to write
	 * @return int on success, the number of bytes written, which may be smaller
	 *             than len; on failure, -1
	 */
	public int write(byte[] buf, int pos, int len) {
		if(this.state == State.ESTABLISHED){
			len = Math.min(this.send_buf.remaining(), len);
			byte[] data = new byte[len];
			System.arraycopy(buf, pos, data, 0, len);
			int length_written = this.send_buf.put(data);
			this.sendData(); // fire data
			return length_written;
		}else{
			return -1;
		}

	}

	/**
	 * Read from the socket up to len bytes into the buffer buf starting at
	 * position pos.
	 *
	 * @param buf byte[] the buffer
	 * @param pos int starting position in buffer
	 * @param len int number of bytes to read
	 * @return int on success, the number of bytes read, which may be smaller
	 *             than len; on failure, -1
	 */
	public int read(byte[] buf, int pos, int len) {
		if(this.state == State.ESTABLISHED || this.state == State.SHUTDOWN){
			len = Math.min(this.recv_buf.size(), len);
			byte[] data = this.recv_buf.get(0, len); // read data
			len = data.length;
			System.arraycopy(data, 0, buf, pos, len);
			this.recv_buf.advance(len); // pop data
			return len;
		}
		return -1;
	}

	/*
	 * End of socket API
	 */

	public int getLocalAddr() {
		return localAddr;
	}

	public void setLocalAddr(int localAddr) {
		this.localAddr = localAddr;
	}

	public int getLocalPort() {
		return localPort;
	}

	public void setLocalPort(int localPort) {
		this.localPort = localPort;
	}

	public int getRemoteAddr() {
		return remoteAddr;
	}

	public void setRemoteAddr(int remoteAddr) {
		this.remoteAddr = remoteAddr;
	}

	public int getRemotePort() {
		return remotePort;
	}

	public void setRemotePort(int remotePort) {
		this.remotePort = remotePort;
	}



	public void sendSYN() {

		if(this.state == State.SYN_SENT){
			System.out.print("S"); // SYN packet
			Transport segment = new Transport(this.localPort, this.remotePort, Transport.SYN, this.send_base, this.send_base, new byte[0]);
			this.tcpMan.send(this, segment);
			try{
				Method method = Callback.getMethod("sendSYN", this, null);
				Callback cb = new Callback(method, (Object)this, null);
				// re-send SYN if timeout
				this.tcpMan.addTimer(1000, cb);
			}catch(Exception e){
				Debug.println("Failed to add timer. sendSYN");
				e.printStackTrace();
			}
		}else{
			// ignore it
			// may have established a connection
			// Debug.print("trying to send SYN in a wrong socket state\n");
		}
	}

	/*
	 * Send ACK
	 **/
	public void sendACK(){		
		Transport segment = new Transport(this.localPort, this.remotePort, Transport.ACK, this.recv_buf.remaining(), this.recv_base, new byte[0]);
		this.tcpMan.send(this, segment);
	}
	private int sendData(){
		int send_len_total = 0;
		// check if there is unsent data in window
		if(this.state == State.ESTABLISHED || this.state == State.SHUTDOWN){
			while(this.send_nextseqnum < this.send_base + this.send_buf.size() &&
					this.send_nextseqnum < this.send_base + this.send_wnd_size && this.remote_wnd_size > 0){

				System.out.print("."); // regular data packet
				int payload_len = Math.min(this.send_buf.size() - (this.send_nextseqnum - this.send_base), 
						this.send_wnd_size - (this.send_nextseqnum - this.send_base));
				payload_len = Math.min(payload_len, Transport.MAX_PAYLOAD_SIZE);
				payload_len = Math.min(payload_len, this.remote_wnd_size);

				byte[] payload;

				payload = this.send_buf.get(this.send_nextseqnum - this.send_base, payload_len);
				Transport segment = new Transport(this.localPort, this.remotePort, Transport.DATA, this.recv_buf.remaining(), this.send_nextseqnum, payload);
				System.out.println(this.send_nextseqnum + ": " + payload_len + " bytes sent.");
				this.tcpMan.send(this, segment);
				// first packet in window
				// add timer
				if(this.send_nextseqnum == this.send_base){
					this.addResendDataTimer(this.send_base);
				}
				this.send_nextseqnum += payload_len;
				send_len_total += payload_len;
			}
		}
		// sending buffer is empty, data all ACKed, sock is shutting down
		if(this.state == State.SHUTDOWN && this.send_nextseqnum == this.send_base){
			sendFIN(this.send_base);
			this.state = State.CLOSED;
			this.clean();
		}
		return send_len_total;
	}
	private void addResendDataTimer(int seq_num){
		try{
			String[] paramTypes = new String[]{"java.lang.Integer"};
			Object[] params = new Object[]{seq_num};
			Method method = Callback.getMethod("resendData", this, paramTypes);
			Callback cb = new Callback(method, this, params);
			// re-send SYN if timeout
			this.tcpMan.addTimer(this.DELTA_T, cb);
		}catch(Exception e){
			Debug.println("Failed to add timer. sendData");
			e.printStackTrace();
		}
	}
	// retransmit if first packet in window is timeout
	public void resendData(Integer seq_num){
		// resend all available packets in window
		// window didn't move, resend all available data in window
		int prev_send_base = seq_num;
		if(prev_send_base == this.send_base){
			System.out.print("!");
			this.send_nextseqnum = this.send_base;
			this.sendData();
		}
	}

	public void sendFIN(int ack_num){
		sendFIN(this.remoteAddr, this.remotePort, ack_num);
	}
	public void sendFIN(int remoteAddr, int remotePort, int ack_num){
		System.out.print("F"); 
		Transport segment = new Transport(this.localPort, remotePort, Transport.FIN, this.recv_buf.remaining(), ack_num, new byte[0]);
		this.tcpMan.send(this.localAddr, remoteAddr, segment);
	}

	/**
	 * Called by TCPmanager if a packet arrives at node and match this sock
	 **/
	public void onReceive(int srcAddr, int srcPort, Transport segment) {
		//Debug.println("Sock onReceive");
		switch(segment.getType()){
		case Transport.SYN:
			receiveSYN(srcAddr, srcPort, segment);
			break;
		case Transport.DATA:
			receiveData(srcAddr, srcPort, segment);
			break;
		case Transport.ACK:
			receiveACK(srcAddr, srcPort, segment);
			break;
		case Transport.FIN:
			receiveFIN(srcAddr, srcPort, segment);
			break;
		default:
			Debug.print(String.format("Unknown TCP package type %s", segment.toString()));
			break;
		}
	}

	private void receiveSYN(int srcAddr, int srcPort, Transport segment) {
		System.out.print("S");
		int seq_num = segment.getSeqNum();
		int ack_num = seq_num + 1;
		if(this.getState() == State.LISTEN){
			// create a new connection socket
			if(this.pendingConnections.size() < this.backlog){
				TCPSock sock = this.tcpMan.socket();
				if(sock == null){
					this.sendFIN(ack_num);
				}
				sock.setLocalAddr(this.localAddr);
				sock.setLocalPort(this.localPort);
				sock.setRemoteAddr(srcAddr);
				sock.setRemotePort(srcPort);
				sock.setState(State.ESTABLISHED);
				sock.setRecv_base(ack_num);
				this.pendingConnections.add(sock);
				sock.sendACK();
			}else{
				this.sendFIN(srcAddr, srcPort, ack_num);
			}

		}else if(this.getState() == State.ESTABLISHED && seq_num == this.recv_base - 1){
			// retransmitted SYN packet
			this.sendACK();
		}
	}

	/**
	 * 
	 * 
	 * FSM:
	 *      SYN_SENT -> ESTABLISHED
	 *      SHUTDOWN -> CLOSED
	 * 
	 **/
	private void receiveACK(int srcAddr, int srcPort, Transport segment) {
		int ack_num = segment.getSeqNum();
		int remote_wnd_size = segment.getWindow();
		// 
		if(this.state == State.SYN_SENT){
			if(ack_num == this.send_base +1){
				System.out.print(":");
				this.state = State.ESTABLISHED;
				this.send_base = ack_num;
				this.send_nextseqnum = this.send_base;
				this.remote_wnd_size = remote_wnd_size;
			}else{
				//drop it
				System.out.print("?"); // an acknowledgement packet that does not advance the field
			}
		}
		// connection is establised or connection is shutting down. sending remaining data in buffer
		else if(this.state == State.ESTABLISHED || this.state == State.SHUTDOWN){
			this.remote_wnd_size = remote_wnd_size;
			if(ack_num > this.send_base && ack_num <= this.send_nextseqnum ){
				// packet ACKed, move send_base and fire more packets, if any.
				int len = ack_num - this.send_base; // length of previous packet
				this.send_base = ack_num;
				// TODO be debugged.
				this.send_buf.advance(len);
				// try to send unsent data in window
				this.sendData();
				this.addResendDataTimer(ack_num);
			}else{
				System.out.print("?"); // an acknowledgement packet that does not advance the field
			}
		}
	}

	private void receiveData(int srcAddr, int srcPort, Transport segment) {
		int seq_num = segment.getSeqNum();
		byte[] payload = segment.getPayload();
		int len = payload.length;
		System.out.print("."); // receive a data packet
		
		if(this.state == State.ESTABLISHED){
			// correct seq_num
			if(seq_num == this.recv_base){
				// recv_buf has enough space
				System.out.println(seq_num + ": " + len + " bytes received.");
				if(this.recv_buf.remaining() >= len){
					this.recv_buf.put(payload);
					this.recv_base += len;
					System.out.print(":"); // send a ACK, advance ACK field
				}
				sendACK(); 
			}else{
				sendACK(); // send ACK for the seq num we want
				System.out.println(seq_num + ": Wrong seq num. Missing " + this.recv_base);
			}
		}else{
			System.out.println(seq_num + ": Connection is not established yet.");
		}
	}

	private void receiveFIN(int srcAddr, int srcPort, Transport segment) {
		// TODO Auto-generated method stub
		System.out.print("F");
		// FIN can not close a LISTENING socket
		if(this.state == State.ESTABLISHED){
			this.close();
		}

	}

	@Override
	public String toString(){
		return String.format("Socket (%d:%d , %d:%d)", this.localAddr, this.localPort, this.remoteAddr, this.remotePort);
	}
}
