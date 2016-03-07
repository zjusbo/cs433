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
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.xsocket.ClosedException;



/**
 * SSL io handler
 *
 * @author grro@xsocket.org
 */
final class IoSSLHandler extends ChainableIoHandler implements SSLProcessor.EventHandler {

	private static final Logger LOG = Logger.getLogger(IoSSLHandler.class.getName());

	// receive & send queue
	private final IoQueue outAppDataQueue = new IoQueue();


	// sync write support
	private final PendingWriteMap pendingWriteMap = new PendingWriteMap();



	private final IOEventHandler ioEventHandler = new IOEventHandler();


	// ssl stuff
	private SSLProcessor sslProcessor = null;
	private boolean isClientMode = false;
	private boolean isSSLConnected = false;
	private final Object initGuard = new Object();

	
	private IOException readException = null;



	/**
	 * constructor
	 *
	 * @param successor      the successor
	 * @param sslContext     the ssl context to use
	 * @param isClientMode   true, if is in client mode
	 * @param memoryManager  the memory manager to use
	 * @throws IOException If some other I/O error occurs
	 */
	IoSSLHandler(ChainableIoHandler successor, SSLContext sslContext,boolean isClientMode, IMemoryManager memoryManager) throws IOException {
		super(successor);

		this.isClientMode = isClientMode;
		sslProcessor = new SSLProcessor(sslContext, isClientMode, memoryManager, this);
	}


	public void init(IIoHandlerCallback callbackHandler) throws IOException {
		setPreviousCallback(callbackHandler);
		getSuccessor().init(ioEventHandler);

		startSSL();
	}


	/**
	 * {@inheritDoc}
	 */
	public boolean reset() {
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
	 * start the ssl mode
	 *
	 * @throws IOException If some other I/O error occurs
	 */
	void startSSL() throws IOException {
		if (!isSSLConnected) {
			sslProcessor.start();
		}

		if (isClientMode) {
			synchronized (initGuard) {
				
				while (!isSSLConnected) {
					if (readException != null) {
						IOException ex = readException;
						readException = null;
						throw ex;
					}
					
					try {
						if (DefaultIoProvider.isDispatcherThread()) {
							LOG.warning("try to initialize ssl client within xSocket I/O thread (" + Thread.currentThread().getName() + "). This will cause a deadlock");
						}
						initGuard.wait();
					} catch (InterruptedException ignore) { }
				}
			}
		}
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



	/**
	 * {@inheritDoc}
	 */
	public final void write(ByteBuffer[] buffers) throws ClosedException, IOException {
		outAppDataQueue.append(buffers);
		flush();
	}

	private void flush() throws ClosedException, IOException {
		synchronized (sslProcessor) {
			if (!sslProcessor.isHandshaking()) {
				if (!outAppDataQueue.isEmpty()) {
					sslProcessor.encrypt(outAppDataQueue.drain());
				}
			} else {
				sslProcessor.encrypt();
			}
		}
	}


	/**
	 * {@inheritDoc}
	 */
	public void flushOutgoing() throws IOException {

	}




	private synchronized void readIncomingEncryptedData(ByteBuffer[] inNetDataList) throws ClosedException, IOException {
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


	public void onHandshakeFinished() throws IOException {
		if (!isSSLConnected) {
			if (LOG.isLoggable(Level.FINE)) {
				if (isClientMode) {
					LOG.fine("[" + getId() + "] handshake has been finished (clientMode)");
				} else {
					LOG.fine("[" + getId() + "] handshake has been finished (serverMode)");
				}
			}

			isSSLConnected = true;
			synchronized (initGuard) {
				initGuard.notifyAll();
			}

			getPreviousCallback().onConnect();
		}

		flush();
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

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("reading decryted data");
		}

		getPreviousCallback().onData(decryptedBufferList);
	}


	public void onDataEncrypted(ByteBuffer plainData, ByteBuffer encryptedData) throws IOException {

		if (encryptedData.hasRemaining()) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("writing encryted data");
			}

			pendingWriteMap.add(plainData, encryptedData);
			ByteBuffer[] buffers = new ByteBuffer[1];
			buffers[0] = encryptedData;
			getSuccessor().write(buffers);
		}
	}



	private final class IOEventHandler implements IIoHandlerCallback {


		public void onData(ByteBuffer[] data) {
			try {
				readIncomingEncryptedData(data);
			} catch (IOException ioe) {
	 			if (LOG.isLoggable(Level.FINE)) {
	 				LOG.fine("[" + getId() + "] error occured while receiving data. Reason: " + ioe.toString());
	 			}

	 			synchronized (initGuard) {
					readException = ioe;
					initGuard.notifyAll();
				}
			}
		}


		public void onConnect() {

		}

		public void onWriteException(IOException ioException, ByteBuffer data) {
			getPreviousCallback().onWriteException(ioException, data);
		}

	
		

		public void onWritten(ByteBuffer data) {
			ByteBuffer plainData = pendingWriteMap.getPlainIfWritten(data);
			if (plainData != null) {
				getPreviousCallback().onWritten(plainData);
			} else {
				// else case shouldn't occur, handle it nevertheless
				getPreviousCallback().onWritten(data);
			}
		}

		public void onDisconnect() {
			sslProcessor.destroy();
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
