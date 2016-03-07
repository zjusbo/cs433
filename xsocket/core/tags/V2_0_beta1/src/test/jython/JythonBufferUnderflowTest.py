
from org.xsocket.connection import Server
from org.xsocket.connection import ConnectionUtils
from org.xsocket.connection import IConnection
from org.xsocket.connection import BlockingConnection
from org.xsocket.connection import IDataHandler
from org.xsocket.connection import IConnectHandler
import time





class ServerHandler(IConnectHandler, IDataHandler):

    def onConnect(self, nbc):
       self.nbc = nbc
       return 1

    def onData(self, nbc):
       response = nbc.readStringByDelimiter('\r\n\r\n\r\n') # cause buffer underflow exception
       return 1



serverHdl = ServerHandler()
server = Server(serverHdl)
ConnectionUtils.start(server)

client = BlockingConnection('localhost', server.localPort)
client.write('testdata')

time.sleep(1)

if serverHdl.nbc.isOpen():
   print 'OK'
else:
   print 'buffer underflow exception caused another exception'


client.close()
server.close()
