import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.IOException;

/**
 * <pre>
 * CommandsParser -- parses topology and keyboard commands
 *
 * COMMANDS ARE CASE SENSITIVE.
 *
 * Pay attention to the spaces.
 * Commands are parsed by using the spaces as delimiters thus it is very important
 * to put spaces where required. Also putting consecutive spaces will cause the command to be incorrect.
 *
 * THUS THE PARSER IS VERY SENSITIVE TO THE LOCATION OF SPACES AND NUMBER OF SPACES
 *
 * The topology file and the keyboard input file have the same format;
 * all the same commands can be entered from either one.  Both
 * are line-oriented (one command per line).
 * Nodes (e.g., a, b) are referred to by their FishnetAddress (0..254).
 *
 *	[// | #] <comment>  -- any line starting with // or # is ignored
 *	edge a b [lossRate <double>] [delay <long>] [bw <int>] [bt <long>]
 *		-- this creates an edge between a and b, with the
 *		specified loss rate, delay (in milliseconds), bw (in B/s), and buffering time (in milliseconds)
 *		or changes the specifics for an existing link
 *		defaults: 0 lossRate, 1 msec delay, 10KB/s bw, and 250 msec buffering time
 *	time [+ ]x  -- any subsequent command is delayed until simulation/real
 *			has reached x (or now + x, if + is used), in milliseconds from start
 *                    NOTE: IF + IS USED THERE MUST BE A SPACE BETWEEN + AND x
 *	fail a [b] -- this removes node a (if b is not specified) or an edge (if it is)
 *	restart a [b]  -- this restarts a node or edge.  previous information about
 *		the node/edge is preserved
 *	echo text -- print the text
 *	exit  -- cleanly stop the simulation/emulation run and print statistics
 *	a <msg>  -- deliver text <msg> to node a (for simulation mode only)
 *	<msg> -- deliver text <msg> to this node (for emulation mode only)
 *		Note that msg cannot start with any keyword defined above
 *
 * To avoid a race condition with respect to starting up the user protocol code,
 * the simulator will only process keyboard commands at time >  0.
 * </pre>
 */

public abstract class CommandsParser {

    private String filename;
    private BufferedReader reader;

    protected CommandsParser() {
	this.filename = null;
	this. reader = null;
    }

    /**
     * Open and process a topology file.
     * @param filename The name of the command file.
     * @param now The current time.
     * @return How long to defer further processing. Returns -1 if do not have to defer
     * @throws FileNotFoundException If the named filed does not exist, is a directory rather than a regular file, or
     *                               for some other reason cannot be opened for reading
     */
    public long parseFile(String filename, long now) throws FileNotFoundException {
	if(filename == null) {
	    return -1;
	}
	this.filename = filename;
	this.reader = new BufferedReader(new FileReader(filename));
	return parseRemainder(now);
    }

    /**
     * Parse the rest of a command file.  Parsing will be deferred if we run into a time command.
     * @param now The current time in microseconds
     * @return How long to defer further processing. Returns -1 if do not have to defer
     */
    public long parseRemainder(long now){
	long deferTill = -1;
	try {
	    String line;
	    while(deferTill == -1 && (line = this.reader.readLine()) != null) {
		deferTill = parseLine(line, now);
	    }
	}catch(IOException e) {
	    System.err.println("IOException occured while trying to read file: " + filename + "\nException: " + e);
	}
	return deferTill;
    }

    /**
     * Process one line of topology file or keyboard input.
     * @param line A command line.
     * @param now The current time in microseconds
     * @return How long to defer further processing. Returns -1 if do not have to defer
     */
    public long parseLine(String line, long now) {

	if(this.skipLine(line)) {
	    return -1;
	}

	String[] cmd = line.split(" ");

	// Java short circuit evaluates boolean expressions
	if(this.parseEdge(cmd) || this.parseFail(cmd) || this.parseRestart(cmd)) {
	    return -1;
	}

	return this.parseCommonCmds(cmd, now);
    }


    /******************** Protected Functions ********************/

    /**
     * Call manager.stop() if the command is exit. Actually will accept exit followed by anything
     * Thus "exit" and "exit blahblah" will both cause fishnet to exit.
     * However "exitblahblah" will not.
     */
    protected abstract void exit(String[] cmd);

    protected abstract void parseNodeCmd(String[] cmd);

    /**
     * Returns true if line should be skipped, either because its empty or is a comment
     */
    protected boolean skipLine(String line) {
	if(line == null || line.equals("")) {
	    return true;
	}
	String[] cmd = line.split(" ");

	if(cmd[0].startsWith("//") || cmd[0].startsWith("#")) {
	    return true;
	}
	return false;
    }

    /**
     * Parses exit, echo, time and node commands
     * Returns how long parsing should be deferred for if time command encountered.
     * Returns -1 if parsing should not be deferred
     */
    protected long parseCommonCmds(String[] cmd, long now) {
	this.exit(cmd);

	if(this.echo(cmd)) {
	    return -1;
	}

	long deferTill = this.parseTime(cmd, now);
	if(deferTill == -1 && !cmd[0].equals("time")) {
	    this.parseNodeCmd(cmd);
	}
	return deferTill;
    }

    // These following functions are overriden by TrawlerCommandsParser so that it can notify trawler of change

    protected void createNewEdge(int nodeA, int nodeB, EdgeOptions options) {
	Topology.GetInstance().newEdge(nodeA, nodeB, options);
    }

    protected boolean failEdge(int nodeA, int nodeB) {
	return Topology.GetInstance().failEdge(nodeA, nodeB);
    }

    protected void failNode(int node) {
	Topology.GetInstance().failNode(node);
    }

    protected boolean restartEdge(int nodeA, int nodeB) {
	return Topology.GetInstance().restartEdge(nodeA, nodeB);
    }

    protected void restartNode(int node) {
	Topology.GetInstance().restartNode(node);
    }

    protected void printStrArray(String[] strArray, int startIndex, int endIndex, PrintStream stream) {
	if(strArray == null || stream == null) {
	    return;
	}
	endIndex = Math.min(endIndex, strArray.length);
	for(int i = startIndex; i < endIndex; i++) {
	    stream.print(strArray[i] + " ");
	}
	stream.println();
    }

    protected void printStrArray(String[] strArray, PrintStream stream) {
	this.printStrArray(strArray, 0, strArray.length, stream);
    }

    /******************** Private Functions ********************/

    private boolean parseEdge(String[] cmd) {
	if(cmd[0].equals("edge")) {
	    try {
		int nodeA;
		int nodeB;
		EdgeOptions options = new EdgeOptions();

		switch(cmd.length) {
		// All options
                /*
                 * Mar. 12, 2006
                 * Hao Wang
                 *
                 * support specification of buffering time
                 */
                case 11:
                    if (cmd[9].equals("bt")) {
                        options.setBT(Integer.valueOf(cmd[10]).longValue());
                    }

		case 9:
		    if(cmd[7].equals("bw")) {
			options.setBW(Integer.valueOf(cmd[8]).intValue());
		    }

		case 7:
		    if(cmd[5].equals("delay")) {
			options.setDelay(Integer.valueOf(cmd[6]).longValue());
		    }

		case 5:
		    if(cmd[3].equals("lossRate")) {
			options.setLossRate(Double.parseDouble(cmd[4]));
		    }

		case 3:
		    nodeA = Integer.parseInt(cmd[1]);
		    nodeB = Integer.parseInt(cmd[2]);
		    break;

		default:
		    throw new Exception();
		}
		this.createNewEdge(nodeA, nodeB, options);
	    }catch(Exception e) {
		System.err.println("Error parsing edge command: ");
		this.printStrArray(cmd, System.err);
	    }
	    return true;
	}

	return false;
    }

    private boolean parseFail(String[] cmd) {
	if(cmd[0].equals("fail")) {
	    try {
		int[] nodes = this.parseFailRestartArgs(cmd);

		if(nodes[1] != -1) {
		    if(!this.failEdge(nodes[0], nodes[1])) {
			System.err.println("No edge exists between node " + nodes[0] + " and node " + nodes[1]);
		    }
		}else {
		    this.failNode(nodes[0]);
		}
	    }catch(Exception e) {
		System.err.println("Error parsing fail command: ");
		this.printStrArray(cmd, System.err);
	    }
	    return true;
	}
	return false;
    }


    private boolean parseRestart(String[] cmd) {
	if(cmd[0].equals("restart")) {
	    try {
		int[] nodes = this.parseFailRestartArgs(cmd);

		if(nodes[1] != -1) {
		    if(!this.restartEdge(nodes[0], nodes[1])) {
			System.err.println("No edge exists between node " + nodes[0] + " and node " + nodes[1]);
		    }
		}else {
		    this.restartNode(nodes[0]);
		}
	    }catch(Exception e) {
		System.err.println("Error parsing restart command: ");
		this.printStrArray(cmd, System.err);
	    }
	    return true;
	}
	return false;
    }

    private int[] parseFailRestartArgs(String[] cmd) throws Exception {
	int[] nodes = {Integer.parseInt(cmd[1]), -1};

	if(cmd.length > 2) {
	    nodes[1] = Integer.parseInt(cmd[2]);
	}
	return nodes;
    }



    // Echo string if cmd is echo
    // Return value indicates whether command was echo or not
    private boolean echo(String[] cmd) {
	if(cmd[0].equals("echo")) {
	    this.printStrArray(cmd, 1, cmd.length, System.out);
	    return true;
	}
	return false;
    }

    // Return -1 if there is no time cmd, else return delay
    // Have to convert parsed time (which is in milliseconds) to microseconds
    private long parseTime(String[] cmd, long now){
	long deferTill = -1;
	if(cmd[0].equals("time")) {
	    try {
		if(cmd[1].equals("+")) {
		    deferTill = now + (Integer.valueOf(cmd[2]).longValue() * 1000);
		}else {
		    deferTill = Integer.valueOf(cmd[1]).longValue() * 1000;
		}
	    }catch(Exception e) {
		System.err.println("Error parsing time command: ");
		this.printStrArray(cmd, System.err);
		return -1;
	    }
	}
	return deferTill;
    }

}
