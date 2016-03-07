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
package org.xsocket.group;


import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;
import org.xsocket.connection.BlockingConnectionPool;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.ConnectionUtils;




/**
 * @author grro@xsocket.org
 */
final class GroupCall  {

	private static final Logger LOG = Logger.getLogger(GroupCall.class.getName());

	private static final byte PREPARE_MSG = 01;
	private static final byte COMMIT_MSG = 05;
	private static final byte ABORT_MSG = 06;
	private static final byte OK_RESPONSE = 55;
	private static final byte NOK_DIFFERENT_GROUPHASH_RESPONSE = 60;

	private static final int MAX_RETRIES = 3;

	private ByteBuffer transId = null;
	private int nextId = 0;

	private final BlockingConnectionPool connectionPool = new BlockingConnectionPool();

	private Executor executor = null;
	private long callTimeoutMillis = 0;


	GroupCall(Executor executor, int transactionIdPrefix, long callTimeoutMillis) {
		this.executor = executor;
		this.callTimeoutMillis = callTimeoutMillis;

		transId = ByteBuffer.allocate(8);
		transId.putInt(transactionIdPrefix);
		transId.putInt(0);
		
		connectionPool.setPooledIdleTimeoutSec(60);
	}


	public int execute(Set<String> addresses, final int groupHash, final int groupSize, final List<ByteBuffer> data) throws IOException {
		int size = 0;
		for (ByteBuffer buffer : data) {
			size += buffer.remaining();
		}

		final long transactionNumber = nextTID();


		try {

			// prepare
			sendPrepareToAll(transactionNumber, addresses, groupHash, groupSize, data);

			// commit
			sendDecisionToAll(transactionNumber, addresses, COMMIT_MSG, callTimeoutMillis, MAX_RETRIES);

		// on error abort
		} catch (InconsistentGroupStateException igse) {
			sendDecisionToAll(transactionNumber, addresses, ABORT_MSG, callTimeoutMillis, MAX_RETRIES);
			throw igse;

		} catch (SocketTimeoutException toe) {
			sendDecisionToAll(transactionNumber, addresses, ABORT_MSG, callTimeoutMillis, MAX_RETRIES);
			throw toe;

		} catch (IOException ioe) {
			sendDecisionToAll(transactionNumber, addresses, ABORT_MSG, callTimeoutMillis, MAX_RETRIES);
			throw ioe;
		}


		return size;
	}

	private long nextTID() {
		long id = 0;
		synchronized (transId) {
			transId.position(4);
			transId.putInt(++nextId);
			transId.flip();

			id = transId.getLong();
		}

		return id;
	}


	public void close() {
		connectionPool.close();
	}




	private void sendPrepareToAll(final long transactionNumber, Set<String> addresses, final int groupHash, final int groupSize, final List<ByteBuffer> data) throws InconsistentGroupStateException, SocketTimeoutException, IOException {

		boolean isGroupConsistent = true;
		boolean timeoutExceptionOccured = false;
		Exception exception = null;


		List<FutureTask<Boolean>> prepareCalls = new ArrayList<FutureTask<Boolean>>();

		for (final String address : addresses) {
			FutureTask<Boolean> call = new FutureTask<Boolean>(new Callable<Boolean>() {
				public Boolean call() throws Exception {
//					return sendPrepare(transactionNumber, guid.getAddress().getAddress(), guid.getAddress().getPort(), groupHash, groupSize, data, callTimeoutMillis);
					return null;
				}

			});
			prepareCalls.add(call);
			executor.execute(call);
		}


		long maxTime = System.currentTimeMillis() + callTimeoutMillis;

		for (FutureTask<Boolean> call : prepareCalls) {

			long waitTime = (maxTime - System.currentTimeMillis());
			if (waitTime < 0) {
				throw new SocketTimeoutException("timeout reached");
			}

			try {

				boolean isConsistent = call.get(waitTime, TimeUnit.MILLISECONDS);
				if (!isConsistent) {
					isGroupConsistent = false;
				}

			} catch (TimeoutException toe) {
				timeoutExceptionOccured = true;

			} catch (Exception ie) {
				exception = ie;
			}
		}


		if (!isGroupConsistent) {
			throw new InconsistentGroupStateException("group is inconsistent");

		} else if (timeoutExceptionOccured) {
			throw new SocketTimeoutException("timeout occured (" + DataConverter.toFormatedDuration(callTimeoutMillis) + ")");

		} else if (exception != null) {
			throw new IOException("unknown error occured " + exception.getMessage());
		}
	}


	private boolean sendPrepare(long transactionNumber, InetAddress address, int port, int groupHash, int groupsize, List<ByteBuffer> data, int receiveTimeout) throws IOException {

		ByteBuffer[] buffers = new ByteBuffer[data.size()];
		for (int i = 0; i < buffers.length; i++) {
			buffers[i] = data.get(i).duplicate();
		}

		IBlockingConnection connection = connectionPool.getBlockingConnection(address, port);
		connection.setAutoflush(false);
		connection.setReceiveTimeoutMillis(receiveTimeout);

		try {
			connection.markWritePosition();
			connection.write(0);

			int written = 0;
			written += connection.write(transactionNumber);
			written += connection.write(PREPARE_MSG);
			written += connection.write(groupHash);
			written += connection.write(groupsize);
			written += connection.write(buffers);
			connection.resetToWriteMark();
			connection.write(written);

			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("sending prepare (transaction " + transactionNumber + ") to " + address + ":" + port);
			}
			connection.flush();


			byte responseCode = connection.readByte();
			connection.close();

			switch (responseCode) {
			case OK_RESPONSE:
				return true;

			case NOK_DIFFERENT_GROUPHASH_RESPONSE:
				return false;

			default:
				throw new IOException("protocol error. got unknown response code " + responseCode);
			}


		} catch (Exception e) {
			connectionPool.destroy(connection);
			throw new IOException(e.toString());
		}
	}


	private void sendDecisionToAll(final long transactionNumber, Set<String> targets, final byte decision, final long receiveTimeout, final int maxRetries) {

		for (final String address : targets) {
			Runnable decisionTask = new Runnable() {
				public void run() {
	//				sendDecision(transactionNumber, guid.getAddress().getAddress(), guid.getAddress().getPort(), decision, receiveTimeout, maxRetries);
				}
			};

			executor.execute(decisionTask);
		}
	}



	private void sendDecision(long transactionNumber, InetAddress address, int port, byte decision, int receiveTimeout, int maxRetries) {

		for (int i = 0; i < maxRetries; i++) {
			IBlockingConnection connection = null;

			try {
				connection = connectionPool.getBlockingConnection(address, port);
				connection.setAutoflush(false);
				connection.setReceiveTimeoutMillis(receiveTimeout);

				connection.write(transactionNumber);
				connection.write(decision);
				connection.flush();

				byte responseCode = connection.readByte();
				connection.close();

				if (responseCode == OK_RESPONSE) {
					return;
				}

			} catch (Exception e) {
				try {
					connectionPool.destroy(connection);
				} catch (IOException ignore) { }
			}

			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("decision call (transaction " + transactionNumber + ") for to " + address + ":" + port + " failed (" + (i + 1) + " trial)");
			}
		}
	}



	public void handleCall(INonBlockingConnection connection, int groupHash, ArrayList<ByteBuffer> receiveQueue) throws IOException, BufferUnderflowException {
		int length = ConnectionUtils.validateSufficientDatasizeByIntLengthField(connection);

		long transactionNumber = connection.readLong();
		byte cmd = connection.readByte();
		length = length - 8 - 1;

		switch (cmd) {
		case PREPARE_MSG:
			handlePrepare(connection, length, transactionNumber, groupHash, receiveQueue);
			break;

		case COMMIT_MSG:
			handleCommit(connection, transactionNumber);
			break;

		case ABORT_MSG:
			handleAbort(connection, transactionNumber);
			break;


		default:
			connection.readByteBufferByLength(length);
			throw new IOException("protocol error. Unknown commond received: " + cmd + "(msg length=" + length + ")");
		}
	}


	private void handlePrepare(INonBlockingConnection connection, int length, long transactionNumber, int groupHash, ArrayList<ByteBuffer> receiveQueue) throws IOException {
		int receivedGroupHash = connection.readInt();
		int receivedGroupSize = connection.readInt();
		ByteBuffer[] buffers = connection.readByteBufferByLength(length - 4 - 4);

		if (receivedGroupHash == groupHash) {
			synchronized (receiveQueue) {
				for (ByteBuffer buffer : buffers) {
					receiveQueue.add(buffer);
				}
			}

			connection.write(OK_RESPONSE);

		} else {
			connection.write(NOK_DIFFERENT_GROUPHASH_RESPONSE);
		}
	}


	private void handleCommit(INonBlockingConnection connection, long transactionNumber) throws IOException {

		connection.write(OK_RESPONSE);
	}


	private void handleAbort(INonBlockingConnection connection, long transactionNumber) throws IOException {

		connection.write(OK_RESPONSE);
	}

}
