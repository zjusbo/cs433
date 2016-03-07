package org.xsocket.server.imap;

import java.io.IOException;


import org.xsocket.server.INonBlockingConnection;
import org.xsocket.server.handler.command.CommandName;



@CommandName("LOGIN")
public final class ImapLOGINCommand extends AbstractImapCommand {
	
	@Override
	public void execute(CommandContext ctx, INonBlockingConnection connection, String parameters, String tag, boolean useUid) throws IOException {
		String userIdAndPassword = parameters;
		String[] args = userIdAndPassword.split(" ");
		
		if (args.length == 2) {
			ctx.setUserId(args[0]);
			sendOKResponse(connection, tag, useUid, "LOGIN completed: User " + args[0] + " logged in");
		} else {
			sendBADResponse(connection, tag, useUid, "LOGIN failed");
		}
	}
}
