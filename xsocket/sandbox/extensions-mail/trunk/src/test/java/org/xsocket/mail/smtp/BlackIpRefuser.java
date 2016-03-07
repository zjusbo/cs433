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
import java.util.logging.Logger;

import org.xsocket.stream.IConnectHandler;
import org.xsocket.stream.INonBlockingConnection;




public final class BlackIpRefuser implements IConnectHandler {
	
	private static final Logger LOG = Logger.getLogger(BlackIpRefuser.class.getName());

	
	private String[] hostnames = null;
		
	public BlackIpRefuser(String... hostnames) {
		this.hostnames = hostnames;
	}
		
		
	public boolean onConnect(INonBlockingConnection connection) throws IOException {
		for (String hostname : hostnames) {
			if (connection.getRemoteAddress().getCanonicalHostName().equals(hostname)) {
				LOG.fine("hostname " + hostname + " is black listed");
				connection.close();
				return true;
			}
		}
			
		return false;
	}
}
