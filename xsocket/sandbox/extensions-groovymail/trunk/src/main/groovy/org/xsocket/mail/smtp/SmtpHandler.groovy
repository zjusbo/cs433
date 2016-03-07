package org.xsocket.mail.smtp


import java.text.SimpleDateFormat
import java.nio.channels.WritableByteChannel

import javax.mail.internet.InternetAddress
import javax.mail.internet.AddressException

import org.xsocket.Resource
import org.xsocket.stream.IServerContext
import org.xsocket.stream.IConnection
import org.xsocket.DataConverter




class SmtpSession {
	def receivedMsg = 0
	def state = 'CMD'
    def isPolite = false
    def currentMailTransaction

}


class SmtpHandler {
	
	private static final CRLF = "\r\n"
	private static final STD_DATE = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z")
	
	def servername
	
	private def implVersion
	private def mailSink 
	private def spamChecker
	
	// statistics
	def countAcceptedMails = 0
	def lastMailTransctionInfo = 0
    
	SmtpHandler(mailSink, servername, spamChecker) {
		this.mailSink = mailSink
		this.servername = servername
		this.spamChecker = spamChecker
	}
  

	
    /////////// 
    // command handler definition
    
    private def helpHandler = { line, nbc, smtpSession ->  
    	nbc.write("214-This server supports the following commands: ${CRLF}")
    	def cmds = ""
    	cmdHandlers.keySet().each { cmd ->
    		cmds += cmd.split(" ")[0] + " "
        }
    	assert (cmds.length() < 50)
    	nbc.write("214 ${cmds} ${CRLF}")
	}


    private def heloHandler = { line, nbc, smtpSession ->  
    	smtpSession.isPolite = true;
    	nbc.write("250 " + servername + " SMTP Service ${CRLF}")
    }

    private def ehloHandler = { line, nbc, smptSession ->  
   		smtpSession.isPolite = true
		nbc.write("250 " + servername + " SMTP Service ${CRLF}")
	}

	
    private def rsetHandler = { line, nbc, smtpSession ->  
		smtpSession.currentMailTransaction = null
		nbc.write("250 Resetting ${CRLF}")
	}
	
    private def quitHandler = { line, nbc, smtpSession -> 
		nbc.write("221 SMTP service closing connection ${CRLF}")
        nbc.close()
    }
	
    private def mailFromHandler = { line, nbc, smtpSession ->
		if (!smtpSession.isPolite) {
   	 		nbc.write("550 HELO or EHLO required ${CRLF}")
    		return 
    	}

	 	if (smtpSession.currentMailTransaction) {
	 		nbc.write("503 Sender already given ${CRLF}")
	 		return
	 	}
	 
	    
	    def address = line.substring("MAIL FROM:".length(), line.length()).trim()
	    if (!isValid(address)) {
	    	nbc.write("501 invalid/unsupported address ${CRLF}")
	    	return
		}

	    smtpSession.currentMailTransaction = mailSink.newMailTransaction()
    	def error = smtpSession.currentMailTransaction.setOriginator(address)
	    if (error) {
	    	nbc.write(error)
	    } else {
	    	nbc.write("250 <" + address + "> is syntactically correct ${CRLF}")
	    }
	}

    
    private def rcptToHandler = { line, nbc, smtpSession -> 
	 	if (!smtpSession.currentMailTransaction) {
	 		nbc.write("503 No sender yet given ${CRLF}")
	 		return
	 	}
	 	
	    def address = line.substring("RCPT TO:".length(), line.length()).trim()
	    if (!isValid(address)) {
	    	nbc.write("501 invalid/unsupported address ${CRLF}")
	    	return
		}

	 	def error = smtpSession.currentMailTransaction.addRecipientAdr(address)
	    if (error) {
	    	nbc.write(error)
	    } else {
	    	nbc.write("250 <" + address + "> verified ${CRLF}")
	    }	 	
	}
	
    
    private def dataHandler = { line, nbc, smtpSession ->
	    if (!smtpSession.currentMailTransaction) {
	 		nbc.write("503 MAIL FROM command must precede DATA ${CRLF}")
	 		return	    	
	    }

	    if (smtpSession.currentMailTransaction.recipients.isEmpty()) {
	 		nbc.write("503 Valid RCPT TO <recipient> must precede DATA ${CRLF}")
	 		return	    	
	    }

	    smtpSession.state = 'Data'
	    smtpSession.receivedMsg++;
	    
	  	def receivedString  = "Received: from " + nbc.getRemoteAddress().getCanonicalHostName() + 
							  " (" + nbc.getRemoteAddress().getCanonicalHostName() + 
			  				  " [" + nbc.getRemoteAddress().getHostAddress() + "])" +
			  				  "\nby " + getServername() + " with ESMTP id " + nbc.getId() + "." + smtpSession.receivedMsg +
			  				  "\nfor " + smtpSession.currentMailTransaction.recipients.get(0) + "; " + STD_DATE.format(System.currentTimeMillis()) + "\n"

		smtpSession.currentMailTransaction.getMaildataStream(nbc.getId()).write(DataConverter.toByteBuffer(receivedString, "ASCII"))
	    nbc.write("354 Enter message, ending with \".\" on a line by itself  ${CRLF}")
    }
	
    
    // command handler
	private def cmdHandlers = ["HELO ":heloHandler, "MAIL FROM: ":mailFromHandler, "RCPT TO: ":rcptToHandler, "DATA":dataHandler, "QUIT":quitHandler, "RSET":rsetHandler, "HELP":helpHandler]

    
    
    
    
    
    ///////////
    // protocol handler's call back methods


    
    def onConnect(nbc) {
    	nbc.setFlushmode(IConnection.FlushMode.ASYNC)
    	nbc.setIdleTimeoutSec(30)
    	nbc.setDefaultEncoding('US-ASCII')
    	
    	nbc.attach(new SmtpSession())
    	nbc.write("220 " + servername + " ready ESMTP [gsmtp service] " + nbc.getId() + " ${CRLF}")
    }
    

    
    def onData(nbc) {
    	def smtpSession = nbc.attachment()
    	
    	if (smtpSession.state != 'CMD') {
    		def delimiterFound = nbc.readAvailableByDelimiter("${CRLF}.${CRLF}", smtpSession.currentMailTransaction.getMaildataStream(nbc.getId()));
    		if (delimiterFound) {
    			smtpSession.state = 'CMD'
    			smtpSession.currentMailTransaction.close()
    			
    			countAcceptedMails++
    			lastMailTransctionInfo = smtpSession.currentMailTransaction.getInfo()
    			
    			smtpSession.currentMailTransaction = mailSink.newMailTransaction()
    			nbc.write("250 message accept for delivery ${CRLF}")
    		}
    		return
    	}
    	
    	
		def line = nbc.readStringByDelimiter(CRLF)  // a BufferUnderflowException will been thrown, if delimiter hasn't been found
		def lineAsUpper = line.toUpperCase()
		
		if (spamChecker) {
			def response = spamChecker.onCommand(line, nbc, smtpSession);
			if (response) {
				nbc.write("${response} ${CRLF}")
				return
			}
		}
		
		for (cmdHandler in cmdHandlers) {
			if (lineAsUpper.startsWith(cmdHandler.key)) {
				cmdHandler.value(line, nbc, smtpSession)
				return
			}
		}

		nbc.write("500 Unrecognized command ${CRLF}")
    }    	
    
    
    def onIdleTimeout(nbc) {
    	nbc.write("421 SMTP command timeout - closing connection ${CRLF}")
    	nbc.close()
    }
    
    def onConnectionTimeout(nbc) {
    	nbc.write("421 SMTP command timeout - closing connection ${CRLF}")
    	nbc.close()
    }
    
    
    def getMailSinkInfo() {
    	return mailSink.getInfo()
    }
        
    private isValid(address) {
		try {
			new InternetAddress(address, true).validate()
			return true
		} catch (AddressException ex) {
			return false
		}
	}  
}  

  