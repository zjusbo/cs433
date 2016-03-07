
from org.xsocket.stream import MultithreadedServer
from org.xsocket.stream import StreamUtils
from org.xsocket.stream import IConnection
from org.xsocket.stream import BlockingConnection
from org.xsocket.stream import IDataHandler
from org.xsocket.stream import IConnectHandler
import java
import time





class ServerHandler(IConnectHandler, IDataHandler):

    def onConnect(self, nbc):
       self.nbc = nbc
       return 1

    def onData(self, nbc):
       response = nbc.readStringByDelimiter('\r\n\r\n\r\n') # cause buffer underflow exception
       return 1



serverHdl = ServerHandler()
server = MultithreadedServer(serverHdl)
StreamUtils.start(server)

client = BlockingConnection('localhost', server.localPort)
client.write('testdata')

time.sleep(1)

if serverHdl.nbc.isOpen():
   print 'OK'
else:
   print 'buffer underflow exception caused another exception'


client.close()
server.close()
