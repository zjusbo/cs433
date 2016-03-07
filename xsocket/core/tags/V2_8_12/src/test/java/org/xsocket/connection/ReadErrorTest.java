/*
 * Copyright (c) xlightweb.org, 2006 - 2010. All rights reserved.
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
package org.xsocket.connection;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.logging.Level;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;



import org.junit.Assert;
import org.junit.Test;


import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;

import sun.security.x509.CertAndKeyGen;
import sun.security.x509.X500Name;



/**
 *
 */
public final class ReadErrorTest {

	private static final String ETX = "\r\n";
	private static final String KEY_TYPE = "RSA";
	private static final String SIGNATURE_ALGORITHM = "MD5WithRSA";
	private static final String SSL_PROTOCOL = "TLS";
	private static final String KEY_MANAGER_ALGORITHM = "SunX509";
	private static final long ONE_YEAR = 60l*60l*24l*365l;


	private static final String SERVER_ALIAS = "server";
	private static final String CLIENT_ALIAS = "client";


	private static final char[] PASSWORD = "password".toCharArray();




	private SSLContext createSSLContext( KeyStore keystore, char[] password ) throws Exception {
		SSLContext ctx = SSLContext.getInstance( SSL_PROTOCOL );
		KeyManagerFactory kmf = KeyManagerFactory.getInstance( KEY_MANAGER_ALGORITHM );
		kmf.init( keystore, password );
		TrustManagerFactory tmf = TrustManagerFactory.getInstance(KEY_MANAGER_ALGORITHM);
		tmf.init(keystore);
		ctx.init( kmf.getKeyManagers(), tmf.getTrustManagers(), null );
		return ctx;
	}

	private KeyStore createKeystore(String alias, char[] password)
			throws KeyStoreException, NoSuchAlgorithmException,
			InvalidKeyException, IOException, CertificateException,
			SignatureException, NoSuchProviderException,
			CertificateExpiredException, CertificateNotYetValidException {
		// 1. generate self signed certificate
		CertAndKeyGen cakg = new CertAndKeyGen(KEY_TYPE, SIGNATURE_ALGORITHM);
		X509Certificate certificate = generateSelfSignedCertificate(cakg);

		// 2. save it in the store
		KeyStore keys = KeyStore.getInstance("JKS");
		keys.load( null, password );
		keys.setKeyEntry(alias, cakg.getPrivateKey(), password, new Certificate[] { certificate });
		return keys;
	}

	private X509Certificate generateSelfSignedCertificate(CertAndKeyGen cakg)
			throws InvalidKeyException, IOException, CertificateException,
			SignatureException, NoSuchAlgorithmException,
			NoSuchProviderException, CertificateExpiredException,
			CertificateNotYetValidException {
		cakg.generate(1024);
		X500Name name = new X500Name("one", "two", "three", "four", "five", "six" );
		X509Certificate certificate = cakg.getSelfCertificate(name, ONE_YEAR);
		certificate.checkValidity();
		return certificate;
	}


	@Test
	public void testSSLError() throws Exception {

		
		int port = 7443;

		KeyStore serverKeys = createKeystore(SERVER_ALIAS, PASSWORD);
		SSLContext serverCtx = createSSLContext(serverKeys, PASSWORD);

		ServerHandler serverHandler = new ServerHandler();
		Server server = new Server( port, serverHandler, serverCtx, true );
		server.start();  



		KeyStore clientKeys = createKeystore(CLIENT_ALIAS, PASSWORD);
		SSLContext clientCtx = createSSLContext(clientKeys, PASSWORD);


		try {
			new BlockingConnection( InetAddress.getLocalHost(), server.getLocalPort(), clientCtx, true );
			Assert.fail("ssl exception should have been thrown");
		} catch (Exception expected) {  }


		server.close();
	}




	private static final class ServerHandler implements IDataHandler {

		private String lastMessage = null;

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			lastMessage = connection.readStringByDelimiter( ETX );
			connection.write( "OK" );
			connection.write( ETX );

			return true;
		}


		String getLastMessage() {
			return lastMessage;
		}
	}
}
