/**
 * <pre>   
 * Parser for the Trawler
 * Trawler is only interested in topology commands
 * </pre>   
 */
public class TrawlerCommandsParser extends CommandsParser {

    /**
     * Create a new parser
     */
    public TrawlerCommandsParser() {
	super();
    }

    protected void createNewEdge(int nodeA, int nodeB, EdgeOptions options) {
	super.createNewEdge(nodeA, nodeB, options);
	Trawler.GetInstance().startEdge(nodeA, nodeB);
    }

    protected boolean failEdge(int nodeA, int nodeB) {
	if(super.failEdge(nodeA, nodeB)) {
	    Trawler.GetInstance().failEdge(nodeA, nodeB);
	    return true;
	}
	return false;
    }

    protected void failNode(int node) {
	Trawler.GetInstance().failNode(node); // Inform trawler BEFORE faling node
	super.failNode(node);
    }

    protected boolean restartEdge(int nodeA, int nodeB) {
	if(super.restartEdge(nodeA, nodeB)) {
	    Trawler.GetInstance().startEdge(nodeA, nodeB);
	    return true;
	}
	return false;
    }

    protected void restartNode(int node) {
	super.restartNode(node);
	Trawler.GetInstance().restartNode(node);
    }
    
    // Trawler is not interested in commands to node
    protected void parseNodeCmd(String[] cmd) {
	System.err.println("Trawler: Could not understand command: ");
	this.printStrArray(cmd, System.err);
    }

    protected void exit(String[] cmd) {
	if(cmd[0].equals("exit")) {
	    Trawler.GetInstance().exit();
	}
    }

}
