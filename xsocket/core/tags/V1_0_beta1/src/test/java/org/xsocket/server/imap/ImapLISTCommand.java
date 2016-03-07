package org.xsocket.server.imap;

import java.io.IOException;
import java.util.Collection;


import org.xsocket.server.INonBlockingConnection;
import org.xsocket.server.handler.command.CommandName;
import org.xsocket.server.imap.store.ImapFolder;




@CommandName("LIST")
public final class ImapLISTCommand extends AbstractImapCommand {
	
	@Override
	public void execute(CommandContext ctx, INonBlockingConnection connection, String parameters, String tag, boolean useUid) throws IOException {
		
		
		String[] args = parameters.split(" ");
		String referenceName = args[0];
		String mailboxName = args[1];

		Collection<ImapFolder> mailboxes = ctx.getStore().getMailboxes(ctx.getUserId(), referenceName, mailboxName);
		for (ImapFolder mailbox : mailboxes) {
			StringBuilder attributes = new StringBuilder();
			if (!mailbox.hasInferiors()) {
				attributes.append("\\Noinferiors ");
			}
			sendIntermediateResponse(connection, "LIST (" + attributes.toString().trim() + ") \"/\" \"" + mailboxName + "\"");			
		}
	
		sendOKResponse(connection, tag, useUid, "LIST Completed");
	}
	
}
