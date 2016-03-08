package server;
import java.io.IOException;
import java.util.HashMap;

import asyncServer.HTTPAsyncServer;
import asyncServer.HTTPAsyncServerChannel;
import utility.HTTPResponse;


/**
 * Server Dispatcher
 */

public class SHTTPTestServer {

	public static void main(String args[]) throws IOException{
		if(args.length != 3 || !args[1].equals("-config")){
			prompt();
			return ;
		}
		// parse server_idx
		int server_idx;
		try{
			server_idx = Integer.valueOf(args[0]);
			
		}catch(NumberFormatException e){
			System.err.println("Unknown servername. It should be a number.");
			prompt();
			return ;
		}
		
		// parse config file
		String configFile = args[2];
		ServerConfig config = ServerConfig.parse(configFile);
		if(config == null){
			return ; // config file error
		}
		HTTPServer server;
		
		RequestHandler.setConfig(config);
		HTTPResponse.setServername(config.servername);
		
		switch(server_idx){
			// sequential server
			case 1:
				server = new HTTPSequenceServer(config);
				break;
			// thread per request
			case 2:
				server = new HTTPPerRequestThreadServer(config);
				break;
			// thread pool with service threads competing on welcome socket;
			case 3:
				server = new HTTPThreadPoolCompetingWelcomSocketServer(config);
				break;
			// thread pool with a shared queue and busy wait;
			case 4:
				server = new HTTPThreadPoolSharedQueueBusyWaitServer(config);
				break;
			// thread pool with a shared queue and suspension;
			case 5:
				server = new HTTPThreadPoolSharedQueueSuspensionServer(config);
				break;
			case 6:
				server = new HTTPAsyncServer(config);
				break;
			case 7:
				server = new HTTPAsyncServerChannel(config);
				break;
			default:
				System.err.println("Unknown servername");
				return ;
		}
		// start server
		server.start();
	}
	static private void prompt(){
		String prompt = "Usage: <servername> -config <config_file_name>\n"
				+ "\t servername:\n"
				+ "\t\t 1 - sequential\n"
				+ "\t\t 2 - per request thread\n"
				+ "\t\t 3 - thread pool with service threads competing on welcome socket\n"
				+ "\t\t 4 - thread pool with a shared queue and busy wait\n"
				+ "\t\t 5 - thread pool with a shared queue and suspension\n"
				+ "\t\t 6 - asynchronous server using select\n"
				+ "\t\t 7 - asynchronous server using channel\n";
		System.err.println(prompt);
	}
}
	

