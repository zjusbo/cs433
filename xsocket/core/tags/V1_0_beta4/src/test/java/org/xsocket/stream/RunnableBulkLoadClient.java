// $Id: RunnableBulkLoadClient.java 806 2007-01-18 07:47:43Z grro $
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

import org.xsocket.DataConverter;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.EchoHandler;
import org.xsocket.stream.IBlockingConnection;



/**
*
* @author grro@xsocket.org
*/
public final class RunnableBulkLoadClient {
		

	public static void main(String... args) throws IOException {
		if (args.length != 4) {
			System.err.println("usage java org.xsocket.server.RunnableBulkLoadClient <Host> <Port> <Threads> <datasizeInBytes>");
			return;
		}
		new RunnableBulkLoadClient().launch(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
		
	}

	
	public void launch(final String host, final int port, final int countThreads, final int dataSizeInBytes) {
		
		System.out.println("starting with " + countThreads + " threads and " + DataConverter.toFormatedBytesSize(dataSizeInBytes) + " data package");
		
		for (int i = 0; i < countThreads; i++) {
			Thread t = new Thread() {
				@Override
				public void run() {
					try {
						send(host, port, dataSizeInBytes);
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

		
	private void send(String host, int port, int dataSizeInBytes) throws IOException{
		
		IBlockingConnection connection = new BlockingConnection(host, port);
		String request = new String(generatedData(dataSizeInBytes));
		
		while (true) {
			try {
				long start = System.nanoTime();
				connection.write(request + EchoHandler.DELIMITER);
				String response = connection.readStringByDelimiter(EchoHandler.DELIMITER);
				
				long elapsed = System.nanoTime()- start;
				if (!request.equals(response)) {
					printMessage("ERROR " + formatDate(elapsed) + "  (Request!= Response)", connection);
				} else {
					printMessage("OK " + formatDate(elapsed) + " ", connection);
				}
			} catch (Exception e) {
				printMessage("ERROR " + e.getMessage(), connection);
				connection = new BlockingConnection(host, port);
			}				
		}		
	}
	
	String formatDate(long nanos) {
		long micros = nanos / 1000;
		double millis = ((double) micros) / 1000;
		return millis + " millis";
	}
	
	byte[] generatedData(int length) {
		byte[] bytes = new byte[length];
		int j = 48;
		for (int i = 0; i < length; i++) {
			j++;
			if (j == 58) {
				j = 48;
			}
			bytes[i] = (byte) j;
		}
		
		return bytes;
	}

	

	private void printMessage(String msg, IBlockingConnection connection) {
		System.out.println(msg + "  ["+ Thread.currentThread().getName() + "/" + connection.getId() + "}");		
	}
	
}
 