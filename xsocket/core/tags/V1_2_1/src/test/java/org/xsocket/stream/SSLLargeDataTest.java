// $Id: SSLTestDirect.java 1023 2007-03-16 16:27:41Z grro $
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
package org.xsocket.stream;


import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.nio.BufferUnderflowException;
import java.security.KeyStore;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import junit.framework.Assert;


import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.SSLTestContextFactory;
import org.xsocket.stream.IDataHandler;
import org.xsocket.stream.INonBlockingConnection;
import org.xsocket.stream.IConnection.FlushMode;



public final class SSLLargeDataTest {
	
	private static final Logger LOG = Logger.getLogger(SSLLargeDataTest.class.getName());
	
	
	private static final boolean SSL = true;
	private static SSLSocketFactory sslSocketFactory = null;
	
	
	@Test
	public void testSimple() throws Exception {
		
		//QAUtil.setLogLevel(Level.FINEST);

		
		// start server
		IMultithreadedServer server = null;
		ServerHandler srvHdl = new ServerHandler();
		if(SSL) {
			String keyStoreFile = SSLTestContextFactory.getTestKeyStoreFilename();
			String keyStorePass = SSLTestContextFactory.PASSWORD;
			server = new MultithreadedServer(0, srvHdl, true, createServerSSLContext(keyStoreFile, keyStorePass));
			
		} else {
			server = new MultithreadedServer(0, srvHdl);
		}
		StreamUtils.start(server);

		
		Assert.assertFalse(srvHdl.onConnectCalled);
		Assert.assertFalse(srvHdl.onDisconnectCalled);


		
		// run client
		//byte[] data = QAUtil.generateByteArray(120);
		//byte[] data = QAUtil.generateByteArray(1200);
		//byte[] data = QAUtil.generateByteArray(12000);
		byte[] data = QAUtil.generateByteArray(128000);
		data[0] = 124;
		
		Socket socket = createSocket(new InetSocketAddress("127.0.0.1", server.getLocalPort()), SSL);
		QAUtil.sleep(200);

		Assert.assertTrue(srvHdl.onConnectCalled);
		Assert.assertFalse(srvHdl.onDisconnectCalled);

		
		InputStream inputStream = socket.getInputStream();
		OutputStream outputStream = socket.getOutputStream();
		outputStream.write(data,0,data.length);
		outputStream.flush();
		
		QAUtil.sleep(1000);
		
		Assert.assertEquals(data.length, srvHdl.getReceivedDataSize());
		
		socket.close();
		QAUtil.sleep(200);
		
		Assert.assertTrue(srvHdl.onConnectCalled);
		Assert.assertTrue(srvHdl.onDisconnectCalled);

		
		server.close();
	}
	
	
	private static Socket createSocket(InetSocketAddress destination, boolean ssl) throws Exception {		
		java.net.Proxy proxy = createProxy(destination.getHostName(), destination.getPort());
		
		Socket socket = new Socket(proxy);
		socket.setKeepAlive(false);
		socket.connect(new InetSocketAddress(destination.getHostName(), destination.getPort()));
		if(ssl) {
			if (sslSocketFactory == null)
				sslSocketFactory = createClientSSLSocketFactory();
			
			socket = sslSocketFactory.createSocket(socket, destination.getHostName(), destination.getPort(), true);
			((SSLSocket) socket).startHandshake();
		}
		
		return socket;
	}
	
	private static java.net.Proxy createProxy(String host, int port) throws Exception {
		java.net.Proxy proxy = null;
		System.setProperty("java.net.useSystemProxies","true");
		ProxySelector ps = ProxySelector.getDefault();
		String uri = "socket://" + host + ":" + port;
		List<java.net.Proxy> l = ps.select(new URI(uri));
		if(l != null && l.size() > 0)
			proxy = l.get(0);

		return proxy;
	}
	
	
	
	private static SSLSocketFactory createClientSSLSocketFactory() {
		try {
			SSLContext sslContext = SSLContext.getInstance("SSLv3");

			KeyStore ks = KeyStore.getInstance("JKS");
			
			String keyStoreFile = SSLTestContextFactory.getTestKeyStoreFilename();
			InputStream is = new FileInputStream(keyStoreFile);

			ks.load(is, null);

			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(ks);

			TrustManager[] trustManagers = tmf.getTrustManagers();
			sslContext.init(null, trustManagers, null);
			
			return sslContext.getSocketFactory();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	
	private static SSLContext createServerSSLContext(String keyStoreFile, String keyStorePass) {
		try {
			SSLContext sslContext = SSLContext.getInstance("SSLv3");
			KeyStore ks = KeyStore.getInstance("JKS");
			ks.load(new FileInputStream(keyStoreFile),keyStorePass.toCharArray());

			KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(ks,keyStorePass.toCharArray());
	
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(ks);
			
			KeyManager[] keyManagers = kmf.getKeyManagers();
			TrustManager[] trustManagers = tmf.getTrustManagers();
			sslContext.init(keyManagers,trustManagers, null);
			
			return sslContext;
		} catch(Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	
	
	
	public static final class ServerHandler implements IConnectHandler, IDataHandler, IDisconnectHandler {
		
		private int receivedDataSize = 0;
		
		private boolean onConnectCalled = false;
		private boolean onDisconnectCalled = false;
		
		
		int getReceivedDataSize() {
			return receivedDataSize;
		}
		
		boolean isOnConnectCalled() {
			return onConnectCalled;
		}
		
		boolean isOnDisconnectCalled() {
			return onDisconnectCalled;
		}
		
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			onConnectCalled = true;
			
			connection.setFlushmode(FlushMode.ASYNC);
			System.out.println("onConnect");
			return true;
		}
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			int length = connection.getNumberOfAvailableBytes();
			connection.readBytesByLength(length);
			LOG.fine("onData read " + length + " bytes");
			
			receivedDataSize += length;
			return true;
		}
		
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			onDisconnectCalled = true;
			
			System.out.println("onDisconnect");
			return true;
		}
		
		
	}
	
	
	
	
	/*

	public static final class TestServer implements IConnectHandler, IDataHandler, IDisconnectHandler, Closeable {
		
		private MultithreadedServer server = null;
		
		private int receivedDataSize = 0;
		
		
		public void run() throws Exception {
			
			if(SSL) {
				
				String keyStoreFile = SSLTestContextFactory.getTestKeyStoreFilename();
				String keyStorePass = SSLTestContextFactory.PASSWORD;
				server = new MultithreadedServer(0, new TestServer(), true, createServerSSLContext(keyStoreFile, keyStorePass));
				
			} else {
				server = new MultithreadedServer(0, new TestServer());
			}
			
			StreamUtils.start(server);
		}

		
		public void close() throws IOException {
			server.close();
		}
		
		public int getPort() {
			return server.getLocalPort();
		}
		
		public int getReceivedDataSize() {
			return receivedDataSize;
		}
		
		


		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			//connection.setAutoflush(false);
	        //connection.setFlushmode(FlushMode.ASYNC);
			System.out.println("onConnect");
			return true;
		}

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			int length = connection.getNumberOfAvailableBytes();
			connection.readBytesByLength(length);
			LOG.fine("onData read " + length + " bytes");
			
			receivedDataSize += length;
			return true;
		}

		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			System.out.println("onDisconnect");
			return true;
		}
	}*/
}
