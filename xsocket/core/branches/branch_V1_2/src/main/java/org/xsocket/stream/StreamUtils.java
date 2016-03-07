// $Id: IHandler.java 845 2007-01-29 08:41:35Z grro $
/*
 *  Copyright (c) xsocket.org, 2006 - 2007. All rights reserved.
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
import java.util.concurrent.CountDownLatch;

import org.xsocket.IDispatcher;



/**
 * utility class for stream based communication
 * 
 * @author grro@xsocket.org
 */
public final class StreamUtils {
		
	public static final String SERVER_TRHREAD_PREFIX = "xServer";
	

	private StreamUtils() { }

	/**
	 * validate, based on a leading int length field, that enough data (getNumberOfAvailableBytes() >= length) is available. If not,
	 * an BufferUnderflowException will been thrown. The 4 bytes of the length field will be ignore by 
	 * getting the available data size. Example:
	 * <pre>
	 * //client
	 * connection.setAutoflush(false);  // avoid immediate write
	 * ...
	 * connection.markWritePosition();  // mark current position
	 * connection.write((int) 0);       // write "emtpy" length field
	 *  
	 * // write and count written size
	 * int written = connection.write(CMD_PUT);
	 * written += ...
	 *  
	 * connection.resetToWriteMark();  // return to length field position
	 * connection.write(written);      // and update it
	 * connection.flush(); // flush (marker will be removed implicit)
	 * ...
	 * 
	 * 
	 * // server
	 * class MyHandler implements IDataHandler {
	 *    ...
	 *    public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
	 *       int length = StreamUtils.validateSufficientDatasizeByIntLengthField(connection);
	 *       
	 *       // enough data (BufferUnderflowException hasn`t been thrown)
	 *       byte cmd = connection.readByte();
	 *       ...
	 *    }
	 *  }      
	 * </pre>
	 * 
	 * @param connection     the connection
	 * @return the length 
	 * @throws IOException if an exception occurs
	 * @throws BufferUnderflowException if not enough data is available
	 */
	public static int validateSufficientDatasizeByIntLengthField(INonBlockingConnection connection) throws IOException, BufferUnderflowException {

		connection.resetToReadMark();
		connection.markReadPosition();
		
		// check if enough data is available
		int length = connection.readInt();
		if (connection.getNumberOfAvailableBytes() < length) {
			// ..no, throwing underflow exception
			throw new BufferUnderflowException();
	
		} else { 
			// ...yes, remove mark
			connection.removeReadMark();
			return length;
		}
	}
	
	
	/**
	 * starts the given server within a dedicated thread. This method blocks 
	 * until the server is open.
	 * 
	 * @param server  the server to start
	 */
	public static void start(IMultithreadedServer server) {
		
		
		final CountDownLatch startedSignal = new CountDownLatch(1);
		
		// create and add startup listener 
		IMutlithreadedServerListener startupListener = new IMutlithreadedServerListener() {
			
			public void onInit() {
				startedSignal.countDown();
			};
			
			@SuppressWarnings("deprecation")
			public void onWorkerPoolUpdated(org.xsocket.IWorkerPool oldWorkerPool, org.xsocket.IWorkerPool newWorkerPool) { }
			
			@SuppressWarnings("unchecked")
			public void onDispatcherRemoved(IDispatcher dispatcher) {};
			
			@SuppressWarnings("unchecked")
			public void onDispatcherAdded(IDispatcher dispatcher) {};
			
			public void onDestroy() {};
		};
		server.addListener(startupListener);
		
		
		// start server within a dedicated thread 
		Thread t = new Thread(server);
		t.start();
	
		
		try {
			startedSignal.await();
		} catch (InterruptedException e) { 
			throw new RuntimeException("start signal doesn't occured. " + e.toString());
		}

		// update thread name
		t.setName(SERVER_TRHREAD_PREFIX + ":" + server.getLocalPort());
		
		// remove the startup listener
		server.removeListener(startupListener);
	}
}
