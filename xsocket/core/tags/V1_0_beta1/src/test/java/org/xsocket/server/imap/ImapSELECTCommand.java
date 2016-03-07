package org.xsocket.server.imap;

import java.io.IOException;

import javax.mail.Flags;

import org.xsocket.server.INonBlockingConnection;
import org.xsocket.server.handler.command.CommandName;
import org.xsocket.server.imap.store.ImapFolder;



@CommandName("SELECT")
public final class ImapSELECTCommand extends AbstractImapCommand {
	
	@Override
	public void execute(CommandContext ctx, INonBlockingConnection connection, String parameters, String tag, boolean useUid) throws IOException {

		ImapFolder mailbox = ctx.getStore().getMailbox(ctx.getUserId(), parameters);
		ctx.setSelected(mailbox);
			
		sendIntermediateResponse(connection, mailbox.getMessageCount() + " EXISTS");
		sendIntermediateResponse(connection, "OK [UNSEEN " +  mailbox.getUnreadMessageCount() + "]");
			
		StringBuilder permanentFlags = new StringBuilder();
		Flags flags = mailbox.getPermanentFlags();
		for (Flags.Flag flag : flags.getSystemFlags()) {
			permanentFlags.append("\\" + printFlag(flag) + " ");
		}
		sendIntermediateResponse(connection, "OK [PERMANENTFLAGS (" + permanentFlags.toString() + ")]");
			
		sendIntermediateResponse(connection, "OK [UIDVALIDITY " + mailbox.getUIDValidity() + "]");
	
		StringBuilder validFlags = new StringBuilder();
		Flags vflags = mailbox.getValidFlags();
		for (Flags.Flag vflag : vflags.getSystemFlags()) {
			validFlags.append("/" + printFlag(vflag) + " ");
		}
			
		sendIntermediateResponse(connection, "FLAGS (" + validFlags.toString() + ")");
		
		sendOKResponse(connection, tag, useUid, "[READ-WRITE] SELECT Completed");
	}
	
	
	private String printFlag(Flags.Flag flag) {
		if (flag == Flags.Flag.ANSWERED) {
			return "Answered";
			
		} else if (flag == Flags.Flag.FLAGGED) {
			return "Flagged";

		} else if (flag == Flags.Flag.DELETED) {
			return "Deleted";

		} else if (flag == Flags.Flag.DRAFT) {
			return "Draft";

		} else if (flag == Flags.Flag.RECENT) {
			return "Recent";
			
		} else if (flag == Flags.Flag.SEEN) {
			return "Seen";
			
		} else if (flag == Flags.Flag.USER) {
			return "User";
			
		} else {
			return "??";
		}
		
	}
}
