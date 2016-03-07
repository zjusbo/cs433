package org.xsocket.bayeux.http;



import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.logging.Level;

import javax.management.JMException;

import org.xsocket.bayeux.http.BayeuxHttpProtocolHandler;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.http.RequestHandlerChain;
import org.xsocket.connection.http.server.Context;
import org.xsocket.connection.http.server.FileServiceHandler;
import org.xsocket.connection.http.server.HttpServer;
import org.xsocket.connection.http.server.HttpServerConnection;



public class TestServer {

	public static void main(String[] args) throws Exception {
		
		QAUtil.setLogLevel("org.xsocket.bayeux.http", Level.FINE);
		
		System.setProperty(HttpServerConnection.SHOW_DETAILED_ERROR_KEY, "true");
		System.setProperty("org.xsocket.connection.dispatcher.maxHandles", "50");
		
		if (args.length == 0) {
			System.out.println("usage java [options] proxy <listen port>\r\n");
			System.out.println("where options include:");
			System.out.println("   -debug    activate debug");
			System.exit(-1);
		} 
		
		ArrayList<String> params = new ArrayList<String>();
		params.addAll(Arrays.asList(args));
		
		
		boolean printMessage = false;
		
		
		for (String arg : args) {
			if (arg.equalsIgnoreCase("-debug")) {
				params.remove(arg);
				System.setProperty(HttpServerConnection.SHOW_DETAILED_ERROR_KEY, "true");
				printMessage = true;
			}
		}

	
		new TestServer(Integer.parseInt(params.get(0)), printMessage);
	}
	
	
	
	
	public TestServer(int listenport, boolean printMessage) throws IOException, JMException {
				
		Context ctx = new Context("");
		
		RequestHandlerChain chain = new RequestHandlerChain();
		
		chain.addLast(new LogFilter());
		
		chain.addLast(new BayeuxHttpProtocolHandler());
		
		ctx.addHandler("/service/*", chain);
		
		String basePath = new File("").getAbsolutePath() + File.separator + "src" + File.separator + "test" + File.separator + "resources";
		ctx.addHandler("/*", new FileServiceHandler(basePath, true));
		
		
		RequestHandlerChain rootChain = new RequestHandlerChain();
		rootChain.addLast(new PerformanceLogFilter());
		rootChain.addLast(ctx);
		
		HttpServer server = new HttpServer(listenport, rootChain);
		server.setWorkerpool(Executors.newFixedThreadPool(10));
		
		ConnectionUtils.registerMBean(server);
		
		server.run();
	}
}
