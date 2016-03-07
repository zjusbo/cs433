// $Id: DisconnectTest.java 1108 2007-03-29 16:44:02Z grro $
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
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.List;



import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IConnectHandler;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IDisconnectHandler;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;




/**
*
* @author grro@xsocket.org
*/
public final class DisconnectTest {


	private int balance = 0;
	private List<String> errors = new ArrayList<String>();

	private int runningTreads = 0;
	
	
	@Test public void testSimple() throws Exception {
		//TestUtil.setLogLevel(Level.FINE);
		final IMultithreadedServer server = new MultithreadedServer(new TestHandler());
		new Thread(server).start();


		for (int i = 0; i < 3; i++) {
			Thread t = new Thread() {
				public void run() {
					runningTreads++;
					try {
						for (int j = 0; j < 2; j++) {
							IBlockingConnection con = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
							con.write("test");
							con.close();
						}
					} catch (Exception e) {
						errors.add(e.toString());
					}
					
					runningTreads--;
				};
			};
			t.start();
		}

		QAUtil.sleep(200);


		do {
			QAUtil.sleep(100);
		} while (runningTreads > 0);

		if (balance !=0) {
			System.out.println("unbalanced connects disconnect ratio " + balance);
		}
		
		if (errors.size() != 0) {
                    for (String error : errors) {
                         System.out.println("error occured: " + error);
                    }
			
		}

		
		Assert.assertTrue(" unbalanced connects disconnect ratio " + balance, balance == 0);
		Assert.assertTrue("error occured", errors.size() == 0);
		
		server.close();
	}



	private class TestHandler implements IDataHandler, IConnectHandler, IDisconnectHandler {

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			balance++;
			return true;
		}

		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			
		/*	try {
				connection.readInt();  // this could be possible, because there could be remaining data in the receive buffer 
				errors++;
			} catch (BufferUnderflowException expected) { }
			
			
			try {
				connection.write("Shouldn't be possible");
				errors++;
			} catch (ClosedConnectionException expected) { }
			
			try {
				connection.startSSL(); // shouldn't be possible
				errors++;
			} catch (ClosedConnectionException expected) { }
			
			try {
				connection.flush(); // shouldn't be possible
				errors++;
			} catch (ClosedConnectionException expected) { }
		*/
			
			balance--;
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			connection.readAvailable();
			return true;
		}
	}
}
