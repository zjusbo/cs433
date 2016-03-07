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
package org.xsocket.mail.smtp;


import java.io.IOException;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.Resource;
import org.xsocket.mail.CommandLoader;
import org.xsocket.mail.smtp.management.SmtpProtocolHandlerMBean;
import org.xsocket.mail.smtp.spi.IQualtityOfServicesController;
import org.xsocket.mail.smtp.spi.ISmtpAuthenticatorService;
import org.xsocket.mail.smtp.spi.ISmtpMessageSinkService;
import org.xsocket.mail.smtp.spi.ISmtpProtocolInterceptor;
import org.xsocket.mail.smtp.spi.SmtpRejectException;
import org.xsocket.stream.IConnectHandler;
import org.xsocket.stream.IConnectionScoped;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.IDisconnectHandler;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.IServerContext;
import org.xsocket.stream.IConnection.FlushMode;





/**
 * 
 *
 * @author grro@xsocket.org
 */
public final class SmtpProtocolHandler implements IConnectHandler, IDataHandler, IConnectionScoped, IDisconnectHandler, ISmtpSession {
	
	private static final Logger LOG = Logger.getLogger(SmtpProtocolHandler.class.getName());

	
	private ISmtpProtocolInterceptor protocolInterceptor = null;
	private ISmtpMessageSinkService messageSinkService = null;
	private ISmtpAuthenticatorService authenicator = null;


	@Resource
	private IServerContext hdlCtx = null;

	
	// smtp commands 
	private IDataConsumer dataConsumer = null;
	private CommandLoader<SmtpCommand> cmdLoader = null;



	// session data
	private long receivingDate = System.currentTimeMillis();
	private InetAddress remoteHost = null;
	private int remotePort = 0;
	private InetAddress localeHost = null;
	private String connectionId = null;
	private int baseId = 1;
	private boolean isAuthenticated = false;
	
	private String receiptNumber = null;
	private String reversePath = null;
	private ArrayList<String> forwardPaths = new ArrayList<String>();

	
	/**
	 * protocol handler for non-authenticated smtp communication 
	 * 
	 * @param messageSinkService the message sink service
	 * @param authenicator the authenticator to use
	 */
	public SmtpProtocolHandler(ISmtpMessageSinkService messageSinkService) {
		this(messageSinkService, null, null);
	}
	
	/**
	 * protocol handler for authenticated smtp communication 
	 * 
	 * @param messageSinkService the message sink service
	 * @param authenicator the authenticator to use
	 */
	public SmtpProtocolHandler(ISmtpMessageSinkService messageSinkService, ISmtpAuthenticatorService authenicator, ISmtpProtocolInterceptor eventListener) {
		if (messageSinkService == null) {
			throw new NullPointerException("parameter messageSinkService has to be set");
		}

		this.messageSinkService = messageSinkService;
		this.authenicator = authenicator;
		this.protocolInterceptor = eventListener;

		// load smtp commands
		cmdLoader = new CommandLoader<SmtpCommand>(SmtpCommand.class.getPackage().getName(), SmtpCommand.class);
		cmdLoader.loadCommands();
		
		if (authenicator == null) {
			LOG.info("xSocket smtp handler initalized in non-authenticated mode. " + cmdLoader.getCommands().size() + " smtp commands loaded (" + getVersionIfo() + ")");
		} else {
			LOG.info("xSocket smtp handler initalized in authenticated mode. " + cmdLoader.getCommands().size() + " smtp commands loaded (" + getVersionIfo() + ")");
		}
	}
	

	
	public boolean onConnect(final INonBlockingConnection connection) throws IOException {
		connection.setAutoflush(false);
		connection.setFlushmode(FlushMode.ASYNC);

		try {
			connectionId = connection.getId();
			remoteHost = connection.getRemoteAddress();
			remotePort = connection.getRemotePort();
			localeHost = connection.getLocalAddress();
			
			String response = null;
			
			// interceptor handling
			if (protocolInterceptor != null) {
				response = protocolInterceptor.onConnect(this, new IQualtityOfServicesController() {
					public void updateReadDelayBytesPerSec(int delay) {
						try {
							connection.setWriteTransferRate(delay);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				});
			}
			
			if (response == null) {
				response = "220 " + getLocaleHost() + " SMTP Service ready (" + connection.getId() + ")";
			}
			
			connection.write(response + SmtpCommand.LINE_DELIMITER, "US-ASCII");
		} catch (SmtpRejectException smtpRecEx) {
			SmtpCommand.sendResponse(connection, smtpRecEx.getSmtpErrorCode() + " " + smtpRecEx.getMessage());
		}
	
		connection.flush();
		return true;
	}

	
	public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
		return true;
	}
	

	public boolean onData(final INonBlockingConnection connection) throws IOException {
		
		try {
			// if data reader set -> let the data read to the stuff
			if (dataConsumer != null) {
				dataConsumer.readData(connection);
				return true;
			}

			
			
			// look for the assigned command 
			String commandParameterString = connection.readStringByDelimiter(SmtpCommand.LINE_DELIMITER, Integer.MAX_VALUE);
			SmtpCommand commandObject = null;
			String arguments = "";
			
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("received "  + commandParameterString);
			}

			
			// interceptor handling
			String response = null;
			if (protocolInterceptor != null) {
				response = protocolInterceptor.onSmtpCommand(commandParameterString, this, new IQualtityOfServicesController() {
					public void updateReadDelayBytesPerSec(int delay) {
						try {
							connection.setWriteTransferRate(delay);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				});
			}
			if (response != null) {
				SmtpCommand.sendResponse(connection, response);
				return true;
			}
			

			
			for (String cmdName : cmdLoader.getCommands().keySet()) {
				if (commandParameterString.toUpperCase().startsWith(cmdName)) {
					commandObject = cmdLoader.getCommand(cmdName);
					arguments = commandParameterString.substring(cmdName.length(), commandParameterString.length()).trim();
					break;
				}
			}
			
			// execute command
			if (commandObject == null) {
				SmtpCommand.sendResponse(connection, "502 Command unrecognized"  + " (" + connection.getId() + ")");
			} else {
				commandObject.execute(connection, this, arguments);
			}

		} catch (BufferUnderflowException be) {
			// do nothing
			
		} catch (SmtpRejectException smtpRecEx) {
			SmtpCommand.sendResponse(connection, smtpRecEx.getSmtpErrorCode() + " " + smtpRecEx.getMessage());

		} catch (SmtpProtocolException smtpEx) {
			SmtpCommand.sendResponse(connection, smtpEx.getSmtpErrorCode() + " " + smtpEx.getMessage());
			
		} catch (Exception me) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("internal error occured " + me.toString());
			}
			SmtpCommand.sendResponse(connection, "554 internal Error occured");
			
		} finally {
			connection.flush();
		}

		return true;
	}
	
	
	
	
	private String getVersionIfo() {
		Package p = Package.getPackage("org.xsocket.server.handler.smtp");
		if (p != null) {
			return p.getSpecificationTitle() + " " + p.getImplementationVersion();
		} else {
			return "";
		}
	}
	
	
	public long getReceivingDate() {
		return receivingDate;
	}
			
	public String getReceiptNumber() {
		return receiptNumber;
	}
	
	public void generateReceiptNumber() {
		receiptNumber = connectionId + "." + (baseId++);
	}
	
	public ArrayList<String> getSmtpForwardPaths() {
		return forwardPaths;
	}
	
	public void setSmtpForwardPaths(ArrayList<String> forwardPaths) {
		this.forwardPaths = forwardPaths;
	}
		
	public void setRemoteHost(InetAddress smtpRemoteHost) {
		this.remoteHost = smtpRemoteHost;
	}
	
	public String getSmtpReversePath() {
		return reversePath;
	}
	
	public void setSmtpReversePath(String reversePath) {
		this.reversePath = reversePath;
	}

	public int getRemotePort() {
		return remotePort;
	}

	public void setRemotePort(int smtpRemotePort) {
		this.remotePort = smtpRemotePort;
	}

	public InetAddress getRemoteHost() {
		return remoteHost;
	}
	
	public InetAddress getLocaleHost() {
		return localeHost;
	}

	public ISmtpMessageSinkService getMessageSinkService() {
		return messageSinkService;
	}
	
	public ISmtpAuthenticatorService getAuthenicator() {
		return authenicator;
	}
	
	public boolean isAuthenticationRequired() {
		if (authenicator == null) {
			return false;
		} else {
			return !isAuthenticated;
		}
	}
	
	public void setAuthenticated(boolean b) {
		isAuthenticated = b;
	}
	
	public boolean isAuthenticated() {
		return isAuthenticated;
	}
	
	public void setDataConsumer(IDataConsumer dataConsumer) {
		this.dataConsumer = dataConsumer;
	}
	
	
	public SmtpEvent[] getSmtpEvents() {
		return null;
	}
	
	public String[] getSupportedComands() {
		Set<String> cmds = cmdLoader.getCommands().keySet();
		return cmds.toArray(new String[cmds.size()]);
	}
	
	public void clearMessageMetaData() {
		receivingDate = System.currentTimeMillis();
		receiptNumber = null;
		reversePath = null;
		forwardPaths = new ArrayList<String>();
	}
	
	
	@Override
	public Object clone() throws CloneNotSupportedException {
		SmtpProtocolHandler copy = (SmtpProtocolHandler) super.clone();
		copy.forwardPaths = new ArrayList<String>();
		return copy;
	}	

		
	private final class SmtpProtocolHanderManagement implements SmtpProtocolHandlerMBean {
		
		public String[] getLoadedCommands() {
			Set<String> cmdNames = cmdLoader.getCommands().keySet();
			return cmdNames.toArray(new String[cmdNames.size()]);
		}
	}
}