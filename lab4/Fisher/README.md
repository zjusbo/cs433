## TCP Protocol Design Document

### Description

This project implements a simple version of TCP protocol. It uses linked-layer API and network topo simulation provided by Fishnet. 

The core code implementing TCP protocol is in **/proj/TCPSock.java** and **/proj/TCPManager.java**

#### API used from linked-layer

	/**
	 * send a packet using linked-layer (unreliable)
	 **/
	sendPkt(int from, int to, byte[] pkt)
	
	
	/**
	 * this callback function is called when a packet is received by linked-layer.
	 **/
	onReceive(Integer from, byte[] msg)

#### API provided to application layer

**All API listed below are non-blocking**

	
	/**
	 * create a new socket
	 **/
	Socket sock()

	/**
	 * close a socket (gracefully)
	 **/
	void close()

	/**
	 * release a socket immediately (abortive shutdown)
	 **/
	void release()
	
	/**
	 * Bind a socket to a local port
	 *
	 * @param localPort int local port number to bind the socket to
	 * @return int 0 on success, -1 otherwise
	 */
	bind(int port)
	
	/**
	 * Initiate connection to a remote socket
	 * @param destAddr int Destination node address
	 * @param destPort int Destination port
	 * @return int 0 on success, -1 otherwise
	 * 
	 * FSM:
	 * 		BIND -> SYN_SENT
	 */
	int connect(int destAddr, int destPort)

	/**
	 * Listen for connections on a socket
	 * @param backlog int Maximum number of pending connections
	 * @return int 0 on success, -1 otherwise
	int listen(int backlog)
	
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
	int write(byte[] buf, int pos, int len) {
	
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
	int read(byte[] buf, int pos, int len)


### Work Flow

#### When a packet arrives at a node

Firstly, tcpman.onReceive() dispatches the packet to corresponding socket. Then socket will unpack the packet and dispatch it to corresponding handler receiveSYN(), receiveACK, receiveData and receiveFIN according to the type of packet.

### Features

 - Reliable Transport
  
    - Use Go-back-n algorithm. (details are in function receiveACK() and sendData())

 - Flow Control and Congestion Control

    - Use TCP/Reno congestion control.  (details are in function receiveACK(), sendData(), resendData() and updateSend_wnd())

### How to run it

 - locate terminal to **src** folder
 - make all
 - open three terminals
 - run **0.sh** (trawler), **1.sh** (server), **2.sh** (client) in each terminal separately 

### Result

Following is the result of a sample run. The package lost rate is 0.2 (very high) and the delay is 5 milliseconds.

#### trawler

	$ ./0.sh
	Trawler awaiting fish...
	Got port 6789: assigning addr: 0
	Got port 6790: assigning addr: 1

#### server

	$ ./1.sh
	Node 0: started
	Node 0: server started, port = 1
	SNode 0: time = 1460419513484 msec
	Node 0: connection accepted
	SS.:.:!!!.:.:.:!!!!!???.:.:!!!.:.:.:!!!!!???.:.:!!!!.:?!.:!!.:.:.:.:.:????.:?.:.
	:.:.:.:.:.:.:????.:.:.:???.:.:.:.:??.:.:.:.:.:.:.:.:.:.:.:.:.:.:.:.:.:.:.:.:.:.:
	.:.:.:?????????.:.:?!!!!????.:.:!??.:.:?.:?.:.:.:.:.:????.:.:.:???.:.:??.:.:.:.:
	?????.:??.:.:.:.:???.:.:.:.:.:.:.:???.:.:.:??.:.:.:.:.:.:.:.:.:.:.:.:.:.:??????.
	:.:.:!!!????.:.:!.:.:.:.:.:?????.:.:.:.:.:.:?.:.:.:.:FFFFFFNode 0: time = 146041
	9546884 msec
	Node 0: connection closed
	Node 0: total bytes received = 10000
	
#### client

	$ ./2.sh
	Node 1: started
	SNode 0 is not a neighbor of node 1
	SSSS:Node 1: time = 1460419516668 msec
	Node 1: started
	Node 1: bytes to send = 10000
	..!..!..:..:.!..!..:..:..!..!..!..!..:..:.!..!..:..:..:.!..!..!..:..:.!..!..:..:
	.:..:..:.:...!..!..:..!..:...:..:..:...:..!...:..:..:.!..:...:..:..!..:..:..:..:
	....:.:..:..:..:.:.:..:.:..:.:..:.:..:..:.:.:..!....!..!..:..:..!..!..!..:...!..
	:..:.!..:..!..:..:.:..:..:.!..:...:..!..!..!..:...!..!..:..:.:..:..!..Node 1: ti
	me = 1460419524767
	Node 1: sending completed
	Node 1: closing connection...
	!..!..:..!..:..:..:..!..:..:.:..:..:..:.!..:..:.:..!..:..:.:..:..:.:..:.:...:..:
	.:.:..!...!..:..:..:.!..!..!..!..:..:.:..:..:.:..:.!..:..:...:.:.:!..:..:::FFFFF
	Node 1: time = 1460419546026 msec
	Node 1: connection closed
	Node 1: total bytes sent = 10000
	Node 1: time elapsed = 29358 msec
	Node 1: Bps = 340.6226582192247

#### explanation

	. represents a regular data package
	! represents a retransmitted data package
	: represents a ACK that advance the acknowledge window
	? represents a ACK that does not advance the acknowledge window
	S represents a SYN packet
	F represents a FIN packet

	Due to the high lost rate, there are a lot of retransmission "!" in the output. 

### Discussion

**Diss1a: Your transport protocol implementation picks an initial sequence number when establishing a new connection. This might be 1, or it could be a random value. Which is better, and why?**

Picking up a random value is better. It will prevent the sender from sending packet to a newly established socket while it is intented to be received by a previouly closed socket. It's in a low possiblity if the packet sequence number still somehow match the receiver's expecting one using random initial number.

**Diss1b: Our connection setup protocol is vulnerable to the following attack. The attacker sends a large number of connection request (SYN) packets to a particular node, but never sends any data. (This is called a SYN flood.) What happens to your implementation if it were attacked in this way? How might you have designed the initial handshake protocol (or the protocol implementation) differently to be more robust to this attack?**

Server will run out of resources and deny legitimate connections. There are several ways to strengthen TCP handshake protocol and make it safer. 

- Implement 3-way hand shake
- Filtering (Block connections sending from notorious IP addresses)
- Increasing backlog
- Reducing SYN-RECEIVED timer
- SYN cookies

**Diss1c: What happens in your implementation when a sender transfers data but never closes a connection? (This is called a FIN attack.) How might we design the protocol differently to better handle this case?**

The receiver will maintain the connection forever. To handle this case, we can set an idle timeout for each socket. If there is no data transfered during the timeout period, the connection will be closed automatically. 

**Diss2: Your transport protocol implementation picks the size of a buffer for received data that is used as part of flow control. How large should this buffer be, and why?**

Well, it depends on the usage of this socket. I set the buffer to 4096 bytes which is a page size in disk. It can't be too small to be easily full nor too large to consume too much resource. 