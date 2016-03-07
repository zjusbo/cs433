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
package org.xsocket.connection.spi;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;




/**
 * activateable SSL io handler
 *
 * @author grro@xsocket.org
 */
final class IoActivateableSSLHandler extends ChainableIoHandler implements SSLProcessor.EventHandler {


	private static final Logger LOG = Logger.getLogger(IoSSLHandler.class.getName());

	private enum Mode { OFF, PLAIN_OUTGOING_SSL_INCOMING, PRE_SSL2, SSL };

	private Mode mode = Mode.OFF;



	// receive & send queue
	private IoQueue inNetDataQueue = new IoQueue();
	private IoQueue outAppDataQueue = new IoQueue();

	// sync write support
	private final PendingWriteMap pendingWriteMap = new PendingWriteMap();


	// event handling
	private final IOEventHandler ioEventHandler = new IOEventHandler();

	// ssl stuff
	private SSLProcessor sslProcessor = null;




	/**
	 * constructor
	 *
	 * @param successor      the successor
	 * @param sslContext     the ssl context to use
	 * @param isClientMode   true, if is in client mode
	 * @param memoryManager  the memory manager to use
	 * @throws IOException If some other I/O error occurs
	 */
	IoActivateableSSLHandler(ChainableIoHandler successor, SSLContext sslContext,boolean isClientMode, IMemoryManager memoryManager) throws IOException {
		super(successor);

		sslProcessor = new SSLProcessor(sslContext, isClientMode, memoryManager, this);
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
			flushOutgoing();
		}

		getSuccessor().close(immediate);
	}


	public boolean isSSLActivated() {
		return (mode == Mode.SSL);
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




	/**
	 * {@inheritDoc}
	 */
	public final void write(ByteBuffer[] buffers) throws ClosedChannelException, IOException {
		outAppDataQueue.append(buffers);
		flushOutgoing();
	}

	/**
	 * {@inheritDoc}
	 */
	public void flushOutgoing() throws IOException {
		if (mode == Mode.SSL) {
			synchronized (sslProcessor) {
				if (sslProcessor.isHandshaking()) {
					sslProcessor.encrypt();

				} else {
					if (!outAppDataQueue.isEmpty()) {
						sslProcessor.encrypt(outAppDataQueue.drain());
					}
				}
			}


		} else if ((mode == Mode.OFF) || (mode == Mode.PLAIN_OUTGOING_SSL_INCOMING)) {
			ByteBuffer[] data = outAppDataQueue.drain();
			getSuccessor().write(data);
		}
	}


	/**
	 * set mode to plain write and encrypted read
	 *
	 * @return true, if mode has been set
	 */
	public boolean preStartSecuredMode() {
		if (mode == Mode.OFF) {
			mode = Mode.PLAIN_OUTGOING_SSL_INCOMING;
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
		if (mode != Mode.PLAIN_OUTGOING_SSL_INCOMING) {
			LOG.warning("connection is not in non_receiving mode (mode=" + mode + ")");
			return;
		}

		mode = Mode.PRE_SSL2;

		if (data != null) {
			if (data.length > 0) {
				inNetDataQueue.addFirst(data);
			}
		}

		sslProcessor.start();
		mode = Mode.SSL;

		flushOutgoing();

		readIncomingEncryptedData(inNetDataQueue.drain());
	}



	public void onHandshakeFinished() throws IOException {
		flushOutgoing();
	}




	private synchronized void readIncomingEncryptedData(ByteBuffer[] inNetDataList) throws ClosedChannelException, IOException {
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

	public void onDataDecrypted(ByteBuffer[] decryptedBufferList) {
		if (decryptedBufferList == null) {
			return;
		}

		if (decryptedBufferList.length == 0) {
			return;
		}

		getPreviousCallback().onData(decryptedBufferList);
	}


	public void onDataEncrypted(ByteBuffer plainData, ByteBuffer encryptedData) throws IOException {
		if (encryptedData.hasRemaining()) {
			pendingWriteMap.add(plainData, encryptedData);
			ByteBuffer[] buffers = new ByteBuffer[1];
			buffers[0] = encryptedData;
			getSuccessor().write(buffers);
		}
	}



	private final class IOEventHandler implements IIoHandlerCallback {


		public void onData(ByteBuffer[] data) {
			try {
				if (mode == Mode.OFF) {
					getPreviousCallback().onData(data);

				} else if (mode == Mode.SSL) {
					readIncomingEncryptedData(data);

				} else {
					assert (mode == Mode.PLAIN_OUTGOING_SSL_INCOMING) || (mode == Mode.PRE_SSL2);
					inNetDataQueue.append(data);
					return;
				}
			} catch (Exception e) {
	 			if (LOG.isLoggable(Level.FINE)) {
	 				LOG.fine("[" + getId() + "] error occured while receiving data. Reason: " + e.toString());
	 			}
			}
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


		public void onConnectionTimeout() {
			getPreviousCallback().onConnectionTimeout();
		}

		public void onIdleTimeout() {
			getPreviousCallback().onIdleTimeout();
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
