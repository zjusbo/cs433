
from org.xsocket.stream import MultithreadedServer
from org.xsocket.stream import StreamUtils
from org.xsocket.stream import HandlerChain
from org.xsocket.stream import IConnection
from org.xsocket.stream import BlockingConnection
from org.xsocket.stream import StreamUtils
from org.xsocket.stream import NonBlockingConnection
from org.xsocket.stream import IDataHandler
from org.xsocket.stream import IConnectHandler
from org.xsocket.stream import IDisconnectHandler





class ProxyHandler(IDisconnectHandler, IDataHandler):

    def onDisconnect(self, nbc):
       if not nbc.attachment() is None:
           nbc.attachment().close()
           nbc.attach(None)
       return 1


    def onData(self, nbc):
       data = nbc.readAvailable()
       nbc.attachment().write(data)
       return 1


class ClientToProxyHandler(ProxyHandler, IConnectHandler):

    def __init__(self, forwardHost, forwardPort):
       self.forwardHost = forwardHost
       self.forwardPort = forwardPort


    def onConnect(self, clientToProxyConnection):
       clientToProxyConnection.flushmode = IConnection.FlushMode.ASYNC

       proxyToServerConnection = NonBlockingConnection(self.forwardHost, self.forwardPort, ProxyHandler())
       proxyToServerConnection.flushmode = IConnection.FlushMode.ASYNC
       proxyToServerConnection.attach(clientToProxyConnection)

       clientToProxyConnection.attach(proxyToServerConnection)
       return 1




server = MultithreadedServer(9988, ClientToProxyHandler("www.web.de", 80))
StreamUtils.start(server)


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
