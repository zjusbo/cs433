// $Id: RunnableEchoServer.java 387 2006-11-14 18:25:23Z grro $
/*
 *  Copyright (c) xsocket.org, 2006. All rights reserved.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Please refer to the LGPL license at: http://www.gnu.org/copyleft/lesser.txt
 * The latest copy of this software may be found on http://www.xsocket.org/
 */
package org.xsocket.server;


import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;




/**
*
* @author grro@xsocket.org
*/
public final class RunnableClassicEchoServer {

	private boolean isRunning = true;
	private ExecutorService executors = Executors.newFixedThreadPool(10);

	
	public static void main(String... args) throws IOException {
		if (args.length != 2) {
			System.err.println("usage java org.xsocket.server.RunnableClassicEchoServer <port> <isPooled>");
			return;
		}
		new RunnableClassicEchoServer().launch(Integer.parseInt(args[0]), Boolean.parseBoolean(args[1]));
		
	}
	
	public void launch(int port, boolean isPooled) throws IOException {
		
		for (int i = 0; i < 10000; i++) {
			Thread t = new Thread(new DummyWorker());
			t.start();
		}
		
		ServerSocket sso = new ServerSocket(port);
		while (isRunning) {
			try {
				Socket s = sso.accept();
				if (isPooled) {
					executors.execute(new Worker(s));
				} else {
					Thread t = new Thread(new Worker(s));
					t.start();
				}
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
			
		};
	}
	
	
	
	private static final class Worker implements Runnable {
		
		private PrintWriter out = null;
		private LineNumberReader in = null;
		
		
		Worker(Socket socket) throws IOException {
			in = new LineNumberReader(new InputStreamReader(socket.getInputStream()));
			out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
			out.flush();
		}
		
		
		public void run() {
			try {
				String line = in.readLine();
				out.println(line);
				out.flush();
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}	
	}
	
	
	private static final class DummyWorker implements Runnable {
		
		private static long result = 0; 
		
		public void run() {
			while (true) {
				int i = 5656;
				i = i /434;
				i = i * 5656;
				result += i;	
				
				try {
					Thread.sleep(50);
				} catch (InterruptedException ignore) {  }
			}
		}	
	}
}
