package org.xsocket.server.imap;

import java.io.IOException;

import org.xsocket.server.INonBlockingConnection;
import org.xsocket.server.handler.command.CommandName;


@CommandName("CAPABILITY")
public final class ImapCAPABILITYCommand extends AbstractImapCommand {
		
	@Override
	public void execute(CommandContext ctx, INonBlockingConnection connection, String parameters, String tag, boolean useUid) throws IOException {
		sendIntermediateResponse(connection, "CAPABILITY IMAP4 IMAP4rev1 LITERAL+ UIDPLUS X-NETSCAPE");
		sendOKResponse(connection, tag, useUid, "CAPABILITY completed");
	}
}
