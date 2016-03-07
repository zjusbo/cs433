package org.xsocket.connection


import org.xsocket.connection.Server
import org.xsocket.connection.IConnection
import org.xsocket.connection.NonBlockingConnection
import org.xsocket.connection.IConnectHandler
import org.xsocket.connection.IDataHandler
 


class ServerHandler implements IConnectHandler, IDataHandler {

 
  def boolean onConnect(INonBlockingConnection nbc) throws IOException {
    nbc.flushmode = IConnection.FlushMode.ASYNC
    return true
  }
 

  def boolean onData(INonBlockingConnection nbc) throws IOException {
     def data = nbc.readAvailableByteBuffer()
     nbc.write(data)
     return true
  } 
}



 
def server = new Server(new ServerHandler())
ConnectionUtils.start(server)


// check if the echo server works
def bc = new BlockingConnection('localhost', server.localPort)

def request = "testdata"
bc.write(request + "\r\n")
def response = bc.readStringByDelimiter("\r\n")

if (request == response) {
  println 'OK'
} else {
  println "error. request " + request + " is not equals to response " + response
}

bc.close()
server.close()
