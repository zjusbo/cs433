package client;
import java.util.HashMap;
import java.util.Map;

//http://journals.ecs.soton.ac.uk/java/tutorial/java/cmdLineArgs/parsing.html
class ClientArgument {
	public final String server, host, filename;
	public final int port, parallel, T, verbose;
	private ClientArgument(String server, String host, String filename, int port, int parallel, int T, int verbose){
		this.server = server;
		this.host = host;
		this.filename = filename;
		this.port = port;
		this.parallel = parallel;
		this.T = T;
		this.verbose = verbose;
	}
	static final String prompt;
	static final Map<String, String> m_err_prompt;
	static{
		prompt = new String("Usage: HTTPClient "
				+ "[-verbose] "
				+ "[-server] <server ip> "
				+ "[-servname] <host name> "
				+ "[-port] <port> "
				+ "[-parallel] <# threads> "
				+ "[-files] <filename> "
				+ "[-T] <timeout seconds>");
		
		m_err_prompt = new HashMap<String, String>();
		m_err_prompt.put("-server", "requires a server ip");
		m_err_prompt.put("-servname", "requires a server ip");
		m_err_prompt.put("-port", "requires a port number");
		m_err_prompt.put("-parallel", "requires a number of threads");
		m_err_prompt.put("-files", "requires an input file");
		m_err_prompt.put("-T", "requires a timeout seconds");
		
	}
	//%java SHTTPTestClient
	// -server <server>
	// -servname <server name>
	// -port <server port>
	// -parallel <# of threads> 
	// -files <file name> 
	// -T <time of test in seconds>
    public static ClientArgument parse(String[] args) {
    	
        int i = 0;
        String arg;
        boolean isError = false;
        int verbose = 0;
        String server = null, host = null, filename = null;
        int port = 80, parallel = 1, T = 3; // default value
        while (i < args.length && args[i].startsWith("-")) {
            arg = args[i++];
            switch(arg){
            	// use this type of check for "wordy" arguments
            	case "-verbose":
            		if (i < args.length){
                        System.out.println("verbose mode on");
            			verbose = Integer.valueOf(args[i++]);
                    }
                    else{
                    	isError = true;
                    	System.err.println("-server " + ClientArgument.m_err_prompt.get("-server"));
                    }
            		break;
	            // use this type of check for arguments that require arguments    
            	case "-server":
            		if (i < args.length)
                        server = args[i++];
                    else{
                    	isError = true;
                    	System.err.println("-server " + ClientArgument.m_err_prompt.get("-server"));
                    }
            		break;
            	case "-servname":
            		if (i < args.length)
                        host = args[i++];
                    else{
                    	isError = true;
                    	System.err.println("-servname " + ClientArgument.m_err_prompt.get("-servname"));
                    }
            		break;
            	case "-port":
            		if (i < args.length)
                        port = Integer.valueOf(args[i++]);
                    else{
                    	isError = true;
                    	System.err.println("-port " + ClientArgument.m_err_prompt.get("-port"));
                    }
            		break;
            	case "-parallel":
            		if (i < args.length)
                        parallel = Integer.valueOf(args[i++]);
                    else{
                    	isError = true;
                    	System.err.println("-parallel " + ClientArgument.m_err_prompt.get("-parallel"));
                    }
            		break;
            	case "-files":
            		if (i < args.length)
                        filename = args[i++];
                    else{
                    	isError = true;
                    	System.err.println("-filename " + ClientArgument.m_err_prompt.get("-filename"));
                    }
            		break;
            	case "-T":
            		if (i < args.length)
                        T = Integer.valueOf(args[i++]);
                    else{
                    	isError = true;
                    	System.err.println("-T " + ClientArgument.m_err_prompt.get("-T"));
                    }
            		break;
            	default:
            		System.err.println("Unknown argument: " + arg);
            		break;
            }
        }
        if (i != args.length || args.length == 0 || isError ){// not success
        	System.err.println(ClientArgument.prompt);
        	return null;
        }
        else{
        	// success
        	return new ClientArgument(server, host, filename, port, parallel, T, verbose);
        }
    }
    @Override
    public String toString(){
    	String s;
    	s = "Client config: \n";
    	s += "\tserver: " + this.server + "\n";
    	s += "\thost: " + this.host + "\n";
    	s += "\tfilename: " + this.filename + "\n";
    	s += "\tport: " + this.port + "\n";
    	s += "\tparallel: " + this.parallel + "\n";
    	s += "\tT: " + this.T + "\n";
    	s += "\tverbose: " + this.verbose;
    	return s;
    }
}