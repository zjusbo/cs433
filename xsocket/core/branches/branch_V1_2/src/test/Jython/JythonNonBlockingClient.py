
from org.xsocket.stream import IConnection
from org.xsocket.stream import NonBlockingConnection
from org.xsocket.stream import IDataHandler
from org.xsocket.stream import IConnectHandler
import time





class ClientHandler(IConnectHandler, IDataHandler):



    def onConnect(self, nbc):
       nbc.flushmode = IConnection.FlushMode.ASYNC  # for performance reasons (only required by writes, which will not be done in this example)
       self.isHandled = 0
       return 1

    def onData(self, nbc):
       if not self.isHandled:
           response = nbc.readStringByDelimiter('\r\n')
           self.isHandled = 1
           if 'OK' in response:
             print 'OK'
           else:
             print 'ERROR got ' + response
       return 1



nbc = NonBlockingConnection('www.web.de', 80, ClientHandler())
try:
   nbc.write('GET / HTTP1/1 \r\n\r\n')
   time.sleep(1)

finally:
  nbc.close()


