import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.net.SocketException;

/**
 * <pre>   
 * Class with main method that starts up a Manager. Either an Emulator or a Simulator
 * Usage:  java Fishnet <simulate> <num nodes> <topo file> [fishnet file] [timescale]
 *         or
 *         java Fishnet <emulate> <trawler host name> <trawler port> <local port to use> [fishnet file]
 *         
 *         Arguments in <> are required and arguments in [] are optional. Fishnet file is a file with commands for a node
 *         Topofile is the topology file to use. It also have commands for a node.
 * </pre>   
 */
public class Fishnet {
    
    private static void usage() {
	System.out.println("Usage:  java Fishnet <simulate> <num nodes> <topo file> [fishnet file] [timescale]\n" + 
			   "or\n" + 
			   "java Fishnet <emulate> <trawler host name> <trawler port> <local port to use> [fishnet file]\n\n" +          
			   "Arguments in <> are required and arguments in [] are optional.\n" +  
			   "Fishnet file is a file with commands for a node\n" + 
			   "Topofile is the topology file to use. It also have commands for a node.");
    }

    /**
     * The main method. Entry point to start a Manager
     */
    public static void main(String[] args) {
	if(args.length < 3) {
	    System.err.println("Missing arguments");
	    usage();
	    return;
	}
	
	try {
	    Manager manager = null;
	    String noFile = "-";

	    if(args[0].equals("simulate")) {
		int numNodes = Integer.parseInt(args[1]);
		String topoFile = args[2];
		try {
		    manager = new Simulator(numNodes, topoFile);
		}catch(IllegalArgumentException e) {
		    System.err.println("Illegal arguments given to Simulator. Exception: " + e);
		    return;
		}catch(FileNotFoundException e) {
		    System.err.println("Incorrect topo file name given to Simulator. Exception: " + e);		    
		    return;
		}

		switch(args.length) {
		case 5: 
		    double timescale = Double.parseDouble(args[4]);
		    manager.setTimescale(timescale);
		case 4:
		    if(!noFile.equals(args[3])) {
			manager.setFishnetFile(args[3]);
		    }		    
		}

	    }else if(args[0].equals("emulate")) {
		if(args.length < 4) {
		    System.err.println("Missing arguments to emulator");
		    usage();
		    return;
		}
		String trawlerName = args[1];
		int trawlerPort = Integer.parseInt(args[2]);
		int localUDPPort = Integer.parseInt(args[3]);
		try {
		    manager = new Emulator(trawlerName, trawlerPort, localUDPPort);
		}catch(UnknownHostException e) {
		    System.err.println("Trawler host name is unkown! Exception: " + e);
		    return;
		}catch(SocketException e) {
		    System.err.println("Could not bind to the given local port: " + localUDPPort + ". Exception: " + e);
		    return;
		}catch(IOException e) {
		    System.err.println("Encountered exception while trying to connect to Trawler. Exception " + e);
		    return;
		}catch(IllegalArgumentException e) {
		    System.err.println("Illegal arguments given to Emulator. Exception: " + e);
		    return;
		}
		if(args.length > 4 && !noFile.equals(args[4])) {
		    manager.setFishnetFile(args[4]);
		}
	    }else {
		System.err.println("Unknown arguments");
		usage();
		return;
	    }

	    manager.start();
	}catch(Exception e) {
	    System.err.println("Exception occured in Fishnet!! Exception: " + e);
	    e.printStackTrace();
	}
    }
}
