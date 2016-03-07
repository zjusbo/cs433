package org.xsocket.server.imap;

import java.util.Date;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;


import javax.mail.Folder;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import junit.framework.JUnit4TestAdapter;
import junit.textui.TestRunner;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;


import org.xsocket.server.MultithreadedServer;
import org.xsocket.server.imap.store.ImapFolder;
import org.xsocket.server.imap.store.ImapStore;




/**
*
* @author grro@xsocket.org
*/
public final class ImapServerTest {
	
	private static MultithreadedServer testServer = null;
	private static ImapProtocolHandler handler = null;
	private static int port = 9143;

	// workaround missing Junit4 support for maven 2 (see http://jira.codehaus.org/browse/SUREFIRE-31)
	public void setUp() throws Exception {
//	@BeforeClass static public void setUp() throws Exception {
		
		testServer = new MultithreadedServer(port, "MailTestSrv");
		testServer.setDispatcherPoolSize(3);
		testServer.setWorkerPoolSize(3);
		testServer.setReceiveBufferPreallocationSize(18);
		testServer.setIdleTimeout(10 * 60 * 1000);

		Session session = Session.getInstance(new Properties() ,null);
		MimeMessage message = new MimeMessage(session);
		message.setSentDate(new Date());
		message.setSubject("TestMail");
		message.setFrom(new InternetAddress("testi@semta.de"));
		message.setRecipients(MimeMessage.RecipientType.TO, new InternetAddress[] {new InternetAddress("testi1@semta.de"), new InternetAddress("testi2@semta.de")});
		message.setContent("this is a simple text mail", "Text/Plain");
		
		ImapFolder mb = new ImapFolder("INBOX");
		
		handler = new ImapProtocolHandler("MailTestSrv", new ImapStore(mb));
		testServer.setHandler(handler);
				
		Thread server = new Thread(testServer);
		server.start();
		
		do {
			try {
				Thread.sleep(250);
			} catch (InterruptedException ignore) { }
		} while (!testServer.isRunning());
	}
		
	
	// workaround missing Junit4 support for maven 2
	public void tearDown() {
//  @AfterClass static public void tearDown() {
		testServer.shutdown();
	}

	
	
	@Test public void testMixed() throws Exception {
		// workaround missing Junit4 support for maven 2
		setUp();

		Properties props = System.getProperties();
		Session session = Session.getInstance(props,null);
		
		Store store = session.getStore("imap");
		store.connect("127.0.0.1", port, "itsMe", "secret");
		
		Folder folder = store.getFolder("INBOX");
		Assert.assertTrue(folder.getName() == "INBOX");
		folder.open(Folder.READ_WRITE);
		
		folder.close(true);

		// workaround missing Junit4 support for maven 2
		tearDown();
	}
	
	
	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(ImapServerTest.class);
	}
		
	
	public static void main (String... args) {
		Logger logger = Logger.getLogger("org.xsocket");
		logger.setLevel(Level.FINE);
		ConsoleHandler hdl = new ConsoleHandler();
		hdl.setLevel(Level.FINE);
		logger.addHandler(hdl);
		
		TestRunner.run(suite());
	}	
}
