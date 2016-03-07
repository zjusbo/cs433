package org.xsocket.bayeux.http;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.Execution;
import org.xsocket.connection.http.IHttpResponse;
import org.xsocket.connection.http.IHttpResponseHandler;
import org.xsocket.connection.http.InvokeOn;
import org.xsocket.connection.http.NonBlockingBodyDataSource;
import org.xsocket.connection.http.PostRequest;
import org.xsocket.connection.http.client.HttpClient;
import org.xsocket.connection.http.client.HttpClientConnection;
import org.xsocket.connection.http.client.IHttpClientEndpoint;

import dojox.cometd.Bayeux;
import dojox.cometd.Client;
import dojox.cometd.Message;




final class BayeuxRemoteClient extends AbstractBayeuxClient implements Closeable {
	
	private static final Logger LOG = Logger.getLogger(BayeuxRemoteClient.class.getName());


	private static final DecimalFormat VERSION_FORMAT = (DecimalFormat) DecimalFormat.getNumberInstance(Locale.US);
	static {
		VERSION_FORMAT.applyPattern("0.0");
	}
		
	
		
	private static final double REQUESTED_BAYEUX_VERSION = 1.0;
	private static final double MINIMUM_BAYEUX_VERSION = 1.0; 
	
	private HttpClient httpClient = null;

	private URL url = null;
	private String clientId = null;
	private String bayeuxVersion = null;
	private String[] serverSupportedConnectionypes = new String[0];
	private final Map<String, String> serverAdvices = new HashMap<String, String>();
	
	private final Object serverToClientGuard = new Object(); 
	private boolean isSubscribed = false;
	private ServerToClientEventHandler serverToClientEventHandler = null;
	
	private int requestId = 0;
	
	
	public BayeuxRemoteClient(URL url) throws UnknownHostException {
		this.url = url;
		httpClient = new HttpClient();
	}


	/**
	 * blocking open
	 */
	public void open() throws IOException {
		
		BlockingResultHandler resultHandler = new BlockingResultHandler();
		open(resultHandler);
		
		resultHandler.getResult();
	}
	
	
	/**
	 * non blocking open
	 */
	public void open(final IResultHandler resultHandler) throws IOException {
		
		performHandshake(resultHandler);		
	}

	
	
	public void subscribe(String toChannel) {
		BlockingResultHandler resultHandler = new BlockingResultHandler();
		subscribe(toChannel, resultHandler);
		resultHandler.getResult();
	}
	
	
	/**
	 * non blocking open
	 */
	public void subscribe(String toChannel, final IResultHandler resultHandler) {
		
		
		IResultHandler resHdl = new IResultHandler() {
			
			public void onResult(Object result) {

				synchronized (serverToClientGuard) {
					if (!isSubscribed) {
						serverToClientEventHandler = new ServerToClientEventHandler();
						isSubscribed = true;
					}
				}
				
				resultHandler.onResult(result);
				
			}
			
			public void onException() {
				resultHandler.onException();
			}			
		};
		
		
		try {
			performSubscribe(toChannel, resHdl);
		} catch (IOException ioe) {
			throw new RuntimeException(ioe.toString());
		}
	}

	
	
	void performHandshake(final IResultHandler resultHandler) throws IOException {
		final String reqId = Integer.toString(requestId++);
		
		MessageImpl msg = MessageImpl.newInstance(Bayeux.META_HANDSHAKE);
		msg.setId(reqId);
		msg.setVersion(VERSION_FORMAT.format(REQUESTED_BAYEUX_VERSION));
		msg.setMinimumVersion(VERSION_FORMAT.format(MINIMUM_BAYEUX_VERSION));
		msg.setSupportedConnectionTypes("long-polling");
		
		PostRequest request = newPostRequest(msg);
	
		BayeuxResponseHandler responseHandler = new BayeuxResponseHandler() {
			
			@Override
			public void onMessages(List<MessageImpl> messages) throws IOException {
				for (MessageImpl message : messages) {
					
					if (message.getChannel().equals(Bayeux.META_HANDSHAKE)) {
						
						if (message.isSuccessful()) {
							// mandatory params
							double version = Double.parseDouble(message.getVersion());
							clientId = message.getClientId();
							serverSupportedConnectionypes = message.getSupportedConnectionTypes();
							
							// optional params
							// minimum version
							// advice
							// ext
							String requestId = message.getId();
							if (requestId != null) {
								if (!reqId.equals(requestId)) {
									System.out.println("error occured");
								}
							}
							

							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("handeshake response received " + requestId);
							}

							performConnect(httpClient, resultHandler);
														
						} else {
							// Handeshake failed
						}
						
						
					} else {
						// LOG illegal message
					}
				}				
			}
			
			public void onException(IOException ioe) {
				// TODO Auto-generated method stub
				
			}
			
		};
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("sending handeshake request " + reqId);
		}
		
		httpClient.send(request, responseHandler);
	}
	
	
	
	void performConnect(IHttpClientEndpoint clientEndpoint, final IResultHandler resultHandler) throws IOException {
		
		
		final String reqId = Integer.toString(requestId++);
		
		MessageImpl msg = MessageImpl.newInstance(Bayeux.META_CONNECT);
		msg.setId(reqId);
		msg.setClientId(clientId);
		
		PostRequest request = newPostRequest(msg);
		
	
		BayeuxResponseHandler responseHandler = new BayeuxResponseHandler() {
			
			@Override
			public void onMessages(List<MessageImpl> messages) throws IOException {
				for (MessageImpl message : messages) {
					
					if (message.getChannel().equals(Bayeux.META_CONNECT)) {
						
						
						if (message.isSuccessful()) {
							// mandatory params
							String cid = message.getClientId();
							if (cid != null) {
								if (!cid.equals(clientId)) {
									System.out.println("error occured");
								}
							}

							
							// optional params
							// error
							// advice
							// ext
							String requestId = message.getId();
							if (requestId != null) {
								if (!reqId.equals(requestId)) {
									System.out.println("error occured");
								}
							}
							
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("connect response received " + requestId);
							}

							
							resultHandler.onResult(true);
							
						} else {
							// Handeshake failed
						}
						
						
					} else {
						// LOG illegal message
					}
				}
			}
			
			

			public void onException(IOException ioe) {
				// TODO Auto-generated method stub
				
			}
		};
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("sending connect request " + reqId);
		}
		
		clientEndpoint.send(request, responseHandler);		
	}

	
	

	void performSubscribe(final String toChannel, final IResultHandler resultHandler) throws IOException {
		
		final String reqId = Integer.toString(requestId++);
		
		MessageImpl msg = MessageImpl.newInstance(Bayeux.META_SUBSCRIBE);
		msg.setId(reqId);
		msg.setClientId(clientId);
		msg.setSubscription(toChannel);
		
		PostRequest request = newPostRequest(msg);
		
	
		BayeuxResponseHandler responseHandler = new BayeuxResponseHandler() {
			
			@Override
			public void onMessages(List<MessageImpl> messages) throws IOException {
				for (MessageImpl message : messages) {
					
					if (message.getChannel().equals(Bayeux.META_SUBSCRIBE)) {
						
						if (message.isSuccessful()) {
							// mandatory params
							
							clientId = message.getClientId();
							String sub = message.getSubscription();
							
							if (!sub.equals(toChannel)) {
								System.out.println("error");
							}
							
							// optional params
							String requestId = message.getId();
							if (requestId != null) {
								if (!reqId.equals(requestId)) {
									System.out.println("error occured");
								}
							}
							

							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("subscribe response (" + toChannel + ") received " + requestId);
							}
							
							resultHandler.onResult(true);
														
						} else {
							// Handeshake failed
						}
						
						
					} else {
						// LOG illegal message
					}
				}				
			}
			
			public void onException(IOException ioe) {
				// TODO Auto-generated method stub
				
			}
			
		};
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("sending subscribe (" + toChannel + ") request " + reqId);
		}
		
		httpClient.send(request, responseHandler);
	}
	
	
	
	void performPublish(String toChannel, String jsonSerialized) throws IOException {

		final String reqId = Integer.toString(requestId++);
		
		MessageImpl msg = MessageImpl.newInstance(toChannel);
		msg.setId(reqId);
		msg.setData(jsonSerialized);
		
		PostRequest request = newPostRequest(msg);
	
	
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("sending handeshake request " + reqId);
		}
		
//		httpClient.send(request, responseHandler);
	}
	
	
	
	private PostRequest newPostRequest(MessageImpl... messages) throws IOException {
		String serializedMessages = MessageImpl.serialize(true, messages);
		return new PostRequest(url.toString(), "application/x-www-form-urlencoded; charset=UTF-8", "message=" + serializedMessages);
	}
	
	
	


	public void close() throws IOException {

		httpClient.close();
	}
	


	
	public String getId() {
		return clientId;
	}
	


	
	public boolean isLocal() {
		return false;
	}

	
	
	public void deliver(Client from, Message message) {
		// TODO Auto-generated method stub
		
	}
	


	public void deliver(Client from, String toChannel, Object data, String id) {
		// TODO Auto-generated method stub
		
	}
	


	public void startBatch() {
		// TODO Auto-generated method stub
		
	}
	


	public void endBatch() {
		// TODO Auto-generated method stub
		
	}
	


	public void publish(String toChannel, Object data, String msgId) {

		//performSendEvent(toChannel, (String) data); 
		
	}
	




	public boolean hasMessages() {
		// TODO Auto-generated method stub
		return false;
	}
	


	public List<Message> takeMessages() {
		// TODO Auto-generated method stub
		return null;
	}
	


	public void unsubscribe(String toChannel) {
		// TODO Auto-generated method stub
		
	}	
	
	
	@Execution(Execution.NONTHREADED)
	@InvokeOn(InvokeOn.MESSAGE_RECEIVED)
	private static abstract class BayeuxResponseHandler implements IHttpResponseHandler {
		
		public void onResponse(IHttpResponse response) throws IOException {
			
			if (response.getContentType().toUpperCase().startsWith("TEXT/JSON")) {
				NonBlockingBodyDataSource body = response.getNonBlockingBody();
				String bodyString = body.readStringByLength(body.available());
				List<MessageImpl> messages = MessageImpl.deserialize(bodyString);
				onMessages(messages);
			} else {
				// TODO exception 
			}
			
		}
		
		public abstract void onMessages(List<MessageImpl> messages) throws IOException;
	}
	
	
	private static interface IResultHandler<T> {
		
		public void onResult(T result);
		
		public void onException();
	}
	
	
	
	private static final class BlockingResultHandler implements IResultHandler {
		
		private final Object waitGuard = new Object();
		private final AtomicReference<Object> result = new AtomicReference<Object>(null);
		
		
		public Object getResult() {
			Object res = null;

			synchronized (waitGuard) {
				do {
					res = result.get();
					if (res == null) {
						try {
							waitGuard.wait();
						} catch (InterruptedException ignore) { }
					}
				} while (res == null);
			}
			
			return res;
		}
		
		
		public void onResult(Object rslt) {
			synchronized (waitGuard) {
				result.set(rslt);
				waitGuard.notify();
			}
		}
		
		public void onException() {
			// TODO Auto-generated method stub
			
		}
	}
	
	
	
	

	private final class ServerToClientEventHandler implements IResultHandler {

		private HttpClientConnection con = null;
		
		ServerToClientEventHandler() {
			try {
				con = new HttpClientConnection(url.getHost(), url.getPort());
				avtivateConnection();
			} catch (IOException ioe) {
				throw new RuntimeException(ioe.toString());
			}
		}
		
		
		public void onResult(Object result) {

			avtivateConnection();
		}
		
		public void onException() {

			avtivateConnection();			
		}

		private void avtivateConnection() {
			try {
				performConnect(con, this);
			} catch (IOException ioe) {
				new RuntimeException(ioe.toString());
			}
		}
	}
}
