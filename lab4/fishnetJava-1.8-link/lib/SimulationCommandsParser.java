/**
 * <pre>   
 * Parser for the Simulator
 * Simulator is interested in all commands
 * </pre>   
 */
public class SimulationCommandsParser extends CommandsParser {    
    private Simulator simulator;

    /**
     * Create a new parser
     * @param simulator The simulator that should be used to get messages to nodes, and be informaed about topology changes
     */
    public SimulationCommandsParser(Simulator simulator) {
	super();
	this.simulator = simulator;
    }

    protected void parseNodeCmd(String[] cmd) {
	if(cmd.length < 2) {
	    return;
	}
	try {
	    int nodeAddr = Integer.parseInt(cmd[0]);
	    String msg = "";
	    for(int i = 1; i < cmd.length; i++) {
		msg += cmd[i] + " ";
	    }
	    // remove last space added by above loop
	    msg = msg.substring(0, msg.length() - 1);
	    if(!this.simulator.sendNodeMsg(nodeAddr, msg)) {
		System.err.println("Node address: " + nodeAddr + " does not exist!");
	    }
	}catch(Exception e) {
	    System.err.println("Error parsing command to node: ");		
	    this.printStrArray(cmd, System.err);
	}
    }

    protected void exit(String[] cmd) {
	if(cmd[0].equals("exit")) {
	    simulator.stop();
	}
    }
}
