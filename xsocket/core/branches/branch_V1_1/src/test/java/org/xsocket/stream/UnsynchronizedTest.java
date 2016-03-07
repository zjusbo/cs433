// $Id: FlushOnCloseTest.java 1017 2007-03-15 08:03:05Z grro $
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
import java.util.concurrent.atomic.AtomicInteger;


import static org.xsocket.Synchronized.Mode.*;
import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.Synchronized;
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
public final class UnsynchronizedTest {

 
	private static final String DELIMITER = "\n\r";

	

	@Test public void testUnsynchronizedHandler() throws Exception {

		UnsynchronizedServerHandler hdl = new UnsynchronizedServerHandler();
		IMultithreadedServer server = new MultithreadedServer(hdl);
		StreamUtils.start(server);
		
		IBlockingConnection connection = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
		connection.setAutoflush(true);
		
		for (int j = 0; j < 100; j++) {
			connection.write("data " + DELIMITER);
			QAUtil.sleep(10);	
		}
		connection.close();
		
		
		QAUtil.sleep(2000);
		
		Assert.assertTrue("Max concurrent is not larger than 1 (" + hdl.maxConcurrent + ")", hdl.maxConcurrent > 1);
		
		server.close();
	}
	
	
	@Test public void testSynchronizedHandler() throws Exception {

		SynchronizedServerHandler hdl = new SynchronizedServerHandler();
		IMultithreadedServer server = new MultithreadedServer(hdl);
		StreamUtils.start(server);

		IBlockingConnection connection = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
		connection.setAutoflush(true);
		
		for (int j = 0; j < 100; j++) {
			connection.write("data " + DELIMITER);
			QAUtil.sleep(10);
		}
		connection.close();
		
		
		QAUtil.sleep(2000);
		
		Assert.assertTrue("Max concurrent is not 1 (" + hdl.maxConcurrent + ")", hdl.maxConcurrent == 1);
		
		server.close();
	}

	
	
	@Test public void testDefaultSyncBehavior() throws Exception {

		DefaultServerHandler hdl = new DefaultServerHandler();
		IMultithreadedServer server = new MultithreadedServer(hdl);
		StreamUtils.start(server);

		IBlockingConnection connection = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
		connection.setAutoflush(true);
		
		for (int j = 0; j < 100; j++) {
			connection.write("data " + DELIMITER);
			QAUtil.sleep(10);
		}
		connection.close();
		
		
		QAUtil.sleep(2000);
		
		Assert.assertTrue("Max concurrent is not 1 (" + hdl.maxConcurrent + ")", hdl.maxConcurrent == 1);
		
		server.close();
	}
	

	@Synchronized(OFF)
	private static final class UnsynchronizedServerHandler implements IDataHandler {
		
		private AtomicInteger concurrent = new AtomicInteger();
		private int maxConcurrent = 0;
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

			int i = concurrent.incrementAndGet();
			if (i > maxConcurrent) {
				maxConcurrent = i;
			}
			
			try {
				synchronized (connection) {
					connection.readByteBufferByDelimiter(DELIMITER);
				}
				
				QAUtil.sleep(20);
				
				return true;
				
			} finally {
				concurrent.decrementAndGet();				
			}
		}
	}
	
	
	@Synchronized(CONNECTION)
	private static final class SynchronizedServerHandler implements IDataHandler {
		
		private AtomicInteger concurrent = new AtomicInteger();
		private int maxConcurrent = 0;
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

			int i = concurrent.incrementAndGet();
			if (i > maxConcurrent) {
				maxConcurrent = i;
			}
			
			try {
				connection.readByteBufferByDelimiter(DELIMITER);
				QAUtil.sleep(20);	
				
				return true;
			} finally {
				concurrent.decrementAndGet();				
			}

		}
	}
	
	
	// by default handlers are synchronized 
	private static final class DefaultServerHandler implements IDataHandler {
		
		private AtomicInteger concurrent = new AtomicInteger();
		private int maxConcurrent = 0;
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			int i = concurrent.incrementAndGet();
			if (i > maxConcurrent) {
				maxConcurrent = i;
			}
			
			try {
				connection.readByteBufferByDelimiter(DELIMITER);
				QAUtil.sleep(20);
				
				return true;
			} finally {
				concurrent.decrementAndGet();				
			}
		}
	}

}
