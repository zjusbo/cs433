package org.xsocket.bayeux.http;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.connection.http.HttpResponse;
import org.xsocket.connection.http.HttpResponseHeader;
import org.xsocket.connection.http.IHttpExchange;



import dojox.cometd.Bayeux;
import dojox.cometd.Channel;
import dojox.cometd.Client;
import dojox.cometd.DataFilter;
import dojox.cometd.Listener;
import dojox.cometd.Message;
import dojox.cometd.SecurityPolicy;


final class BayeuxBroker implements Bayeux {
	
	private static final Logger LOG = Logger.getLogger(BayeuxBroker.class.getName());

	
	enum ConnectionType { LongPolling,  CallbackPolling, IFrame, Flash }  


	private final HashMap<String, BayeuxClientProxy> remoteClients = new HashMap<String, BayeuxClientProxy>(); 
	private final HashMap<String, ChannelImpl> channels = new HashMap<String, ChannelImpl>();

	private int nextMsgId = 0;
	private final Object nextMsgIdGuard = new Object();

	
	// statistics
	private final MessageLog messageLog = new MessageLog(100);

	
	
	
	public Client newClient(String idprefix, Listener listener) {
		//ClientImpl client = new ClientImpl(idprefix + UUID.randomUUID().toString());
		//client.setListener(listener);
		//runningClients.put(client.getId(), client);
		
		//return client;
		return null;
	}
	
	
	public Client newClient(String arg0) {
		return null;
	}
	
	private void registerClient(BayeuxClientProxy client) {
		synchronized (remoteClients) {
			remoteClients.put(client.getId(), client);
		}
	}
	

	public Client getClient(String clientId) {
		synchronized (remoteClients) {
			return remoteClients.get(clientId);
		}
	}
	
	
	@SuppressWarnings("unchecked")
	public Collection<Client> getClients() {
		Set<Client> result = new HashSet<Client>();
		
		HashMap<String, BayeuxClientProxy> clientsCopy = null;
		synchronized (remoteClients) {
			clientsCopy = (HashMap<String, BayeuxClientProxy>) remoteClients.clone();
		}
		
		for (BayeuxClientProxy client : clientsCopy.values()) {
			result.add(client);
		}
		
		return result;
	}
	

	public boolean hasClient(String clientId) {
		synchronized (remoteClients) {
			return remoteClients.containsKey(clientId);
		}
	}
	
	public Client removeClient(String clientId) {
		synchronized (remoteClients) {
			return remoteClients.remove(clientId);
		}
	}

	
	public Channel getChannel(String channelId, boolean create) {
		
		synchronized (channels) {
			ChannelImpl channel = channels.get(channelId);
			if ((channel == null) && create) {
				channel = new ChannelImpl(channelId);
				channels.put(channelId, channel);
			}
			
			return channel;
		}
	}
	

	public Channel removeChannel(String channelId) {
		synchronized (channels) {
			return channels.remove(channelId);
		}
	}
	
	
	public boolean hasChannel(String channelId) {
		synchronized (channels) {
			return channels.containsKey(channelId);
		}
	}
	
	
	@SuppressWarnings("unchecked")
	public Collection<Channel> getChannels() {
		
		Set<Channel> result = new HashSet<Channel>();
		
		HashMap<String, ChannelImpl> channelsCopy = null;
		synchronized (channels) {
			channelsCopy = (HashMap<String, ChannelImpl>) channels.clone();
		}
		
		for (ChannelImpl channel :channelsCopy.values()) {
			result.add(channel);
		}
		
		return result;
	}
	

	
	public void publish(Client fromClient, String toChannel, Object data, String msgId) {

		messageLog.add("[" + new Date() + "] channel=" + toChannel + " from=" + fromClient + " data=" + data);
		ChannelImpl channel = (ChannelImpl) getChannel(toChannel, false);
		if (channel == null) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("message received for channel " + toChannel + " which not exists. Ignore message");
			}
			return;
		}
		channel.publish(data, msgId);
	}
	
	

	
	public void deliver(Client fromClient, Client toClient, String arg2, Message arg3) {
		
	}
	
	public void subscribe(String toChannel, Client subscriber) {
		Channel channel = getChannel(toChannel, true);
		channel.subscribe(subscriber);
	}
	
	
	public void unsubscribe(String toChannel, Client subscriber) {
		
		Channel channel = getChannel(toChannel, false);
		if (channel != null) {
			channel.unsubscribe(subscriber);
		}
	}

	
	public void removeFilter(String channels, DataFilter filter) {
		// TODO Auto-generated method stub
		
	}


	
	public void setSecurityPolicy(SecurityPolicy securityPolicy) {
		// TODO Auto-generated method stub
		
	}
	
	
	public SecurityPolicy getSecurityPolicy() {
		// TODO Auto-generated method stub
		return null;
	}
	
	
	public void addFilter(String channels, DataFilter filter) {
		// TODO Auto-generated method stub
		
	}
	
	
	public void onMessages(List<MessageImpl> messages, IHttpExchange exchange) {
		
		BayeuxClientProxy client = null; 
		List<MessageImpl> responseMessages = new ArrayList<MessageImpl>();
		
		// handle each message
		for (MessageImpl request : messages) {
			
			if (client == null) {
				synchronized (remoteClients) {
					client = remoteClients.get(request.getClientId());
				} 
			}
			
			if (client != null) {
				client.incMessageReceived();
			}
			
		
			// meta message
			if (request.isMetaMessage()) {
				
				if (request.getChannel().equals(Bayeux.META_HANDSHAKE)) {
					if (client == null) {
						client = new BayeuxClientProxy(this, "prx" + UUID.randomUUID().toString());
						registerClient(client);
					}
					
					MessageImpl response = MessageImpl.newInstance(request.getChannel());
					boolean successful = client.handleHandeshake(request, response);
					if (successful) {
						synchronized (remoteClients) {
							remoteClients.put(client.getId(), client);
						} 
					}
					
					responseMessages.add(response);
				
					
				} else if (request.getChannel().equals(Bayeux.META_CONNECT)) {
					// client has to be set
					if (client == null) {
						System.out.println("Cleint is null");
						throw new RuntimeException("client does not exists");
					}
					
					MessageImpl response = client.handleConnect(request);
					if (response != null) {
						responseMessages.add(response);
					} else {
						client.addExchange(exchange);
					}
					
					
				} else if (request.getChannel().equals(Bayeux.META_DISCONNECT)) {
					// client has to be set
					if (client == null) {
						System.out.println("Cleint is null");
					}
					
					MessageImpl response = client.handleDisconnect(request);
					if (response != null) {
						responseMessages.add(response);
					} else {
						client.addExchange(exchange);
					}
										
					
				} else if (request.getChannel().equals(Bayeux.META_SUBSCRIBE)) {
					// client has to be set
					if (client == null) {
						System.out.println("Cleint is null");
					}


					MessageImpl response = client.handleSubscribe(request);
					responseMessages.add(response);
					

				} else if (request.getChannel().equals(Bayeux.META_UNSUBSCRIBE)) {
					// client has to be set
					if (client == null) {
						System.out.println("Cleint is null");
					}


					MessageImpl response = client.handleUnsubscribe(request);
					responseMessages.add(response);
					
				} else {
					System.out.println("UNKNOWN META MESSAGE");
					
				}
				

			// event message
			} else {
				
				boolean isJsonCommentFiltered = false;
				if (client != null) {
					if (client.isJsonCommentFiltered()) {
						isJsonCommentFiltered = true;
					}
				}
				

				// mandatory params (spec 1.0draft1) 
				String data = request.getData();
					
				// optional params
				String id = request.getId();
				String clientId = request.getClientId();

				publish(client, request.getChannel(), data, generatedMessageId());
				
				
				// mandatory params
				MessageImpl response = MessageImpl.newInstance(request.getChannel());
				response.setChannel(request.getChannel());
				response.setSuccessful(true);
				
				
				// optional params
				if (clientId != null) {
					response.setClientId(clientId);
				} 
				if (id != null) {
					response.setId(id);
				}
				// error
				// advice
				// ext

				responseMessages.add(response);
			}
		}

		if (!responseMessages.isEmpty()) {
			boolean isJsonCommentFiltered = false;
			if (client != null) {
				isJsonCommentFiltered = client.isJsonCommentFiltered();
			}
			send(responseMessages, exchange, isJsonCommentFiltered);
		}
	}
	
	
	void send(List<MessageImpl> messages, IHttpExchange exchange, boolean isJsonCommentFiltered) {
		
		String contentType = null;
		if (isJsonCommentFiltered) {
			contentType = "text/json-comment-filtered; charset=utf-8";
		} else {
			contentType = "text/json";
		}
		
		String resString = MessageImpl.serialize(false, messages);
		if (isJsonCommentFiltered) {
			resString = "/*" + resString + "*/";
		}
		
		try {
			HttpResponseHeader responseHeader = new HttpResponseHeader(200, contentType);
			responseHeader.setHeader("Connection", "close");
			HttpResponse response = new HttpResponse(responseHeader, resString);
			exchange.send(response);
		} catch (IOException ioe) {
			throw new RuntimeException(ioe.toString());
		}
	}

	
	@SuppressWarnings("unchecked")
	List<String> getRegisteredClients() {
		List<String> result = new ArrayList<String>();
		
		
		Map<String, BayeuxClientProxy> remoteClientsCopy = null;
		synchronized (remoteClients) {
			remoteClientsCopy = (Map<String, BayeuxClientProxy>) remoteClients.clone();
		}

		result.addAll(remoteClientsCopy.keySet());
		return result;
	}
	


	

	List<String> getMessageLog() {
		return messageLog.getMessages();
	}
	
	int getMessageLogMaxSize() {
		return messageLog.getMaxSize();
	}
	
	void setMessageLogMaxSize(int maxSize) {
		messageLog.setMaxSize(maxSize);
	}
 
	
	private String generatedMessageId() {
		synchronized (nextMsgIdGuard) {
			int msgId = nextMsgId++;
			if (msgId < 0) {
				nextMsgId = 0;
				
				return generatedMessageId();
			}
			return Integer.toString(msgId);
		}
	}
	
	
	static ConnectionType resolve(String connectionType) {
		if (connectionType.equalsIgnoreCase("LONG-POLLING")) {
			return ConnectionType.LongPolling;
			
		} else if (connectionType.equalsIgnoreCase("CALLBACK-POLLING")) {
			return ConnectionType.CallbackPolling;
			
		} else if (connectionType.equalsIgnoreCase("IFRAME")) {
			return ConnectionType.IFrame;

		} else if (connectionType.equalsIgnoreCase("FLASH")) {
			return ConnectionType.Flash;
			
		} else {
			throw new RuntimeException("unsupported connectiontype " + connectionType);
		}
	}
	
	

	private static final class MessageLog  {
		
		private int maxSize = 0;
		private final LinkedList<String> messages = new LinkedList<String>(); 
		
		public MessageLog(int maxSize) {
			this.maxSize = maxSize;
		}
		
		int getMaxSize() {
			return maxSize;
		}
		
		void setMaxSize(int maxSize) {
			this.maxSize = maxSize;
		}

		void add(String msg) {
			messages.addFirst(msg);
			
			while (messages.size() > maxSize) {
				messages.removeLast();
			}
		}
	
		@SuppressWarnings("unchecked")
		List<String> getMessages() {
			return (List<String>) messages.clone();
		}
	}
}
