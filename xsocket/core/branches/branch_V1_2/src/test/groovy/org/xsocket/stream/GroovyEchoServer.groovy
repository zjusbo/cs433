package org.xsocket.stream


import org.xsocket.stream.MultithreadedServer
import org.xsocket.stream.IConnection
import org.xsocket.stream.NonBlockingConnection
import org.xsocket.stream.IConnectHandler
import org.xsocket.stream.IDataHandler



class ServerHandler implements IDataHandler, IConnectHandler {


  public boolean onConnect(INonBlockingConnection nbc) throws IOException {
    nbc.flushmode = IConnection.FlushMode.ASYNC
    return true
  }


  public boolean onData(INonBlockingConnection nbc) throws IOException {
     def data = nbc.readAvailable()
     nbc.write(data)
     return true
  }
}


class FirstVisitThrottlingFilter implements IConnectHandler {

  def writeRate
  def knownIps = []

  FirstVisitThrottlingFilter(writeRate) {
     this.writeRate = writeRate
  }


  public boolean onConnect(INonBlockingConnection nbc) throws IOException {
     nbc.flushmode = IConnection.FlushMode.ASYNC

     def ipAddress = nbc.remoteAddress.hostAddress
     if (!knownIps.contains(ipAddress)) {
       knownIps.add(ipAddress)
       nbc.writeTransferRate = writeRate
     }

     return false  // false -> successor element in handler chain will be called (true -> chain processing will be terminated)
  }
}






def hdl = new ServerHandler()


// uncomment following code for using the first visit throttling filter
def firstVisitFilter = new FirstVisitThrottlingFilter(5)
def chain = new HandlerChain()
chain.addLast(firstVisitFilter)
chain.addLast(hdl)
hdl = chain


def server = new MultithreadedServer(hdl)
StreamUtils.start(server)


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
