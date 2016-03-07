
from org.xsocket.connection import IConnection
from org.xsocket.connection import NonBlockingConnection
from org.xsocket.connection import IDataHandler
from org.xsocket.connection import IConnectHandler
import time





class ClientHandler(IConnectHandler, IDataHandler):



    def onConnect(self, nbc):
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



nbc = NonBlockingConnection('www.gmx.de', 80, ClientHandler())

nbc.write('GET / HTTP1/1 \r\n\r\n')
time.sleep(1)

nbc.close()


