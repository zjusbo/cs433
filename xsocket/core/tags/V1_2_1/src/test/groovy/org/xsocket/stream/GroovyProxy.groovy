package org.xsocket.stream


import org.xsocket.stream.MultithreadedServer
import org.xsocket.stream.IConnection
import org.xsocket.stream.NonBlockingConnection
import org.xsocket.stream.IConnectHandler
import org.xsocket.stream.IDisconnectHandler
import org.xsocket.stream.IDataHandler



class ProxyHandler implements IDataHandler, IConnectHandler, IDisconnectHandler {

  def boolean onConnect(INonBlockingConnection nbc) throws IOException {
	nbc.flushmode = IConnection.FlushMode.ASYNC
    return true
  }

  def boolean onDisconnect(INonBlockingConnection nbc) throws IOException {
    if (nbc.attachment != null) {
    	nbc.attachment.close()
        nbc.attach(null)
    }
    return true
  }


  def boolean onData(INonBlockingConnection nbc) throws IOException {

       def data = nbc.readAvailable()
       nbc.attachment.write(data)

      return true
  }
}


class ClientToProxyHandler extends ProxyHandler {

	def forwardHost
	def forwardPort

	ClientToProxyHandler(forwardHost, forwardPort) {
       this.forwardHost = forwardHost
       this.forwardPort = forwardPort
	}

	def boolean onConnect(INonBlockingConnection clientToProxyConnection) throws IOException {
       super.onConnect(clientToProxyConnection)

       def proxyToServerConnection = new NonBlockingConnection(forwardHost, forwardPort, new ProxyHandler())
       clientToProxyConnection.attachment = proxyToServerConnection
       proxyToServerConnection.attachment = clientToProxyConnection

       return true
    }
}





def server = new MultithreadedServer(9998, new ClientToProxyHandler("www.web.de", 80))
StreamUtils.start(server)


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
