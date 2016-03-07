// $Id$
/*
 *  Copyright (c) xsocket.org, 2006. All rights reserved.
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
package org.xsocket.server;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.ObjectName;

import org.xsocket.IConnection;



public final class SmtpProtocolHandler implements IConnectHandler, IDataHandler, IConnectionScoped {

	private static final Logger LOG = Logger.getLogger(SmtpProtocolHandler.class.getName());

	private int state = 0;
	
	private String sender = null;
	private String recipient = null;
	
	private SmtpProtocolMonitor monitor = null;


	public SmtpProtocolHandler(String jmxPrefix, String name) {
		monitor = new SmtpProtocolMonitor(jmxPrefix, name);
	}
	
			
	public boolean onConnectionOpening(INonBlockingConnection connection) throws IOException {
		sendResponse("220 mail.example.com SMTP Testserver", connection);
		return false;
	}
	

	
	public boolean onData(INonBlockingConnection connection) throws IOException {
		switch (state) {
			case 2:
				LOG.fine("readRecord");
				ByteBuffer[] buffers = connection.readRecord("\r\n.\r\n");
				LOG.fine("donen");
				if (buffers != null) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + connection.getId() + "] data has been read");
					}
					handleData(buffers, connection);
				}
				break;

			default:
				LOG.fine("readWord");
				String command = connection.readWord("\r\n", "US-ASCII");
				LOG.fine("donen");
				if (command != null) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + connection.getId() + "] command has been read " + command);
					}
					handleComand(command, connection);
				}
				break;
		}
		
		return true;
	}
	
	
	private void handleComand(String cmd, INonBlockingConnection connection) throws IOException {
		String cmdAsUpper = cmd.toUpperCase(); 
		
		if (cmdAsUpper.startsWith("HELO")) {
			sendResponse("250 Testserver Hello " + connection.getRemoteAddress().getCanonicalHostName() + " pleased to meet you", connection);
			monitor.registeredExecuted("HELO");
			
		} else if (cmdAsUpper.startsWith("MAIL FROM:")) {
			sender = cmd.substring("MAIL FROM:".length() , cmd.length()).trim(); 
			sendResponse("250 Sender " + sender + " OK", connection);
			monitor.registeredExecuted("MAIL FROM:");
			
		} else if (cmdAsUpper.startsWith("RCPT TO:")) {
			recipient = cmd.substring("RCPT TO:".length() , cmd.length()).trim(); 
			sendResponse("250 Recipient " + sender + " OK", connection);
			monitor.registeredExecuted("RCPT TO:");

		} else if (cmdAsUpper.startsWith("DATA")) {
			state = 2;
			sendResponse("354 End data with <CR><LF>.<CR><LF>", connection);
			monitor.registeredExecuted("DATA");
			
		} else if (cmdAsUpper.startsWith("QUIT")) {
			connection.stopReceiving();
			sendResponse("221 See you later", connection);
			connection.close();
			monitor.registeredExecuted("QUIT");
			
		} else {
			sendResponse("502 Command '" + cmdAsUpper + "' not implemented", connection);
			monitor.registeredExecuted(cmdAsUpper);
		}


	}
	
	
	private void handleData(ByteBuffer[] bufs, IConnection connection) throws IOException {
		state = 0;
		//System.out.println(TextUtils.toString(bufs, "UTF-8"));
		System.out.print(".");
		sendResponse("250 Message accepted for delivery (" + recipient + ")", connection);
	}


	private void sendResponse(String s, IConnection connection) throws IOException {
		LOG.fine("writeResponse");
		connection.write(s + "   " + connection.getId() +  "; " + Thread.currentThread().getId() + " \r\n", "US-ASCII");
	}
	
	
	private static final class SmtpProtocolMonitor implements SmtpProtocolMonitorMBean {
		
		private Map<String, Integer> executed = new HashMap<String, Integer>();
		
		SmtpProtocolMonitor(String jmxPrefix, String name) {
	        try {
	        	ObjectName mbeanName = new ObjectName(jmxPrefix + ":type=ProtocolMonitor,name=" + name);
		    	ManagementFactory.getPlatformMBeanServer().registerMBean(this, mbeanName);
	        } catch (Exception mbe) {
	        	mbe.printStackTrace();
	        }
		}
		
		public String[] getExecutedCommands() {
			List<String> result = new ArrayList<String>();
			for (String cmd : executed.keySet()) {
				result.add(cmd + "  (" + executed.get(cmd) + " times)");
			}
			
			return result.toArray(new String[result.size()]);
		}
		
		public void registeredExecuted(String cmd) {
			if (executed.containsKey(cmd)) {
				int count = executed.get(cmd);
				count++;
				executed.put(cmd, count);
			} else {
				executed.put(cmd, 1);
			}
		}
	}
	
	
	
	/**
	 * @see Object
	 */
	public Object clone() throws CloneNotSupportedException {
		SmtpProtocolHandler clone = (SmtpProtocolHandler) super.clone();
		
		return clone;
	}

	
	public interface SmtpProtocolMonitorMBean {
		public String[] getExecutedCommands();
	}
}