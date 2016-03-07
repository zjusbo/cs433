
from org.xsocket.connection import Server
from org.xsocket.connection import ConnectionUtils
from org.xsocket.connection import IConnection
from org.xsocket.connection import BlockingConnection
from org.xsocket.connection import IDataHandler
from org.xsocket.connection import IConnectHandler





class EchoHandler(IConnectHandler, IDataHandler):

    def onConnect(self, nbc):
       nbc.flushmode= IConnection.FlushMode.ASYNC
       return 1

    def onData(self, nbc):
       data = nbc.readByteBufferByLength(nbc.available())
       nbc.write(data)
       return 1




server = Server(EchoHandler())
ConnectionUtils.start(server)


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
