package org.xsocket.bayeux.http;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.xsocket.connection.http.HttpResponse;
import org.xsocket.connection.http.IHttpExchange;



final class BayeuxHttpServerConnection {

	
	private Stack<IHttpExchange> exchangeStack = new Stack<IHttpExchange>();

	private List<MessageImpl> outQueue = new ArrayList<MessageImpl>();
	
	private boolean isJsonCommentFiltered = false;
	private boolean isResponseCommited = false;

	
	final void sendMessage(MessageImpl message, boolean isJsonCommentFiltered) {
		this.isJsonCommentFiltered = this.isJsonCommentFiltered || isJsonCommentFiltered;
		
		synchronized (outQueue) {
			outQueue.add(message);
		}
	}
	
	
	private void addrExchange(IHttpExchange exchange) {
		exchangeStack.push(exchange);
	}

	
	void flush(IHttpExchange exchange) {
		assert (!isResponseCommited) : "response has alreday been committed";

		List<MessageImpl> messages = null;
		synchronized (outQueue) {
			if (!outQueue.isEmpty()) {
				messages = outQueue;
				outQueue = new ArrayList<MessageImpl>();
			}
		}
		
		if (messages != null) {
			send(messages, exchange);
			isResponseCommited = true;
		}
	}	
	

	private void send(List<MessageImpl> messages, IHttpExchange exchange) {
		
		String contentType = null;
		if (isJsonCommentFiltered) {
			contentType = "text/json-comment-filtered; charset=utf-8";
		} else {
			contentType = "text/json";
		}
		
		StringBuilder sb = new StringBuilder();
		for (MessageImpl bayeuxMessage : messages) {
			sb.append(bayeuxMessage.toString() + ", ");
		}
		String resString = sb.toString().trim();
		if (resString.length() > 0)
		resString = "[" + resString.substring(0, resString.length() - 1) + "]";
		if (isJsonCommentFiltered) {
			resString = "/*" + resString + "*/";
		}
		
		try {
			HttpResponse response = new HttpResponse(200, contentType, resString);
			exchange.send(response);
		} catch (IOException ioe) {
			throw new RuntimeException(ioe.toString());
		}
	}
}
