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

public class TCPSock {
    // TCP socket states
    enum State {
        // protocol states
        CLOSED, // closed, not used, ready to be reused
        INIT, // newly created
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
    
    private int send_seq_num, send_wnd_size, send_wnd_base, send_wnd_limit, send_wnd_next;
    
    
    private int backlog = 0;
    private ByteBuffer recv_buf, send_buf;
    
    private List<TCPSock> pendingConnections = null;
    public TCPSock(TCPManager manager) {
    	this.tcpMan = manager;
    	
    }

    public void init(){
    	localPort = remoteAddr = remotePort = -1;
    	this.localAddr = this.tcpMan.getAddr();
    	this.state = State.INIT;
    	recv_buf = ByteBuffer.allocate(4096); // one page
    	send_buf = ByteBuffer.allocate(4096); // one page
    }
    
    public void clean(){
    	localAddr = localPort = remoteAddr = remotePort = -1;
    	state = State.CLOSED;
    	recv_buf = null;
    	send_buf = null;
    	for(TCPSock sock : pendingConnections){
    		sock.clean();
    		
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
        	Debug.println(String.format("%s: Binded to port %d", this.toString(), this.localPort));
    		this.localPort = localPort;
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
    	if(this.localPort != -1 && this.state == State.INIT){
        	Debug.println(String.format("%s: Listening at port %d", this.toString(), this.localPort));
    		this.pendingConnections = new LinkedList<TCPSock>();
    		this.backlog = backlog;
    		this.state = State.LISTEN;
    		return 0;
    	}
    	return -1;
 
    }

    public State getState() {
		return state;
	}

	public void setState(State state) {
		this.state = state;
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
     * Initiate connection to a remote socket
     * @param destAddr int Destination node address
     * @param destPort int Destination port
     * @return int 0 on success, -1 otherwise
     */
    public int connect(int destAddr, int destPort) {
    	Debug.println(String.format("Socket (%d:%d) connecting to (%d:%d)", this.localAddr, this.localPort, this.remoteAddr, this.remotePort));
    	if(this.state == State.INIT && this.localPort != -1){
    		this.remoteAddr = destAddr;
    		this.remotePort = destPort;
    		this.state = State.SYN_SENT;
    		Random rand = new Random();
    		this.send_seq_num = rand.nextInt(1024);
    		sendSYN();
    		return 0;
    	}
        return -1;
    }

    private void sendSYN() {
    	Debug.println("In function sendSYN");
    	if(this.state == State.SYN_SENT){
    		Transport segment = new Transport(this.localPort, this.remotePort, Transport.SYN, 1, this.send_seq_num, null);
    		this.tcpMan.send(this, segment);
    		try{
    			Method method = Callback.getMethod("sendSYN", this, null);
                Callback cb = new Callback(method, (Object)this, null);
                this.tcpMan.addTimer(1000, cb);
    		}catch(Exception e){
    			Debug.println("Failed to add timer. sendSYN");
    		}
    	}else{
    		Debug.print("trying to send SYN in a wrong socket state\n");
    	}
    	
	}

	/**
     * Initiate closure of a connection (graceful shutdown)
     */
    public void close() {
    	
    }

    /**
     * Release a connection immediately (abortive shutdown)
     */
    public void release() {
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
        return -1;
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

	/**
	 * Called by TCPmanager if a packet arrives at node and match this sock
	 **/
	public void onReceive(int srcAddr, int srcPort, Transport segment) {
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

	private void receiveFIN(int srcAddr, int srcPort, Transport segment) {
		// TODO Auto-generated method stub
		
	}

	private void receiveACK(int srcAddr, int srcPort, Transport segment) {
		// TODO Auto-generated method stub
		
	}

	private void receiveData(int srcAddr, int srcPort, Transport segment) {
		// TODO Auto-generated method stub
		
	}

	private void receiveSYN(int srcAddr, int srcPort, Transport segment) {
		System.out.print("S");
		if(this.getState() == State.LISTEN){
			// create a new connection socket
			
		}//else if(this.getState() == State.ESTABLISHED && ){
			
		//}
	}

	@Override
	public String toString(){
		return String.format("Socket (%d:%d , %d:%d)", this.localAddr, this.localPort, this.remoteAddr, this.remotePort);
	}
}
