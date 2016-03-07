// $Id: RunnableBulkConcurrentClient.java 764 2007-01-15 06:26:17Z grro $
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.xsocket.DataConverter;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.EchoHandler;



/**
*
* @author grro@xsocket.org
*/
public final class RunnableBulkConcurrentClient {
		
	private Random random = new Random();
	
	public static void main(String... args) throws IOException {
		if (args.length != 4) {
			System.err.println("usage java org.xsocket.server.RunnableBulkConcurrentClient <Host> <Port> <Threads> <ConnectionsPerThread>");
			return;
		}
		new RunnableBulkConcurrentClient().launch(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
		
	}

	
	public void launch(final String host, final int port, final int countThreads, final int connectionsPerThread) {
		
		System.out.println("opening " + (connectionsPerThread * countThreads) + " connections by using " + countThreads + " threads");
		
		for (int i = 0; i < countThreads; i++) {
			Thread t = new Thread() {
				@Override
				public void run() {
					try {
						send(host, port, connectionsPerThread);
					} catch (Exception e) {
						System.out.println("Error occured. Reason " + e.toString());
					}
				}
			};
			t.setName("Sender " + i);
			t.start();
		}
		
		while (true) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException ignore) { }
		}
	}

		
	private void send(String host, int port, int countConnections) throws IOException{
		
		List<BlockingConnection> connections = new ArrayList<BlockingConnection>();
		for (int i = 0; i < countConnections; i++) {
			try {
				connections.add(new BlockingConnection(host, port));
			} catch (Exception e) {
				System.out.println("couldn't open connection. Reason " + e.toString());
			}
		}
		
		while (true) {
			System.out.println("thread " + Thread.currentThread().getName() + " started with " + connections.size() + " connetions");
			for (BlockingConnection connection : connections) {
				try {
					long start = System.currentTimeMillis();
	
					String request = "rtertrete";
					connection.write(request);
					connection.write(EchoHandler.DELIMITER);
					String response = connection.receiveStringByDelimiter(EchoHandler.DELIMITER);
					
					String elapsed = DataConverter.toFormatedDuration(System.currentTimeMillis()- start);
					if (!request.equals(response)) {
						printMessage("ERROR " + elapsed + "  (Request!= Response)", connection);
					} else {
						printMessage("OK    " + elapsed + " ", connection);
					}
				} catch (Exception e) {
					printMessage("ERROR " + e.getMessage(), connection);
				}
					
				randomPause();
			}
		}		
	}

	private void printMessage(String msg, BlockingConnection connection) {
		System.out.println(msg + "  ["+ Thread.currentThread().getName() + "/" + connection.getId() + "}");		
	}
	
	void randomPause() {
		int i = 0;
		do {
			i = random.nextInt();
		} while ((i < 100) || (i > 2000));
		
		pause(i);
	}
	
	
	private void pause(long duration) {
		try {
			Thread.sleep(duration);
		} catch (InterruptedException ignore) { }
	}

}
