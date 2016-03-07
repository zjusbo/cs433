// $Id: DisconnectTest.java 1156 2007-04-15 08:10:39Z grro $
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
package org.xsocket.connection;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.BufferUnderflowException;


import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;


 
/**
*
* @author grro@xsocket.org
*/
public final class BenchmarkingClients {

	private static final String DELIMITER = "\r\n";
	
	public static final void main(String... args) throws Exception {
		if (args.length != 2) {
			System.out.println("usage java org.xsocket.stream.BenchmarkClient <host> <port>");
			System.exit(-1);
		}
		
		new BenchmarkingClients().launch(args[0], Integer.parseInt(args[1]));
	}
	
	
	public void launch(String host, int port) throws IOException {
		compare(host, port, 10, 100, 1);
		
		
		System.out.println("\n\n\nloops, dataSize, packages, direct,  blocking,  nonblocking");
		compare(host, port, 10, 1000, 1);
		compare(host, port, 10, 1000, 10);
		compare(host, port, 1000, 1000, 1);
		compare(host, port, 1000, 1000, 10);
		compare(host, port, 50000, 1000, 2);
		compare(host, port, 1000000, 50, 1);
	}
	
	
	private void compare(String host, int port, int size, int loops, int packages) throws IOException {
		long timeDirect = callDirect(host, port, size, loops, packages); 
		long timeBlocking = callBlockingConnection(host, port, size, loops, packages); 
		long timeNonBlocking = callNonBlockingConnection(host, port, size, loops, packages); 
		System.out.println(loops + ",  " + size + ",  " + packages + ",  " + timeDirect + ",  " + timeBlocking + ",  " + timeNonBlocking);
	}
	

	
	static long callDirect(String host, int port, int dataSize, int loops, int packages) throws IOException {
		String data = new String(QAUtil.generateByteArray(dataSize));
		
		long start = System.currentTimeMillis();
		
		for (int i = 0; i < loops; i++) {
			Socket socket = new Socket(host, port);
			PrintWriter pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
			LineNumberReader lnr = new LineNumberReader(new InputStreamReader(socket.getInputStream()));

			for (int j = 0; j < packages; j++) {
				pw.println(data);
				pw.flush();
			
				String result = lnr.readLine();
			
				if (!data.equals(result)) {
					System.out.println("error!");
				}
			}

			pw.close();
			lnr.close();
			socket.close();
		}
		
		long end = System.currentTimeMillis();
		return (end - start);
	}
	

	static long callBlockingConnection(String host, int port, int dataSize, int loops, int packages) throws IOException {
		String data = new String(QAUtil.generateByteArray(dataSize));
		
		long start = System.currentTimeMillis();
		
		for (int i = 0; i < loops; i++) {
			IBlockingConnection connection = new BlockingConnection(host, port);
			connection.setAutoflush(true);
			
			for (int j = 0; j < packages; j++) {
				connection.write(data + DELIMITER);
				String result = connection.readStringByDelimiter(DELIMITER);
				
				if (!data.equals(result)) {
					System.out.println("error!");
				}
			}

			connection.close();
		}
		
		long end = System.currentTimeMillis();
		return (end - start);
	}

	
	
	static long callNonBlockingConnection(String host, int port, int dataSize, int loops, int packages) throws IOException {
		String data = new String(QAUtil.generateByteArray(dataSize));
		
		long start = System.currentTimeMillis();
		
		for (int i = 0; i < loops; i++) {
			ClientHandler hdl = new ClientHandler();
			INonBlockingConnection connection = new NonBlockingConnection(host, port, hdl);
			connection.setAutoflush(true);
			
			
			for (int j = 0; j < packages; j++) {
				synchronized (hdl) {
					connection.write(data + DELIMITER);
					try {
						hdl.wait();
					} catch (InterruptedException ignore) { }
				}
				
				if (!data.equals(hdl.result)) {
					System.out.println("error!");
				}
			}
			
			connection.close();
		}
		
		long end = System.currentTimeMillis();
		return (end - start);
	}

	
	private static final class ClientHandler implements IDataHandler {
		
		private String result = null;
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			result = connection.readStringByDelimiter(DELIMITER);
			
			synchronized (this) {
				this.notify();
			}
			
			return true;
		}
	}
}
