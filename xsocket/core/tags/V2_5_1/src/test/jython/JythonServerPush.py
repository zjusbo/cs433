
from java.util import Timer
from java.util import TimerTask

from org.xsocket.connection import Server
from org.xsocket.connection import ConnectionUtils
from org.xsocket.connection import IConnection
from org.xsocket.connection import BlockingConnection
from org.xsocket.connection import NonBlockingConnection
from org.xsocket.connection import IConnectHandler
from org.xsocket.connection import IDisconnectHandler




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



server = Server(0, Handler())
ConnectionUtils.start(server)

bc = BlockingConnection('localhost', server.localPort)

success = 0
for i in range(1, 5):
   serverMsg = bc.readStringByDelimiter('\r\n')
   

bc.close()
server.close()

print 'OK'
