package org.xsocket.web.webdav


import groovy.xml.StreamingMarkupBuilder

import java.nio.channels.Channels
import java.nio.BufferUnderflowException

import org.xsocket.web.http.IMessageHandler
import org.xsocket.web.http.IHttpConnection 
import org.xsocket.web.http.INonBlockingReadableChannelHandler
import org.xsocket.web.http.INonBlockingReadableChannel
 

class WebDavProtocolHandler implements IMessageHandler {
	
	def ns = new groovy.xml.Namespace("DAV:", 'ns')
	def rootPath = new File("C:/temp").absolutePath  // remove this
	def fileStore = new FileStore("C:/webdav")
	
	/*
	http://www.cs.unibo.it/~fabio/webdav/WebDAV.pdf
	
	 */
	

	public void onMessageHeader(IHttpConnection con) throws BufferUnderflowException, IOException {
		
		// retrieve the header (sholud be always there -> onMessage event occured!)
		println 'reading header'
		def header = con.receiveMessageHeader()
		
		switch(header.getMethod()) {
		
			case "OPTIONS":
				onOptions(con, header)
				break
			
			case "PROPFIND":
				println 'propfind reading body'
				def messageBodyReadChannel = con.receiveMessageBody() // retrieve blocking(!) bodyReadChannel 
				onPropfind(con, header, messageBodyReadChannel)
				break
				
			case "GET":
				onGet(con, header)
				break
				
			case "POST":
				onPost(con, header)
				break

			case "PUT":
				onPut(con, header)
				break
	
			default:
				println header
				
		}
	}

	
	def onOptions(con, requestHeader)  {
		def responseHeader = con.createResponseHeader(200);
		responseHeader.addHeader("DAV", "1");
		responseHeader.addHeader("Allow", "GET,HEAD,POST,PUT,DELETE,OPTIONS,TRACE,PROPFIND,PRPATCH,MKCOL,COPY,MOVE");
		con.sendMessage(responseHeader);			
	} 
		
		
	def onPropfind(con, requestHeader, bodyReadChannel)  {
		
		
		def propFindRequest = new XmlParser().parse(Channels.newInputStream(bodyReadChannel))
		def requestedProps = [:]
		propFindRequest[ns.prop][0].each{ node -> requestedProps[node.name.localPart] = node.value }

		// send response header (and get non-blocking write body channel) 
		def bodyHandle = con.sendChunkedMessageHeader(con.createResponseHeader(207))
		
		// write the body data
		def depth = requestHeader.getHeader("DEPTH")
		def requestedUri = requestHeader.requestURI
		String properties = retrievePropInfoXml(requestedProps, requestedUri, depth)
		bodyHandle.write(properties);
		
		// close request (implicite flush & chunked: terminator will be written)
		bodyHandle.close();
	}
	
	       
	
	def retrievePropInfoXml(requestedProps, requestedUri, depth) {
		
		def xml = new StreamingMarkupBuilder().bind {
			mkp.declareNamespace(D:'DAV:')

			D.multistatus {

				def dir = new File(rootPath + requestedUri)
				
				def start = System.currentTimeMillis()
				dir.eachFile { file -> 
					D.response {
						D.href('http://www.fooe.bar/temp/'+ file.name)
					  	D.propstat() {
							D.prop() {
								D.creationdate()
								D.displayname()		
								if (file.isDirectory()) {
									D.resourcetype() {
										D.collection()
					    			}
									D.getcontentlength()
								} else {
									D.resourcetype()
									D.getcontentlength(file.length())
								}
							}	
							D.status('HTTP/1.1 200 OK')
						}		
					}
				}
				
				def elapsed = System.currentTimeMillis() - start
				println "${elapsed} millis" 
			}
		}
		
		return '<?xml version="1.0" encoding="utf-8" ?>' + xml.toString() 
	}
	
	
	def onGet(con, requestHeader)  {
		
		def file = new File(rootPath + requestHeader.requestURI)
		if (file.exists()) {		
			def bodyHandle = con.sendChunkedMessageHeader(con.createResponseHeader(200))
			def channel = new RandomAccessFile(file, "r").channel
			bodyHandle.transferFrom(channel)
			bodyHandle.close()
			channel.close()
			
		} else {
			con.sendMessage(con.createResponseHeader(404));
		}
	} 

	def onPost(con, requestHeader)  {
		
		if (requestHeader.contentLength > 0) {
			String body = con.receiveMessageBody().readStringByLength(requestHeader.contentLength)
			println body
		}
		
		con.sendMessage(con.createResponseHeader(404));
	}
	
	
	def onPut(con, requestHeader)  {
		
		println requestHeader
		
		if (requestHeader.contentLength > 0) {			
			def fileChannel = fileStore.createFile(requestHeader.requestURI)
			con.receiveMessageBody(new PutMessageHandler(con, fileChannel));
			
		} else {
			con.sendMessage(con.createResponseHeader(400));
		}
	}
}





class PutMessageHandler implements INonBlockingReadableChannelHandler {

	def con = null;
	def fileChannel = null
	
	PutMessageHandler(con, fileChannel) {
		this.con = con
		this.fileChannel = fileChannel
	}
	
	
	public void onData(INonBlockingReadableChannel channel) throws BufferUnderflowException, IOException {
		
		def transfered = channel.transferTo(fileChannel)
		if (channel.isRead()) {
			fileChannel.close()
			con.sendMessage(con.createResponseHeader(200))
		}
	}
}