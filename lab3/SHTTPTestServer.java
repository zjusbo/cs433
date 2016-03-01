import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;


/**
 * Server Dispatcher
 */
public class SHTTPTestServer {
	static private HashMap<String, String> config;
	static private HashSet<String> requiredLabels;
	static public Double cache_size = 0.0; // cache_size in kb
	static{
		 requiredLabels = new HashSet<String>();
		 requiredLabels.add("listen"); // case insensitive
		 requiredLabels.add("threadpoolsize");
		 requiredLabels.add("cachesize");
		 requiredLabels.add("documentroot");
		 requiredLabels.add("servername");
	}
	public static void main(String args[]) throws FileNotFoundException{
		if(args.length != 3 || !args[1].equals("-config")){
			prompt();
			return ;
		}
		
		// parse server_idx
		int server_idx;
		try{
			server_idx = Integer.valueOf(args[0]);
			if(server_idx > 5 || server_idx < 1){
				throw new NumberFormatException();
			}
		}catch(NumberFormatException e){
			System.err.println("Unknown servername. It should be a number from 1 to 5");
			prompt();
			return ;
		}
		
		// parse config file
		String configFile = args[1];
		SHTTPTestServer.config = parseConfiguration(configFile);
		if(SHTTPTestServer.config == null){
			return ; // config file error
		}
		
		switch(server_idx){
			// sequential server
			case 1:
				
				break;
			// thread per request
			case 2:
				
				break;
			// thread pool with service threads competing on welcome socket;
			case 3:
				
				break;
			// thread pool with a shared queue and busy wait;
			case 4:
				break;
			// thread pool with a shared queue and suspension;
			case 5:
				break;
			default:
				System.err.println("Unknown servername");
				break;
		}
		
	}
	static private void prompt(){
		String prompt = "Usage: <servername> -config <config_file_name>"
				+ "\t servername:\n"
				+ "\t\t 1 - sequential\n"
				+ "\t\t 2 - per request thread\n"
				+ "\t\t 3 - thread pool with service threads competing on welcome socket\n"
				+ "\t\t 4 - thread pool with a shared queue and busy wait\n"
				+ "\t\t 5 - thread pool with a shared queue and suspension\n";
		System.err.println(prompt);
	}
	static private HashMap<String, String> parseConfiguration(String filename) throws FileNotFoundException{
		HashMap<String, String> re = new HashMap<String, String>();
		Scanner scanner = new Scanner(new FileReader(filename));
		while(scanner.hasNext()){
			String line = scanner.next();
			// ignore <virtualHost line
			if(line.startsWith("<")){
				continue;
			}
			String token[] = line.split("\\s+");
			
			if(token == null || token.length < 2){
				System.out.println("configuration file illegal: " + line);
			}
			// case insensitive
			token[0] = token[0].toLowerCase();
			token[1] = token[1].toLowerCase();
			if(SHTTPTestServer.requiredLabels.contains(token[0])){
				// only parse required fields
				re.put(token[0], token[1]);
			}
		}
		scanner.close();
		boolean isError = false;
		for(String label : SHTTPTestServer.requiredLabels){
			if(!re.containsKey(label)){
				isError = true;
				System.err.println("Configuration file error: " + label + " required");
			}
		}
		if(isError){
			return null;
		}else{
			return re;
		}
		
	}
}
