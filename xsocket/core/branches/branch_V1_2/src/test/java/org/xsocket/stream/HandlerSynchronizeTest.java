// $Id: IndexOfTest.java 1190 2007-04-29 11:30:53Z grro $
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



import org.junit.Assert;
import org.junit.Test;


import org.xsocket.Synchronized;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.stream.BlockingConnection;
import org.xsocket.stream.IBlockingConnection;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IMultithreadedServer;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.MultithreadedServer;



/**
*
* @author grro@xsocket.org
*/
public final class HandlerSynchronizeTest {

	private static final String DELIMITER = "\n";
	
	private static final int LOOPS = 20;
	private static final int SLEEP_TIME = 50;

	
	@Test 
	public void testNonSynchronized() throws Exception {
		NonSynchronizedHandler hdl = new NonSynchronizedHandler();
		IMultithreadedServer server = new MultithreadedServer(hdl);
		StreamUtils.start(server);

		
		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
			
		for (int j = 0; j < LOOPS; j++) {
			String request = "record";
			connection.write(request + DELIMITER);
				
			QAUtil.sleep(SLEEP_TIME / 5);
		}
			
		connection.close();
		server.close();
		
		Assert.assertTrue(hdl.counter.max > 1);
	}


	
	@Test 
	public void testSynchronized() throws Exception {
		SynchronizedHandler hdl = new SynchronizedHandler();
		IMultithreadedServer server = new MultithreadedServer(hdl);
		StreamUtils.start(server);;

		
		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
			
		for (int j = 0; j < LOOPS; j++) {
			String request = "record";
			connection.write(request + DELIMITER);
				
			QAUtil.sleep(SLEEP_TIME / 5);
		}
			
		connection.close();
		server.close();
		
		Assert.assertTrue(hdl.counter.max == 1);
	}

	
	
	@Synchronized(Synchronized.Mode.OFF)
	private final class NonSynchronizedHandler implements IDataHandler {
		
		private Counter counter = new Counter();
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			counter.inc();
			
			try {
				connection.readStringByDelimiter(DELIMITER);
				QAUtil.sleep(SLEEP_TIME);

			} finally {
				counter.dec();
			}
			
			return true;
		}
	}
	
	
	@Synchronized(Synchronized.Mode.CONNECTION)
	private final class SynchronizedHandler implements IDataHandler {
		
		private Counter counter = new Counter();
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			counter.inc();
			
			try {
				connection.readStringByDelimiter(DELIMITER);
				QAUtil.sleep(SLEEP_TIME);

			} finally {
				counter.dec();
			}
			
			return true;
		}
	}
	
	
	private static final class Counter {
		
		private int i = 0;
		private int max = 0;
		
		public synchronized void inc() {
			i++;
			if (i > max) {
				max = i;
			}
		}
		
		public synchronized void dec() {
			i--;
		}
	}
}
