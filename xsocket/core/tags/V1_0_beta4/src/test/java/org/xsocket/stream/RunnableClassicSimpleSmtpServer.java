// $Id: RunnableClassicSimpleSmtpServer.java 764 2007-01-15 06:26:17Z grro $
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
package org.xsocket.stream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;





/**
*
* @author grro@xsocket.org
*/
public final class RunnableClassicSimpleSmtpServer {

	private boolean isRunning = true;
	private ExecutorService executors = Executors.newFixedThreadPool(10);
	
	public static void main(String... args) throws IOException {
		if (args.length != 1) {
			System.err.println("usage java org.xsocket.server.RunnableClassicSimpleSmtpServer <port>");
			return;
		}
		new RunnableClassicSimpleSmtpServer().launch(Integer.parseInt(args[0]));
	}
	
	
	
	public void launch(int port) throws IOException {
		
		ServerSocket sso = new ServerSocket(port);
		while (isRunning) {
			try {
				Socket s = sso.accept();
				executors.execute(new Worker(s));
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
			
		};
	}
	
	
	private static final class Worker implements Runnable {
		
		private static final int COMMAND_HANDLING = 0;
		private static final int MESSAGE_DATA_HANDLING = 1;
		
		
		private boolean isRuning = true; 
		
		private Socket socket = null;
		private PrintWriter out = null;
		private BufferedInputStream in = null;
		
		private boolean writeFile = true;
		

		private int state = COMMAND_HANDLING;
		private String mailFrom = null;
		private List<String> rcptTos = new ArrayList<String>();
		private FileOut msgSink = null;
	
		
		
		
		Worker(Socket socket) throws IOException {
			this.socket = socket;
			in = new BufferedInputStream(socket.getInputStream());
			out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
		}
		
		
		public void run() {
			
			try {
				out.println("220 this is the example smtp server");
				out.flush();
			
				do {
					switch (state) {
						case COMMAND_HANDLING:
							handleCommand();
							out.flush();
							break;
			
						case MESSAGE_DATA_HANDLING:
							handleMessageData();
							break;
					}
					
				} while (isRuning);
				
				
				in.close();
				out.close();
				socket.close();
				
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
		
		
		
		
		private void handleCommand() throws IOException, BufferUnderflowException {
			String cmd = readWord().toUpperCase();
			
			if (cmd.startsWith("HELO")) {
				out.println("250 helo nice to meet you ");
				
			} else if(cmd.startsWith("MAIL FROM:")) {
				mailFrom = cmd.substring("MAIL FROM:".length(), cmd.length());
				out.println("250 " + mailFrom + " sender OK");
				
			} else if(cmd.startsWith("RCPT TO:")) {
				String rcptTo = cmd.substring("RCPT TO:".length(), cmd.length());
				rcptTos.add(rcptTo);
				out.println("250 " + rcptTo + " Receiver OK");
				
			} else if(cmd.startsWith("DATA")) {
				out.println("354 Start mail input; end with <CRLF>.<CRLF>");
				msgSink = new FileOut();					
				state = MESSAGE_DATA_HANDLING;   // switch state
				
			} else if(cmd.startsWith("QUIT")) {
				out.println("221 closing");
				isRuning = false;

			} else {
				out.println("502 Command unrecognized");
			}
		}

		
		private void handleMessageData() throws IOException, BufferUnderflowException {

			boolean delimiterFound = false;

			do {
				byte[] data = readLine();
				msgSink.write(data);
				
				if (data.length > 0) {
					if (data[data.length-1] == '.') {
						delimiterFound = true;
					}
				}
			} while (!delimiterFound);
			
			msgSink.close();
				
			// reset addressees
			mailFrom = null;
			rcptTos.clear();
			msgSink = null;
				
			// switch state
			state = COMMAND_HANDLING;
				
			out.println("250 message accept for delivery");	
			out.flush();
		}
		
		
		
		
		private String readWord() throws IOException {
			return new String(readLine());
		}
		
		private byte[] readLine() throws IOException {
			int endPos = 1;
			in.mark(Integer.MAX_VALUE);
					
			boolean found = false;
			do {
				int i = in.read();
				
				boolean firstFound = false;
				if (i == '\r') {
					firstFound = true;
				} else {
					firstFound = false;
				}
				
				if (firstFound) {
					i = in.read();	
					if (i == '\n') {
						found = true;
					}
				}
				
				endPos++;
			} while (!found);
			
			in.reset();
			byte[] buffer = new byte[endPos -2];
			
			in.read(buffer);
			in.read();
			in.read();
			
			return buffer;
		}
	}
	
	
	
	private static class FileOut  {
		private File file = null;
		private FileOutputStream fos = null;
		
		FileOut() throws IOException {
			File file = File.createTempFile("test", "test");
			fos = new FileOutputStream(file);
		}
		
		
		public void write(byte[] data) throws IOException {
			fos.write(data);
		}	
		
		public void close() throws IOException {
			fos.close();
			file.delete();
		}
	}
}
