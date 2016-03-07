package org.xsocket.server.smtp;


import java.util.ArrayList;
import java.util.List;

import org.xsocket.BlockingConnection;
import org.xsocket.IBlockingConnection;



public final class PerformanceTestClient {
	
	
	
	public static void main(String... args) throws Exception {
		new PerformanceTestClient().launch(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
	}
	
	
	public void launch(String hostname, int port, int loops, int workerSize) throws Exception {
		
		// warm up
		new Caller(hostname, port, loops).run();
		
		List<Caller> callers = new ArrayList<Caller>();
		for (int i = 0; i < workerSize; i++) {
			callers.add(new Caller(hostname, port, loops));
		}
		
		for (Caller caller : callers) {
			new Thread(caller).start();
		}
		
		
		boolean allFinished = true;
		do {
			allFinished = true;
			try {
				Thread.sleep(500);
			} catch (InterruptedException ignore) { }

			for (Caller caller : callers) {
				allFinished = allFinished & caller.finished;
			}

			
		} while(!allFinished);


		for (Caller caller : callers) {
			System.out.println(caller.printResult() + "\n");
		}

		
	}
	
	
	private static class Caller implements Runnable {
		
		private String hostname = null;
		private int port = 0;
		private int loops = 0;
		
		private boolean finished = false;
		
		
		private long openTime = -1;
		private List<Long> calls = new ArrayList<Long>();
		private long threadId = 0;
		
		public Caller(String hostname, int port , int loops) {
			this.hostname = hostname;
			this.port = port;
			this.loops = loops;
		}
		
		public void run() {
			threadId = Thread.currentThread().getId();
			
			try {
				long startC = System.nanoTime();
				IBlockingConnection connection = new BlockingConnection(hostname, port);
				long endC = System.nanoTime();
				
				openTime = endC - startC;
				
				connection.setDefaultEncoding("ASCII");
				connection.receiveRecord("\r\n");
				
				for (int i = 0; i < loops; i++) {
					long start = System.nanoTime();
					connection.writeWord("HELO \r\n");
					connection.receiveWord("\r\n");
					long end = System.nanoTime();
					calls.add(end- start);
				}
					
				connection.close();
			} catch (Exception e) {
				
			}
			
			finished = true;
		}
		
		public boolean isFinished() {
			return finished;
		}
		
		
		public String printResult() {
			StringBuilder sb = new StringBuilder();
			sb.append("Thread ID=" + threadId + "\n ");
			sb.append("Elapsed Time new Connection  " + ((double ) openTime) / 1000000 + " millis \n");
			
			for (long time : calls) {
				sb.append("Elapsed Time hello req/res  " + ((double ) time) / 1000000 + " millis \n");
			}
			
			return sb.toString();

		}
	}
}
