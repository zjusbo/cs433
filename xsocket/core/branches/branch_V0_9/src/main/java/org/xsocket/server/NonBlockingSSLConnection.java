// $Id: MultithreadedServer.java 41 2006-06-22 06:30:23Z grro $
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


package org.xsocket.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;

import org.xsocket.ClosedConnectionException;
import org.xsocket.IConnection;


/**
 * A non blocking ssl connection  
 * 
 * @author grro@xsocket.org
 */
final class NonBlockingSSLConnection extends NonBlockingConnection {

	private static final Logger LOG = Logger.getLogger(NonBlockingSSLConnection.class.getName());
	
	
	private SSLEngine sslEngine = null;
	private boolean isHandshaking = true;

	private int minPacketBufferSize = 0;
	private int minAppBufferSize = 0;

	private boolean connectionOpenEvent = false;
	
	public boolean isEstablished() {
		return !isHandshaking; 
	}
	
	/**
	 * constructor 
	 * 
	 * @param channel the underlying channel
	 * @param id the assigned id 
	 * @param sslContext the sslcontext to use 
	 * @throws IOException if the channel can not be configured ain a non-blocking mode
	 */
	public NonBlockingSSLConnection(SocketChannel channel, String id, SSLContext sslContext) throws IOException {
		super(channel, id);
		
		sslEngine = sslContext.createSSLEngine();
		minPacketBufferSize = sslEngine.getSession().getPacketBufferSize();
		minAppBufferSize = sslEngine.getSession().getApplicationBufferSize();
	}

	/**
	 * @see IInternalNonBlockingConnection
	 */
	public void init(final InternalHandler handler) throws IOException {
		setHandler(handler);		
		
		sslEngine.setUseClientMode(false);
		sslEngine.beginHandshake();
	}

	
	
	/**
	 * @see NonBlockingConnection
	 */
	@Override
	public void handleNonBlockingRead() throws ClosedConnectionException, IOException {
	
		int appBytesReceived = 0;
		
		// read and unwrap received data 
		ByteBuffer inNetData = readPhysical();		
		List<ByteBuffer> inAppDataList = unwrap(inNetData);
			
		// add the unwrap result to receive queue
		for (ByteBuffer inAppData : inAppDataList) {
			if (inAppData.limit() > 0) {
				appBytesReceived += addToReceiveQueue(inAppData);
			}
		}

		// write required?
		if (sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
			handleNonBlockingWrite();
		}
		
		if (connectionOpenEvent) {
			connectionOpenEvent = false;
			getAssignedHandler().onConnectionOpened(this);
		}
		
		
		if (appBytesReceived > 0) {
			getAssignedHandler().onDataReceived(this);
		}
	}
	
	
	/**
	 * @see NonBlockingConnection
	 */
	@Override
	public void handleNonBlockingWrite() throws ClosedConnectionException, IOException {
		
		// get content of send queue 
		ByteBuffer[] outAppDataList = drainSendQueue();

		// wrap the content and put it back to the send queue
		int i = 0;
		do {
			ByteBuffer outAppData = null;
			if (outAppDataList.length > i) {
				outAppData = outAppDataList[i];
			} else {
				outAppData = ByteBuffer.allocateDirect(0);
			}
			List<ByteBuffer> outNetDataList = wrap(outAppData);
			for (ByteBuffer outNetData : outNetDataList) {
				addToSendQueue(outNetData);
			}
			i++;
		} while (i < outAppDataList.length);
		
		// call super class to precess the handling
		super.handleNonBlockingWrite();	
	}

	
	
	
	private List<ByteBuffer> unwrap(ByteBuffer inNetData) throws SSLException, ClosedConnectionException {			
		List<ByteBuffer> inAppDataList = new ArrayList<ByteBuffer>();
		
		boolean repeat = false;
		int minSize = minAppBufferSize;
		
		do {
			repeat = false;
			SSLEngineResult engineResult = null;
			
			// processing received data
			ByteBuffer inAppData = ThreadBoundMemoryManager.acquireMemory(minSize);	
			engineResult = sslEngine.unwrap(inNetData, inAppData);
			LOG.fine(engineResult.toString());

			if (engineResult.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
				ThreadBoundMemoryManager.recycleMemory(inAppData);
				repeat = true;
				minSize += minSize;
				continue;
				
			} else if (engineResult.getStatus() == SSLEngineResult.Status.CLOSED) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("ssl connection is closed. closing connection");
				}
				ThreadBoundMemoryManager.recycleMemory(inAppData);
				ClosedConnectionException cce = new ClosedConnectionException("Couldn't unwrap, because connection is closed");
				LOG.throwing(this.getClass().getName(), "unwrap", cce);
				throw cce;
				
			} else if (engineResult.getStatus() == SSLEngineResult.Status.OK) {
			
				// get remaining inNetData
				if (inNetData.position() < inNetData.limit()) {
					inNetData = inNetData.slice();
				}
				
				// extract  data
				inAppData.flip();
				inAppData = ThreadBoundMemoryManager.extractAndRecycleMemory(inAppData);
				inAppDataList.add(inAppData);				
			}

			// need task?
			if (sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
				Runnable task = null;
				while ((task = sslEngine.getDelegatedTask()) != null) {
				    task.run();
				}
				LOG.fine(sslEngine.getHandshakeStatus().toString());
			}  
									
			// unwrap ?
			if (sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
				if (inNetData.hasRemaining()) {
					repeat = true;
				}
			}
		
		} while (repeat);
		
		return inAppDataList;
	}
	
	
	
	private List<ByteBuffer> wrap(ByteBuffer outAppData) throws SSLException, ClosedConnectionException {			
		List<ByteBuffer> outNetDataList = new ArrayList<ByteBuffer>();
		
		boolean repeat = false;
		int minSize = minPacketBufferSize;
		
		do {
			repeat = false;
			SSLEngineResult engineResult = null;
			
			// processing received data
			ByteBuffer outNetData = ThreadBoundMemoryManager.acquireMemory(minSize);
			engineResult = sslEngine.wrap(outAppData, outNetData);
			LOG.fine(engineResult.toString());

			if (engineResult.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
				ThreadBoundMemoryManager.recycleMemory(outNetData);
				repeat = true;
				minSize += minSize;
				continue;
				
			} else if (engineResult.getStatus() == SSLEngineResult.Status.CLOSED) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("ssl connection is closed. closing connection");
				}
				ThreadBoundMemoryManager.recycleMemory(outNetData);
				ClosedConnectionException cce = new ClosedConnectionException("Couldn't unwrap, because connection is closed");
				LOG.throwing(this.getClass().getName(), "wrap", cce);
				throw cce;
				
			} else if (engineResult.getStatus() == SSLEngineResult.Status.OK) {
			
				// get remaining outAppData
				if (outAppData.position() < outAppData.limit()) {
					outAppData = outAppData.slice();
				}
				
				// extract  data
				outNetData.flip();
				outNetData = ThreadBoundMemoryManager.extractAndRecycleMemory(outNetData);
				outNetDataList.add(outNetData);

				
				// finished ?
				if (engineResult.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED) {
					isHandshaking = false;
					connectionOpenEvent = true;
					break; 
				} 
			}
				
			// need task?
			if (sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
				Runnable task = null;
				while ((task = sslEngine.getDelegatedTask()) != null) {
				    task.run();
				}
				LOG.fine(sslEngine.getHandshakeStatus().toString());
			}  
				
				
			// wrap 
			if (sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
				repeat = true;
			}
		
		} while (repeat);
		
		return outNetDataList;
	}
	
	
	/**
	 * @see IConnection
	 */
	@Override
	public synchronized void close() {
		sslEngine.closeOutbound();
		super.close();
	}
}
