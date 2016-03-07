package org.xsocket.connection


import org.xsocket.connection.Server
import org.xsocket.connection.IConnection
import org.xsocket.connection.NonBlockingConnection
import org.xsocket.connection.IConnectHandler
import org.xsocket.connection.IDisconnectHandler
import org.xsocket.connection.IDataHandler
 

 
class ProxyHandler implements IDataHandler, IDisconnectHandler {

   def boolean onDisconnect(INonBlockingConnection nbc) throws IOException {
     if (nbc.attachment != null) {
        nbc.attachment.close()
        nbc.attachment = null
     }
     return true
  }


  def boolean onData(INonBlockingConnection nbc) throws IOException {

     def data = nbc.readByteBufferByLength(nbc.available())
     nbc.attachment.write(data)

     return true
  }
}


class ClientToProxyHandler extends ProxyHandler implements IConnectHandler {

	def forwardHost
	def forwardPort

	ClientToProxyHandler(forwardHost, forwardPort) {
       this.forwardHost = forwardHost
       this.forwardPort = forwardPort
	}

	def boolean onConnect(INonBlockingConnection clientToProxyConnection) throws IOException {
		clientToProxyConnection.flushmode = IConnection.FlushMode.ASYNC

        def proxyToServerConnection = new NonBlockingConnection(forwardHost, forwardPort, new ProxyHandler())
		proxyToServerConnection.flushmode = IConnection.FlushMode.ASYNC
        proxyToServerConnection.attachment = clientToProxyConnection

        clientToProxyConnection.attachment = proxyToServerConnection
        
        return true
    } 
}





def server = new Server(9998, new ClientToProxyHandler("www.gmx.de", 80))
ConnectionUtils.start(server)


def bc = new BlockingConnection('localhost', server.localPort)

try {
  bc.write("GET / HTTP1/1 \r\n\r\n")

  def response = bc.readStringByDelimiter("\r\n")
  if (response.contains('200')) {
    println 'OK'
  } else {
    println 'ERROR got ' + response
  }
} finally {
  bc.close()
  server.close()
}
