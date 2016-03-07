/*
 *  Copyright (c) xsocket.org, 2006 - 2008. All rights reserved.
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
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;




/**
 * activateable SSL io handler
 *
 * @author grro@xsocket.org
 */
final class IoActivateableSSLHandler extends IoChainableHandler implements IoSSLProcessor.EventHandler {


	private static final Logger LOG = Logger.getLogger(IoSSLHandler.class.getName());

	private enum Mode { OFF, PLAIN_OUTGOING_SSL_INCOMING, PRE_SSL2, SSL };

	private AtomicReference<Mode> mode = new AtomicReference<Mode>(Mode.OFF);



	// receive & send queue
	private IoQueue inNetDataQueue = new IoQueue();
	private IoQueue outNetDataQueue = new IoQueue();
	private IoQueue outAppDataQueue = new IoQueue();
	

	// sync write support
	private final PendingWriteMap pendingWriteMap = new PendingWriteMap();


	// event handling
	private final IOEventHandler ioEventHandler = new IOEventHandler();

	// ssl stuff
	private IoSSLProcessor sslProcessor = null;




	/**
	 * constructor
	 *
	 * @param successor      the successor
	 * @param sslContext     the ssl context to use
	 * @param isClientMode   true, if is in client mode
	 * @param memoryManager  the memory manager to use
	 * @throws IOException If some other I/O error occurs
	 */
	IoActivateableSSLHandler(IoChainableHandler successor, SSLContext sslContext,boolean isClientMode, AbstractMemoryManager memoryManager) throws IOException {
		super(successor);

		sslProcessor = new IoSSLProcessor(sslContext, isClientMode, memoryManager, this);
	}



	public void init(IIoHandlerCallback callbackHandler) throws IOException {
		setPreviousCallback(callbackHandler);
		getSuccessor().init(ioEventHandler);
	}



	/**
	 * {@inheritDoc}
	 */
	public boolean reset() {
		inNetDataQueue.drain();
		outNetDataQueue.drain();
		outAppDataQueue.drain();

		pendingWriteMap.clear();

		return super.reset();
	}



	/**
	 * {@inheritDoc}
	 */
	public void setPreviousCallback(IIoHandlerCallback callbackHandler) {
		super.setPreviousCallback(callbackHandler);
		getSuccessor().setPreviousCallback(ioEventHandler);
	}


	/**
	 * {@inheritDoc}
	 */
	public final void close(boolean immediate) throws IOException {
		if (!immediate) {
			hardFlush();
		}

		getSuccessor().close(immediate);
	}


	public boolean isSSLActivated() {
		return (mode.get() == Mode.SSL);
	}


    /**
     * {@inheritDoc}
     */
    @Override
    public int getPendingWriteDataSize() {
    	return outAppDataQueue.getSize() + super.getPendingWriteDataSize();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasDataToSend() {
    	return (!outAppDataQueue.isEmpty() || super.hasDataToSend());
    }

    
    
    @Override
    public void write(ByteBuffer[] buffers) throws ClosedChannelException, IOException {
    	outAppDataQueue.append(buffers);
	}
    
    @Override
    public void hardFlush() throws IOException {
    	flush();
    }

    
	/**
	 * {@inheritDoc}
	 */
	public void flush() throws IOException {
	
		if (mode.get() == Mode.SSL) {
			
			synchronized (outAppDataQueue) {
				if (!outAppDataQueue.isEmpty()) {
					sslProcessor.addOutAppData(outAppDataQueue.drain());
				} 
			}
			sslProcessor.encrypt();

		} else if ((mode.get() == Mode.OFF) || (mode.get() == Mode.PLAIN_OUTGOING_SSL_INCOMING)) {
			synchronized (outAppDataQueue) {
				if (!outAppDataQueue.isEmpty()) {
					ByteBuffer[] data = outAppDataQueue.drain();
					getSuccessor().write(data);
				}
			}
			getSuccessor().flush();
		}
	}


	/**
	 * set mode to plain write and encrypted read
	 *
	 * @return true, if mode has been set
	 */
	public boolean preStartSecuredMode() {
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + getId() + "] switch to prestart secured mode (interpret incomming as SSL, writing plain outgoing plain)");
		}
		
		if (mode.get() == Mode.OFF) {
			mode.set(Mode.PLAIN_OUTGOING_SSL_INCOMING);
			return true;
	
		} else {
			LOG.warning("connection is already in ssl mode (mode=" + mode + "). Ignore (pre)startSecured Mode");
			return false;
		}
	}


	/**
	 * Return already received data to ssl handler (this data
	 * will be interpreted as encrypted data). <br>
	 *
	 * @param readQueue      the queue with already received data
	 * @throws IOException if an io exception occurs
	 */
	public void startSecuredMode(ByteBuffer[] data) throws IOException {
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + getId() + "] switch to secured mode (handle incomming and outgoing as SSL)");
		}

		if (mode.get() != Mode.PLAIN_OUTGOING_SSL_INCOMING) {
			LOG.warning("connection is not in non_receiving mode (mode=" + mode + ")");
			return;
		}
	
		mode.set(Mode.PRE_SSL2);

		if (data != null) {
			if (data.length > 0) {
				inNetDataQueue.addFirst(data);
			}
		}

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + getId() + "] start ssl processor");
		}
		sslProcessor.start();
		
		mode.set(Mode.SSL);
		
		hardFlush();

		readIncomingEncryptedData(inNetDataQueue.drain());
	}



	public void onHandshakeFinished() throws IOException {
		hardFlush();
	}




	private void readIncomingEncryptedData(ByteBuffer[] inNetDataList) throws ClosedChannelException, IOException {
		if (inNetDataList != null) {
			if (LOG.isLoggable(Level.FINE)) {
				int size = 0;
				for (ByteBuffer buffer : inNetDataList) {
					size += buffer.remaining();
				}

				LOG.fine("received " + size + " bytes encrypted data");
			}

			sslProcessor.decrypt(inNetDataList);
		}
	}


	public void onSSLProcessorClosed() throws IOException {
		close(true);
	}

	
	/**
	 * has to be called within a synchronized context
	 */
	public void onDataDecrypted(ByteBuffer decryptedBuffer) {
		
		if ((decryptedBuffer == null) || !decryptedBuffer.hasRemaining()) {
			return;
		}

		getPreviousCallback().onData(new ByteBuffer[] { decryptedBuffer });
	}

	
	public void onPostDataDecrypted() {
		getPreviousCallback().onPostData();		
	}
	

	public void onDataEncrypted(ByteBuffer plainData, ByteBuffer encryptedData) throws IOException {
		
		if (encryptedData.hasRemaining()) {
			pendingWriteMap.add(plainData, encryptedData);
		}
		
		synchronized (outNetDataQueue) {
			outNetDataQueue.append(encryptedData);
		}
	}

	
	
	public void onPostDataEncrypted() throws IOException {
		
		synchronized (outNetDataQueue) {
			ByteBuffer[] data = outNetDataQueue.drain();
			
			if (LOG.isLoggable(Level.FINE)) {
				int size = 0;
				for (ByteBuffer buffer : data) {
					size += buffer.remaining();
				}
				
				LOG.fine("sending out app data (" + size + ")");
			}

			getSuccessor().write(data);
		}
		getSuccessor().flush();
	}
	
	


	private final class IOEventHandler implements IIoHandlerCallback {


		public void onData(ByteBuffer[] data) {
			try {
				
				Mode md = mode.get();
				
				if (md == Mode.OFF) {
					getPreviousCallback().onData(data);
					getPreviousCallback().onPostData();

				} else if (md == Mode.SSL) {				
					readIncomingEncryptedData(data);

				} else {
					assert (md == Mode.PLAIN_OUTGOING_SSL_INCOMING) || (md == Mode.PRE_SSL2);
					inNetDataQueue.append(data);
					return;
				}
			} catch (Exception e) {
	 			if (LOG.isLoggable(Level.FINE)) {
	 				LOG.fine("[" + getId() + "] error occured while receiving data. Reason: " + e.toString());
	 			}
			}
		}
		
		public void onPostData() {
			
		}

		public void onConnect() {
			getPreviousCallback().onConnect();
		}


		public void onWriteException(IOException ioException, ByteBuffer data) {
			getPreviousCallback().onWriteException(ioException, data);
		}
		


		public void onWritten(ByteBuffer data) {
			ByteBuffer plainData = pendingWriteMap.getPlainIfWritten(data);
			if (plainData != null) {
				getPreviousCallback().onWritten(plainData);
			} else{
				getPreviousCallback().onWritten(data);
			}
		}

		public void onDisconnect() {
			//getSSLProcessor().destroy();
			getPreviousCallback().onDisconnect();
		}

		public void onConnectionAbnormalTerminated() {
			getPreviousCallback().onConnectionAbnormalTerminated();
		}
	}


	private static final class PendingWriteMap {

		private Map<ByteBuffer, List<ByteBuffer>> plainEncryptedMapping = new IdentityHashMap<ByteBuffer, List<ByteBuffer>>();
		private Map<ByteBuffer, ByteBuffer> encryptedPlainMapping = new IdentityHashMap<ByteBuffer, ByteBuffer>();


		public synchronized void add(ByteBuffer plain, ByteBuffer encrypted) {
			// ignore system data (plain is empty)
			if (plain.limit() > 0) {
				List<ByteBuffer> encryptedList = plainEncryptedMapping.get(plain);
				if (encryptedList == null) {
					encryptedList = new ArrayList<ByteBuffer>();
					plainEncryptedMapping.put(plain, encryptedList);
				}

				encryptedList.add(encrypted);
				encryptedPlainMapping.put(encrypted, plain);
			}
		}


		public synchronized ByteBuffer getPlainIfWritten(ByteBuffer encrypted) {
			ByteBuffer plain = encryptedPlainMapping.remove(encrypted);
			if (plain != null) {
				List<ByteBuffer> encryptedList = plainEncryptedMapping.get(plain);
				encryptedList.remove(encrypted);
				if (encryptedList.isEmpty()) {
					plainEncryptedMapping.remove(plain);
					return plain;
				}
			}
			return null;
		}

		public synchronized void clear() {
			plainEncryptedMapping.clear();
			encryptedPlainMapping.clear();

		}
	}
}
