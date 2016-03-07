
from org.xsocket.stream import Server
from org.xsocket.stream import StreamUtils
from org.xsocket.stream import HandlerChain
from org.xsocket.stream import IConnection
from org.xsocket.stream import BlockingConnection
from org.xsocket.stream import IDataHandler
from org.xsocket.stream import IConnectHandler
import java





class EchoHandler(IConnectHandler, IDataHandler):

    def onConnect(self, nbc):
       nbc.flushmode= IConnection.FlushMode.ASYNC
       return 1

    def onData(self, nbc):
       data = nbc.readAvailable()
       nbc.write(data)
       return 1


class FirstVisitThrottlingFilter(IConnectHandler):

    def __init__(self, writeRate):
       self.writeRate = writeRate
       self.knownIps = []


    def onConnect(self, nbc):
        nbc.flushmode = IConnection.FlushMode.ASYNC

        ipAddress = nbc.remoteAddress.hostAddress
        if not ipAddress in self.knownIps:
           self.knownIps.append(ipAddress)
           nbc.setWriteTransferRate(self.writeRate)

        return 0  # 0=false -> successor element in handler chain will be called (true -> chain processing will be terminated)



hdl = EchoHandler()


# uncomment following code for using the first visit throttling filter
# firstVisitFilter = FirstVisitThrottlingFilter(5)
# chain = HandlerChain()
# chain.addLast(firstVisitFilter)
# chain.addLast(hdl)
# hdl = chain





server = Server(hdl)
StreamUtils.start(server)


# check if the echo server works
bc = BlockingConnection('localhost', server.localPort)

request = 'testdata'
bc.write(request + '\r\n')
response = bc.readStringByDelimiter('\r\n')

if request == response:
   print 'OK'
else:
   print 'error. request ' + request + ' is not equals to response ' + response


bc.close()
server.close()
