
from java.util import Timer
from java.util import TimerTask

from org.xsocket.stream import MultithreadedServer
from org.xsocket.stream import StreamUtils
from org.xsocket.stream import IConnection
from org.xsocket.stream import BlockingConnection
from org.xsocket.stream import StreamUtils
from org.xsocket.stream import NonBlockingConnection
from org.xsocket.stream import IConnectHandler
from org.xsocket.stream import IDisconnectHandler




class Handler(IConnectHandler, IDisconnectHandler):

    def __init__(self):
       self.timer = Timer(1)

    def onConnect(self, nbc):
       notifier = Notifier(nbc)
       self.timer.schedule(notifier, 500, 500);
       return 1

    def onDisconnect(self, nbc):
       self.timer.cancel();
       return 1


class Notifier(TimerTask):

    def __init__(self, nbc):
       self.nbc = nbc
    
    def run(self):       
       self.nbc.write('pong\r\n')



server = MultithreadedServer(0, Handler())
StreamUtils.start(server)

bc = BlockingConnection('localhost', server.localPort)

success = 0
for i in range(1, 5):
   serverMsg = bc.readStringByDelimiter('\r\n')
   

bc.close()
server.close()

print 'OK'
