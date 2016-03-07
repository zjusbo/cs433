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


def nbc = new NonBlockingConnection('www.gmx.de', 80, new ClientHandler())

nbc.write("GET / HTTP1/1 \r\n\r\n")
sleep(1000)
nbc.close()



