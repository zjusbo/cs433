package org.xsocket.web.webdav


import groovy.xml.StreamingMarkupBuilder

import java.nio.channels.Channels
import java.nio.BufferUnderflowException

import org.xsocket.web.http.IMessageHandler
import org.xsocket.web.http.IHttpConnection 
import org.xsocket.web.http.INonBlockingReadableChannelHandler
import org.xsocket.web.http.INonBlockingReadableChannel
 

class FileStore {

	def rootdirname
	
	FileStore(rootdirname) {
		this.rootdirname = rootdirname
		
		def root = new File(rootdirname)
		if (!root.exists()) {
			root.mkdirs()
			println "${root.getAbsolutePath()} created"
		}
		println "store path is ${root.getAbsolutePath()}"
	}
	
	
	
	def createFile(filepath) {
		def file = new File(rootdirname + filepath)
		file.createNewFile() 
		def channel = new RandomAccessFile(file, "rw").channel
		
		return channel 
	}
}