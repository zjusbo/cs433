package org.xsocket.server.imap;

import org.xsocket.server.imap.store.ImapFolder;
import org.xsocket.server.imap.store.ImapStore;



/**
 * command context  
 * 
 * @author grro@xsocket.org
 */
public final class CommandContext  {

	private ImapStore store = null;
	private String userId = null;
	private ImapFolder selected = null;

	public CommandContext(ImapStore store) {
		this.store = store;
	}

	
	public ImapStore getStore() {
		return store;
	}
	

	public void setSelected(ImapFolder folder) {
		this.selected = folder;
	}
	
	public ImapFolder getSelected() {
		return selected;
	}
	
	public String getUserId() {
		return userId;
	}
	
	public void setUserId(String userId) {
		this.userId = userId;
	}	
}