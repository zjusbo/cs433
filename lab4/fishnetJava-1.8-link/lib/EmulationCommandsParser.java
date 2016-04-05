/**
 * <pre>   
 * Parser for the Emulator
 * Emulator is interested in all commands except topology commands
 * </pre>   
 */
public class EmulationCommandsParser extends CommandsParser {

    private Emulator emulator;

    /**
     * Create a new parser
     * @param emulator The emulator that should be used to get messages to nodes
     */
    public EmulationCommandsParser(Emulator emulator) {
	super();
	this.emulator = emulator;
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

	return this.parseCommonCmds(cmd, now);
    }  

    protected void parseNodeCmd(String[] cmd) {
	String msg = "";
	for(int i = 0; i < cmd.length; i++) {
	    msg += cmd[i] + " ";
	}
	// remove last space added by above loop
	msg = msg.substring(0, msg.length() - 1);
	// Node addr does not matter for emulator
	this.emulator.sendNodeMsg(0, msg);
    }

    protected void exit(String[] cmd) {
	if(cmd[0].equals("exit")) {
	    emulator.stop();
	}
    }
}
