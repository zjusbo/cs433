/**
 * <pre>   
 * Class that stores information about a Ping request that was sent
 * </pre>   
 */
public class PingRequest {

    private int destAddr;
    private byte[] msg;
    private long timeSent;

    /**
     * Initialize member variables
     * @param destAddr The address of the destination host
     * @param msg The message that was sent
     * @param timeSent The time that the ping was sent
     */
    public PingRequest(int destAddr, byte[] msg, long timeSent) {
	this.destAddr = destAddr;
	this.msg = msg;
	this.timeSent = timeSent;
    }

    /**
     * @return The address of the destination host
     */
    public int getDestAddr() {
	return this.destAddr;
    }

    /**
     * @return The message that was sent in the Ping
     */
    public byte[] getMsg() {
	return this.msg;
    }

    /**
     * @return The time that the ping was sent
     */
    public long getTimeSent() {
	return this.timeSent;
    }

    /**
     * @return String representation
     */
    public String toString() {
	return new String("Dest: " + destAddr + " Send Time: " + timeSent + " Message: " + Utility.byteArrayToString(msg));
    }
}
