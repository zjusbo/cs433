package org.xsocket.server.imap;

import java.io.IOException;

import org.xsocket.server.INonBlockingConnection;
import org.xsocket.server.handler.command.CommandName;


@CommandName("Unrecognized")
public final class ImapUnrecognizedCommand extends AbstractImapCommand {
	
	@Override
	public void execute(CommandContext ctx, INonBlockingConnection connection, String parameters, String tag, boolean useUid) throws IOException {
		sendBADResponse(connection, tag, useUid, "Command unrecognized");
	}
}
