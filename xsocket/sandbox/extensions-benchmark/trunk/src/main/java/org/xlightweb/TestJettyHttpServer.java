package org.xlightweb;




import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.HashSessionManager;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.jetty.servlet.SessionHandler;

 


public final class TestJettyHttpServer {

public static void main( String[] args ) throws Exception {
		
		if (args.length < 1) {
			System.out.println("usage oorg.xlightweb.TestJettyHttpServer <port> <using nio>");
			System.exit(-1);
		}
		
		new TestJettyHttpServer().launch(args);
	}
		
		
	public void launch(String... args) throws Exception {
	
		int port = Integer.parseInt(args[0]);
	
		
		System.out.println("\r\n---------------------------------------");

	
		
		Server jettyServer = new Server(port);
		Context rootCtx = new Context(jettyServer, "");
		rootCtx.setSessionHandler(new SessionHandler(new HashSessionManager()));
		ServletHolder servletHolder = new ServletHolder(new HandlerServlet());
		rootCtx.addServlet(servletHolder, "/*");


		if ((args.length > 1) && (Boolean.parseBoolean(args[1]) == true)) {
			SelectChannelConnector selector = new SelectChannelConnector();
			selector.setPort(port);
			jettyServer.setConnectors(new Connector[] { selector });
			System.out.println("nio mode");
			
		} else {
			
			
			System.out.println("classic mode");
		}
		 
		System.out.println("---------------------------------------\r\n");
		
		System.out.println("running jetty http server");

		jettyServer.start();		
    }
	
	
	
	public static class HandlerServlet extends HttpServlet {
		
		private static final long serialVersionUID = 7149317632846027667L;

		private static final int OFFSET = 48;
		
		private static Timer timer = new Timer(true);
		private static int count = 0;
		private static long time = System.currentTimeMillis();
	
		private int cachedSize = 100; 
		private byte[] cached = generateByteArray(cachedSize);

		static {
			TimerTask task = new TimerTask() {
				
				public void run() {
					try {
						String rate = printRate();
						System.out.println(rate);
					} catch (Exception ignore) { }
				}
			};
			timer.schedule(task, 5000, 5000);
		}
		
		
		@Override
		protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

			int size = Integer.parseInt(request.getParameter("dataLength"));
			
			if (size > 0) {
				if (size != cachedSize) {
					cachedSize = size;
					cached = generateByteArray(size);
				} 
			}
			
		
			try {
				response.setContentType("text/plain");
				OutputStream os = response.getOutputStream();
				os.write(cached);
				os.close();
				
				count++;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		


		public static ByteBuffer generateByteBuffer(int length) {
			ByteBuffer buffer = ByteBuffer.wrap(generateByteArray(length));
			return buffer;
		}
		
		

		public static byte[] generateByteArray(int length) {
			
			byte[] bytes = new byte[length];
			
			int item = OFFSET;
			
			for (int i = 0; i < length; i++) {
				bytes[i] = (byte) item;
				
				item++;
				if (item > (OFFSET + 9)) {
					item = OFFSET;
				}
			}
			
			return bytes;
		}
		

		private static String printRate() {
			long current = System.currentTimeMillis();
			
			long rate = 0;
			
			if (count == 0) {
				rate = 0;
			} else {
				rate = (count * 1000) / (current - time);
			}

			time = System.currentTimeMillis(); 
			count = 0;
			
			return Long.toString(rate);
		}
	}
}
