

package org.xsocket.mail.smtp


import java.nio.channels.WritableByteChannel


import org.xsocket.DataConverter
import org.xsocket.stream.Server
import org.xsocket.stream.StreamUtils




public class MailTransactionManager {
	
	private def storePath 
	
	MailTransactionManager() {
		def file = File.createTempFile("test", null)
		storePath = file.getParent()
		file.delete()
	}
	
	def newMailTransaction() {
		return new MailTransaction(storePath)
	}
	
	def getInfo() {
		def countMailfiles = 0
		
		new File(storePath).eachFileMatch(~/.*?\.mail/) { file -> countMailfiles++ }
		
		return storePath + " (" + countMailfiles + " mail files)"
	}
}   
    

class MailTransaction {
	  
	private static final CRLF = "\r\n"
	
	private def originator
	private def recipients = []
	
	private def fileChannel
	
	private def id 
	private def storePath
	private def startTime = System.currentTimeMillis()
	
	def info = ''
	
	MailTransaction(storePath) {
		this.storePath = storePath	
	}
	
	
	def setOriginator(originator) {
		// check id accepted
		
		this.originator = originator
		return null
	}
	
	def addRecipientAdr(addr) {
		// check id accepted
		
		recipients.add(addr)
		return null
	}
	
	def getRecipientAdrs() {
		return recipients
	}
	
	def getMaildataStream(id) {
		this.id = id
		
		if (!fileChannel) {
			def file = new File(storePath + File.separator + id + ".mail");
			fileChannel = new RandomAccessFile(file, "rw").getChannel();
			
			println "storing mail data into " + file.getAbsolutePath()
		}
		
		return fileChannel
	}
	
	def close() {
		info = 'elapsedTime=' + DataConverter.toFormatedDuration(System.currentTimeMillis() - startTime)
		info += ' mailId=' + id

		
		if (fileChannel) {
			fileChannel.close()
		}
	}
}