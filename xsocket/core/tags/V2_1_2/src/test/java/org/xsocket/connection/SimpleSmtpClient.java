/*
 *  Copyright (c) xsocket.org, 2006-2008. All rights reserved.
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
package org.xsocket.connection;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;



import org.xsocket.LogFormatter;



/**
*
* @author grro@xsocket.org
*/
public final class SimpleSmtpClient {

	private static final Logger LOG = Logger.getLogger(SimpleSmtpClient.class.getName());
	
	
	private String host = null;
	private int port = -1;

	public static void main(String[] args) throws Exception {
		
		Logger logger = Logger.getLogger(SimpleSmtpClient.class.getName());
		logger.setLevel(Level.FINE);

		ConsoleHandler ch = new ConsoleHandler();
		ch.setLevel(Level.FINE);
		ch.setFormatter(new LogFormatter());
		logger.addHandler(ch);	
		
		
		if (args.length != 3) {
			System.out.println("usage org.xsocket.connection.SimpleSmtpClient <host> <port> <message file>");
			System.exit(-1);
		}
		
		String host = args[0];
		int port = Integer.parseInt(args[1]);
		String file = args[2];
		
		if (!new File(file).exists()) {
			System.out.println("message file " + file + "  does not exits");
			System.exit(-1);
		}
		
		
		FileInputStream fos = new FileInputStream(file);
		byte[] b = new byte[fos.available()];
		fos.read(b);
		fos.close ();
		String msg = new String(b);
		
		SimpleSmtpClient smtpClient = new SimpleSmtpClient(host, port);
		smtpClient.send(msg);
	}
	
	
	
	public SimpleSmtpClient(String host, int port) throws IOException {
		this.host = host;
		this.port = port;
	}
	
	
	
	public void send(String message) throws IOException {

		IBlockingConnection con = new BlockingConnection(host, port);
		
		// read greeting
		readResponse(con);

		sendCmd(con, "Helo you");
		readResponse(con);
		

		int i = message.indexOf("From: ");
		String sender = message.substring(i + "From: ".length(), message.indexOf("\r\n", i));
		sendCmd(con, "Mail From: " + sender);
		readResponse(con);
		
		i = message.indexOf("To: ");
		String receiver = message.substring(i + "To: ".length(), message.indexOf("\r\n", i));
		sendCmd(con, "Rcpt To: " + receiver);
		readResponse(con);

		sendCmd(con, "Data");
		readResponse(con);
				
		String[] lines = message.split("\r\n");
		for (String line : lines) {
			if (line.startsWith(".")) {
				line = "." + line; 
			}
			
			con.write(line + "\r\n");
		}
		con.write("\r\n.\r\n");
		
		
		sendCmd(con, "Quit");
		readResponse(con);
		
		con.close();
	}
	
	
	private void sendCmd(IBlockingConnection con, String cmd) throws IOException {
		LOG.fine("sending " + cmd);
		con.write(cmd + "\r\n");
	}
	
	private String readResponse(IBlockingConnection con) throws IOException {
		String response = con.readStringByDelimiter("\r\n");
		LOG.fine("receiving " + response);
		
		return response;
	}
}
