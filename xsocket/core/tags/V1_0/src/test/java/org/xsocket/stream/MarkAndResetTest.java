// $Id: MarkAndResetTest.java 1023 2007-03-16 16:27:41Z grro $
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
import java.nio.ByteBuffer;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.TestUtil;
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
public final class MarkAndResetTest {

	private int running = 0;
	private int handled = 0;


	@Test public void testReadLengthField() throws Exception {
		IMultithreadedServer server = new MultithreadedServer(new ReadServerHandler());
		new Thread(server).start();
	
		
		IBlockingConnection bc = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
		
		byte[] request = TestUtil.generatedByteArray(20);
		
		bc.write((int) (request.length));
		bc.flush();
		
		TestUtil.sleep(500);
		
		bc.write(request);
		bc.flush();
		
		byte[] response = bc.readBytesByLength(request.length);
		Assert.assertTrue(TestUtil.isEquals(request, response));
	
		
		bc.close();
		server.close();
	}


	
	@Test public void testReadSplittedLengthField() throws Exception {
		final IMultithreadedServer server = new MultithreadedServer(new ReadServerHandler());
		new Thread(server).start();
	
		int countThread = 5;
		final int countLoops = 100;

		for (int j = 0; j < countThread ; j++) {
			Thread t = new Thread() {
				@Override
				public void run() {
					try {
						IBlockingConnection bc = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
						bc.setAutoflush(false);
	
						byte[] request = TestUtil.generatedByteArray(20);
	
						for (int i = 0; i < countLoops; i++) {
					
							ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
							lengthBuffer.putInt(request.length);
							lengthBuffer.clear();
							
							// send length field byte by byte
							while (lengthBuffer.hasRemaining()) {
								bc.write(lengthBuffer.get());
								bc.flush();
							}
							
							bc.write(request);
							bc.flush();
							
							byte[] response = bc.readBytesByLength(request.length);
							Assert.assertTrue(TestUtil.isEquals(request, response));
							
							handled++;
						}
					
						
						bc.close();
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
						running--;
					}
				}
			};
			t.start();
			running++;
		}
		
	
		while(running > 0) {
			TestUtil.sleep(200);
		}
		
		Assert.assertTrue(handled == (countLoops * countThread));
		
		server.close();
	}

	
	
	
	@Test public void testReadMulti() throws Exception {
		MultiServerHandler handler = new MultiServerHandler();
		final IMultithreadedServer server = new MultithreadedServer(handler);
		new Thread(server).start();
		
		IBlockingConnection bc = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
		
		bc.write((int) 45);
		bc.flush();
		TestUtil.sleep(500);
		
		bc.write((int) 77);
		bc.flush();
		TestUtil.sleep(500);

		bc.write((int) 99);
		bc.flush();
		TestUtil.sleep(500);

		
		bc.write((int) 43);
		bc.flush();

		
		Assert.assertTrue(handler.errorOccured == false);
		
		bc.close();
		server.close();
	}

	
	
	@Test public void testWrite() throws Exception {
		WriteServerHandler handler = new WriteServerHandler();
		final IMultithreadedServer server = new MultithreadedServer(handler);
		new Thread(server).start();
		
		IBlockingConnection bc = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
		
		// magic head
		bc.write((int) 88);
		
		// mark writte position
		bc.markWritePosition();
		
		
		// write garbage
		bc.write(45.56);
		bc.write((byte) 3);
		
		// jump back
		bc.resetToWriteMark();
		
		
		// "true" write
		int written = 0;
		
		// write "empty" length field
		bc.write((int) 0); 
		
		// write content 
		written += bc.write((byte) 5);
		written += bc.write(4354353L);
		written += bc.write(34);
		
		// update length filed
		bc.resetToWriteMark();
		bc.write((int) written); 
		
		// lets go
		bc.flush();
		
		
		// check response
		Assert.assertTrue(bc.readByte() == (byte) 5);
		Assert.assertTrue(bc.readLong() == 4354353L);
		Assert.assertTrue(bc.readInt() == 34);
		
		
		bc.close();
		server.close();
	}
	
	
	@Test public void testWriteAutoflushOn() throws Exception {
		WriteServerHandler handler = new WriteServerHandler();
		final IMultithreadedServer server = new MultithreadedServer(handler);
		new Thread(server).start();
		
		IBlockingConnection bc = new BlockingConnection(server.getLocalAddress(), server.getLocalPort());
		bc.setAutoflush(true);
		
		bc.markWritePosition();
		bc.write(88);

		boolean success = bc.resetToWriteMark();
		Assert.assertFalse(success);
		
		
		
		bc.close();
		server.close();
	}
	
	
	
	private static final class WriteServerHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException {
			
			connection.resetToReadMark();
			connection.markReadPosition();
			
			int i = connection.readInt();
			int length = connection.readInt();
			
			if (connection.getNumberOfAvailableBytes() >= length) {
				connection.removeReadMark();
				
				connection.write(connection.readByteBufferByLength(length));
			} 
			
			return true;
		}
	}
	
	
	private static final class MultiServerHandler implements IDataHandler {
		
		boolean errorOccured = false;
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			
			connection.resetToReadMark();
			connection.markReadPosition();
			
			int i = connection.readInt();
			if (i != 45) {
				errorOccured = true;
			}
			
			int i2 = connection.readInt();
			if (i2 != 77) {
				errorOccured = true;
			}
			
			int i3 = connection.readInt();
			if (i3 != 99) {
				errorOccured = true;
			}

			
			int i4 = connection.readInt();
			if (i4 != 43) {
				errorOccured = true;
			}
		
			return false;
		}
	}
	
	
	
	private static final class ReadServerHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException {
			
			
			boolean isReset = connection.resetToReadMark();  // reset marked by previous reads with BufferUnderflowExceptions
			
			// read the length field
			connection.markReadPosition();
			int length = connection.readInt();  // BufferUnderflowException will not be handled 
				
				
			// if all data available
			if (connection.getNumberOfAvailableBytes() >= length) {
				connection.removeReadMark();
					
				ByteBuffer[] data = connection.readByteBufferByLength(length);
				connection.write(data);
			}

			
			return true;
		}
	}
}
