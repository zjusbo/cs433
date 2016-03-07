package org.xsocket.server.imap;

import java.io.IOException;

import org.xsocket.server.INonBlockingConnection;


public abstract class AbstractImapCommand {
	
	public static final String LINE_DELIMITER = "" + '\r' + '\n';	
	
	 
	
	public abstract void execute(CommandContext ctx, INonBlockingConnection connection, String parameters, String tag, boolean useUid) throws IOException;
	
	
	protected static void sendIntermediateResponse(INonBlockingConnection connection, String msg) throws IOException {
		connection.writeWord("* " + msg);
		connection.writeWord(LINE_DELIMITER);
	}
	
	
	
	protected static void sendOKResponse(INonBlockingConnection connection, String tag, boolean useUID, String msg) throws IOException {
		if (useUID) {
			connection.writeWord(tag + " OK UID " + msg);
		} else {
			connection.writeWord(tag + " OK " + msg);
		}
		
		connection.writeWord(LINE_DELIMITER);
	}
	

	protected static void sendBADResponse(INonBlockingConnection connection, String tag, boolean useUID, String msg) throws IOException {
		if (useUID) {
			connection.writeWord(tag + " BAD UID " + msg);
		} else {
			connection.writeWord(tag + " BAD " + msg);
		}
		
		connection.writeWord(LINE_DELIMITER);
	}

	
	protected static void sendNOResponse(INonBlockingConnection connection, String tag, boolean useUID, String msg) throws IOException {
		if (useUID) {
			connection.writeWord(tag + " NO UID " + msg);
		} else {
			connection.writeWord(tag + " NO " + msg);
		}
		
		connection.writeWord(LINE_DELIMITER);
	}
}
