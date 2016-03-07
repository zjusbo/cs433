package org.xsocket.bayeux.http;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArraySet;

import dojox.cometd.Channel;
import dojox.cometd.Client;
import dojox.cometd.DataFilter;



final class ChannelImpl implements Channel {
		
	private final CopyOnWriteArraySet<Client> clients = new CopyOnWriteArraySet<Client>();
	private String channelId = null; 


	
	public ChannelImpl(String channelId) {
		this.channelId = channelId;
	}
	
	
	
	


	public Collection<DataFilter> getDataFilters() {
		// TODO Auto-generated method stub
		return null;
	}
	


	public DataFilter removeDataFilter(DataFilter arg0) {
		// TODO Auto-generated method stub
		return null;
	}
	


	public void addDataFilter(DataFilter arg0) {
		// TODO Auto-generated method stub
		
	}

	


	public boolean remove() {
		return true;
	}
		



	public String getId() {

		return null;
	}

    
    @SuppressWarnings("unchecked")
	void publish(Object data, String msgId) {
    	
    	for (Client client : clients) {
        	((BayeuxClientProxy) client).deliver(channelId, data, msgId);
		}
	}

    
    
	public void publish(Client fromClient, Object data, String msgId) {
		
	}
	
	    


    public boolean isPersistent() {
    	return false;
    }
	    
    


    public void setPersistent(boolean persistent) {
    	
    }
	    
    


    public void subscribe(Client subscriber) {
    	clients.add(subscriber);
    }


    
    public void unsubscribe(Client subscriber) {
		clients.remove(subscriber);
    }

    


    public Collection<Client> getSubscribers() {
    	// TODO Auto-generated method stub
    	return null;
    }
    
    
    @SuppressWarnings("unchecked")
    public String toString() {
    	
    	StringBuilder sb = new StringBuilder("[" + channelId + "] subscribers: ");
    	
    	for (Client client : clients) {
    		sb.append(client.getId() + " ");
		}
    	
    	return sb.toString().trim();
    }
    
}
