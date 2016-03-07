package org.xsocket.mail.smtp;


import org.xsocket.QAUtil
import org.xsocket.JmxServer
import org.xsocket.stream.ServerMBeanProxyFactory
import org.xsocket.stream.Server
import org.xsocket.stream.StreamUtils
import org.xsocket.stream.BlockingConnection


class SmtpServerTest extends GroovyTestCase {
	
	
	def dummyMail =
		"Received: from localhost (localhost [127.0.0.1])\r\n"  +
		"by xsocket.org with ESMTP id 881588961.1153334034540.1900236652.1\r\n" +
		"for testi@xsocket.org; Mi, 19 Jul 2006 20:34:00 +0200\r\n" +
		"Message-ID: <24807938.01153334039898.JavaMail.test@127.0.0.1>\r\n"  + 
		"Date: Wed, 19 Jul 2006 20:33:59 +0200 (CEST)\r\n" +
		"From: testi <testi@xsocket.org>\r\n" +
		"To: Other one  <otherone@xsocket.org>\r\n" + 
		"Subject: Test mail\r\n" +
		"MIME-Version: 1.0\r\n" +
		"Content-Type: multipart/mixed;\r\n" + 
		"boundary=\"----=_Part_1_14867177.1153334039707\"\r\n" + 
		"\r\n" +
		"This is a multi-part message in MIME format.\r\n" +
		"------=_Part_1_14867177.1153334039707\r\n" + 
		"Content-Type: multipart/mixed;\r\n" +
		"boundary=\"----=_Part_0_14158819.1153334039687\"\r\n" + 
		"\r\n" +
		"------=_Part_0_14158819.1153334039687\r\n" + 
		"Content-Type: text/plain; charset=us-ascii\r\n" + 
		"Content-Transfer-Encoding: 7bit\r\n" +
		"\r\n" +
		"Halli Hallo\r\n" + 
		"------=_Part_0_14158819.1153334039687\r\n" + 
		"------=_Part_1_14867177.1153334039707--"
	
		
		

	

	void testSimple() {
		QAUtil.setLogLevel('FINE')
			
		def server = new Server(25, new SmtpHandler(new MailTransactionManager(), 'GServer', null), 50)
		StreamUtils.start(server)
		
		def jmxServer = new JmxServer();
		jmxServer.start("testmanagement");
		ServerMBeanProxyFactory.createAndRegister(server, "test");

		
		
		def bc = new BlockingConnection(server.getLocalAddress(), server.getLocalPort())
		def greeting = bc.readStringByDelimiter('\r\n')
		println "received " + greeting
		
		bc.write("Helo you\r\n")
		def heloResponse = bc.readStringByDelimiter('\r\n')
		println 'helo response ' + heloResponse
		
		bc.write("Mail From: testi@xsocket.org\r\n")
		def mailFromResponse = bc.readStringByDelimiter('\r\n')
		println 'mailFromResponse ' + mailFromResponse

		bc.write("Rcpt To: otherOne@xsocket.org\r\n")
		def rcptToResponse = bc.readStringByDelimiter('\r\n')
		println 'rcptToResponse ' + rcptToResponse
		
		bc.write("data\r\n")
		def dataResponse = bc.readStringByDelimiter('\r\n')
		println 'dataResponse ' + dataResponse
		
		bc.write(dummyMail + "\r\n.\r\n")
		def response = bc.readStringByDelimiter('\r\n')
		println 'delivery ' + response
		
		
		bc.write("quite\r\n")
		def quiteResponse = bc.readStringByDelimiter('\r\n')
		println 'quiteResponse ' + quiteResponse
		
		bc.close()
		server.close()		
  }
}
