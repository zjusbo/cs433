package org.xsocket.server.imap.store;


import javax.mail.Flags;




public final class ImapFolder  {

	private String name = null;
	 
	public ImapFolder(String name) {
		this.name = name;
	}

	public int getMessageCount() {
		return 4;
	}
	
	public int getUnreadMessageCount() {
		return 3;
	}
	
	
	public long getUIDValidity() {
		return 1;
	}
	
	public boolean hasInferiors() {
		return false;
	}
	
	public Flags getPermanentFlags() {
		Flags flags = new Flags();
		flags.add(Flags.Flag.DELETED);
		flags.add(Flags.Flag.SEEN);
		flags.add(Flags.Flag.ANSWERED);
		
		return flags;
	}

	public Flags getValidFlags() {
		Flags flags = new Flags();
		flags.add(Flags.Flag.DELETED);
		flags.add(Flags.Flag.SEEN);
		flags.add(Flags.Flag.ANSWERED);
		flags.add(Flags.Flag.FLAGGED);
		flags.add(Flags.Flag.DRAFT);
		
		return flags;
	}

	public String getName() {
		return name;
	}
}
