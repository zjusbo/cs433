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

import org.xsocket.mail.CommandName;
import org.xsocket.stream.INonBlockingConnection;



/**
 * 
 * 
 * @author grro@xsocket.org
 */
@CommandName("AUTH LOGIN")
final class SmtpAUTHCommand extends SmtpCommand {

	public SmtpAUTHCommand() {
	}
	
	
	@Override
	public void execute(INonBlockingConnection connection, ISmtpSession session, String argument) throws IOException {
		if (!session.isAuthenticationRequired()) {
			sendResponse(connection, "503 " + " user is already authenticated");
			return;
		}

		
		LoginDataReader dr = new LoginDataReader(session);
		session.setDataConsumer(dr);
		dr.startSequence(connection);
	}

	
	
	private static final class LoginDataReader implements IDataConsumer {
		private ISmtpSession session = null;
		private String username = null;
		private String password = null;

		public LoginDataReader(ISmtpSession session) {
			this.session = session;
		}
		
		void startSequence(INonBlockingConnection connection) throws IOException {
			sendResponse(connection, "334 " + Base64.encodeBytes("Username:".getBytes()));
		}
		
		public void readData(INonBlockingConnection connection) throws SmtpProtocolException, IOException {
			String data = connection.readStringByDelimiter(LINE_DELIMITER, Integer.MAX_VALUE);
			
			// exit char?
			if (data.equals("*")) {
				session.setDataConsumer(null);
				sendResponse(connection, "501 cancel request of client received ");
				return;
			}
			
			String decoded = new String(Base64.decode(data));

			if (username == null) {
				username = decoded;
				sendResponse(connection, "334 " + Base64.encodeBytes("Password:".getBytes()));
				
			} else {
				session.setDataConsumer(null);
				password = decoded;
				
				// auth check
				session.getAuthenicator().authenticate(username, password);
				session.setAuthenticated(true);
				sendResponse(connection, "235 Authentication successful");
			}

			
		}
		
		public void terminate() { }
	}
}
