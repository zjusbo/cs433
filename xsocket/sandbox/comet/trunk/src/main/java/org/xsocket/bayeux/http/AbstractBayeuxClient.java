package org.xsocket.bayeux.http;

import java.util.EventListener;
import java.util.concurrent.CopyOnWriteArraySet;


import dojox.cometd.Client;
import dojox.cometd.Listener;




abstract class AbstractBayeuxClient implements Client {
	
	private final CopyOnWriteArraySet<EventListener> listeners = new CopyOnWriteArraySet<EventListener>();


	public final void addListener(EventListener listener) {
		listeners.add(listener);
	}
	
	public final void setListener(Listener listener) {
		listeners.add(listener);
	}
	
	public final Listener getListener() {
		if (listeners.isEmpty()) {
			return null;
		}
		return (Listener) listeners.iterator().next();
	}
	
	public final void removeListener(EventListener listener) {
		listeners.remove(listener);
	}		
}
