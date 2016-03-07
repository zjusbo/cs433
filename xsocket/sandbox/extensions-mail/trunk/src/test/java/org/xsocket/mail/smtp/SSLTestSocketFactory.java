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
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

public final class SSLTestSocketFactory extends SSLSocketFactory {
	
	private SSLSocketFactory delegee = null;
	
	public SSLTestSocketFactory() {
		SSLContext ctx = new SSLTestContextFactory().getSSLContext();
		delegee = ctx.getSocketFactory();
	}
	
	@Override
	public Socket createSocket(InetAddress arg0, int arg1) throws IOException {
		return delegee.createSocket(arg0, arg1);
	}
	
	@Override
	public Socket createSocket(InetAddress arg0, int arg1, InetAddress arg2, int arg3) throws IOException {
		return delegee.createSocket(arg0, arg1, arg2, arg3);
	}
	
	
	@Override
	public Socket createSocket(Socket arg0, String arg1, int arg2, boolean arg3) throws IOException {
		return delegee.createSocket(arg0, arg1, arg2, arg3);
	}
	
	
	@Override
	public Socket createSocket(String arg0, int arg1) throws IOException, UnknownHostException {			
		return delegee.createSocket(arg0, arg1);
	}
	
	
	@Override
	public Socket createSocket(String arg0, int arg1, InetAddress arg2, int arg3) throws IOException, UnknownHostException {
		return delegee.createSocket(arg0, arg1, arg2, arg3);
	}
	
	@Override
	public String[] getDefaultCipherSuites() {
		return delegee.getDefaultCipherSuites();
	}
	
	@Override
	public String[] getSupportedCipherSuites() {
		return delegee.getSupportedCipherSuites();
	}
}