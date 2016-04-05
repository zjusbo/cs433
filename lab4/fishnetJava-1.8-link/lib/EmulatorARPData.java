import java.net.InetAddress;

/**
 * <pre>
 * Structure for storing data contained in the ARP cache of an emulated node
 * </pre>
 */
public class EmulatorARPData {

    private InetAddress ipAddress;
    private int port;

    /*
     * Feb. 28, 2006
     * Hao Wang
     *
     * optional physical link emulation
     */
    private EmulatedLink link;

    /**
     * Create a new structure
     * @param ipAddress The IP address of a neighboring node
     * @param port The port used by the neighboring node for communications
     */
    public EmulatorARPData(InetAddress ipAddress, int port) {
	this.ipAddress = ipAddress;
	this.port = port;

        /*
         * Feb. 28, 2006
         * Hao Wang
         *
         * no physical link emulation by default
         */
        this.link = null;
    }

    /*
     * Feb. 28, 2006
     * Hao Wang
     *
     * Create a new structure with an emulated physical link
     */
    public EmulatorARPData(InetAddress ipAddress, int port, EdgeOptions options) {
        this.ipAddress = ipAddress;
        this.port = port;
        this.link = new EmulatedLink(options);
    }

    /**
     * Get the IP address
     * @return The IP address
     */
    public InetAddress getIPAddress() {
	return this.ipAddress;
    }

    /**
     * Get the port
     * @return The port
     */
    public int getPort() {
	return this.port;
    }

    /**
     * Return the emulated physical link, or null if no emulation
     * @return EmulatedLink The emulated physical link
     */
    public EmulatedLink getEmulatedLink() {
        return this.link;
    }
}
