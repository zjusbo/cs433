package org.xsocket.connection


import org.xsocket.connection.IConnection
import org.xsocket.connection.NonBlockingConnection
import org.xsocket.connection.IConnectHandler
import org.xsocket.connection.IDataHandler

 


class ClientHandler implements IDataHandler, IConnectHandler {

  def isHandled = false

  def Object waitGuard = new Object() 

  def boolean onConnect(INonBlockingConnection nbc) throws IOException {
    return true
  }


  def boolean onData(INonBlockingConnection nbc) throws IOException {
    if (!isHandled) {
      def responseCode = nbc.readStringByDelimiter("\r\n")

      if (responseCode.contains('HTTP')) {
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

nbc.write("GET / HTTP/1.1\r\nUser-Agent: me\r\nHost: www.web.de\r\n\r\n")
sleep(1000)
nbc.close()



