// $Id: AbstractGetCommand.java 335 2006-10-16 06:10:05Z grro $

/*
 *  Copyright (c) xsocket.org, 2006 - 2007. All rights reserved.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Please refer to the LGPL license at: http://www.gnu.org/copyleft/lesser.txt
 * The latest copy of this software may be found on http://www.xsocket.org/
 */
package org.xsocket.mail.pop3;


import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;



import org.xsocket.Resource;
import org.xsocket.mail.CommandLoader;
import org.xsocket.mail.pop3.management.Pop3ProtocolHandlerMBean;
import org.xsocket.mail.pop3.spi.IPOP3AuthenticatorService;
import org.xsocket.mail.pop3.spi.IPOP3Maildrop;
import org.xsocket.mail.pop3.spi.IPOP3MaildropService;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.IConnectHandler;
import org.xsocket.stream.IConnectionScoped;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IServerContext;
import org.xsocket.stream.ITimeoutHandler;
import org.xsocket.stream.IConnection.FlushMode;


/**
 * 
 * 
 * @author grro@xsocket.org
 */
public final class POP3ProtocolHandler implements IConnectHandler, IDataHandler, ITimeoutHandler, IConnectionScoped {

	private static final Logger LOG = Logger.getLogger(POP3ProtocolHandler.class.getName());
	
	@Resource
	private IServerContext handlerContext;
	
	
	private CommandContext ctx =  null;
	
	private CommandLoader<AbstractPOP3Command> cmdLoader = null;
	

	public POP3ProtocolHandler(IPOP3MaildropService mailDropService, IPOP3AuthenticatorService authenticator) {
		
		if (mailDropService == null) {
			throw new NullPointerException("parameter mailDropService hast to be set");
		}
		if (authenticator == null) {
			throw new NullPointerException("parameter authenticator hast to be set");
		}
		
		ctx = new CommandContext(mailDropService, authenticator);
		
		cmdLoader = new CommandLoader<AbstractPOP3Command>(AbstractPOP3Command.class.getPackage().getName(), AbstractPOP3Command.class);
		cmdLoader.loadCommands();
		
		LOG.info("xSocket pop3 handler initalized. " + cmdLoader.getCommands().size() + " commands loaded (" + getVersionIfo() + ")");
	}


	public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
		connection.setAutoflush(false);
		connection.setFlushmode(FlushMode.ASYNC);

		AbstractPOP3Command.sendPositiveResponse(connection, "POP server ready");
		connection.flush();
		return true;
	}
	
	
	public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
		
		String request = connection.readStringByDelimiter(AbstractPOP3Command.LINE_DELIMITER, "US-ASCII", Integer.MAX_VALUE).trim();
		
		try {
			String[] elements = request.split(" ");
			if (elements.length > 0) {
				String[] arguments = new String[elements.length - 1];
				if (arguments.length > 0) {
					System.arraycopy(elements, 1, arguments, 0, arguments.length);
				}
				
				AbstractPOP3Command commandObject = cmdLoader.getCommand(elements[0].toUpperCase());
			
				if (commandObject != null) {
					try {
					
						commandObject.execute(ctx, connection, arguments);
					
					} catch (POP3ProtocolException popEx) {
						AbstractPOP3Command.sendNegativeResponse(connection, popEx.getMessage());
	
					} catch (Exception e) {
						LOG.warning("internal server error occured by handling request " + request + " (command: " + commandObject.getClass().getName() + "). Reason: " + e.toString());
						AbstractPOP3Command.sendNegativeResponse(connection, "internal server error");
					}
	
					return true;
				}
			}
			
			AbstractPOP3Command.sendNegativeResponse(connection, "unknown command " + request);;
	
			return true;
			
		} finally {
			connection.flush();
		}
	}
	

	public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
		IPOP3Maildrop maildrop = ctx.getMaildrop();
		if (maildrop != null) {
			maildrop.close();
		}
		connection.close();
		
		return true;
	}
	
	public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
		IPOP3Maildrop maildrop = ctx.getMaildrop();
		if (maildrop != null) {
			maildrop.close();
		}

		connection.close();		
		
		return true;
	}
	
	private String getVersionIfo() {
		Package p = Package.getPackage("org.xsocket.server.handler.pop3");
		if (p != null) {
			return p.getSpecificationTitle() + " " + p.getImplementationVersion();
		} else {
			return "";
		}
	}

	@Override
	public Object clone() throws CloneNotSupportedException {
		POP3ProtocolHandler copy = (POP3ProtocolHandler) super.clone();
		copy.ctx = (CommandContext) ctx.clone();
		return copy;
	}
	
	
	private static final class CommandContext implements ICommandContext, Cloneable {

		private ICommandContext.State state = ICommandContext.State.AUTHORIZATION;
		private IPOP3MaildropService mailDropService = null;
		private IPOP3AuthenticatorService authenticator = null;
		private String user = null; 
		private IPOP3Maildrop maildrop = null;
		private int nextFreeMsgNumber = 0;
		private Map<String, Integer> uidToNumberMap = new HashMap<String, Integer>();
		private Set<Integer> messagesToDelete = new HashSet<Integer>();

	
		public CommandContext(IPOP3MaildropService mailDropService, IPOP3AuthenticatorService authenticator) {
			this.mailDropService = mailDropService;
			this.authenticator = authenticator;
		}
		
		public State getState() {
			return state;
		}
		
		public void setState(State state) {
			this.state = state;
		}
		
		public IPOP3MaildropService getMaildropService() {
			return mailDropService;
		}
		
	
		public String getUser() {
			return user;
		}
	
		public void setUser(String user) {
			this.user = user;
		}
	
		public IPOP3AuthenticatorService getAuthenticator() {
			return authenticator;
		}
	
		public IPOP3Maildrop getMaildrop() {
			return maildrop;
		}
	
		public void setMaildrop(IPOP3Maildrop maildrop) {
			this.maildrop = maildrop;
		}
	
		public boolean addMessageNumberToDelete(Integer msgNumber) {
			return messagesToDelete.add(msgNumber);
		}
		
		public void clearMessageNumbersToDelete() {
			messagesToDelete.clear();
		}
		
		public Integer[] getMessageNumbersToDelete() {
			return messagesToDelete.toArray(new Integer[messagesToDelete.size()]);
		}
		
		public boolean isMessageNumberMarkedAsDeleted(Integer msgNumber) {
			return messagesToDelete.contains(msgNumber);
		}
		
		public String getMessageUid(Integer msgNumber) {
			for (String uid : uidToNumberMap.keySet()) {
				Integer number = uidToNumberMap.get(uid);
				if (number.equals(msgNumber)) {
					return uid;
				}
			}
			
			return null;
		}
	
		public Integer[] mapUidToMessageNumber(String[] uids) {
			Integer[] msgNumbers = new Integer[uids.length];
	
			for (int i = 0; i < uids.length; i++) {
				if (!uidToNumberMap.containsKey(uids[i])) {
					uidToNumberMap.put(uids[i], ++nextFreeMsgNumber);
				}
				msgNumbers[i] = uidToNumberMap.get(uids[i]);
			}
			
			return msgNumbers;
		}

		public void clear() {
			state = ICommandContext.State.AUTHORIZATION;
			user = null; 
			nextFreeMsgNumber = 0;
			uidToNumberMap = new HashMap<String, Integer>();
			messagesToDelete = new HashSet<Integer>();
		}
		
		@Override
		protected Object clone() throws CloneNotSupportedException {
			CommandContext copy = (CommandContext) super.clone();
			copy.clear();
			return copy;
		}
	}
	


	private final class Pop3ProtocolHanderManagement implements Pop3ProtocolHandlerMBean {
		
		public String[] getLoadedCommands() {
			Set<String> cmdNames = cmdLoader.getCommands().keySet();
			return cmdNames.toArray(new String[cmdNames.size()]);
		}
	}
}
