import java.net.InetAddress;
import java.util.ArrayList;

/**
 * <pre>
 * Provides method to create and parse commands sent between the Trawler and the emulated node
 * </pre>
 */
public class TrawlerNodeARPCommands {

    /**
     * Return a command to add a neighbor.
     * @param fishAddr Fishnet address of neighbor
     * @param ipAddress IP Address of neighbor
     * @param port Port the neighbor is using for communication with peers
     * @return A command to add a neighbor.
     */
    public static String addNeighbor(int fishAddr, InetAddress ipAddress, int port) {
	String cmd = "add " + String.valueOf(fishAddr) + " ";
	cmd += ipAddress.getHostName() + " " + String.valueOf(port);
	return cmd;
    }

    /*
     * Feb. 27, 2006
     * Hao Wang
     *
     * Return a command to add a neighbor with edge options
     * @param fishAddr int Fishnet address of neighbor
     * @param ipAddress InetAddress IP Address of neighbor
     * @param port int Port the neighbor is using for communication with peers
     * @param options EdgeOptions Options of edge to this neighbor
     * @return String
     */
    public static String addNeighborOptions(int fishAddr,
                                            InetAddress ipAddress, int port,
                                            EdgeOptions options) {
        String cmd = addNeighbor(fishAddr, ipAddress, port);
        cmd += " lossRate " + String.valueOf(options.getLossRate());
        cmd += " delay " + String.valueOf(options.getDelay());
        cmd += " bw " + String.valueOf(options.getBW());
        cmd += " bt " + String.valueOf(options.getBT());
        return cmd;
    }

    /**
     * Return a command to remove a neighbor
     * @param fishAddr Fishnet address of neighbor to remove
     * @return A command to remove a neighbor
     */
    public static String removeNeighbor(int fishAddr) {
	return new String("remove " + String.valueOf(fishAddr));
    }

    /**
     * Return a command to reset a node
     * @return A command to reset a node
     */
    public static String reset() {
	return new String("reset");
    }

    /**
     * Parse an addNeighbor command.
     * @param cmd The command received
     * @param data An ArrayList that will be populated with the data if this is an addNeighbor command.
     *             data[0] = fishned address and data[1] = EmulatorARPData.
     * @return True if this was an addNeighbor command
     */
    public static boolean receiveAddNeighbor(String cmd, ArrayList data) {
	String[] args = cmd.split(" ");
        /*
         * Feb. 28, 2006
         * Hao Wang
         *
         * check for optional edge options
         */
        if (receiveAddNeighborOptions(cmd, data)) {
            return true;
        }

	if(args.length != 4 || !args[0].equals("add")) {
	    return false;
	}
	try {
	    Integer fishAddr = Integer.valueOf(args[1]);
	    InetAddress ipAddress = InetAddress.getByName(args[2]);
	    int port = Integer.parseInt(args[3]);
	    data.add(fishAddr);
	    data.add(new EmulatorARPData(ipAddress, port));
	    return true;
	}catch(Exception e) {
	    // Do nothing. Will return false
	}
	return false;
    }

    /**
     * Parse an addNeighbor command with edge options.
     * @param cmd The command received
     * @param data An ArrayList that will be populated with the data if this is an addNeighbor command.
     *             data[0] = fishned address and data[1] = EmulatorARPData.
     * @return True if this was an addNeighbor command
     */
    public static boolean receiveAddNeighborOptions(String cmd, ArrayList data) {
        String[] args = cmd.split(" ");
        if (args.length != 12 || !args[0].equals("add") ||
            !args[4].equals("lossRate") || !args[6].equals("delay") ||
            !args[8].equals("bw") || !args[10].equals("bt")) {
            return false;
        }

        try {
            Integer fishAddr = Integer.valueOf(args[1]);
            InetAddress ipAddress = InetAddress.getByName(args[2]);
            int port = Integer.parseInt(args[3]);
            EdgeOptions options = new EdgeOptions();
            options.setLossRate(Double.parseDouble(args[5]));
            options.setDelay(Long.parseLong(args[7]));
            options.setBW(Integer.parseInt(args[9]));
            options.setBT(Long.parseLong(args[11]));
            data.add(fishAddr);
            data.add(new EmulatorARPData(ipAddress,port, options));
            return true;
        } catch (Exception e) {
            // Do nothing. Will return false
        }

        return false;
    }

    /**
     * Parse a removeNeighbor command
     * @param cmd The command received
     * @return The address of the node to remove. -1 if this was not a removeNeighbor command
     */
    public static int receiveRemoveNeighbor(String cmd) {
	String[] args = cmd.split(" ");
	if(args.length != 2 || !args[0].equals("remove")) {
	    return -1;
	}
	try {
	    return Integer.parseInt(args[1]);
	}catch(Exception e) {
	    //Do nothing. Will return -1
	}
	return -1;
    }

    /**
     * Parse a reset command
     * @param cmd The command seen
     * @return True if this was a reset command
     */
    public static boolean receiveReset(String cmd) {
	return cmd.equals("reset");
    }
}


