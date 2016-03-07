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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;



public final class FirstConnectionRefuser implements IConnectHandler {		

	private static final Logger LOG = Logger.getLogger(FirstConnectionRefuser.class.getName());

	
	private final List<String> knownHostnames = new ArrayList<String>();
	
	
	public boolean onConnectionOpening(INonBlockingConnection connection) throws IOException {
		String remoteHostname = connection.getRemoteAddress().getCanonicalHostName();
		if (knownHostnames.contains(remoteHostname)) {
			return false;
		} else {
			knownHostnames.add(remoteHostname);	
			LOG.fine("hostname " + remoteHostname + " is unknown.");
			connection.close();

			return true;
		}
	}
}

