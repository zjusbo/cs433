package org.xsocket.group;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;
import org.xsocket.IDataSink;
import org.xsocket.stream.BlockingConnectionPool;
import org.xsocket.stream.IBlockingConnection;


final class InternalGroupMessage {

	private static final Logger LOG = Logger.getLogger(InternalGroupMessage.class.getName());
	
	
	static final byte ACK = 0;
	static final byte CALL = 10;
	static final byte VOTE_COMMIT = 10;
	static final byte VOTE_ABORT = 11;
	static final byte GLOBAL_ABORT = 12;
	static final byte GLOBAL_COMMIT = 13;
	
	
	private BlockingConnectionPool connectionPool = null;
	private Executor workerPool = null;

	
	public InternalGroupMessage(Executor workerPool, BlockingConnectionPool connectionPool) {
		this.workerPool = workerPool;
		this.connectionPool = connectionPool;
	}
	
	
	/**
	 * send message to all member of the group
	 * 
	 * @param message         the message
	 * @param viewId          the view id
	 * @param timeoutMillis 
	 * @throws IOException
	 * @throws SocketTimeoutException
	 */
	public void send(Message message, int viewId, long timeoutMillis) throws IOException, SocketTimeoutException {
		
		boolean commitVote = sendPrepare(message, viewId, timeoutMillis);
		
		if (commitVote) {
	//		sendVote(GLOBAL_COMMIT, message.getDestinationAddresses(), timeoutMillis);
		} else {
	//		sendVote();
		}
	}

	
	private boolean sendPrepare(final Message message, int viewId, long timeoutMillis) {
		
		// create send tasks
		List<Callable<Boolean>> messageTasks = new ArrayList<Callable<Boolean>>();
		for (Address destinationAddress : message.getDestinationAddresses()) {
			MessageSendTask messageTask = new MessageSendTask(CALL, destinationAddress, viewId, timeoutMillis) {
				@Override
				protected int writeDate(IDataSink dataSink) throws IOException {
					return message.writeTo(dataSink);
				}
			};
			messageTasks.add(messageTask);
		}
		
		// send it all
		send(messageTasks);
		
		return true;
	}
	
	
	

	private void sendVote(byte vote, Set<Address> destinations, int msgId, long timeoutMillis) {
		
	/*	// create send tasks
		List<Callable<Boolean>> voteTasks = new ArrayList<Callable<Boolean>>();
		for (Address destinationAddress : message.getDestinationAddresses()) {
			MessageSendTask voteTask = new MessageSendTask(vote, destinationAddress, viewId, timeoutMillis) { };
			voteTasks.add(voteTask);
		}
		
		// send it all
		send(voteTasks);*/
	}
	
	
	private String send(List<Callable<Boolean>> tasks) {
		return null;
/*		String errors = null;
		
		List<Future<Boolean>> results = null;
		
		long start = System.currentTimeMillis();
		try { 
			results = workerPool.invokeAll(tasks);
		} catch (InterruptedException timoutEx) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("timeout has been reached");
			}
			errors = "timout hasbeen reached"; 
		}
		long elapsed = System.currentTimeMillis() - start;

		int successCount = 0;
		for (Future<Boolean> result : results) {
			try {
				if (!result.isCancelled()) {
					try {
						if (result.get().booleanValue()) {
							successCount++;
						}
					} catch (InterruptedException ignore) { }
				}
			} catch (ExecutionException ignore) { }
		}
		
		if (successCount < tasks.size()) {
			String errorMessage = "error occured by sending (only " + successCount + " of " + tasks.size() + " has been sent successfully. elapsed time= " + elapsed + ")";
			if (LOG.isLoggable(Level.INFO)) {
				LOG.info(errorMessage);
			}
	//		return false;
			
		} else {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("message has been sent to " + tasks.size() + " peers. elapsed time= " + elapsed + ")");
			}
	//		return true;
		}
		
		return errors;*/
	}
	
	
	
	
	
	private abstract class MessageSendTask implements Callable<Boolean> {
			
		private byte callType = 0;
		private Address peerAddress = null;
		private int viewId = 0;
		private long timeoutMillis = 0;
		
		
		MessageSendTask(byte callType, Address peerAddress, int viewId, long timeoutMillis) {
			this.callType = callType;
			this.peerAddress = peerAddress;
			this.viewId = viewId;
			this.timeoutMillis = timeoutMillis;
		}
		
		
		public Boolean call() throws Exception {
			try {
				IBlockingConnection connection = connectionPool.getBlockingConnection(peerAddress.getAddress(), peerAddress.getPort());
				
				if (connection != null) {
					try {
						connection.setReceiveTimeoutMillis(timeoutMillis);

						connection.markWritePosition();
						connection.write((int) 0);
						
						int written = connection.write(callType);
						written += connection.write(viewId);
						written += writeDate(connection);
						
						connection.resetToWriteMark();
						connection.write(written);
						
						connection.flush();
						
						if (connection.readByte() == ACK) {
							connection.close();	
						} else {
							throw new IOException("error occured. didn't get ACK from " + peerAddress.toString());
						}
						
					} catch (IOException ioe) {
						try {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine(ioe.toString() + " occured by sending data to " + peerAddress.toString() + ". Destroy connection");
							}
							connectionPool.destroyConnection(connection);
						} catch (IOException ignore) { }
						throw ioe;
					}
					
				// .. didn't get a connection
				} else {
					throw new IOException("couldn'r establish a connection to " + peerAddress.toString());
				}
				return true;
				
			} catch (SocketTimeoutException receiveTimeoutException) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.info("timeout occured by getting ack for sent data to " + peerAddress.toString() + " (timeout=" + DataConverter.toFormatedDuration(timeoutMillis) + ")");
				}
				return false;
				
			} catch (IOException ioException) {
				if (LOG.isLoggable(Level.INFO)) {
					LOG.info("error occured by sending message to " + peerAddress.toString());
				}
				return false;
			}
		}
		
		protected int writeDate(IDataSink dataSink) throws IOException {
			return 0;
		}
	}		
}
