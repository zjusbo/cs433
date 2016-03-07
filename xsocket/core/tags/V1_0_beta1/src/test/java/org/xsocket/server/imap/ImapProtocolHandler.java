package org.xsocket.server.imap;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.StringTokenizer;

import org.xsocket.server.IConnectHandler;
import org.xsocket.server.IConnectionScoped;
import org.xsocket.server.IDataHandler;
import org.xsocket.server.INonBlockingConnection;
import org.xsocket.server.ITimeoutHandler;
import org.xsocket.server.handler.command.CommandLoader;
import org.xsocket.server.imap.store.ImapStore;



public final class ImapProtocolHandler implements IConnectHandler, IDataHandler, ITimeoutHandler, IConnectionScoped {

	private CommandLoader<AbstractImapCommand> cmdLoader = null;
	private CommandContext ctx = null;
	
	
	public ImapProtocolHandler(String servername, ImapStore store) {
		this.ctx = new CommandContext(store);
		
		cmdLoader = new CommandLoader<AbstractImapCommand>(AbstractImapCommand.class.getPackage().getName(), AbstractImapCommand.class);
		cmdLoader.loadCommands();
	}
	
	public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
		connection.writeWord("* OK IMAP4rev1 server ready" + AbstractImapCommand.LINE_DELIMITER, "US-ASCII");
		return true;
	}
	
	
	public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
		String command = connection.readWord(AbstractImapCommand.LINE_DELIMITER);
		
		// parse tag
		int firstBlank = command.indexOf(' ');
		String tag = command.substring(0, firstBlank);
		String cmdWithArgs = command.substring(firstBlank + 1, command.length());

		// handle uid
		boolean usageUid = false;
		if (cmdWithArgs.toUpperCase().startsWith("UID")) {
			usageUid = true;
			cmdWithArgs = cmdWithArgs.substring(3, cmdWithArgs.length()).trim();
		}

		// parse commandString and arguments
		StringTokenizer st = new StringTokenizer(cmdWithArgs, " ");
		String cmd = st.nextToken().toUpperCase();
		String argument = "";
		if (st.hasMoreTokens()) {
			argument = cmdWithArgs.substring(cmd.length(), cmdWithArgs.length()).trim();
		}


		AbstractImapCommand commandObject = cmdLoader.getCommand(cmd);
		if (commandObject == null) {
			commandObject = new ImapUnrecognizedCommand();
		}

		commandObject.execute(ctx, connection, argument, tag, usageUid);
		return true;
	}
	

	public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
		connection.stopReceiving();
		connection.writeWord("* BYE connection time too long " + AbstractImapCommand.LINE_DELIMITER, "US-ASCII");
		connection.close();
		
		return true;
	}
	
	public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
		connection.stopReceiving();
		connection.writeWord("* BYE idle time too long " + AbstractImapCommand.LINE_DELIMITER, "US-ASCII");
		connection.close();		
		
		return true;
	}
	
	
	
	@Override
	public Object clone() throws CloneNotSupportedException {
		ImapProtocolHandler copy = (ImapProtocolHandler) super.clone();
		copy.ctx = new CommandContext(ctx.getStore());
		
		return copy;
	}
}
