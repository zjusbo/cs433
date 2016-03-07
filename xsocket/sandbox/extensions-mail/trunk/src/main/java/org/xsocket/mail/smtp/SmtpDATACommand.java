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
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;

import org.xsocket.DataConverter;
import org.xsocket.mail.CommandName;
import org.xsocket.mail.smtp.spi.ISmtpMessageSink;
import org.xsocket.mail.smtp.spi.SmtpRejectException;
import org.xsocket.stream.INonBlockingConnection;




/**
 * 
 * 
 * @author grro@xsocket.org
 */
@CommandName("DATA")
final class SmtpDATACommand extends SmtpCommand {

	private static final SimpleDateFormat STD_DATE = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");

	
	public SmtpDATACommand() {
		
	}
	
	
	@Override
	public void execute(INonBlockingConnection connection, ISmtpSession session, String argument) throws SmtpProtocolException, IOException {
		
		checkTrue(!session.isAuthenticationRequired(), 501, "authentification required");

		
		checkTrue(session.getSmtpReversePath() != null, 504, "error sender hasn't been set");
		checkTrue(session.getSmtpForwardPaths().size() > 0, 504, "error receiver hasn't been set");
		
		session.setDataConsumer(new DataReader(session));
		sendResponse(connection, "354 Start mail input; end with <CRLF>.<CRLF>");
	}
 
	
	private static final class DataReader implements IDataConsumer {
		
		private ISmtpSession session = null;
		private ISmtpMessageSink messageSink = null;
		
		 
		DataReader(ISmtpSession session) {
			this.session = session;
		}
		
		public void readData(INonBlockingConnection connection) throws SmtpProtocolException, IOException {

			try {
				if (messageSink == null) {
					// generated Message id
					session.generateReceiptNumber();
											
					// add leading receive line to message header
					String receivedString  = "Received: from " + connection.getRemoteAddress().getCanonicalHostName() 
					+ " (" + connection.getRemoteAddress().getCanonicalHostName() 
					+ " [" +connection.getRemoteAddress().getHostAddress() + "])"
					+ "\n by " + session.getLocaleHost() + " with ESMTP id " + session.getReceiptNumber();
								if (session.getSmtpForwardPaths().size() == 1) {
						receivedString = receivedString + "\n for " + session.getSmtpForwardPaths().get(0) + "; " + STD_DATE.format(System.currentTimeMillis()) + "\n";
					} else {
						receivedString = receivedString + "\n";
					}
					ByteBuffer receivedPart = DataConverter.toByteBuffer(receivedString, "ASCII");
			
					// get message sink
					messageSink = session.getMessageSinkService().newMessageSink(session);
					messageSink.write(receivedPart);
					
				} 
				
				
				// read received data and write it into messageSink
				boolean delimiterFound = connection.readAvailableByDelimiter(SmtpCommand.DATA_DELIMITER, messageSink);
				
				// if end of data reached -> reset receivers and sender and close messageSink
				if (delimiterFound) {
					messageSink.close();
					reset();
					
					sendResponse(connection, "250 message accept for delivery");
				}
				
			// did the message sink reject the message? 	
			} catch (SmtpRejectException re) {
				if (messageSink != null) {
					messageSink.delete();
				}
				reset();

				throw re;
			}
		}
		
		private void reset() {
			messageSink = null;
			session.setDataConsumer(null);
			session.clearMessageMetaData();
		}
	}
}
