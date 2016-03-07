package org.xsocket.server.imap.store;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;



public final class ImapStore {

	private final Map<String, ImapFolder> folders = new HashMap<String, ImapFolder>(); 
	
	
	public ImapStore(ImapFolder... flds) {
		for (ImapFolder folder : flds) {
			folders.put(folder.getName(), folder);
		}
	}
	
	public ImapFolder getMailbox(String userId, String mailboxname) {
		return folders.get(mailboxname);
	}
	
	public Collection<ImapFolder> getMailboxes(String account, String referenceName, String mailboxName) {
		return folders.values();
	}
	
}
