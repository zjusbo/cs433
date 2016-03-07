package org.xsocket.stream


import org.xsocket.stream.IConnection
import org.xsocket.stream.NonBlockingConnection
import org.xsocket.stream.IConnectHandler
import org.xsocket.stream.IDataHandler




class ClientHandler implements IDataHandler, IConnectHandler {

  def isHandled = false


  def boolean onConnect(INonBlockingConnection nbc) throws IOException {
    nbc.flushmode = IConnection.FlushMode.ASYNC
    return true
  }


  def boolean onData(INonBlockingConnection nbc) throws IOException {
    if (!isHandled) {
      def responseCode = nbc.readStringByDelimiter("\r\n")

      if (responseCode.contains('200')) {
         println 'OK'
      } else {
         println 'ERROR got ' + response
      }

      isHandled = true
    }
    return true
  }
}



def nbc = new NonBlockingConnection('www.web.de', 80, new ClientHandler())

try {
  nbc.write("GET / HTTP1/1 \r\n\r\n")

  // do somthing else
  try {
    Thread.sleep(1000)
  } catch (InterruptedException ignore) { }

} finally {
  nbc.close()
}

