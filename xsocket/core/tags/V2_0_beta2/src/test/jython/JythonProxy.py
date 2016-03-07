
from org.xsocket.connection import Server
from org.xsocket.connection import ConnectionUtils
from org.xsocket.connection import IConnection
from org.xsocket.connection import BlockingConnection
from org.xsocket.connection import NonBlockingConnection
from org.xsocket.connection import IDataHandler
from org.xsocket.connection import IConnectHandler
from org.xsocket.connection import IDisconnectHandler





class ProxyHandler(IDisconnectHandler, IDataHandler):

    def onDisconnect(self, nbc):
       if not nbc.attachment is None:
           nbc.attachment.close()
           nbc.attachment = None
       return 1


    def onData(self, nbc):
       data = nbc.readByteBufferByLength(nbc.available())
       nbc.attachment.write(data)
       return 1


class ClientToProxyHandler(IConnectHandler, ProxyHandler):

    def __init__(self, forwardHost, forwardPort):
       self.forwardHost = forwardHost
       self.forwardPort = forwardPort


    def onConnect(self, clientToProxyConnection):
       clientToProxyConnection.flushmode = IConnection.FlushMode.ASYNC
       
       proxyToServerConnection = NonBlockingConnection(self.forwardHost, self.forwardPort, ProxyHandler())
       proxyToServerConnection.flushmode = IConnection.FlushMode.ASYNC
       proxyToServerConnection.attachment = clientToProxyConnection

       clientToProxyConnection.attachment = proxyToServerConnection
       
       return 1




server = Server(9988, ClientToProxyHandler("www.web.de", 80))
ConnectionUtils.start(server)


# check if the proxy works
bc = BlockingConnection('localhost', server.localPort)
try:
   bc.write('GET / HTTP1/1 \r\n\r\n')
   response = bc.readStringByDelimiter('\r\n')

   if 'OK' in response:
      print 'OK'
   else:
      print 'ERROR got ' + response

finally:
   bc.close()
   server.close()
