package proj;

import lib.Callback;
import lib.Manager;
import lib.Protocol;
import lib.Transport;
import proj.TCPSock.State;

/**
 * <p>Title: CPSC 433/533 Programming Assignment</p>
 *
 * <p>Description: Fishnet TCP manager</p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: Yale University</p>
 *
 * @author Hao Wang
 * @version 1.0
 */
public class TCPManager {
    private Node node;
    private int addr;
    private Manager manager;
    private TCPSock[] TCPSocks = new TCPSock[256];
    private static final byte dummy[] = new byte[0];
    
    public TCPManager(Node node, int addr, Manager manager) {
        this.node = node;
        this.addr = addr;
        this.manager = manager;
        // init tcpsock array
        for(int i = 0; i < TCPSocks.length; i++){
        	TCPSocks[i] = new TCPSock(this);
        }
        
    }

    public int getAddr(){
    	return this.addr;
    }
    /**
     * Start this TCP manager
     */
    public void start() {

    }

    /*
     * Begin socket API
     */

    /**
     * Create a socket
     *
     * @return TCPSock the newly created socket, which is not yet bound to
     *                 a local port
     */
    public TCPSock socket() {
    	for(TCPSock sock : TCPSocks){
    		if(sock.getState() == State.CLOSED){
    			sock.setState(State.INIT);
    			sock.init();
    			return sock;
    		}
    	}
        return null;
    }



    /*
     * End Socket API
     */

    /*
     * start internal project API
     */

    
    public void addTimer(int deltaT, Callback cb){
    	this.manager.addTimer(this.addr, deltaT, cb);
    }
    /*
     * send segment using second layer interface 
     **/
    public void send(TCPSock sock, Transport segment){
    	this.send(sock.getLocalAddr(), sock.getRemoteAddr(), segment);
    }
    public void send(int localAddr, int remoteAddr, Transport segment){
    	this.node.sendSegment(localAddr, remoteAddr, Protocol.TRANSPORT_PKT, segment.pack());  	
    }
    
    /**
     * Called by node when a TCP packet arrives
     * demultiplex: decide which sock should handle this packet based on its 4 tuples
     *  
     **/
	public void onReceive(int srcAddr, int destAddr, Transport segment) {
		int destPort = segment.getDestPort();
		int srcPort = segment.getSrcPort();
		// matnnection sock
		TCPSock sock = getSock(srcAddr, srcPort, destAddr, destPort);
		// connection sock not found or connection sock is closed
		if(sock == null || sock.isClosed()){
			// match welcome sock
			sock = getSock(destAddr, destPort, TCPSock.State.LISTEN);
		}
		if(sock != null){
			sock.onReceive(srcAddr, srcPort, segment);
		}else{
			String msg = String.format("No matching sock on server: target sock is (%d, %d, %d, %d)", srcAddr, srcPort, destAddr, destPort);
			Debug.print(msg);
		}
	}
	public TCPSock getSock(int destAddr, int destPort, State state) {
		for(TCPSock sock: TCPSocks){
			if(sock.getLocalAddr() == destAddr && sock.getLocalPort() == destPort && sock.getState() == state){
				return sock;
			}
		}
		return null;
	}

	/**
	 * get sock from its 4 tuples
	 **/
	public TCPSock getSock(int srcAddr, int srcPort, int destAddr, int destPort){
		for(TCPSock sock: TCPSocks){
			if(sock.getRemoteAddr() == srcAddr && sock.getRemotePort() == srcPort && sock.getLocalAddr() == destAddr && sock.getLocalPort() == destPort){
				return sock;
			}
		}
		return null;
	}
	
	public TCPSock getSock(int destAddr, int destPort){
		for(TCPSock sock: TCPSocks){
			if(sock.getLocalAddr() == destAddr && sock.getLocalPort() == destPort){
				return sock;
			}
		}
		return null;
	}
	
	public long now(){
		return this.manager.now();
	}
	
	@Override
	public String toString(){
		return "TCPManager";
	}
}
