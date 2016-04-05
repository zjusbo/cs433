import java.util.ArrayList;
import java.util.HashMap;
import java.util.ListIterator;

/**
 * <pre>
 * Topology class keeps track of connections between nodes.
 * This is a Singleton object
 * </pre>
 */
public class Topology {

    private ArrayList edges;
    private HashMap failedNodes;
    private boolean allToAll;
    private static Topology topology = null;

    /**
     * Static method for getting an instance of Topology
     * @return An instance of Topology
     */
    public static Topology GetInstance() {
	if(Topology.topology == null) {
	    Topology.topology = new Topology(false);
	}
	return Topology.topology;
    }

    /**
     * Static method for getting an instance of Topology
     * @return An instance of Topology
     */
    public static Topology GetInstance(boolean allToAll) {
	if(Topology.topology == null) {
	    Topology.topology = new Topology(allToAll);
	}
	return Topology.topology;
    }

    /**
     * Returns the edge between a and b, if one exists and it is live. Returns null otherwise
     * @param a Int specifying a node
     * @param b Int specifying a node
     * @return The live edge between a and b. If one does not exist then null is returned
     */
    public Edge getLiveEdge(int a, int b) {
	Edge e = getEdge(a, b);
	if(e == null || !e.isLive() || !this.isNodeAlive(a) || !this.isNodeAlive(b)) {
	    return null;
	}
	return e;
    }

    /**
     * Returns true if the given node is alive, else return false
     * @param node Int specifying node
     */
    public boolean isNodeAlive(int node) {
	return !this.failedNodes.containsKey(new Integer(node));
    }

    /**
     * Creates a new edge or updates an existing one
     * @param a Int specifying a node
     * @param b Int specifying a node
     * @param options Options to set for the given edge
     */
    public void newEdge(int a, int b, EdgeOptions options) {
	Edge e = this.getEdge(a, b);
	if (e != null) {
            /*
             * Feb. 27, 2006
             * Hao Wang
             *
             * Set edge options
             */
            /*
             * e.setState(true);
             */
            e.setOptions(options);
	}else {
	    e = new Edge(a, b, options);
	    edges.add(e);
	}
    }

    /**
     * Mark the given edge as failed so that we don't use it
     * @param a Int specifying a node
     * @param b Int specifying a node
     * @return True if edge was succesfully failed. False if no edge exists between a and b
     */
    public boolean failEdge(int a, int b) {
	return changeEdge(a, b, false);
    }

    /**
     * Restart the given edge so that we can use it again
     * @param a Int specifying a node
     * @param b Int specifying a node
     * @return True if edge was succesfully restarted. False if no edge exists between a and b
     */
    public boolean restartEdge(int a, int b) {
	return changeEdge(a, b, true);
    }

    /**
     * Mark the given node as failed, so that we don't use any edge that goes through it
     * @param a Int specifying a node
     */
    public void failNode(int a) {
	this.failedNodes.put(new Integer(a), null);
    }

    /**
     * Mark the given node as ok, so that we can use it again
     * @param a Int specifying a node
     */
    public void restartNode(int a) {
	this.failedNodes.remove(new Integer(a));
    }


    //********** Private Functions **********

    private Topology(boolean allToAll) {
	this.edges = new ArrayList();
	this.failedNodes = new HashMap();
	this.allToAll = allToAll;
    }

    private Edge getEdge(int a, int b) {
	ListIterator iter = this.edges.listIterator();
	Edge e;
	while(iter.hasNext()) {
	    e = (Edge)iter.next();
	    if (e.isEdge(a, b)) {
		return e;
	    }
	}
	if(this.allToAll) {
	    // if no edge exists create one
	    e = new Edge(a, b, new EdgeOptions());
	    return e;
	}

	return null;
    }

    private boolean changeEdge(int a, int b, boolean state) {
	Edge e = this.getEdge(a, b);
	if(e != null) {
	    e.setState(state);
	    return true;
	}

	System.err.println("No edge exists between " + String.valueOf(a) + " and " + String.valueOf(b));
	return false;
    }

}
