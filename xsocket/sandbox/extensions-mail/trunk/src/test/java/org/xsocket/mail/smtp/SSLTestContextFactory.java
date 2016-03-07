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


import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.security.KeyStore;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
*
* @author grro@xsocket.org
*/
public final class SSLTestContextFactory {

	private static final Logger LOG = Logger.getLogger(SSLTestContextFactory.class.getName());
	
	public SSLTestContextFactory() {
	}
	
	public SSLContext getSSLContext() {
		
		try {
			String filename = null;
			URL keystoreUrl = SSLTestContextFactory.class.getResource("keystore.db");
System.out.println("keystoreUrl=" + keystoreUrl);			
			if (keystoreUrl != null) {
				filename = keystoreUrl.getFile();
			} else {
				filename = new File("src" + File.separator + "test" + File.separator 
						           + "resources" + File.separator + "org" + File.separator
						           + "xsocket" + File.separator + "mail" + File.separator
						           + "keystore.db").getAbsolutePath();
			}

System.out.println("filename=" + filename + " exist=" + new File(filename).exists());
			if (!new File(filename).exists()) {
				return null;
			}
			
			
			char[] passphrase = "secret".toCharArray();
			KeyStore ks = KeyStore.getInstance("JKS");
			ks.load(new FileInputStream(filename), passphrase);
	
			KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
			kmf.init(ks, passphrase);
			
			TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
			tmf.init(ks);
	
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

			return sslContext;

		} catch (Exception e) {
			return null;
		}
	}
}
