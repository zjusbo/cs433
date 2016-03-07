package org.xsocket.server.imap;

import java.io.IOException;

import org.xsocket.server.INonBlockingConnection;
import org.xsocket.server.handler.command.CommandName;





@CommandName("CLOSE")
public final class ImapCLOSECommand extends AbstractImapCommand {
	
	@Override
	public void execute(CommandContext ctx, INonBlockingConnection connection, String parameters, String tag, boolean useUid) throws IOException {
		ctx.setSelected(null);
		sendOKResponse(connection, tag, useUid, "CLOSE Completed");
	}
}
