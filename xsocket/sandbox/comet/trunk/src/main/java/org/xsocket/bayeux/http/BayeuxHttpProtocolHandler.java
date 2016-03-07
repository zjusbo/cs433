package org.xsocket.bayeux.http;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


import org.xsocket.Execution;
import org.xsocket.connection.http.IHttpExchange;
import org.xsocket.connection.http.IHttpRequestHandler;
import org.xsocket.connection.http.InvokeOn;

import sun.security.action.GetBooleanAction;







public final class BayeuxHttpProtocolHandler implements IHttpRequestHandler {

	 
	private final BayeuxBroker bayeux = new BayeuxBroker();

	
	
	@Execution(Execution.NONTHREADED)
	@InvokeOn(InvokeOn.MESSAGE_RECEIVED)
	public void onRequest(IHttpExchange exchange) throws IOException {
		
		
		// parse the bayeux messages 
		String contentType = exchange.getRequest().getContentType().toUpperCase();
		
		
		
		
		String serializedMessages = null;
		
		if (contentType.startsWith("APPLICATION/X-WWW-FORM-URLENCODED")) {
			serializedMessages = exchange.getRequest().getParameter("message");
			
		} else if (contentType.startsWith("TEXT/JSON")) {
			 serializedMessages = exchange.getRequest().getBlockingBody().readString();
			 
		} else {
			exchange.sendError(400, "unsupported content type " + exchange.getRequest().getContentType());
			return;
		}
		
		
		if (serializedMessages == null) {
			// TODO send error response
			System.out.println("STOP");
		}
		
		List<MessageImpl> messages = MessageImpl.deserialize(serializedMessages.trim());
		
		// TODO
		// sort list with order: 1. first meta/    2. first meta/handshake, meta/connection ...
		

		
		// get the assigned bayeux connection
		BayeuxHttpServerConnection bayeuxConnection = (BayeuxHttpServerConnection) exchange.getConnection().getAttachment();
		if (bayeuxConnection == null) {
			bayeuxConnection = new BayeuxHttpServerConnection();
			exchange.getConnection().setAttachment(bayeuxConnection);
		}
		
		// handle messages 
		bayeux.onMessages(messages, exchange);
	}


	List<String> getChannels() {
		List<String> result = new ArrayList<String>();
		for (Object channel : bayeux.getChannels()) {
			result.add(channel.toString());
		}
		return result;
	}
	
	List<String> getClients() {
		List<String> result = new ArrayList<String>();
		for (Object client : bayeux.getClients()) {
			result.add(client.toString());
		}
		return result;
	}
	 
	int getOpenConnections() {
		int cons = 0;
		
		for (Object client : bayeux.getClients()) {
			cons += ((BayeuxClientProxy) client).getOpenConnectionsS2C();
		}
		return cons;		
	}
	 
	List<String> getMessageLog() {
		return bayeux.getMessageLog();
	}
	
	int getMessageLogMaxSize() {
		return bayeux.getMessageLogMaxSize();
	}
	
	void setMessageLogMaxSize(int maxSize) {
		bayeux.setMessageLogMaxSize(maxSize);
	}
	
	String getVersion() {
		return BayeuxUtils.getVersionInfo();
	}
}
